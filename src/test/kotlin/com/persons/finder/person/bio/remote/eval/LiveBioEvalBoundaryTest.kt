package com.persons.finder.person.bio.remote.eval

import com.persons.finder.person.bio.BioGenerationFailure
import com.persons.finder.person.bio.remote.ModelGenerationRequest
import com.persons.finder.person.bio.remote.ModelProviderClient
import com.persons.finder.person.bio.remote.ModelProviderResult
import com.persons.finder.person.bio.remote.ProviderHttpRequest
import com.persons.finder.person.bio.remote.ProviderHttpResponse
import com.persons.finder.person.bio.remote.ProviderHttpTransport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI
import java.time.Duration
import tools.jackson.databind.json.JsonMapper

class LiveBioEvalBoundaryTest {
    private val objectMapper = JsonMapper.builder().build()
    private val corpus = BioEvalCorpusLoader.load()
    private val fingerprint =
        captureApplicationRequestFingerprint(
            objectMapper,
            corpus.cases.first().toRequest(),
        )

    @Test
    fun `application request inspector derives fingerprints and blocks mutation before delegation`() {
        var delegateCalls = 0
        val validRequest = captureValidRequest()
        val inspector =
            InspectingModelProviderClient(
                delegate =
                    ModelProviderClient {
                        delegateCalls++
                        ModelProviderResult.Generated(
                            VALID_PROSE_OUTPUT,
                        )
                    },
                objectMapper = objectMapper,
                expectedFingerprint = fingerprint,
            )

        inspector.generate(validRequest)
        listOf(
            validRequest.copy(instructions = "${validRequest.instructions}\nmutation"),
            validRequest.copy(outputSchemaJson = """{"type":"string"}"""),
            validRequest.copy(maxOutputTokens = validRequest.maxOutputTokens + 1),
            validRequest.copy(inputJson = """{"name":"RAW-SECRET-PROFILE-VALUE"}"""),
        ).forEach { mutated ->
            assertEquals(
                ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT),
                inspector.generate(mutated),
            )
        }

        assertEquals(1, delegateCalls)
        assertEquals(1, inspector.providerClientInvocations)
        assertEquals(
            mapOf(
                "application_prompt_fingerprint" to 1,
                "application_output_schema_fingerprint" to 1,
                "application_output_token_bound" to 1,
                "application_input_allowlist" to 1,
            ),
            inspector.violationCounts,
        )
        assertFalse(inspector.violationCounts.toString().contains(validRequest.instructions))
        assertFalse(inspector.violationCounts.toString().contains("RAW-SECRET-PROFILE-VALUE"))
    }

    @Test
    fun `all provider clients satisfy the live HTTP boundary before fake delegation`() {
        val request = captureValidRequest()

        LiveBioEvalProvider.entries.forEach { provider ->
            var delegateCalls = 0
            val inspector =
                InspectingProviderHttpTransport(
                    delegate =
                        ProviderHttpTransport {
                            delegateCalls++
                            ProviderHttpResponse(200, "{}")
                        },
                    provider = provider,
                    exactModelId = "model-v1",
                    maxCalls = 1,
                )
            val client =
                provider.createClient(
                    credential = "synthetic-secret",
                    model = "model-v1",
                    objectMapper = objectMapper,
                    transport = inspector,
                )

            client.generate(request)

            assertEquals(1, delegateCalls, provider.wireValue)
            assertEquals(1, inspector.delegatedHttpSendAttempts, provider.wireValue)
            assertEquals(emptyMap<String, Int>(), inspector.violationCounts, provider.wireValue)
        }
    }

    @Test
    fun `HTTP inspector enforces destination body and actual call budget without retaining secrets`() {
        var delegateCalls = 0
        val delegate =
            ProviderHttpTransport {
                delegateCalls++
                ProviderHttpResponse(200, "{}")
            }
        val inspector =
            InspectingProviderHttpTransport(
                delegate = delegate,
                provider = LiveBioEvalProvider.OPENAI,
                exactModelId = "model-v1",
                maxCalls = 1,
            )
        val valid = validOpenAiRequest(body = """{"store":false}""")

        inspector.send(valid)
        assertThrows<IllegalStateException> {
            inspector.send(valid)
        }

        assertEquals(1, delegateCalls)
        assertEquals(1, inspector.delegatedHttpSendAttempts)
        assertEquals(mapOf("http_call_budget" to 1), inspector.violationCounts)
        assertFalse(inspector.violationCounts.toString().contains("synthetic-secret"))

        val unsafeInspector =
            InspectingProviderHttpTransport(
                delegate = delegate,
                provider = LiveBioEvalProvider.OPENAI,
                exactModelId = "model-v1",
                maxCalls = 1,
            )
        assertThrows<IllegalStateException> {
            unsafeInspector.send(
                validOpenAiRequest(body = """{"metadata":{"secret":"synthetic-secret"}}"""),
            )
        }
        assertEquals(
            mapOf("http_forbidden_body_field" to 1),
            unsafeInspector.violationCounts,
        )
        assertEquals(1, delegateCalls)

        val portInspector =
            InspectingProviderHttpTransport(
                delegate = delegate,
                provider = LiveBioEvalProvider.OPENAI,
                exactModelId = "model-v1",
                maxCalls = 1,
            )
        assertThrows<IllegalStateException> {
            portInspector.send(
                validOpenAiRequest(
                    body = """{"store":false}""",
                    uri = URI.create("https://api.openai.com:8443/v1/responses"),
                ),
            )
        }
        assertEquals(mapOf("http_port" to 1), portInspector.violationCounts)
        assertEquals(1, delegateCalls)
    }

    private fun captureValidRequest(): ModelGenerationRequest {
        var captured: ModelGenerationRequest? = null
        val generator =
            com.persons.finder.person.bio.remote.RemoteBioGenerator(
                ModelProviderClient { request ->
                    captured = request
                    ModelProviderResult.Generated(
                        VALID_PROSE_OUTPUT,
                    )
                },
                objectMapper,
            )
        generator.generate(corpus.cases.first().toRequest())
        return checkNotNull(captured)
    }

    private fun validOpenAiRequest(
        body: String,
        uri: URI = URI.create("https://api.openai.com/v1/responses"),
    ) =
        ProviderHttpRequest(
            uri = uri,
            headers =
                mapOf(
                    "Authorization" to "Bearer synthetic-secret",
                    "Content-Type" to "application/json",
                ),
            body = body,
            timeout = Duration.ofSeconds(5),
        )

    private companion object {
        const val VALID_PROSE_OUTPUT =
            """{"bio_template":"{{NAME}} turns {{HOBBY}} into a quirky side quest after work as a {{JOB}}."}"""
    }
}
