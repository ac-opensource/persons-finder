@file:Suppress("DEPRECATION")

package com.persons.finder.person.location.update

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.persons.finder.person.model.ObservationId
import com.persons.finder.person.model.PersonId
import com.persons.finder.web.ApiExceptionHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.mock
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class UpdatePersonLocationControllerContractTest {
    private var updateOutcome: UpdateLocationOutcome = acceptedUpdate()
    private var updateInvocations = 0
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        updateInvocations = 0
        val objectMapper =
            jacksonObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        val updateLocation =
            mock(UpdatePersonLocationService::class.java) { invocation ->
                if (invocation.method.name == "execute") {
                    updateInvocations++
                    updateOutcome
                } else {
                    Answers.RETURNS_DEFAULTS.answer(invocation)
                }
            }
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(
                    UpdatePersonLocationController(updateLocation),
                )
                .setControllerAdvice(ApiExceptionHandler())
                .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
                .build()
    }

    @Test
    fun `PUT accepts the no-key form and returns no coordinates`() {
        mockMvc.perform(
            put("/persons/{id}/location", PERSON_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"latitude":-36.8485,"longitude":174.7633}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(6))
            .andExpect(jsonPath("$.personId").value(PERSON_ID))
            .andExpect(jsonPath("$.observationId").value(OBSERVATION_ID))
            .andExpect(jsonPath("$.capturedAt").value("2026-07-19T05:07:00.000Z"))
            .andExpect(jsonPath("$.receivedAt").value("2026-07-19T05:07:00.000Z"))
            .andExpect(jsonPath("$.latitude").doesNotExist())
            .andExpect(jsonPath("$.longitude").doesNotExist())
            .andExpect(jsonPath("$.clientUpdateId").doesNotExist())
    }

    @Test
    fun `PUT enforces capturedAt and clientUpdateId as both or neither`() {
        mockMvc.perform(
            put("/persons/{id}/location", PERSON_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "latitude": -36.8485,
                      "longitude": 174.7633,
                      "capturedAt": "2026-07-19T05:06:07.123Z"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.violations[0].field").value("clientUpdateId"))
            .andExpect(jsonPath("$.violations[0].code").value("REQUIRED"))
    }

    @Test
    fun `invalid PUT coordinates fail before the use case boundary`() {
        mockMvc.perform(
            put("/persons/{id}/location", PERSON_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"latitude":-36.8485,"longitude":180.0001}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.violations[0].field").value("longitude"))
            .andExpect(jsonPath("$.violations[0].code").value("OUT_OF_RANGE"))
        assertEquals(0, updateInvocations)
    }

    @Test
    fun `PUT maps missing person and idempotency conflict`() {
        updateOutcome = UpdateLocationOutcome.PersonNotFound
        mockMvc.perform(
            put("/persons/{id}/location", PERSON_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"latitude":-36.8485,"longitude":174.7633}"""),
        )
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"))

        updateOutcome = UpdateLocationOutcome.IdempotencyKeyReused
        mockMvc.perform(
            put("/persons/{id}/location", PERSON_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "latitude": -36.8485,
                      "longitude": 174.7633,
                      "capturedAt": "2026-07-19T05:06:07.123Z",
                      "clientUpdateId": "e49cb43e-9df1-4b36-b52f-59d622a7a0ca"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
    }

    private companion object {
        const val PERSON_ID = "c0a8012e-6d3b-4f0d-8de5-dfb1d5fd2fd8"
        const val OBSERVATION_ID = "e6878898-4aba-4f17-b18c-7a98d4a7a40a"

        fun acceptedUpdate(): UpdateLocationOutcome.Accepted =
            UpdateLocationOutcome.Accepted(
                LocationUpdateResult(
                    personId = PersonId.from(UUID.fromString(PERSON_ID)),
                    observationId = ObservationId.from(UUID.fromString(OBSERVATION_ID)),
                    capturedAt = Instant.parse("2026-07-19T05:07:00Z"),
                    receivedAt = Instant.parse("2026-07-19T05:07:00Z"),
                    lastKnownObservationId = ObservationId.from(UUID.fromString(OBSERVATION_ID)),
                    lastKnownLocationAt = Instant.parse("2026-07-19T05:07:00Z"),
                ),
            )
    }
}
