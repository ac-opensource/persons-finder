package com.persons.finder.person.nearby

import com.persons.finder.person.model.GeoPoint
import com.persons.finder.web.ProblemViolation
import com.persons.finder.web.RequestValidationException
import com.persons.finder.web.ViolationCode
import com.persons.finder.web.asApiTimestamp
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode

@RestController
@RequestMapping("/persons")
class FindNearbyController(
    private val findNearby: FindNearbyService,
) {
    @GetMapping("/nearby")
    @Operation(
        parameters = [
            Parameter(
                name = "lat",
                `in` = ParameterIn.QUERY,
                required = true,
                schema = Schema(type = "number", minimum = "-90", maximum = "90"),
            ),
            Parameter(
                name = "lon",
                `in` = ParameterIn.QUERY,
                required = true,
                schema = Schema(type = "number", minimum = "-180", maximum = "180"),
            ),
            Parameter(
                name = "radius",
                `in` = ParameterIn.QUERY,
                required = true,
                description = "Search radius in kilometres",
                schema = Schema(type = "number", exclusiveMinimum = true, minimum = "0", maximum = "100"),
            ),
        ],
    )
    fun find(request: HttpServletRequest): List<NearbyPersonResponse> =
        findNearby
            .execute(request.toFindNearbyQuery())
            .map(NearbyPerson::toResponse)
}

private fun HttpServletRequest.toFindNearbyQuery(): FindNearbyQuery {
    val violations =
        parameterMap.keys
            .filterNot(ALLOWED_QUERY_PARAMETERS::contains)
            .mapTo(mutableListOf()) { ProblemViolation(it, ViolationCode.UNKNOWN_FIELD) }

    val latitude = singleDouble("lat", violations)
    val longitude = singleDouble("lon", violations)
    val radius = singleDouble("radius", violations)

    if (latitude != null && (!latitude.isFinite() || latitude !in -90.0..90.0)) {
        violations += ProblemViolation("lat", ViolationCode.OUT_OF_RANGE)
    }
    if (longitude != null && (!longitude.isFinite() || longitude !in -180.0..180.0)) {
        violations += ProblemViolation("lon", ViolationCode.OUT_OF_RANGE)
    }
    if (radius != null && (!radius.isFinite() || radius <= 0.0 || radius > RadiusKm.MAXIMUM)) {
        violations += ProblemViolation("radius", ViolationCode.OUT_OF_RANGE)
    }
    if (violations.isNotEmpty()) {
        throw RequestValidationException(violations)
    }

    return FindNearbyQuery(
        origin = GeoPoint.from(latitude!!, longitude!!),
        radius = RadiusKm.from(radius!!),
    )
}

private fun HttpServletRequest.singleDouble(
    name: String,
    violations: MutableList<ProblemViolation>,
): Double? {
    val values = parameterMap[name]
    if (values == null) {
        violations += ProblemViolation(name, ViolationCode.REQUIRED)
        return null
    }
    if (values.size != 1 || values.single().isBlank()) {
        violations += ProblemViolation(name, ViolationCode.INVALID_FORMAT)
        return null
    }
    return values.single().toDoubleOrNull()
        ?: run {
            violations += ProblemViolation(name, ViolationCode.INVALID_FORMAT)
            null
        }
}

private fun NearbyPerson.toResponse(): NearbyPersonResponse =
    NearbyPersonResponse(
        id = id.value,
        name = profile.name,
        jobTitle = profile.jobTitle,
        hobbies = profile.hobbies,
        bio = bio,
        createdAt = createdAt.asApiTimestamp(),
        lastKnownLocationAt = lastKnownLocationAt.asApiTimestamp(),
        distanceKm =
            BigDecimal
                .valueOf(distanceKm)
                .setScale(1, RoundingMode.HALF_UP),
    )

private val ALLOWED_QUERY_PARAMETERS = setOf("lat", "lon", "radius")
