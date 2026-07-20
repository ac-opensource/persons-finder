package com.persons.finder.person.nearby

import com.persons.finder.person.model.PersonId
import com.persons.finder.person.model.PersonProfile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class JdbcNearbyPersonRepository(
    private val jdbcTemplate: JdbcTemplate,
) : NearbyPersonRepository {
    override fun find(query: FindNearbyQuery): List<NearbyPerson> =
        jdbcTemplate.query(
            INDEXED_NEARBY_QUERY,
            nearbyPersonRowMapper,
            query.origin.longitude,
            query.origin.latitude,
            query.radius.value,
        )

    private val nearbyPersonRowMapper =
        RowMapper { resultSet, _ ->
            val hobbies =
                (resultSet.getArray("hobbies").array as Array<*>)
                    .map { it as String }
            NearbyPerson(
                id = PersonId.from(resultSet.getObject("id", java.util.UUID::class.java)),
                profile =
                    PersonProfile.create(
                        name = resultSet.getString("name"),
                        jobTitle = resultSet.getString("job_title"),
                        hobbies = hobbies,
                    ),
                bio = resultSet.getString("bio"),
                createdAt = resultSet.getTimestamp("created_at").toInstant(),
                lastKnownLocationAt =
                    resultSet.getTimestamp("last_known_location_at").toInstant(),
                distanceKm = resultSet.getDouble("distance_metres") / METRES_PER_KILOMETRE,
            )
        }
}

internal val INDEXED_NEARBY_QUERY =
    """
    WITH search AS (
        SELECT
            ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography AS origin,
            ?::double precision * 1000.0 AS radius_metres
    )
    SELECT
        person.id,
        person.name,
        person.job_title,
        person.hobbies,
        person.bio,
        person.created_at,
        last_known.captured_at AS last_known_location_at,
        ST_Distance(last_known.location, search.origin, true) AS distance_metres
    FROM last_known_location_projection AS last_known
    JOIN person
        ON person.id = last_known.person_id
    CROSS JOIN search
    WHERE ST_DWithin(
        last_known.location,
        search.origin,
        search.radius_metres,
        true
    )
    ORDER BY
        distance_metres,
        person.id
    """.trimIndent()

private const val METRES_PER_KILOMETRE = 1000.0
