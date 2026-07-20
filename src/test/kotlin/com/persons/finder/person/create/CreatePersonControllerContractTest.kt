@file:Suppress("DEPRECATION")

package com.persons.finder.person.create

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jayway.jsonpath.JsonPath
import com.persons.finder.person.model.PersonId
import com.persons.finder.person.model.PersonProfile
import com.persons.finder.web.ApiExceptionHandler
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.matchesPattern
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class CreatePersonControllerContractTest {
    private var createOutcome: CreatePersonOutcome = createdOutcome()
    private var createInvocations = 0
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        createInvocations = 0
        val objectMapper =
            jacksonObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        val createPerson =
            mock(CreatePersonService::class.java) { invocation ->
                if (invocation.method.name == "execute") {
                    createInvocations++
                    createOutcome
                } else {
                    Answers.RETURNS_DEFAULTS.answer(invocation)
                }
            }
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    CreatePersonController(createPerson),
                )
                .setControllerAdvice(ApiExceptionHandler())
                .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
                .build()
    }

    @Test
    fun `POST persons creates only the approved public representation`() {
        mockMvc.perform(
            post("/persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE),
        )
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", matchesPattern("^/persons/$UUID_V4_PATTERN$")))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(7))
            .andExpect(jsonPath("$.id").value(matchesPattern(UUID_V4_PATTERN)))
            .andExpect(jsonPath("$.name").value("Aroha"))
            .andExpect(jsonPath("$.jobTitle").value("Software engineer"))
            .andExpect(jsonPath("$.hobbies[0]").value("tramping"))
            .andExpect(jsonPath("$.hobbies[1]").value("pottery"))
            .andExpect(jsonPath("$.bio").value("Aroha, a quirky Software engineer, has a soft spot for tramping."))
            .andExpect(jsonPath("$.createdAt").value("2026-07-19T05:06:07.123Z"))
            .andExpect(jsonPath("$.lastKnownLocationAt").value("2026-07-19T05:06:07.123Z"))
            .andExpect(jsonPath("$.location").doesNotExist())
            .andExpect(jsonPath("$.latitude").doesNotExist())
            .andExpect(jsonPath("$.longitude").doesNotExist())
            .andExpect { result ->
                val responseId = JsonPath.read<String>(result.response.contentAsString, "$.id")
                assertEquals("/persons/$responseId", result.response.getHeader("Location"))
            }
    }

    @Test
    fun `POST rejects unknown input without echoing it`() {
        val rejectedValue = "DO_NOT_ECHO_7f5d01f3"
        mockMvc.perform(
            post("/persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE.dropLast(2) + """, "nickname": "$rejectedValue" }"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.violations[0].field").value("nickname"))
            .andExpect(jsonPath("$.violations[0].code").value("UNKNOWN_FIELD"))
            .andExpect(jsonPath("$.instance").doesNotExist())
            .andExpect(content().string(not(containsString(rejectedValue))))
    }

    @Test
    fun `POST rejects unknown query parameters`() {
        mockMvc.perform(
            post("/persons")
                .queryParam("dryRun", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.violations[0].field").value("dryRun"))
            .andExpect(jsonPath("$.violations[0].code").value("UNKNOWN_FIELD"))
    }

    @Test
    fun `POST rejects null hobby elements before the service boundary`() {
        mockMvc.perform(
            post("/persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE.replace("\"tramping\"", "null")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.violations[0].field").value("hobbies"))
            .andExpect(jsonPath("$.violations[0].code").value("INVALID_TYPE"))
        assertEquals(0, createInvocations)
    }

    @Test
    fun `POST maps typed profile validation without exposing domain exception details`() {
        mockMvc.perform(
            post("/persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE.replace("Aroha", "   ")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.violations[0].field").value("profile"))
            .andExpect(jsonPath("$.violations[0].code").value("REQUIRED"))

        val longName = "N".repeat(PersonProfile.MAX_NAME_CODE_POINTS + 1)
        mockMvc.perform(
            post("/persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(VALID_CREATE.replace("Aroha", longName)),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.violations[0].field").value("name"))
            .andExpect(jsonPath("$.violations[0].code").value("TOO_LONG"))
    }

    @Test
    fun `invalid POST coordinates fail before the use case boundary`() {
        mockMvc.perform(
            post("/persons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    VALID_CREATE.replace(
                        """"latitude": -41.2865""",
                        """"latitude": -90.0001""",
                    ),
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.violations[0].field").value("location.latitude"))
            .andExpect(jsonPath("$.violations[0].code").value("OUT_OF_RANGE"))
        assertEquals(0, createInvocations)
    }

    @Test
    fun `POST maps bio policy and generator failures without internals`() {
        createOutcome = CreatePersonOutcome.BioInputRejected
        mockMvc.perform(post("/persons").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.code").value("BIO_INPUT_REJECTED"))
            .andExpect(jsonPath("$.violations").doesNotExist())

        createOutcome = CreatePersonOutcome.BioGenerationUnavailable
        mockMvc.perform(post("/persons").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.code").value("BIO_GENERATION_UNAVAILABLE"))
            .andExpect(jsonPath("$.violations").doesNotExist())
    }

    @Test
    fun `nearby and stale versioned routes are not added in this slice`() {
        mockMvc.perform(get("/persons/nearby")).andExpect(status().isNotFound)
        mockMvc.perform(get("/api/v1/persons")).andExpect(status().isNotFound)
    }

    private companion object {
        const val PERSON_ID = "c0a8012e-6d3b-4f0d-8de5-dfb1d5fd2fd8"
        const val UUID_V4_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
        val VALID_CREATE =
            """
            {
              "name": "Aroha",
              "jobTitle": "Software engineer",
              "hobbies": ["tramping", "pottery"],
              "location": {
                "latitude": -41.2865,
                "longitude": 174.7762
              }
            }
            """.trimIndent()

        fun createdOutcome(): CreatePersonOutcome.Created =
            CreatePersonOutcome.Created(
                CreatePersonResult(
                    id = PersonId.from(UUID.fromString(PERSON_ID)),
                    profile =
                        PersonProfile.create(
                            "Aroha",
                            "Software engineer",
                            listOf("tramping", "pottery"),
                        ),
                    bio = "Aroha, a quirky Software engineer, has a soft spot for tramping.",
                    createdAt = Instant.parse("2026-07-19T05:06:07.123Z"),
                    lastKnownLocationAt = Instant.parse("2026-07-19T05:06:07.123Z"),
                ),
            )

    }
}
