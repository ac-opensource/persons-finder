package com.persons.finder.person.create

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

data class CreatePersonCommand(
    val profile: PersonProfile,
    val initialLocation: GeoPoint,
)

sealed interface CreatePersonOutcome {
    data class Created(val person: CreatePersonResult) : CreatePersonOutcome

    data object BioCompositionDoesNotFit : CreatePersonOutcome

    data object BioInputRejected : CreatePersonOutcome

    data object BioGenerationUnavailable : CreatePersonOutcome
}

data class CreatePersonResult(
    val id: PersonId,
    val profile: PersonProfile,
    val bio: String,
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
                return CreatePersonOutcome.BioInputRejected
            }

        val generated =
            try {
                bioGenerator.generate(prepared.request)
            } catch (_: RuntimeException) {
                return CreatePersonOutcome.BioGenerationUnavailable
            }
        val template =
            when (generated) {
                is BioGenerationResult.Template -> generated.value
                is BioGenerationResult.Failure -> return CreatePersonOutcome.BioGenerationUnavailable
            }
        val bio =
            try {
                bioPolicy.compose(template, command.profile, prepared.selectedHobby)
            } catch (_: IllegalArgumentException) {
                return CreatePersonOutcome.BioGenerationUnavailable
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
