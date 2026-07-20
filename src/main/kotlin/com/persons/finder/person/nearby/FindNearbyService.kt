package com.persons.finder.person.nearby

import com.persons.finder.person.model.GeoPoint
import com.persons.finder.person.model.PersonId
import com.persons.finder.person.model.PersonProfile
import org.springframework.stereotype.Service
import java.time.Instant

data class FindNearbyQuery(
    val origin: GeoPoint,
    val radius: RadiusKm,
)

data class NearbyPerson(
    val id: PersonId,
    val profile: PersonProfile,
    val bio: String,
    val createdAt: Instant,
    val lastKnownLocationAt: Instant,
    val location: GeoPoint,
    val distanceKm: Double,
)

@JvmInline
value class RadiusKm private constructor(val value: Double) {
    companion object {
        const val MAXIMUM = 100.0

        fun from(value: Double): RadiusKm {
            require(value.isFinite() && value > 0.0 && value <= MAXIMUM) {
                "Radius must be finite, greater than zero, and at most 100 kilometres"
            }
            return RadiusKm(value)
        }
    }
}

@Service
class FindNearbyService(
    private val nearbyPersons: NearbyPersonRepository,
) {
    fun execute(query: FindNearbyQuery): List<NearbyPerson> =
        nearbyPersons.find(query)
}
