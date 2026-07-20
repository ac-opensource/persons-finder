package com.persons.finder.person.nearby

import com.persons.finder.person.create.CreatePersonCommand
import com.persons.finder.person.create.CreatePersonOutcome
import com.persons.finder.person.create.CreatePersonService
import com.persons.finder.person.model.GeoPoint
import com.persons.finder.person.model.PersonId
import com.persons.finder.person.model.PersonProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Path

@SpringBootTest
@Testcontainers
class JdbcNearbyPersonRepositoryTest {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var createPerson: CreatePersonService

    @Autowired
    private lateinit var findNearby: FindNearbyService

    @BeforeEach
    fun cleanRows() {
        jdbc.execute(
            "TRUNCATE TABLE last_known_location_projection, location_observation, person CASCADE",
        )
    }

    @Test
    fun `nearby query is feature-owned and returns stable unrounded PostGIS results`() {
        val first = create("Andrew", GeoPoint.from(-41.2865, 174.7762))
        val second = create("Tama", GeoPoint.from(-41.2865, 174.7762))
        create("Mere", GeoPoint.from(-36.8485, 174.7633))

        val results =
            findNearby.execute(
                FindNearbyQuery(
                    origin = GeoPoint.from(-41.2865, 174.7762),
                    radius = RadiusKm.from(1.0),
                ),
            )

        assertEquals(listOf(first, second).sorted(), results.map(NearbyPerson::id))
        assertEquals(listOf(0.0, 0.0), results.map(NearbyPerson::distanceKm))
        assertEquals(setOf("Andrew", "Tama"), results.map { it.profile.name }.toSet())
        assertEquals(2, results.map { it.bio }.filter(String::isNotBlank).size)
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
    }
}
