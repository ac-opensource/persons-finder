package com.persons.finder.person.location.update

import com.persons.finder.person.model.CapturedAt
import com.persons.finder.person.model.ClientUpdateId
import com.persons.finder.person.model.PersonId
import com.persons.finder.web.IdempotencyKeyReusedApiException
import com.persons.finder.web.PersonNotFoundApiException
import com.persons.finder.web.ProblemViolation
import com.persons.finder.web.RequestValidationException
import com.persons.finder.web.ViolationCode
import com.persons.finder.web.asApiTimestamp
import com.persons.finder.web.rejectQueryParameters
import com.persons.finder.web.validatedGeoPoint
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/persons")
class UpdatePersonLocationController(
    private val updateLocation: UpdatePersonLocationService,
) {
    @PutMapping("/{id}/location")
    fun updateLocation(
        @PathVariable id: String,
        @RequestBody request: UpdatePersonLocationRequest,
        servletRequest: HttpServletRequest,
    ): UpdatePersonLocationResponse {
        servletRequest.rejectQueryParameters()
        val command = request.toCommand(id)
        return when (val outcome = updateLocation.execute(command)) {
            is UpdateLocationOutcome.Accepted -> outcome.result.toResponse()
            UpdateLocationOutcome.PersonNotFound -> throw PersonNotFoundApiException()
            UpdateLocationOutcome.IdempotencyKeyReused -> throw IdempotencyKeyReusedApiException()
            UpdateLocationOutcome.CapturedAtTooFarInFuture ->
                throw RequestValidationException(
                    listOf(ProblemViolation("capturedAt", ViolationCode.OUT_OF_RANGE)),
                )
        }
    }
}

private fun UpdatePersonLocationRequest.toCommand(pathId: String): UpdateLocationCommand {
    val personId =
        try {
            val parsed = UUID.fromString(pathId)
            require(parsed.toString().equals(pathId, ignoreCase = true))
            PersonId.from(parsed)
        } catch (_: IllegalArgumentException) {
            throw RequestValidationException(
                listOf(ProblemViolation("id", ViolationCode.INVALID_FORMAT)),
            )
        }

    val retryIdentity =
        when {
            capturedAt == null && clientUpdateId == null -> RetryIdentity.NoKey
            capturedAt == null ->
                throw RequestValidationException(
                    listOf(ProblemViolation("capturedAt", ViolationCode.REQUIRED)),
                )

            clientUpdateId == null ->
                throw RequestValidationException(
                    listOf(ProblemViolation("clientUpdateId", ViolationCode.REQUIRED)),
                )

            else ->
                RetryIdentity.ClientKey(
                    capturedAt =
                        try {
                            CapturedAt.parse(capturedAt)
                        } catch (_: IllegalArgumentException) {
                            throw RequestValidationException(
                                listOf(ProblemViolation("capturedAt", ViolationCode.INVALID_FORMAT)),
                            )
                        },
                    clientUpdateId =
                        try {
                            ClientUpdateId.from(UUID.fromString(clientUpdateId))
                        } catch (_: IllegalArgumentException) {
                            throw RequestValidationException(
                                listOf(ProblemViolation("clientUpdateId", ViolationCode.INVALID_FORMAT)),
                            )
                        },
                )
        }
    return UpdateLocationCommand(
        personId = personId,
        point = validatedGeoPoint(latitude, longitude),
        retryIdentity = retryIdentity,
    )
}

private fun LocationUpdateResult.toResponse(): UpdatePersonLocationResponse =
    UpdatePersonLocationResponse(
        personId = personId.value,
        observationId = observationId.value,
        capturedAt = capturedAt.asApiTimestamp(),
        receivedAt = receivedAt.asApiTimestamp(),
        lastKnownObservationId = lastKnownObservationId.value,
        lastKnownLocationAt = lastKnownLocationAt.asApiTimestamp(),
    )
