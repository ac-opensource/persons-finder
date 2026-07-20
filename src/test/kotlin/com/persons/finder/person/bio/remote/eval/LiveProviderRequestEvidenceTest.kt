package com.persons.finder.person.bio.remote.eval

import com.persons.finder.person.bio.BioGenerationResult
import com.persons.finder.person.bio.remote.ModelGenerationRequest
import com.persons.finder.person.bio.remote.ModelProviderClient
import com.persons.finder.person.bio.remote.ModelProviderResult
import com.persons.finder.person.bio.remote.ProviderHttpRequest
import com.persons.finder.person.bio.remote.ProviderHttpResponse
import com.persons.finder.person.bio.remote.ProviderHttpTransport
import com.persons.finder.person.bio.remote.RemoteBioGenerator
import java.net.URI
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class LiveProviderRequestEvidenceTest {
    private val objectMapper = JsonMapper.builder().build()
    private val applicationRequest = captureApplicationRequest()
    private val fingerprint =
        ApplicationRequestFingerprint(
            promptSha256 = BioEvalHash.sha256(applicationRequest.instructions),
            outputSchemaSha256 = BioEvalHash.sha256(applicationRequest.outputSchemaJson),
            maxOutputTokens = applicationRequest.maxOutputTokens,
        )

    @Test
    fun `all provider request shapes record complete sanitized configuration evidence`() {
        val modelByProvider =
            mapOf(
                LiveBioEvalProvider.OPENAI to "gpt-5.6-luna",
                LiveBioEvalProvider.GEMINI to "gemini/model v1",
                LiveBioEvalProvider.ANTHROPIC to "claude-model",
            )

        modelByProvider.forEach { (provider, model) ->
            val request = captureProviderRequest(provider, model)
            val evidence =
                LiveProviderRequestEvidenceAccumulator(
                    provider = provider,
                    exactModelId = model,
                    expectedHeaderValueFingerprints =
                        provider.expectedHeaderValueFingerprints("synthetic-secret"),
                    expectedFingerprint = fingerprint,
                    objectMapper = objectMapper,
                )

            val sanitized = evidence.record(request)
            val summary = evidence.summary()
            val rendered = summary.toString()

            assertTrue(sanitized.expectedConfigurationMatched, provider.wireValue)
            assertTrue(sanitized.destinationMatched, provider.wireValue)
            assertTrue(sanitized.headersMatched, provider.wireValue)
            assertTrue(sanitized.promptFingerprintMatched == true, provider.wireValue)
            assertTrue(sanitized.inputAllowlistMatched == true, provider.wireValue)
            assertEquals(applicationRequest.maxOutputTokens, sanitized.maxOutputTokens)
            assertTrue(sanitized.systemInstructionUtf8Bytes!! > 0)
            assertTrue(sanitized.inputPayloadUtf8Bytes!! > 0)
            assertTrue(sanitized.outputSchemaUtf8Bytes!! > 0)
            assertEquals(0, sanitized.unexpectedHeaderNameCount)
            assertEquals(0, sanitized.unexpectedConfigurationFieldCount)
            assertEquals(true, summary["all_requests_match_expected_configuration"])
            assertFalse(rendered.contains("synthetic-secret"))
            assertFalse(rendered.contains(applicationRequest.instructions))
            assertFalse(rendered.contains(applicationRequest.inputJson))
            assertFalse(rendered.contains(applicationRequest.outputSchemaJson))
        }
    }

    @Test
    fun `OpenAI evidence captures reasoning and rejects changed or unexpected controls`() {
        val valid = captureProviderRequest(LiveBioEvalProvider.OPENAI, "gpt-5.6-luna")
        val evidence =
            LiveProviderRequestEvidenceAccumulator(
                provider = LiveBioEvalProvider.OPENAI,
                exactModelId = "gpt-5.6-luna",
                expectedHeaderValueFingerprints =
                    LiveBioEvalProvider.OPENAI
                        .expectedHeaderValueFingerprints("synthetic-secret"),
                expectedFingerprint = fingerprint,
                objectMapper = objectMapper,
            )

        val validEvidence = evidence.record(valid)
        val changedMax =
            evidence.record(
                valid.copyBody(
                    valid.body.replace(
                        "\"max_output_tokens\":${applicationRequest.maxOutputTokens}",
                        "\"max_output_tokens\":7",
                    ),
                ),
            )
        val changedReasoning =
            evidence.record(
                valid.copyBody(
                    valid.body.replace(
                        "\"reasoning\":{\"effort\":\"none\"}",
                        "\"reasoning\":{\"effort\":\"high\"}",
                    ),
                ),
            )
        val unexpectedSampling =
            evidence.record(
                valid.copyBody(
                    valid.body.dropLast(1) + ""","temperature":1.0}""",
                ),
            )

        assertEquals("none", validEvidence.reasoningEffort)
        assertTrue(validEvidence.expectedConfigurationMatched)
        assertFalse(changedMax.expectedConfigurationMatched)
        assertEquals(7, changedMax.maxOutputTokens)
        assertFalse(changedReasoning.expectedConfigurationMatched)
        assertEquals("high", changedReasoning.reasoningEffort)
        assertFalse(unexpectedSampling.expectedConfigurationMatched)
        assertEquals(1.0, unexpectedSampling.temperature)
        assertEquals(1, unexpectedSampling.unexpectedConfigurationFieldCount)
        assertEquals(1, evidence.summary()["requests_with_explicit_sampling_configuration"])
    }

    @Test
    fun `malformed duplicate JSON and unexpected header names remain content free`() {
        val evidence =
            LiveProviderRequestEvidenceAccumulator(
                provider = LiveBioEvalProvider.OPENAI,
                exactModelId = "gpt-5.6-luna",
                expectedHeaderValueFingerprints =
                    LiveBioEvalProvider.OPENAI
                        .expectedHeaderValueFingerprints("synthetic-secret"),
                expectedFingerprint = fingerprint,
                objectMapper = objectMapper,
            )
        val request =
            ProviderHttpRequest(
                uri = URI.create("https://api.openai.com/v1/responses"),
                headers =
                    mapOf(
                        "Authorization" to "Bearer synthetic-secret",
                        "Content-Type" to "application/json",
                        "RAW-SECRET-AS-HEADER-NAME" to "RAW-SECRET-HEADER-VALUE",
                    ),
                body =
                    """{"model":"gpt-5.6-luna","model":"RAW-SECRET-DUPLICATE"}""",
                timeout = Duration.ofSeconds(5),
            )

        val sanitized = evidence.record(request)
        val rendered = evidence.summary().toString()

        assertFalse(sanitized.jsonObjectParsed)
        assertFalse(sanitized.expectedConfigurationMatched)
        assertEquals(1, sanitized.unexpectedHeaderNameCount)
        assertEquals(
            listOf("Authorization", "Content-Type"),
            sanitized.approvedHeaderNamesPresent,
        )
        assertFalse(rendered.contains("RAW-SECRET"))
        assertFalse(rendered.contains("synthetic-secret"))
    }

    private fun captureApplicationRequest(): ModelGenerationRequest {
        val corpus = BioEvalCorpusLoader.load()
        var captured: ModelGenerationRequest? = null
        val generator =
            RemoteBioGenerator(
                providerClient =
                    ModelProviderClient { request ->
                        captured = request
                        ModelProviderResult.Generated(
                            """{"bio_template":"{{NAME}} enjoys {{HOBBY}} as a {{JOB}}."}""",
                        )
                    },
                objectMapper = objectMapper,
            )
        check(generator.generate(corpus.cases.first().toRequest()) is BioGenerationResult.Template)
        return checkNotNull(captured)
    }

    private fun captureProviderRequest(
        provider: LiveBioEvalProvider,
        model: String,
    ): ProviderHttpRequest {
        var captured: ProviderHttpRequest? = null
        provider.createClient(
            credential = "synthetic-secret",
            model = model,
            objectMapper = objectMapper,
            transport =
                ProviderHttpTransport { request ->
                    captured = request
                    ProviderHttpResponse(200, "{}")
                },
        ).generate(applicationRequest)
        return checkNotNull(captured)
    }

    private fun ProviderHttpRequest.copyBody(body: String): ProviderHttpRequest =
        ProviderHttpRequest(
            method = method,
            uri = uri,
            headers = headers,
            body = body,
            timeout = timeout,
        )
}
