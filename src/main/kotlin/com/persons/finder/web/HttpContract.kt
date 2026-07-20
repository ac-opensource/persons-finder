package com.persons.finder.web

import com.persons.finder.person.model.GeoPoint
import jakarta.servlet.http.HttpServletRequest
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder

fun HttpServletRequest.rejectQueryParameters() {
    if (parameterMap.isNotEmpty()) {
        throw RequestValidationException(
            parameterMap.keys.map { ProblemViolation(it, ViolationCode.UNKNOWN_FIELD) },
        )
    }
}

fun validatedGeoPoint(
    latitude: Double?,
    longitude: Double?,
    prefix: String = "",
): GeoPoint {
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

fun MutableList<ProblemViolation>.required(field: String): Nothing? {
    add(ProblemViolation(field, ViolationCode.REQUIRED))
    return null
}

fun Instant.asApiTimestamp(): String = API_TIMESTAMP_FORMATTER.format(this)

private val API_TIMESTAMP_FORMATTER =
    DateTimeFormatterBuilder().appendInstant(3).toFormatter()
