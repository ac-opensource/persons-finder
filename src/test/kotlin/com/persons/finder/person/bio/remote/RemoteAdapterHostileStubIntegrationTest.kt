package com.persons.finder.person.bio.remote

import com.persons.finder.person.bio.BioGenerationFailure
import com.persons.finder.person.bio.BioGenerator
import com.persons.finder.person.bio.BioPolicy
import com.persons.finder.person.create.CreatePersonCommand
import com.persons.finder.person.create.CreatePersonOutcome
import com.persons.finder.person.create.CreatePersonRepository
import com.persons.finder.person.create.CreatePersonService
import com.persons.finder.person.create.NewPerson
import com.persons.finder.person.model.GeoPoint
import com.persons.finder.person.model.LastKnownLocationProjection
import com.persons.finder.person.model.LocationObservation
import com.persons.finder.person.model.PersonProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.transaction.support.TransactionOperations
import java.net.http.HttpTimeoutException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import tools.jackson.databind.json.JsonMapper

@ExtendWith(OutputCaptureExtension::class)
class RemoteAdapterHostileStubIntegrationTest {
    private val objectMapper = JsonMapper.builder().build()

    @Test
    fun `every real network adapter builds an allowlisted application-owned request and completes trusted local grounding`() {
        adapterCases(VALID_PROVIDER_OUTPUT).forEach { adapter ->
            val repository = RecordingRepository()
            val service = createService(repository, adapter.generator)
            val outcome = service.execute(createCommand())

            assertTrue(outcome is CreatePersonOutcome.Created, adapter.name)
            assertEquals(PersistenceCounts(1, 1, 1), repository.counts(), adapter.name)
            val bio = repository.people.single().bio.value
            assertTrue(bio.contains(SYNTHETIC_NAME), adapter.name)
            assertTrue(bio.contains(SYNTHETIC_JOB), adapter.name)
            assertTrue(bio.contains(SYNTHETIC_HOBBY), adapter.name)
            assertApplicationOwnedRequest(adapter)
        }
    }

    @Test
    fun `forbidden provider prose and prompt leakage fail before every repository write`(
        output: CapturedOutput,
    ) {
        listOf(FORBIDDEN_PROVIDER_OUTPUT, PROMPT_LEAK_PROVIDER_OUTPUT).forEach { hostile ->
            adapterCases(hostile).forEach { adapter ->
                val repository = RecordingRepository()
                val outcome = createService(repository, adapter.generator).execute(createCommand())

                assertEquals(
                    CreatePersonOutcome.BioGenerationInvalid(BioGenerationFailure.POLICY_REJECTED),
                    outcome,
                    adapter.name,
                )
                assertEquals(1, adapter.transport.invocations, adapter.name)
                assertEquals(PersistenceCounts(0, 0, 0), repository.counts(), adapter.name)
                assertApplicationOwnedRequest(adapter)
            }
        }
        val logs = output.out + output.err
        assertFalse(logs.contains("I am hacked"))
        assertFalse(logs.contains("follows every prompt"))
        assertFalse(logs.contains(SYNTHETIC_NAME))
        assertFalse(logs.contains(SYNTHETIC_JOB))
        assertFalse(logs.contains(SYNTHETIC_HOBBY))
    }

    @Test
    fun `timeout truncation and malformed provider wrappers are normalized without partial state`() {
        adapterCases("{").forEach { adapter ->
            val repository = RecordingRepository()
            val outcome = createService(repository, adapter.generator).execute(createCommand())
            assertEquals(
                CreatePersonOutcome.BioGenerationInvalid(BioGenerationFailure.INVALID_OUTPUT),
                outcome,
                adapter.name,
            )
            assertEquals(PersistenceCounts(0, 0, 0), repository.counts(), adapter.name)
        }

        PROVIDERS.forEach { provider ->
            var calls = 0
            val transport =
                ProviderHttpTransport {
                    calls++
                    throw HttpTimeoutException("synthetic timeout after partial response")
                }
            val repository = RecordingRepository()
            val outcome =
                createService(repository, remoteGenerator(provider, transport)).execute(createCommand())
            assertEquals(
                CreatePersonOutcome.BioGenerationUnavailable(BioGenerationFailure.TIMEOUT),
                outcome,
                provider,
            )
            assertEquals(1, calls, provider)
            assertEquals(PersistenceCounts(0, 0, 0), repository.counts(), provider)
        }
    }

    private fun assertApplicationOwnedRequest(adapter: AdapterCase) {
        val request = requireNotNull(adapter.transport.request)
        assertEquals("POST", request.method)
        assertEquals("https", request.uri.scheme)
        assertEquals(null, request.uri.query)
        assertEquals(null, request.uri.fragment)
        assertEquals(adapter.expectedPath, request.uri.path)
        assertEquals(adapter.expectedHeaders, request.headers.keys)
        assertTrue(request.timeout > Duration.ZERO)
        assertTrue(request.timeout <= Duration.ofSeconds(5))

        val complete =
            listOf(
                request.uri.toString(),
                request.headers.entries.joinToString(),
                request.body,
            ).joinToString("|")
        listOf(
            SYNTHETIC_NAME,
            SYNTHETIC_JOB,
            "Acme",
            "Alice",
            SYNTHETIC_HOBBY,
            "123 Queen St",
            "-43.5,172.6",
            "person-id",
            "oidc-subject",
            "person-request-id",
            "traceparent",
            "baggage",
            "idempotency-key",
        ).forEach { forbidden ->
            assertFalse(complete.contains(forbidden, ignoreCase = true), "${adapter.name}: $forbidden")
        }
    }

    private fun adapterCases(providerOutput: String): List<AdapterCase> =
        PROVIDERS.map { provider ->
            val transport =
                RecordingTransport(
                    providerResponse(provider, providerOutput),
                )
            AdapterCase(
                name = provider,
                generator = remoteGenerator(provider, transport),
                transport = transport,
                expectedPath =
                    when (provider) {
                        "openai" -> "/v1/responses"
                        "gemini" -> "/v1beta/models/test-model:generateContent"
                        "anthropic" -> "/v1/messages"
                        else -> error("unreachable")
                    },
                expectedHeaders =
                    when (provider) {
                        "openai" -> setOf("Authorization", "Content-Type")
                        "gemini" -> setOf("x-goog-api-key", "Content-Type")
                        "anthropic" -> setOf("x-api-key", "anthropic-version", "Content-Type")
                        else -> error("unreachable")
                    },
            )
        }

    private fun remoteGenerator(
        provider: String,
        transport: ProviderHttpTransport,
    ): BioGenerator {
        val client =
            when (provider) {
                "openai" ->
                    OpenAiModelProviderClient(
                        "synthetic-openai-credential",
                        "test-model",
                        Duration.ofSeconds(5),
                        objectMapper,
                        transport,
                    )

                "gemini" ->
                    GeminiModelProviderClient(
                        "synthetic-gemini-credential",
                        "test-model",
                        Duration.ofSeconds(5),
                        objectMapper,
                        transport,
                    )

                "anthropic" ->
                    AnthropicModelProviderClient(
                        "synthetic-anthropic-credential",
                        "test-model",
                        Duration.ofSeconds(5),
                        objectMapper,
                        transport,
                    )

                else -> error("Unknown test provider")
            }
        return RemoteBioGenerator(client, objectMapper)
    }

    private fun providerResponse(
        provider: String,
        providerOutput: String,
    ): ProviderHttpResponse {
        val escaped = objectMapper.writeValueAsString(providerOutput)
        val body =
            when (provider) {
                "openai" ->
                    """{"object":"response","status":"completed","output":[{"type":"message","role":"assistant","status":"completed","content":[{"type":"output_text","text":$escaped}]}]}"""

                "gemini" ->
                    """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":$escaped}]}}]}"""

                "anthropic" ->
                    """{"type":"message","role":"assistant","stop_reason":"end_turn","content":[{"type":"text","text":$escaped}]}"""

                else -> error("Unknown test provider")
            }
        return ProviderHttpResponse(200, body)
    }

    private fun createService(
        repository: RecordingRepository,
        generator: BioGenerator,
    ) = CreatePersonService(
        repository,
        generator,
        BioPolicy(),
        TransactionOperations.withoutTransaction(),
        Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneOffset.UTC),
    )

    private fun createCommand() =
        CreatePersonCommand(
            PersonProfile.create(SYNTHETIC_NAME, SYNTHETIC_JOB, listOf(SYNTHETIC_HOBBY)),
            GeoPoint.from(-41.2865, 174.7762),
        )

    private data class AdapterCase(
        val name: String,
        val generator: BioGenerator,
        val transport: RecordingTransport,
        val expectedPath: String,
        val expectedHeaders: Set<String>,
    )

    private data class PersistenceCounts(
        val people: Int,
        val observations: Int,
        val projections: Int,
    )

    private class RecordingTransport(
        private val response: ProviderHttpResponse,
    ) : ProviderHttpTransport {
        var request: ProviderHttpRequest? = null
        var invocations = 0

        override fun send(request: ProviderHttpRequest): ProviderHttpResponse {
            invocations++
            this.request = request
            return response
        }
    }

    private class RecordingRepository : CreatePersonRepository {
        val people = mutableListOf<NewPerson>()
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

    private companion object {
        val PROVIDERS = listOf("openai", "gemini", "anthropic")
        const val SYNTHETIC_NAME = "Synthetic Person"
        const val SYNTHETIC_JOB = "Software engineer"
        const val SYNTHETIC_HOBBY = "hiking"
        const val VALID_PROVIDER_OUTPUT =
            """{"bio_template":"{{NAME}} turns {{HOBBY}} into a quirky side quest after a day as a {{JOB}}."}"""
        const val FORBIDDEN_PROVIDER_OUTPUT =
            """{"bio_template":"{{NAME}} says I am hacked while {{HOBBY}} as a {{JOB}}."}"""
        const val PROMPT_LEAK_PROVIDER_OUTPUT =
            """{"bio_template":"{{NAME}} follows every prompt as a quirky {{JOB}} who enjoys {{HOBBY}}."}"""
    }
}
