package com.persons.finder.person.location.update

import com.persons.finder.person.model.CapturedAt
import com.persons.finder.person.model.ClientUpdateId
import com.persons.finder.person.model.GeoPoint
import com.persons.finder.person.model.LastKnownLocationProjection
import com.persons.finder.person.model.LocationObservation
import com.persons.finder.person.model.ObservationId
import com.persons.finder.person.model.ObservationSource
import com.persons.finder.person.model.PersonId
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Instant

data class UpdateLocationCommand(
    val personId: PersonId,
    val point: GeoPoint,
    val retryIdentity: RetryIdentity,
)

sealed interface RetryIdentity {
    data object NoKey : RetryIdentity

    data class ClientKey(
        val capturedAt: CapturedAt,
        val clientUpdateId: ClientUpdateId,
    ) : RetryIdentity
}

sealed interface UpdateLocationOutcome {
    data class Accepted(val result: LocationUpdateResult) : UpdateLocationOutcome

    data object PersonNotFound : UpdateLocationOutcome

    data object IdempotencyKeyReused : UpdateLocationOutcome

    data object CapturedAtTooFarInFuture : UpdateLocationOutcome
}

data class LocationUpdateResult(
    val personId: PersonId,
    val observationId: ObservationId,
    val capturedAt: Instant,
    val receivedAt: Instant,
    val lastKnownObservationId: ObservationId,
    val lastKnownLocationAt: Instant,
)

@Service
class UpdatePersonLocationService(
    private val repository: UpdatePersonLocationRepository,
    private val transactions: TransactionOperations,
    private val clock: Clock,
) {
    fun execute(command: UpdateLocationCommand): UpdateLocationOutcome =
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

        val receivedAt = clock.instant()
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

        val receivedAt = clock.instant()
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
