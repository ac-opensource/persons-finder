package com.persons.finder.person.model

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class GeoPoint private constructor(
    val latitude: Double,
    val longitude: Double,
) {
    override fun equals(other: Any?): Boolean =
        other is GeoPoint &&
            latitude == other.latitude &&
            longitude == other.longitude

    override fun hashCode(): Int {
        var result = latitude.hashCode()
        result = 31 * result + longitude.hashCode()
        return result
    }

    companion object {
        fun from(
            latitude: Double,
            longitude: Double,
        ): GeoPoint {
            require(latitude.isFinite() && longitude.isFinite()) {
                "Coordinates must be finite"
            }
            require(latitude in -90.0..90.0) { "Latitude is outside its valid range" }
            require(longitude in -180.0..180.0) { "Longitude is outside its valid range" }

            val canonicalLatitude = latitude.positiveZero()
            val canonicalLongitude =
                when {
                    canonicalLatitude == -90.0 || canonicalLatitude == 90.0 -> 0.0
                    longitude == 180.0 -> -180.0
                    else -> longitude.positiveZero()
                }

            return GeoPoint(canonicalLatitude, canonicalLongitude)
        }
    }
}

@JvmInline
value class CapturedAt private constructor(val value: Instant) {
    companion object {
        val MAXIMUM_FUTURE_SKEW: Duration = Duration.ofMinutes(5)

        fun parse(value: String): CapturedAt {
            require(CAPTURED_AT_PATTERN.matches(value)) {
                "capturedAt must use the restricted RFC 3339 format"
            }

            val parsed =
                runCatching {
                    OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
                }.getOrElse {
                    throw IllegalArgumentException("capturedAt is not a valid timestamp", it)
                }
            val normalized = parsed.toMilliseconds()
            return CapturedAt(normalized)
        }

        fun fromStored(value: Instant): CapturedAt = CapturedAt(value.toMilliseconds())
    }
}

sealed interface ObservationSource {
    data object Initial : ObservationSource

    data object NoKeyUpdate : ObservationSource

    data class ClientUpdate(val id: ClientUpdateId) : ObservationSource
}

class LocationObservation private constructor(
    val id: ObservationId,
    val personId: PersonId,
    val point: GeoPoint,
    val capturedAt: Instant,
    val receivedAt: Instant,
    val source: ObservationSource,
) : Comparable<LocationObservation> {
    override fun compareTo(other: LocationObservation): Int =
        compareObservationOrder(
            capturedAt = capturedAt,
            receivedAt = receivedAt,
            observationId = id,
            otherCapturedAt = other.capturedAt,
            otherReceivedAt = other.receivedAt,
            otherObservationId = other.id,
        )

    companion object {
        fun initial(
            id: ObservationId,
            personId: PersonId,
            point: GeoPoint,
            recordedAt: Instant,
        ): LocationObservation {
            return LocationObservation(
                id = id,
                personId = personId,
                point = point,
                capturedAt = recordedAt,
                receivedAt = recordedAt,
                source = ObservationSource.Initial,
            )
        }

        fun noKeyUpdate(
            id: ObservationId,
            personId: PersonId,
            point: GeoPoint,
            receivedAt: Instant,
        ): LocationObservation =
            LocationObservation(
                id = id,
                personId = personId,
                point = point,
                capturedAt = receivedAt,
                receivedAt = receivedAt,
                source = ObservationSource.NoKeyUpdate,
            )

        fun clientUpdate(
            id: ObservationId,
            personId: PersonId,
            point: GeoPoint,
            capturedAt: CapturedAt,
            receivedAt: Instant,
            clientUpdateId: ClientUpdateId,
        ): LocationObservation {
            require(
                !capturedAt.value.isAfter(
                    receivedAt.plus(CapturedAt.MAXIMUM_FUTURE_SKEW),
                ),
            ) {
                "capturedAt is too far in the future"
            }
            return LocationObservation(
                id = id,
                personId = personId,
                point = point,
                capturedAt = capturedAt.value,
                receivedAt = receivedAt,
                source = ObservationSource.ClientUpdate(clientUpdateId),
            )
        }
    }
}

class LastKnownLocationProjection private constructor(
    val personId: PersonId,
    val observationId: ObservationId,
    val point: GeoPoint,
    val capturedAt: Instant,
    val receivedAt: Instant,
) {
    fun advance(candidate: LocationObservation): LastKnownLocationProjection {
        require(candidate.personId == personId) {
            "A last-known projection cannot accept another person's observation"
        }
        val candidateWins =
            compareObservationOrder(
                capturedAt = candidate.capturedAt,
                receivedAt = candidate.receivedAt,
                observationId = candidate.id,
                otherCapturedAt = capturedAt,
                otherReceivedAt = receivedAt,
                otherObservationId = observationId,
            ) > 0
        return if (candidateWins) from(candidate) else this
    }

    companion object {
        fun from(observation: LocationObservation): LastKnownLocationProjection =
            LastKnownLocationProjection(
                personId = observation.personId,
                observationId = observation.id,
                point = observation.point,
                capturedAt = observation.capturedAt,
                receivedAt = observation.receivedAt,
            )

        fun restore(
            personId: PersonId,
            observationId: ObservationId,
            point: GeoPoint,
            capturedAt: Instant,
            receivedAt: Instant,
        ): LastKnownLocationProjection =
            LastKnownLocationProjection(
                personId = personId,
                observationId = observationId,
                point = point,
                capturedAt = capturedAt,
                receivedAt = receivedAt,
            )
    }
}

private fun compareObservationOrder(
    capturedAt: Instant,
    receivedAt: Instant,
    observationId: ObservationId,
    otherCapturedAt: Instant,
    otherReceivedAt: Instant,
    otherObservationId: ObservationId,
): Int {
    val capturedAtComparison = capturedAt.compareTo(otherCapturedAt)
    if (capturedAtComparison != 0) {
        return capturedAtComparison
    }

    val receivedAtComparison = receivedAt.compareTo(otherReceivedAt)
    return if (receivedAtComparison != 0) {
        receivedAtComparison
    } else {
        observationId.compareTo(otherObservationId)
    }
}

private val CAPTURED_AT_PATTERN =
    Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:[0-5]\d(?:\.\d{1,3})?(?:Z|[+-]\d{2}:\d{2})$""")

private fun Double.positiveZero(): Double = if (this == 0.0) 0.0 else this

private fun Instant.toMilliseconds(): Instant = truncatedTo(ChronoUnit.MILLIS)
