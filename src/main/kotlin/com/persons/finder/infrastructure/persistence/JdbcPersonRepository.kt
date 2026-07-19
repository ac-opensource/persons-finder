package com.persons.finder.infrastructure.persistence

import com.persons.finder.application.NewPerson
import com.persons.finder.application.PersonRepository
import com.persons.finder.domain.model.ClientUpdateId
import com.persons.finder.domain.model.GeoPoint
import com.persons.finder.domain.model.LastKnownLocationProjection
import com.persons.finder.domain.model.LocationObservation
import com.persons.finder.domain.model.ObservationId
import com.persons.finder.domain.model.ObservationSource
import com.persons.finder.domain.model.PersonId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp

@Repository
class JdbcPersonRepository(
    private val jdbcTemplate: JdbcTemplate,
) : PersonRepository {
    override fun insertPerson(person: NewPerson) {
        jdbcTemplate.update(
            { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO person (id, name, job_title, hobbies, bio, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).apply {
                    setObject(1, person.id.value)
                    setString(2, person.profile.name)
                    setString(3, person.profile.jobTitle)
                    setArray(4, connection.createArrayOf("text", person.profile.hobbies.toTypedArray()))
                    setString(5, person.bio)
                    setTimestamp(6, Timestamp.from(person.createdAt))
                }
            },
        )
    }

    override fun insertObservation(observation: LocationObservation) {
        jdbcTemplate.update(
            """
            INSERT INTO location_observation (
                id,
                person_id,
                captured_at,
                received_at,
                source,
                client_update_id,
                location
            )
            VALUES (?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)
            """.trimIndent(),
            observation.id.value,
            observation.personId.value,
            Timestamp.from(observation.capturedAt),
            Timestamp.from(observation.receivedAt),
            observation.source.databaseValue(),
            observation.source.clientUpdateIdOrNull(),
            observation.point.longitude,
            observation.point.latitude,
        )
    }

    override fun insertLastKnown(projection: LastKnownLocationProjection) {
        jdbcTemplate.update(
            """
            INSERT INTO last_known_location_projection (
                person_id,
                observation_id,
                captured_at,
                received_at,
                location
            )
            VALUES (?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)
            """.trimIndent(),
            projection.personId.value,
            projection.observationId.value,
            Timestamp.from(projection.capturedAt),
            Timestamp.from(projection.receivedAt),
            projection.point.longitude,
            projection.point.latitude,
        )
    }

    override fun lockLastKnown(personId: PersonId): LastKnownLocationProjection? =
        jdbcTemplate
            .query(
                """
                SELECT
                    person_id,
                    observation_id,
                    captured_at,
                    received_at,
                    ST_Y(location::geometry) AS latitude,
                    ST_X(location::geometry) AS longitude
                FROM last_known_location_projection
                WHERE person_id = ?
                FOR UPDATE
                """.trimIndent(),
                projectionRowMapper,
                personId.value,
            )
            .singleOrNull()

    override fun findObservation(
        personId: PersonId,
        clientUpdateId: ClientUpdateId,
    ): LocationObservation? =
        jdbcTemplate
            .query(
                """
                SELECT
                    id,
                    person_id,
                    captured_at,
                    received_at,
                    source,
                    client_update_id,
                    ST_Y(location::geometry) AS latitude,
                    ST_X(location::geometry) AS longitude
                FROM location_observation
                WHERE person_id = ? AND client_update_id = ?
                """.trimIndent(),
                observationRowMapper,
                personId.value,
                clientUpdateId.value,
            )
            .singleOrNull()

    override fun updateLastKnown(projection: LastKnownLocationProjection) {
        val updated =
            jdbcTemplate.update(
                """
                UPDATE last_known_location_projection
                SET
                    observation_id = ?,
                    captured_at = ?,
                    received_at = ?,
                    location = ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
                WHERE person_id = ?
                """.trimIndent(),
                projection.observationId.value,
                Timestamp.from(projection.capturedAt),
                Timestamp.from(projection.receivedAt),
                projection.point.longitude,
                projection.point.latitude,
                projection.personId.value,
            )
        check(updated == 1) { "Expected one last-known projection to be updated" }
    }

    private val projectionRowMapper =
        RowMapper { resultSet, _ ->
            val observation =
                LocationObservation.initial(
                    id = ObservationId.from(resultSet.getObject("observation_id", java.util.UUID::class.java)),
                    personId = PersonId.from(resultSet.getObject("person_id", java.util.UUID::class.java)),
                    point = resultSet.point(),
                    recordedAt = resultSet.getTimestamp("captured_at").toInstant(),
                )
            projectionFromStoredValues(
                observation = observation,
                capturedAt = resultSet.getTimestamp("captured_at").toInstant(),
                receivedAt = resultSet.getTimestamp("received_at").toInstant(),
            )
        }

    private val observationRowMapper =
        RowMapper { resultSet, _ ->
            val id = ObservationId.from(resultSet.getObject("id", java.util.UUID::class.java))
            val personId = PersonId.from(resultSet.getObject("person_id", java.util.UUID::class.java))
            val point = resultSet.point()
            val capturedAt = resultSet.getTimestamp("captured_at").toInstant()
            val receivedAt = resultSet.getTimestamp("received_at").toInstant()
            when (resultSet.getString("source")) {
                "INITIAL" ->
                    LocationObservation.initial(
                        id = id,
                        personId = personId,
                        point = point,
                        recordedAt = capturedAt,
                    )

                "NO_KEY" ->
                    LocationObservation.noKeyUpdate(
                        id = id,
                        personId = personId,
                        point = point,
                        receivedAt = receivedAt,
                    )

                "CLIENT_UPDATE" ->
                    LocationObservation.clientUpdate(
                        id = id,
                        personId = personId,
                        point = point,
                        capturedAt = com.persons.finder.domain.model.CapturedAt.fromStored(capturedAt),
                        receivedAt = receivedAt,
                        clientUpdateId =
                            ClientUpdateId.from(
                                resultSet.getObject("client_update_id", java.util.UUID::class.java),
                            ),
                    )

                else -> error("Unknown persisted observation source")
            }
        }
}

private fun ObservationSource.databaseValue(): String =
    when (this) {
        ObservationSource.Initial -> "INITIAL"
        ObservationSource.NoKeyUpdate -> "NO_KEY"
        is ObservationSource.ClientUpdate -> "CLIENT_UPDATE"
    }

private fun ObservationSource.clientUpdateIdOrNull(): java.util.UUID? =
    (this as? ObservationSource.ClientUpdate)?.id?.value

private fun ResultSet.point(): GeoPoint =
    GeoPoint.from(
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
    )

private fun projectionFromStoredValues(
    observation: LocationObservation,
    capturedAt: java.time.Instant,
    receivedAt: java.time.Instant,
): LastKnownLocationProjection =
    LastKnownLocationProjection.restore(
        personId = observation.personId,
        observationId = observation.id,
        point = observation.point,
        capturedAt = capturedAt,
        receivedAt = receivedAt,
    )
