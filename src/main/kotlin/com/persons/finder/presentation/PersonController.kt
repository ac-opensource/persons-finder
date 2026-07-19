package com.persons.finder.presentation

import com.persons.finder.application.CreatePersonCommand
import com.persons.finder.application.CreatePersonOutcome
import com.persons.finder.application.CreatePersonUseCase
import com.persons.finder.application.RetryIdentity
import com.persons.finder.application.UpdateLocationCommand
import com.persons.finder.application.UpdateLocationOutcome
import com.persons.finder.application.UpdatePersonLocationUseCase
import com.persons.finder.domain.model.CapturedAt
import com.persons.finder.domain.model.ClientUpdateId
import com.persons.finder.domain.model.GeoPoint
import com.persons.finder.domain.model.PersonId
import com.persons.finder.domain.model.PersonProfile
import com.persons.finder.presentation.api.CreatePersonRequest
import com.persons.finder.presentation.api.LocationRequest
import com.persons.finder.presentation.api.PersonResponse
import com.persons.finder.presentation.api.ProblemViolation
import com.persons.finder.presentation.api.UpdatePersonLocationRequest
import com.persons.finder.presentation.api.UpdatePersonLocationResponse
import com.persons.finder.presentation.api.ViolationCode
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import java.util.UUID

@RestController
@RequestMapping("/persons")
class PersonController(
    private val createPerson: CreatePersonUseCase,
    private val updateLocation: UpdatePersonLocationUseCase,
) {
    @PostMapping
    fun create(
        @RequestBody request: CreatePersonRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<PersonResponse> {
        servletRequest.rejectQueryParameters()
        val command = request.toCommand()
        return when (val outcome = createPerson.execute(command)) {
            is CreatePersonOutcome.Created -> {
                val response = outcome.person.toResponse()
                ResponseEntity
                    .status(HttpStatus.CREATED)
                    .header(HttpHeaders.LOCATION, "/persons/${response.id}")
                    .body(response)
            }

            CreatePersonOutcome.BioCompositionDoesNotFit ->
                throw RequestValidationException(
                    listOf(ProblemViolation("hobbies", ViolationCode.TOO_LONG)),
                )

            CreatePersonOutcome.BioInputRejected -> throw BioInputRejectedApiException()
            CreatePersonOutcome.BioGenerationUnavailable ->
                throw BioGenerationUnavailableApiException()
        }
    }

    @PutMapping("/{id}/location")
    fun updateLocation(
        @PathVariable id: String,
        @RequestBody request: UpdatePersonLocationRequest,
        servletRequest: HttpServletRequest,
    ): UpdatePersonLocationResponse {
        servletRequest.rejectQueryParameters()
        val command = request.toCommand(id)
        return when (val outcome = updateLocation.execute(command)) {
            is UpdateLocationOutcome.Accepted ->
                UpdatePersonLocationResponse(
                    personId = outcome.result.personId.value,
                    observationId = outcome.result.observationId.value,
                    capturedAt = outcome.result.capturedAt.asApiTimestamp(),
                    receivedAt = outcome.result.receivedAt.asApiTimestamp(),
                    lastKnownObservationId = outcome.result.lastKnownObservationId.value,
                    lastKnownLocationAt = outcome.result.lastKnownLocationAt.asApiTimestamp(),
                )

            UpdateLocationOutcome.PersonNotFound -> throw PersonNotFoundApiException()
            UpdateLocationOutcome.IdempotencyKeyReused -> throw IdempotencyKeyReusedApiException()
            UpdateLocationOutcome.CapturedAtTooFarInFuture ->
                throw RequestValidationException(
                    listOf(ProblemViolation("capturedAt", ViolationCode.OUT_OF_RANGE)),
                )
        }
    }
}

private fun CreatePersonRequest.toCommand(): CreatePersonCommand {
    val violations = mutableListOf<ProblemViolation>()
    val requiredName = name ?: violations.required("name")
    val requiredJobTitle = jobTitle ?: violations.required("jobTitle")
    val requiredHobbies = hobbies ?: violations.required("hobbies")
    val requiredLocation = location ?: violations.required("location")
    if (violations.isNotEmpty()) {
        throw RequestValidationException(violations)
    }

    val profile =
        try {
            PersonProfile.create(
                name = requiredName!!,
                jobTitle = requiredJobTitle!!,
                hobbies = requiredHobbies!!,
            )
        } catch (exception: IllegalArgumentException) {
            throw RequestValidationException(
                listOf(
                    profileViolation(
                        exception.message,
                        requiredName!!,
                        requiredJobTitle!!,
                        requiredHobbies!!,
                    ),
                ),
            )
        }
    return CreatePersonCommand(
        profile = profile,
        initialLocation = requiredLocation!!.toPoint("location"),
    )
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
    val point = LocationRequest(latitude, longitude).toPoint("")

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

            else -> {
                val parsedCapturedAt =
                    try {
                        CapturedAt.parse(capturedAt)
                    } catch (_: IllegalArgumentException) {
                        throw RequestValidationException(
                            listOf(ProblemViolation("capturedAt", ViolationCode.INVALID_FORMAT)),
                        )
                    }
                val parsedClientUpdateId =
                    try {
                        ClientUpdateId.from(UUID.fromString(clientUpdateId))
                    } catch (_: IllegalArgumentException) {
                        throw RequestValidationException(
                            listOf(ProblemViolation("clientUpdateId", ViolationCode.INVALID_FORMAT)),
                        )
                    }
                RetryIdentity.ClientKey(parsedCapturedAt, parsedClientUpdateId)
            }
        }
    return UpdateLocationCommand(
        personId = personId,
        point = point,
        retryIdentity = retryIdentity,
    )
}

private fun LocationRequest.toPoint(prefix: String): GeoPoint {
    val violations = mutableListOf<ProblemViolation>()
    val latitudeField = if (prefix.isEmpty()) "latitude" else "$prefix.latitude"
    val longitudeField = if (prefix.isEmpty()) "longitude" else "$prefix.longitude"
    val requiredLatitude = latitude ?: violations.required(latitudeField)
    val requiredLongitude = longitude ?: violations.required(longitudeField)
    if (violations.isNotEmpty()) {
        throw RequestValidationException(violations)
    }
    return try {
        GeoPoint.from(requiredLatitude!!, requiredLongitude!!)
    } catch (_: IllegalArgumentException) {
        if (!requiredLatitude!!.isFinite() || requiredLatitude !in -90.0..90.0) {
            violations += ProblemViolation(latitudeField, ViolationCode.OUT_OF_RANGE)
        }
        if (!requiredLongitude!!.isFinite() || requiredLongitude !in -180.0..180.0) {
            violations += ProblemViolation(longitudeField, ViolationCode.OUT_OF_RANGE)
        }
        throw RequestValidationException(violations)
    }
}

private fun profileViolation(
    message: String?,
    name: String,
    jobTitle: String,
    hobbies: List<String>,
): ProblemViolation =
    when {
        hobbies.isEmpty() -> ProblemViolation("hobbies", ViolationCode.REQUIRED)
        hobbies.size > PersonProfile.MAX_HOBBIES ->
            ProblemViolation("hobbies", ViolationCode.TOO_MANY_ITEMS)

        name.codePointCount(0, name.length) > PersonProfile.MAX_NAME_CODE_POINTS ->
            ProblemViolation("name", ViolationCode.TOO_LONG)

        jobTitle.codePointCount(0, jobTitle.length) > PersonProfile.MAX_JOB_TITLE_CODE_POINTS ->
            ProblemViolation("jobTitle", ViolationCode.TOO_LONG)

        hobbies.any { it.codePointCount(0, it.length) > PersonProfile.MAX_HOBBY_CODE_POINTS } ->
            ProblemViolation("hobbies", ViolationCode.TOO_LONG)

        message?.contains("blank", ignoreCase = true) == true ->
            ProblemViolation("profile", ViolationCode.REQUIRED)

        else -> ProblemViolation("profile", ViolationCode.INVALID_FORMAT)
    }

private fun MutableList<ProblemViolation>.required(field: String): Nothing? {
    add(ProblemViolation(field, ViolationCode.REQUIRED))
    return null
}

private fun HttpServletRequest.rejectQueryParameters() {
    if (parameterMap.isNotEmpty()) {
        throw RequestValidationException(
            parameterMap.keys.map { ProblemViolation(it, ViolationCode.UNKNOWN_FIELD) },
        )
    }
}

private fun com.persons.finder.application.PersonResult.toResponse(): PersonResponse =
    PersonResponse(
        id = id.value,
        name = profile.name,
        jobTitle = profile.jobTitle,
        hobbies = profile.hobbies,
        bio = bio,
        createdAt = createdAt.asApiTimestamp(),
        lastKnownLocationAt = lastKnownLocationAt.asApiTimestamp(),
    )

private val API_TIMESTAMP_FORMATTER =
    DateTimeFormatterBuilder().appendInstant(3).toFormatter()

private fun Instant.asApiTimestamp(): String = API_TIMESTAMP_FORMATTER.format(this)
