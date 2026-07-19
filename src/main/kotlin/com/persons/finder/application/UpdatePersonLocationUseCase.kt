package com.persons.finder.application

import com.persons.finder.domain.model.CapturedAt
import com.persons.finder.domain.model.ClientUpdateId
import com.persons.finder.domain.model.GeoPoint
import com.persons.finder.domain.model.ObservationId
import com.persons.finder.domain.model.PersonId
import java.time.Instant

fun interface UpdatePersonLocationUseCase {
    fun execute(command: UpdateLocationCommand): UpdateLocationOutcome
}

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
