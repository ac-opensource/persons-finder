package com.persons.finder.person.create

import com.persons.finder.person.model.PersonProfile
import com.persons.finder.person.model.ProfileValidationException
import com.persons.finder.person.model.ProfileValidationField
import com.persons.finder.person.model.ProfileValidationReason
import com.persons.finder.web.BioGenerationInvalidApiException
import com.persons.finder.web.BioGenerationUnavailableApiException
import com.persons.finder.web.ProblemViolation
import com.persons.finder.web.RequestValidationException
import com.persons.finder.web.UnsafeBioInputApiException
import com.persons.finder.web.ViolationCode
import com.persons.finder.web.asApiTimestamp
import com.persons.finder.web.rejectQueryParameters
import com.persons.finder.web.required
import com.persons.finder.web.validatedGeoPoint
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/persons")
class CreatePersonController(
    private val createPerson: CreatePersonService,
) {
    @PostMapping
    fun create(
        @RequestBody request: CreatePersonRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<CreatePersonResponse> {
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

            CreatePersonOutcome.UnsafeBioInput -> throw UnsafeBioInputApiException()
            is CreatePersonOutcome.BioGenerationInvalid -> throw BioGenerationInvalidApiException()
            is CreatePersonOutcome.BioGenerationUnavailable ->
                throw BioGenerationUnavailableApiException()
        }
    }
}

private fun CreatePersonRequest.toCommand(): CreatePersonCommand {
    val violations = mutableListOf<ProblemViolation>()
    val requiredName = name ?: violations.required("name")
    val requiredJobTitle = jobTitle ?: violations.required("jobTitle")
    val requiredHobbies = hobbies ?: violations.required("hobbies")
    val requiredLocation = location ?: violations.required("location")
    if (requiredHobbies?.any { it == null } == true) {
        violations += ProblemViolation("hobbies", ViolationCode.INVALID_TYPE)
    }
    if (violations.isNotEmpty()) {
        throw RequestValidationException(violations)
    }

    val profile =
        try {
            PersonProfile.create(
                name = requiredName!!,
                jobTitle = requiredJobTitle!!,
                hobbies = requiredHobbies!!.filterNotNull(),
            )
        } catch (exception: ProfileValidationException) {
            throw RequestValidationException(
                listOf(exception.toProblemViolation()),
            )
        }
    return CreatePersonCommand(
        profile = profile,
        initialLocation =
            validatedGeoPoint(
                latitude = requiredLocation!!.latitude,
                longitude = requiredLocation.longitude,
                prefix = "location",
            ),
    )
}

private fun ProfileValidationException.toProblemViolation(): ProblemViolation =
    ProblemViolation(
        field =
            when (field) {
                ProfileValidationField.PROFILE -> "profile"
                ProfileValidationField.NAME -> "name"
                ProfileValidationField.JOB_TITLE -> "jobTitle"
                ProfileValidationField.HOBBIES -> "hobbies"
            },
        code =
            when (reason) {
                ProfileValidationReason.REQUIRED -> ViolationCode.REQUIRED
                ProfileValidationReason.INVALID_FORMAT -> ViolationCode.INVALID_FORMAT
                ProfileValidationReason.TOO_LONG -> ViolationCode.TOO_LONG
                ProfileValidationReason.TOO_MANY_ITEMS -> ViolationCode.TOO_MANY_ITEMS
            },
    )

private fun CreatePersonResult.toResponse(): CreatePersonResponse =
    CreatePersonResponse(
        id = id.value,
        name = profile.name,
        jobTitle = profile.jobTitle,
        hobbies = profile.hobbies,
        bio = bio.value,
        createdAt = createdAt.asApiTimestamp(),
        lastKnownLocationAt = lastKnownLocationAt.asApiTimestamp(),
    )
