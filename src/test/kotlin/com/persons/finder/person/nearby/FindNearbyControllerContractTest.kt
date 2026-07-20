@file:Suppress("DEPRECATION")

package com.persons.finder.person.nearby

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.persons.finder.person.model.GeoPoint
import com.persons.finder.person.model.PersonId
import com.persons.finder.person.model.PersonProfile
import com.persons.finder.web.ApiExceptionHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class FindNearbyControllerContractTest {
    private var result: List<NearbyPerson> = nearbyPeople()
    private val queries = mutableListOf<FindNearbyQuery>()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        queries.clear()
        val findNearby =
            mock(FindNearbyService::class.java) { invocation ->
                if (invocation.method.name == "execute") {
                    queries += invocation.arguments.single() as FindNearbyQuery
                    result
                } else {
                    Answers.RETURNS_DEFAULTS.answer(invocation)
                }
            }
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(FindNearbyController(findNearby))
                .setControllerAdvice(ApiExceptionHandler())
                .setMessageConverters(
                    MappingJackson2HttpMessageConverter(jacksonObjectMapper()),
                )
                .build()
    }

    @Test
    fun `GET nearby returns a bare array with nested location and one-decimal distance`() {
        mockMvc.perform(validRequest())
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].length()").value(9))
            .andExpect(jsonPath("$[0].id").value(FIRST_PERSON_ID))
            .andExpect(jsonPath("$[0].name").value("Aroha"))
            .andExpect(jsonPath("$[0].jobTitle").value("Software engineer"))
            .andExpect(jsonPath("$[0].hobbies[0]").value("tramping"))
            .andExpect(jsonPath("$[0].bio").value("A quirky local profile."))
            .andExpect(jsonPath("$[0].createdAt").value("2026-07-19T05:06:07.123Z"))
            .andExpect(jsonPath("$[0].lastKnownLocationAt").value("2026-07-19T05:07:08.456Z"))
            .andExpect(jsonPath("$[0].location.length()").value(2))
            .andExpect(jsonPath("$[0].location.latitude").value(-41.2865))
            .andExpect(jsonPath("$[0].location.longitude").value(174.7762))
            .andExpect(jsonPath("$[0].distanceKm").value(1.2))
            .andExpect(jsonPath("$[0].latitude").doesNotExist())
            .andExpect(jsonPath("$[0].longitude").doesNotExist())
            .andExpect(jsonPath("$[1].distanceKm").value(1.3))

        assertEquals(1, queries.size)
        assertEquals(GeoPoint.from(-41.2865, 174.7762), queries.single().origin)
        assertEquals(100.0, queries.single().radius.value)
    }

    @Test
    fun `GET nearby returns an empty bare array without pagination metadata`() {
        result = emptyList()

        mockMvc.perform(validRequest())
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
    }

    @Test
    fun `GET nearby requires exactly one nonempty value for every approved parameter`() {
        mockMvc.perform(get("/persons/nearby"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.violations.length()").value(3))
            .andExpect(jsonPath("$.violations[0].field").value("lat"))
            .andExpect(jsonPath("$.violations[0].code").value("REQUIRED"))
            .andExpect(jsonPath("$.violations[1].field").value("lon"))
            .andExpect(jsonPath("$.violations[2].field").value("radius"))

        mockMvc.perform(
            get("/persons/nearby")
                .queryParam("lat", "-41", "-42")
                .queryParam("lon", "174")
                .queryParam("radius", "1"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.violations[0].field").value("lat"))
            .andExpect(jsonPath("$.violations[0].code").value("INVALID_FORMAT"))

        mockMvc.perform(
            get("/persons/nearby")
                .queryParam("lat", "")
                .queryParam("lon", "174")
                .queryParam("radius", "1"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.violations[0].field").value("lat"))
            .andExpect(jsonPath("$.violations[0].code").value("INVALID_FORMAT"))

        assertEquals(0, queries.size)
    }

    @Test
    fun `GET nearby rejects alternate unknown malformed and out-of-range parameters`() {
        mockMvc.perform(
            get("/persons/nearby")
                .queryParam("latitude", "-41")
                .queryParam("lon", "174")
                .queryParam("radius", "1"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.violations[0].field").value("lat"))
            .andExpect(jsonPath("$.violations[0].code").value("REQUIRED"))
            .andExpect(jsonPath("$.violations[1].field").value("latitude"))
            .andExpect(jsonPath("$.violations[1].code").value("UNKNOWN_FIELD"))

        listOf("0", "-1", "100.0001", "NaN", "Infinity").forEach { invalidRadius ->
            mockMvc.perform(
                get("/persons/nearby")
                    .queryParam("lat", "-41")
                    .queryParam("lon", "174")
                    .queryParam("radius", invalidRadius),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.violations[0].field").value("radius"))
                .andExpect(jsonPath("$.violations[0].code").value("OUT_OF_RANGE"))
        }

        mockMvc.perform(
            get("/persons/nearby")
                .queryParam("lat", "south")
                .queryParam("lon", "181")
                .queryParam("radius", "1"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.violations[0].field").value("lat"))
            .andExpect(jsonPath("$.violations[0].code").value("INVALID_FORMAT"))
            .andExpect(jsonPath("$.violations[1].field").value("lon"))
            .andExpect(jsonPath("$.violations[1].code").value("OUT_OF_RANGE"))

        assertEquals(0, queries.size)
    }

    @Test
    fun `only the exact unversioned nearby route is mapped`() {
        mockMvc.perform(
            get("/api/v1/persons/nearby")
                .queryParam("lat", "-41")
                .queryParam("lon", "174")
                .queryParam("radius", "1"),
        )
            .andExpect(status().isNotFound)
    }

    private fun validRequest() =
        get("/persons/nearby")
            .queryParam("lat", "-41.2865")
            .queryParam("lon", "174.7762")
            .queryParam("radius", "100")

    private companion object {
        const val FIRST_PERSON_ID = "00000000-0000-4000-8000-000000000001"
        const val SECOND_PERSON_ID = "00000000-0000-4000-8000-000000000002"

        fun nearbyPeople(): List<NearbyPerson> =
            listOf(
                nearbyPerson(FIRST_PERSON_ID, "Aroha", 1.24),
                nearbyPerson(SECOND_PERSON_ID, "Tama", 1.25),
            )

        fun nearbyPerson(
            id: String,
            name: String,
            distanceKm: Double,
        ): NearbyPerson =
            NearbyPerson(
                id = PersonId.from(UUID.fromString(id)),
                profile =
                    PersonProfile.create(
                        name,
                        "Software engineer",
                        listOf("tramping"),
                    ),
                bio = "A quirky local profile.",
                createdAt = Instant.parse("2026-07-19T05:06:07.123Z"),
                lastKnownLocationAt = Instant.parse("2026-07-19T05:07:08.456Z"),
                location = GeoPoint.from(-41.2865, 174.7762),
                distanceKm = distanceKm,
            )
    }
}
