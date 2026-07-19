package com.persons.finder.application

import com.persons.finder.domain.model.LastKnownLocationProjection
import com.persons.finder.domain.model.LocationObservation
import com.persons.finder.domain.model.ObservationId
import com.persons.finder.domain.model.ObservationSource
import com.persons.finder.domain.model.PersonId
import com.persons.finder.domain.model.CapturedAt
import com.persons.finder.domain.model.toPostgresPrecision
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock

@Service
class CreatePersonService(
    private val repository: PersonRepository,
    private val bioGenerator: BioGenerator,
    private val bioPolicy: BioPolicy,
    private val transactions: TransactionOperations,
    private val clock: Clock,
) : CreatePersonUseCase {
    override fun execute(command: CreatePersonCommand): CreatePersonOutcome {
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

        val recordedAt = clock.instant().toPostgresPrecision()
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
            PersonResult(
                id = personId,
                profile = command.profile,
                bio = bio,
                createdAt = recordedAt,
                lastKnownLocationAt = recordedAt,
            ),
        )
    }
}

@Service
class UpdatePersonLocationService(
    private val repository: PersonRepository,
    private val transactions: TransactionOperations,
    private val clock: Clock,
) : UpdatePersonLocationUseCase {
    override fun execute(command: UpdateLocationCommand): UpdateLocationOutcome =
        transactions.execute {
            val current =
                repository.lockLastKnown(command.personId)
                    ?: return@execute UpdateLocationOutcome.PersonNotFound

            when (val retryIdentity = command.retryIdentity) {
                RetryIdentity.NoKey -> updateWithoutKey(command, current)
                is RetryIdentity.ClientKey -> updateWithKey(command, retryIdentity, current)
            }
        }

    private fun updateWithoutKey(
        command: UpdateLocationCommand,
        current: LastKnownLocationProjection,
    ): UpdateLocationOutcome {
        if (current.point == command.point) {
            return UpdateLocationOutcome.Accepted(current.asResult(current))
        }

        val receivedAt = clock.instant().toPostgresPrecision()
        val observation =
            LocationObservation.noKeyUpdate(
                id = ObservationId.new(),
                personId = command.personId,
                point = command.point,
                receivedAt = receivedAt,
            )
        return persistAndResolve(observation, current)
    }

    private fun updateWithKey(
        command: UpdateLocationCommand,
        retryIdentity: RetryIdentity.ClientKey,
        current: LastKnownLocationProjection,
    ): UpdateLocationOutcome {
        val existing =
            repository.findObservation(command.personId, retryIdentity.clientUpdateId)
        if (existing != null) {
            val identical =
                existing.point == command.point &&
                    existing.capturedAt == retryIdentity.capturedAt.value &&
                    (existing.source as? ObservationSource.ClientUpdate)?.id ==
                    retryIdentity.clientUpdateId
            return if (identical) {
                UpdateLocationOutcome.Accepted(existing.asResult(current))
            } else {
                UpdateLocationOutcome.IdempotencyKeyReused
            }
        }

        val receivedAt = clock.instant().toPostgresPrecision()
        if (retryIdentity.capturedAt.value.isAfter(receivedAt.plus(CapturedAt.MAXIMUM_FUTURE_SKEW))) {
            return UpdateLocationOutcome.CapturedAtTooFarInFuture
        }
        val observation =
            LocationObservation.clientUpdate(
                id = ObservationId.new(),
                personId = command.personId,
                point = command.point,
                capturedAt = retryIdentity.capturedAt,
                receivedAt = receivedAt,
                clientUpdateId = retryIdentity.clientUpdateId,
            )
        return persistAndResolve(observation, current)
    }

    private fun persistAndResolve(
        observation: LocationObservation,
        current: LastKnownLocationProjection,
    ): UpdateLocationOutcome {
        repository.insertObservation(observation)
        val resolved = current.advance(observation)
        if (resolved !== current) {
            repository.updateLastKnown(resolved)
        }
        return UpdateLocationOutcome.Accepted(observation.asResult(resolved))
    }
}

private fun LocationObservation.asResult(
    current: LastKnownLocationProjection,
): LocationUpdateResult =
    LocationUpdateResult(
        personId = personId,
        observationId = id,
        capturedAt = capturedAt,
        receivedAt = receivedAt,
        lastKnownObservationId = current.observationId,
        lastKnownLocationAt = current.capturedAt,
    )

private fun LastKnownLocationProjection.asResult(
    current: LastKnownLocationProjection,
): LocationUpdateResult =
    LocationUpdateResult(
        personId = personId,
        observationId = observationId,
        capturedAt = capturedAt,
        receivedAt = receivedAt,
        lastKnownObservationId = current.observationId,
        lastKnownLocationAt = current.capturedAt,
    )
