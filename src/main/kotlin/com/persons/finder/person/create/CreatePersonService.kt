package com.persons.finder.person.create

import com.persons.finder.person.bio.BioCompositionDoesNotFitException
import com.persons.finder.person.bio.BIO_GENERATION_DEADLINE
import com.persons.finder.person.bio.BioGenerationContext
import com.persons.finder.person.bio.BioGenerationDeadlineExceededException
import com.persons.finder.person.bio.BioGenerationFailure
import com.persons.finder.person.bio.BioGenerationResult
import com.persons.finder.person.bio.BioGenerator
import com.persons.finder.person.bio.BioPolicy
import com.persons.finder.person.bio.GeneratedBio
import com.persons.finder.person.bio.GeneratedBioTemplate
import com.persons.finder.person.bio.UnsafeBioInputException
import com.persons.finder.person.model.GeoPoint
import com.persons.finder.person.model.LastKnownLocationProjection
import com.persons.finder.person.model.LocationObservation
import com.persons.finder.person.model.ObservationId
import com.persons.finder.person.model.PersonId
import com.persons.finder.person.model.PersonProfile
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CancellationException

data class CreatePersonCommand(
    val profile: PersonProfile,
    val initialLocation: GeoPoint,
)

sealed interface CreatePersonOutcome {
    data class Created(val person: CreatePersonResult) : CreatePersonOutcome

    data object BioCompositionDoesNotFit : CreatePersonOutcome

    data object UnsafeBioInput : CreatePersonOutcome

    data class BioGenerationInvalid(val reason: BioGenerationFailure) : CreatePersonOutcome

    data class BioGenerationUnavailable(val reason: BioGenerationFailure) : CreatePersonOutcome
}

data class CreatePersonResult(
    val id: PersonId,
    val profile: PersonProfile,
    val bio: GeneratedBio,
    val createdAt: Instant,
    val lastKnownLocationAt: Instant,
)

@Service
class CreatePersonService(
    private val repository: CreatePersonRepository,
    private val bioGenerator: BioGenerator,
    private val bioPolicy: BioPolicy,
    private val transactions: TransactionOperations,
    private val clock: Clock,
) {
    fun execute(command: CreatePersonCommand): CreatePersonOutcome {
        val prepared =
            try {
                bioPolicy.prepare(command.profile)
            } catch (_: BioCompositionDoesNotFitException) {
                return CreatePersonOutcome.BioCompositionDoesNotFit
            } catch (_: UnsafeBioInputException) {
                return CreatePersonOutcome.UnsafeBioInput
            }

        val generationContext = BioGenerationContext.start(BIO_GENERATION_DEADLINE)
        val generated =
            try {
                bioGenerator.generate(prepared.request, generationContext)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                return BioGenerationFailure.UNAVAILABLE.toCreateOutcome()
            }
        val template =
            when (generated) {
                is BioGenerationResult.Template ->
                    when (
                        val revalidated =
                            GeneratedBioTemplate.validate(
                                generated.value.value,
                                command.profile.hobbies.size,
                            )
                    ) {
                        is BioGenerationResult.Template -> revalidated.value
                        is BioGenerationResult.Failure ->
                            return revalidated.reason.toCreateOutcome()
                    }

                is BioGenerationResult.Failure ->
                    return generated.reason.toCreateOutcome()
            }
        try {
            generationContext.requireRemaining()
        } catch (_: BioGenerationDeadlineExceededException) {
            return BioGenerationFailure.TIMEOUT.toCreateOutcome()
        }
        val bio =
            try {
                bioPolicy.compose(template, command.profile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                return BioGenerationFailure.INVALID_OUTPUT.toCreateOutcome()
            }

        val recordedAt = clock.instant()
        val personId = PersonId.new()
        val observation =
            LocationObservation.initial(
                id = ObservationId.new(),
                personId = personId,
                point = command.initialLocation,
                recordedAt = recordedAt,
            )
        val projection = LastKnownLocationProjection.from(observation)
        transactions.executeWithoutResult {
            repository.insertPerson(
                NewPerson(
                    id = personId,
                    profile = command.profile,
                    bio = bio,
                    createdAt = recordedAt,
                ),
            )
            repository.insertObservation(observation)
            repository.insertLastKnown(projection)
        }

        return CreatePersonOutcome.Created(
            CreatePersonResult(
                id = personId,
                profile = command.profile,
                bio = bio,
                createdAt = recordedAt,
                lastKnownLocationAt = recordedAt,
            ),
        )
    }
}

/**
 * The application-owned mapping from normalized generator failures to use-case
 * outcomes. HTTP adapters never inspect provider-specific failures.
 */
private fun BioGenerationFailure.toCreateOutcome(): CreatePersonOutcome =
    when (this) {
        BioGenerationFailure.INVALID_OUTPUT,
        BioGenerationFailure.POLICY_REJECTED,
        -> CreatePersonOutcome.BioGenerationInvalid(this)

        BioGenerationFailure.TIMEOUT,
        BioGenerationFailure.RATE_LIMITED,
        BioGenerationFailure.UNAVAILABLE,
        -> CreatePersonOutcome.BioGenerationUnavailable(this)
    }
