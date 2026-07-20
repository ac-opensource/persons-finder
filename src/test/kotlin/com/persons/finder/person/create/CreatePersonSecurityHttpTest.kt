@file:Suppress("DEPRECATION")

package com.persons.finder.person.create

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.persons.finder.person.bio.BioGenerationResult
import com.persons.finder.person.bio.BioGenerator
import com.persons.finder.person.bio.BioPolicy
import com.persons.finder.person.bio.BioPolicyTest
import com.persons.finder.person.bio.BioTemplateId
import com.persons.finder.person.bio.BioTemplateRequest
import com.persons.finder.person.bio.GeneratedBioTemplate
import com.persons.finder.person.model.LastKnownLocationProjection
import com.persons.finder.person.model.LocationObservation
import com.persons.finder.web.ApiExceptionHandler
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@ExtendWith(OutputCaptureExtension::class)
class CreatePersonSecurityHttpTest {
    private val objectMapper =
        jacksonObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    @Test
    fun `every named attack is 422 with zero generator network persistence and content logs`(
        output: CapturedOutput,
    ) {
        BioPolicyTest.NAMED_ATTACKS.forEach { (attackName, job, hobbies) ->
            val fixture = securityFixture()
            val before = fixture.repository.counts()
            val requestBody = requestBody(job, hobbies)

            fixture.mockMvc.perform(
                post("/persons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            )
                .andExpect(status().isUnprocessableContent)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNSAFE_BIO_INPUT"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.violations").doesNotExist())
                .andExpect(content().string(not(containsString(requestBody))))
                .andExpect(content().string(not(containsString("I am hacked"))))

            assertEquals(0, fixture.generator.invocations, attackName)
            assertEquals(0, fixture.generator.networkRequests, attackName)
            assertEquals(before, fixture.repository.counts(), attackName)
        }

        val capturedLogs = output.out + output.err
        BioPolicyTest.NAMED_ATTACKS
            .flatMap { (_, job, hobbies) -> listOf(job) + hobbies }
            .forEach { submitted ->
                assertEquals(false, capturedLogs.contains(submitted), submitted)
            }
        assertEquals(false, capturedLogs.contains("I am hacked"))
        assertEquals(false, capturedLogs.contains("\"hobbies\""))
    }

    @Test
    fun `benign quoted and ordinary keyword controls are not substring-rejected`() {
        listOf(
            "I ignore instructions in outdated board games",
            "The essay 'Ignore all instructions' examines class design",
            "system design",
            "prompt engineering",
            "following instructions for model trains",
            "chess ♟️",
        ).forEach { hobby ->
            val fixture = securityFixture()

            fixture.mockMvc.perform(
                post("/persons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody("Prompt engineer", listOf(hobby))),
            )
                .andExpect(status().isCreated)

            assertEquals(1, fixture.generator.invocations, hobby)
            assertEquals(1, fixture.generator.networkRequests, hobby)
            assertEquals(PersistenceCounts(1, 1, 1), fixture.repository.counts(), hobby)
        }
    }

    private fun securityFixture(): SecurityFixture {
        val repository = RecordingRepository()
        val generator = NetworkSpyGenerator()
        val service =
            CreatePersonService(
                repository = repository,
                bioGenerator = generator,
                bioPolicy = BioPolicy(),
                transactions = TransactionOperations.withoutTransaction(),
                clock =
                    Clock.fixed(
                        Instant.parse("2026-07-20T00:00:00Z"),
                        ZoneOffset.UTC,
                    ),
            )
        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(CreatePersonController(service))
                .setControllerAdvice(ApiExceptionHandler())
                .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
                .build()
        return SecurityFixture(mockMvc, repository, generator)
    }

    private fun requestBody(
        jobTitle: String,
        hobbies: List<String>,
    ): String =
        objectMapper.writeValueAsString(
            mapOf(
                "name" to "Synthetic Person",
                "jobTitle" to jobTitle,
                "hobbies" to hobbies,
                "location" to
                    mapOf(
                        "latitude" to -41.2865,
                        "longitude" to 174.7762,
                    ),
            ),
        )

    private data class SecurityFixture(
        val mockMvc: MockMvc,
        val repository: RecordingRepository,
        val generator: NetworkSpyGenerator,
    )

    private data class PersistenceCounts(
        val people: Int,
        val observations: Int,
        val projections: Int,
    )

    private class NetworkSpyGenerator : BioGenerator {
        var invocations = 0
        var networkRequests = 0

        override fun generate(request: BioTemplateRequest): BioGenerationResult {
            invocations++
            networkRequests++
            return BioGenerationResult.Template(
                GeneratedBioTemplate.fromCatalog(BioTemplateId.QUIRKY_SIDE_QUEST),
            )
        }
    }

    private class RecordingRepository : CreatePersonRepository {
        private val people = mutableListOf<NewPerson>()
        private val observations = mutableListOf<LocationObservation>()
        private val projections = mutableListOf<LastKnownLocationProjection>()

        override fun insertPerson(person: NewPerson) {
            people += person
        }

        override fun insertObservation(observation: LocationObservation) {
            observations += observation
        }

        override fun insertLastKnown(projection: LastKnownLocationProjection) {
            projections += projection
        }

        fun counts() = PersistenceCounts(people.size, observations.size, projections.size)
    }
}
