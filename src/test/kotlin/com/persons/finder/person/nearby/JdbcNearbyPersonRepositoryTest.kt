package com.persons.finder.person.nearby

import com.persons.finder.person.create.CreatePersonCommand
import com.persons.finder.person.create.CreatePersonOutcome
import com.persons.finder.person.create.CreatePersonService
import com.persons.finder.person.location.update.RetryIdentity
import com.persons.finder.person.location.update.UpdateLocationCommand
import com.persons.finder.person.location.update.UpdateLocationOutcome
import com.persons.finder.person.location.update.UpdatePersonLocationService
import com.persons.finder.person.model.CapturedAt
import com.persons.finder.person.model.ClientUpdateId
import com.persons.finder.person.model.GeoPoint
import com.persons.finder.person.model.PersonId
import com.persons.finder.person.model.PersonProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs

@SpringBootTest
@Testcontainers
class JdbcNearbyPersonRepositoryTest {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var createPerson: CreatePersonService

    @Autowired
    private lateinit var updateLocation: UpdatePersonLocationService

    @Autowired
    private lateinit var findNearby: FindNearbyService

    @Autowired
    private lateinit var webApplicationContext: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun cleanRows() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
        clearRows()
    }

    @Test
    fun `search migration adds one GiST index only to the last-known projection`() {
        val migration =
            jdbc.queryForMap(
                """
                SELECT version, success
                FROM flyway_schema_history
                WHERE version = '3'
                """.trimIndent(),
            )
        assertEquals("3", migration["version"])
        assertEquals(true, migration["success"])

        val indexes =
            jdbc.queryForList(
                """
                SELECT tablename, indexname, indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexdef ILIKE '%USING gist%'
                ORDER BY indexname
                """.trimIndent(),
            )
        assertEquals(1, indexes.size)
        assertEquals("last_known_location_projection", indexes.single()["tablename"])
        assertEquals(
            "last_known_location_projection_location_gist_idx",
            indexes.single()["indexname"],
        )
        assertTrue(indexes.single()["indexdef"].toString().contains("(location)"))
    }

    @Test
    fun `HTTP nearby route returns the projection result with nested location`() {
        val personId = create("Aroha", GeoPoint.from(-41.2865, 174.7762))

        mockMvc.perform(
            get("/persons/nearby")
                .queryParam("lat", "-41.2865")
                .queryParam("lon", "174.7762")
                .queryParam("radius", "1"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].length()").value(9))
            .andExpect(jsonPath("$[0].id").value(personId.value.toString()))
            .andExpect(jsonPath("$[0].distanceKm").value(0.0))
            .andExpect(jsonPath("$[0].bio").isNotEmpty)
            .andExpect(jsonPath("$[0].location.length()").value(2))
            .andExpect(jsonPath("$[0].location.latitude").value(-41.2865))
            .andExpect(jsonPath("$[0].location.longitude").value(174.7762))
            .andExpect(jsonPath("$[0].latitude").doesNotExist())
            .andExpect(jsonPath("$[0].longitude").doesNotExist())
    }

    @Test
    fun `HTTP nearby follows move out and reentry without expiring a stale current location`() {
        val origin = GeoPoint.from(-41.2865, 174.7762)
        val mover = create("Mover", origin)
        val staleCurrentPerson = fixtureId(41)
        insertAt(
            personId = staleCurrentPerson,
            latitude = origin.latitude,
            longitude = origin.longitude,
            recordedAt = Instant.parse("2000-01-01T00:00:00Z"),
        )
        val base = Instant.now().plusSeconds(1).truncatedTo(ChronoUnit.MILLIS)

        assertEquals(setOf(mover.value, staleCurrentPerson), nearbyIds(origin))

        val movedOut =
            updateLocation.execute(
                UpdateLocationCommand(
                    personId = mover,
                    point = GeoPoint.from(-36.8485, 174.7633),
                    retryIdentity =
                        RetryIdentity.ClientKey(
                            capturedAt = CapturedAt.fromStored(base),
                            clientUpdateId = ClientUpdateId.from(UUID.randomUUID()),
                        ),
                ),
            )
        assertTrue(movedOut is UpdateLocationOutcome.Accepted)
        assertEquals(setOf(staleCurrentPerson), nearbyIds(origin))

        val movedBack =
            updateLocation.execute(
                UpdateLocationCommand(
                    personId = mover,
                    point = origin,
                    retryIdentity =
                        RetryIdentity.ClientKey(
                            capturedAt = CapturedAt.fromStored(base.plusSeconds(1)),
                            clientUpdateId = ClientUpdateId.from(UUID.randomUUID()),
                        ),
                ),
            )
        assertTrue(movedBack is UpdateLocationOutcome.Accepted)
        assertEquals(setOf(mover.value, staleCurrentPerson), nearbyIds(origin))
    }

    @Test
    fun `indexed spheroidal query matches brute force at boundary duplicates and tied distances`() {
        val duplicateFirst = fixtureId(1)
        val duplicateSecond = fixtureId(2)
        val boundary = fixtureId(3)
        val inside = fixtureId(4)
        val outside = fixtureId(5)
        val tieFirst = fixtureId(6)
        val tieSecond = fixtureId(7)

        insertAt(duplicateFirst, latitude = 0.0, longitude = 0.0)
        insertAt(duplicateSecond, latitude = 0.0, longitude = 0.0)
        insertProjected(boundary, 0.0, 0.0, distanceMetres = 10_000.0, bearingDegrees = 0.0)
        insertProjected(inside, 0.0, 0.0, distanceMetres = 9_999.999, bearingDegrees = 45.0)
        insertProjected(outside, 0.0, 0.0, distanceMetres = 10_000.001, bearingDegrees = 180.0)
        insertProjected(tieFirst, 0.0, 0.0, distanceMetres = 5_000.0, bearingDegrees = 90.0)
        insertProjected(tieSecond, 0.0, 0.0, distanceMetres = 5_000.0, bearingDegrees = 270.0)

        val results = assertIndexedMatchesBruteForce(GeoPoint.from(0.0, 0.0), 10.0)
        assertEquals(listOf(duplicateFirst, duplicateSecond), results.take(2).map { it.id.value })
        assertEquals(listOf(0.0, 0.0), results.take(2).map(NearbyPerson::distanceKm))
        assertTrue(results.any { it.id.value == boundary })
        assertTrue(results.any { it.id.value == inside })
        assertFalse(results.any { it.id.value == outside })

        val tied =
            results.filter {
                it.id.value == tieFirst || it.id.value == tieSecond
            }
        assertEquals(listOf(tieFirst, tieSecond), tied.map { it.id.value })
        assertTrue(abs(tied[0].distanceKm - tied[1].distanceKm) < 1e-12)
    }

    @Test
    fun `indexed spheroidal query matches brute force across antimeridian and both poles`() {
        val antimeridian = fixtureId(11)
        insertAt(antimeridian, latitude = 0.0, longitude = -179.95)
        val antimeridianResults =
            assertIndexedMatchesBruteForce(
                origin = GeoPoint.from(0.0, 179.95),
                radiusKm = 20.0,
            )
        assertEquals(listOf(antimeridian), antimeridianResults.map { it.id.value })

        clearRows()
        val northPole = fixtureId(12)
        val northNearDifferentMeridian = fixtureId(13)
        val southPole = fixtureId(14)
        val southNearDifferentMeridian = fixtureId(15)
        insertAt(northPole, latitude = 90.0, longitude = 0.0)
        insertAt(northNearDifferentMeridian, latitude = 89.9, longitude = 135.0)
        insertAt(southPole, latitude = -90.0, longitude = 0.0)
        insertAt(southNearDifferentMeridian, latitude = -89.9, longitude = -45.0)

        val northResults =
            assertIndexedMatchesBruteForce(
                origin = GeoPoint.from(90.0, 175.0),
                radiusKm = 20.0,
            )
        assertEquals(
            setOf(northPole, northNearDifferentMeridian),
            northResults.map { it.id.value }.toSet(),
        )

        val southResults =
            assertIndexedMatchesBruteForce(
                origin = GeoPoint.from(-90.0, -90.0),
                radiusKm = 20.0,
            )
        assertEquals(
            setOf(southPole, southNearDifferentMeridian),
            southResults.map { it.id.value }.toSet(),
        )
    }

    @Test
    fun `projection rebuild from retained observations preserves nearby results`() {
        val personId = create("Aroha", GeoPoint.from(-41.2865, 174.7762))
        val update =
            updateLocation.execute(
                UpdateLocationCommand(
                    personId = personId,
                    point = GeoPoint.from(-36.8485, 174.7633),
                    retryIdentity = RetryIdentity.NoKey,
                ),
            )
        assertTrue(update is UpdateLocationOutcome.Accepted)

        val query =
            FindNearbyQuery(
                origin = GeoPoint.from(-36.8485, 174.7633),
                radius = RadiusKm.from(1.0),
            )
        val before = findNearby.execute(query)
        assertEquals(2, rowCount("location_observation"))

        jdbc.execute("DELETE FROM last_known_location_projection")
        jdbc.execute(
            """
            INSERT INTO last_known_location_projection (
                person_id,
                observation_id,
                captured_at,
                received_at,
                location
            )
            SELECT DISTINCT ON (person_id)
                person_id,
                id,
                captured_at,
                received_at,
                location
            FROM location_observation
            ORDER BY person_id, captured_at DESC, received_at DESC, id DESC
            """.trimIndent(),
        )

        assertEquals(before, findNearby.execute(query))
        assertEquals(2, rowCount("location_observation"))
        assertEquals(1, rowCount("last_known_location_projection"))
    }

    @Test
    fun `representative plan uses projection GiST and never derives latest history`() {
        seedRepresentativeProjectionRows(20_000)
        insertAt(fixtureId(9_001), latitude = 0.0, longitude = 0.0)
        insertAt(fixtureId(9_002), latitude = 0.0, longitude = 0.005)
        jdbc.execute("ANALYZE person")
        jdbc.execute("ANALYZE last_known_location_projection")

        val planLines =
            jdbc.queryForList(
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) $INDEXED_NEARBY_QUERY",
                String::class.java,
                0.0,
                0.0,
                1.0,
            )
        val plan = planLines.joinToString(System.lineSeparator())

        assertTrue(plan.contains("last_known_location_projection_location_gist_idx"), plan)
        assertTrue(plan.contains("last_known_location_projection"), plan)
        assertFalse(plan.contains("location_observation"), plan)
        assertFalse(INDEXED_NEARBY_QUERY.contains("LIMIT", ignoreCase = true))
        assertFalse(INDEXED_NEARBY_QUERY.contains("location_observation"))
        assertTrue(INDEXED_NEARBY_QUERY.contains("ST_DWithin"))
        assertTrue(INDEXED_NEARBY_QUERY.contains("ST_Distance"))

        val evidenceDirectory = Path.of("build", "evidence")
        Files.createDirectories(evidenceDirectory)
        Files.writeString(
            evidenceDirectory.resolve("nearby-query-plan.txt"),
            buildString {
                appendLine("Persons Finder nearby query-plan evidence")
                appendLine("Generated: ${Instant.now()}")
                appendLine("Architecture: ${System.getProperty("os.arch")}")
                appendLine("Database: ${jdbc.queryForObject("SELECT version()", String::class.java)}")
                appendLine(
                    "PostGIS: ${
                        jdbc.queryForObject(
                            "SELECT PostGIS_Full_Version()",
                            String::class.java,
                        )
                    }",
                )
                appendLine(
                    "Dataset: 20,000 deterministic globally distributed projection rows " +
                        "plus 2 known local matches",
                )
                appendLine("Parameters: lon=0.0, lat=0.0, radiusKm=1.0")
                appendLine("Scope: one execution; this is plan evidence, not a performance conclusion")
                appendLine()
                appendLine("Query:")
                appendLine(INDEXED_NEARBY_QUERY)
                appendLine()
                appendLine("EXPLAIN (ANALYZE, BUFFERS):")
                appendLine(plan)
            },
        )
    }

    @Test
    fun `nearby radius enforces its feature contract`() {
        listOf(0.0, -1.0, 100.0001, Double.NaN, Double.POSITIVE_INFINITY).forEach { radius ->
            assertThrows(IllegalArgumentException::class.java) {
                RadiusKm.from(radius)
            }
        }
        assertEquals(100.0, RadiusKm.from(100.0).value)
    }

    private fun assertIndexedMatchesBruteForce(
        origin: GeoPoint,
        radiusKm: Double,
    ): List<NearbyPerson> {
        val query =
            FindNearbyQuery(
                origin = origin,
                radius = RadiusKm.from(radiusKm),
            )
        val indexed = findNearby.execute(query)
        val bruteForce =
            jdbc.query(
                BRUTE_FORCE_SPHEROIDAL_QUERY,
                { resultSet, _ ->
                    ExactNearbyResult(
                        id = resultSet.getObject("id", UUID::class.java),
                        distanceMetres = resultSet.getDouble("distance_metres"),
                    )
                },
                origin.longitude,
                origin.latitude,
                radiusKm,
            )

        assertEquals(bruteForce.map(ExactNearbyResult::id), indexed.map { it.id.value })
        assertEquals(bruteForce.size, indexed.size)
        bruteForce.zip(indexed).forEach { (expected, actual) ->
            assertEquals(expected.distanceMetres / 1000.0, actual.distanceKm, 1e-12)
        }
        return indexed
    }

    private fun insertAt(
        personId: UUID,
        latitude: Double,
        longitude: Double,
        recordedAt: Instant = FIXTURE_RECORDED_AT,
    ) {
        insertPerson(personId, recordedAt)
        jdbc.update(
            """
            INSERT INTO location_observation (
                id, person_id, captured_at, received_at, source, client_update_id, location
            )
            VALUES (
                ?, ?, ?, ?,
                'INITIAL', NULL,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography
            )
            """.trimIndent(),
            observationId(personId),
            personId,
            Timestamp.from(recordedAt),
            Timestamp.from(recordedAt),
            longitude,
            latitude,
        )
        insertProjection(personId)
    }

    private fun insertProjected(
        personId: UUID,
        originLatitude: Double,
        originLongitude: Double,
        distanceMetres: Double,
        bearingDegrees: Double,
    ) {
        insertPerson(personId)
        jdbc.update(
            """
            INSERT INTO location_observation (
                id, person_id, captured_at, received_at, source, client_update_id, location
            )
            VALUES (
                ?, ?, '2026-07-20T00:00:00Z', '2026-07-20T00:00:00Z',
                'INITIAL', NULL,
                ST_Project(
                    ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                    ?,
                    radians(?)
                )
            )
            """.trimIndent(),
            observationId(personId),
            personId,
            originLongitude,
            originLatitude,
            distanceMetres,
            bearingDegrees,
        )
        insertProjection(personId)
    }

    private fun insertPerson(
        personId: UUID,
        createdAt: Instant = FIXTURE_RECORDED_AT,
    ) {
        jdbc.update(
            """
            INSERT INTO person (id, name, job_title, hobbies, bio, created_at)
            VALUES (?, ?, 'Software engineer', ARRAY['hiking'], 'A quirky local profile.', ?)
            """.trimIndent(),
            personId,
            "Person ${personId.toString().takeLast(4)}",
            Timestamp.from(createdAt),
        )
    }

    private fun insertProjection(personId: UUID) {
        jdbc.update(
            """
            INSERT INTO last_known_location_projection (
                person_id, observation_id, captured_at, received_at, location
            )
            SELECT person_id, id, captured_at, received_at, location
            FROM location_observation
            WHERE person_id = ?
            """.trimIndent(),
            personId,
        )
    }

    private fun seedRepresentativeProjectionRows(count: Int) {
        jdbc.update(
            """
            INSERT INTO person (id, name, job_title, hobbies, bio, created_at)
            SELECT
                ('20000000-0000-4000-8000-' || lpad(to_hex(i), 12, '0'))::uuid,
                'Plan fixture ' || i,
                'Software engineer',
                ARRAY['hiking']::text[],
                'A quirky local profile.',
                '2026-07-20T00:00:00Z'::timestamptz
            FROM generate_series(1, ?) AS sequence(i)
            """.trimIndent(),
            count,
        )
        jdbc.update(
            """
            INSERT INTO location_observation (
                id, person_id, captured_at, received_at, source, client_update_id, location
            )
            SELECT
                ('30000000-0000-4000-8000-' || lpad(to_hex(i), 12, '0'))::uuid,
                ('20000000-0000-4000-8000-' || lpad(to_hex(i), 12, '0'))::uuid,
                '2026-07-20T00:00:00Z'::timestamptz,
                '2026-07-20T00:00:00Z'::timestamptz,
                'INITIAL',
                NULL,
                ST_SetSRID(
                    ST_MakePoint(
                        -179.9 + mod(i * 73, 3598)::double precision / 10.0,
                        -89.9 + mod(i * 37, 1798)::double precision / 10.0
                    ),
                    4326
                )::geography
            FROM generate_series(1, ?) AS sequence(i)
            """.trimIndent(),
            count,
        )
        jdbc.execute(
            """
            INSERT INTO last_known_location_projection (
                person_id, observation_id, captured_at, received_at, location
            )
            SELECT person_id, id, captured_at, received_at, location
            FROM location_observation
            """.trimIndent(),
        )
    }

    private fun create(
        name: String,
        location: GeoPoint,
    ): PersonId =
        (
            createPerson.execute(
                CreatePersonCommand(
                    profile =
                        PersonProfile.create(
                            name = name,
                            jobTitle = "Software engineer",
                            hobbies = listOf("tramping"),
                        ),
                    initialLocation = location,
                ),
            ) as CreatePersonOutcome.Created
        ).person.id

    private fun nearbyIds(origin: GeoPoint): Set<UUID> {
        val response =
            mockMvc.perform(
                get("/persons/nearby")
                    .queryParam("lat", origin.latitude.toString())
                    .queryParam("lon", origin.longitude.toString())
                    .queryParam("radius", "1"),
            )
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
        return com.jayway.jsonpath.JsonPath
            .read<List<String>>(response, "$[*].id")
            .map(UUID::fromString)
            .toSet()
    }

    private fun clearRows() {
        jdbc.execute(
            "TRUNCATE TABLE last_known_location_projection, location_observation, person CASCADE",
        )
    }

    private fun rowCount(table: String): Int =
        jdbc.queryForObject("SELECT count(*) FROM $table", Int::class.java)!!

    private data class ExactNearbyResult(
        val id: UUID,
        val distanceMetres: Double,
    )

    companion object {
        @Container
        @JvmStatic
        private val postgres =
            PostgreSQLContainer(
                DockerImageName
                    .parse(
                        ImageFromDockerfile("persons-finder-postgis-test:17", true)
                            .withDockerfile(Path.of("Dockerfile.postgis").toAbsolutePath())
                            .get(),
                    )
                    .asCompatibleSubstituteFor("postgres"),
            ).apply {
                withDatabaseName("persons_finder")
                withUsername("persons_finder")
                withPassword("test-only-password")
            }

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.flyway.enabled") { "true" }
        }

        private const val BRUTE_FORCE_SPHEROIDAL_QUERY =
            """
            WITH search AS (
                SELECT
                    ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography AS origin,
                    ?::double precision * 1000.0 AS radius_metres
            )
            SELECT
                person.id,
                ST_Distance(last_known.location, search.origin, true) AS distance_metres
            FROM last_known_location_projection AS last_known
            JOIN person
                ON person.id = last_known.person_id
            CROSS JOIN search
            WHERE ST_Distance(last_known.location, search.origin, true)
                <= search.radius_metres
            ORDER BY
                distance_metres,
                person.id
            """

        private fun fixtureId(value: Int): UUID =
            UUID.fromString(
                "00000000-0000-4000-8000-${value.toString().padStart(12, '0')}",
            )

        private fun observationId(personId: UUID): UUID =
            UUID.fromString(personId.toString().replaceFirst("00000000", "10000000"))

        private val FIXTURE_RECORDED_AT: Instant =
            Instant.parse("2026-07-20T00:00:00Z")
    }
}
