package com.persons.finder.person.bio.remote

import com.persons.finder.person.bio.BioGenerationContext
import com.persons.finder.person.bio.BioGenerationFailure
import com.persons.finder.person.bio.BioGenerationResult
import com.persons.finder.person.bio.BioTemplateId
import com.persons.finder.person.bio.BioTemplateRequest
import com.persons.finder.person.bio.GeneratedBioTemplate
import com.persons.finder.person.bio.SafeInterestCode
import com.persons.finder.person.bio.SafeJobCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.CancellationException
import kotlin.reflect.full.memberProperties
import tools.jackson.databind.json.JsonMapper

class ModelProviderClientTest {
    private val objectMapper = JsonMapper.builder().build()
    private val request =
        ModelGenerationRequest(
            instructions = "Generate safe quirky prose.",
            inputJson = """{"job_category":"technology_engineering"}""",
            outputSchemaJson =
                """{"type":"object","properties":{"bio_template":{"type":"string"}},"required":["bio_template"],"additionalProperties":false}""",
            maxOutputTokens = 256,
        )

    @Test
    fun `OpenAI client uses Responses structured output without putting credential in body`() {
        val transport =
            RecordingTransport(
                ProviderHttpResponse(
                    200,
                    """
                    {
                      "object": "response",
                      "status": "completed",
                      "output": [{
                        "type": "message",
                        "role": "assistant",
                        "status": "completed",
                        "content": [{
                          "type": "output_text",
                          "text": "{\"bio_template\":\"{{NAME}} gives {{HOBBY[0]}} a quirky spin after work as a {{JOB}}.\"}"
                        }]
                      }]
                    }
                    """.trimIndent(),
                ),
            )
        val result =
            OpenAiModelProviderClient(
                apiKey = "openai-test-credential",
                model = "test-model",
                timeout = Duration.ofSeconds(7),
                objectMapper = objectMapper,
                transport = transport,
            ).generate(request)

        assertEquals(
            ModelProviderResult.Generated(
                VALID_PROSE_OUTPUT,
            ),
            result,
        )
        val sent = requireNotNull(transport.request)
        assertEquals("POST", sent.method)
        assertEquals("https://api.openai.com/v1/responses", sent.uri.toString())
        assertEquals(null, sent.uri.query)
        assertEquals(setOf("Authorization", "Content-Type"), sent.headers.keys)
        assertEquals("Bearer openai-test-credential", sent.headers["Authorization"])
        assertEquals(Duration.ofSeconds(7), sent.timeout)
        assertFalse(sent.body.contains("openai-test-credential"))
        val body = objectMapper.readTree(sent.body)
        assertEquals(
            setOf("model", "instructions", "input", "store", "max_output_tokens", "text"),
            body.propertyNames().toSet(),
        )
        assertEquals("test-model", body.path("model").stringValue())
        assertEquals(request.inputJson, body.path("input").stringValue())
        assertFalse(body.path("store").asBoolean())
        assertEquals(256, body.path("max_output_tokens").asInt())
        assertEquals("json_schema", body.path("text").path("format").path("type").stringValue())
        assertTrue(body.path("text").path("format").path("strict").asBoolean())
        assertFalse(
            body.path("text").path("format").path("schema")
                .path("additionalProperties")
                .asBoolean(),
        )
        assertEquals(
            OPENAI_BIO_TEMPLATE_PLACEHOLDER_PATTERN,
            body.path("text").path("format").path("schema")
                .path("properties").path("bio_template").path("pattern").stringValue(),
        )
        val placeholderPattern = Regex(OPENAI_BIO_TEMPLATE_PLACEHOLDER_PATTERN)
        listOf(
            "{{NAME}} a {{JOB}} b {{HOBBY[0]}}.",
            "{{NAME}} a {{HOBBY[0]}} b {{JOB}}.",
            "{{JOB}} a {{NAME}} b {{HOBBY[0]}}.",
            "{{JOB}} a {{HOBBY[0]}} b {{NAME}}.",
            "{{HOBBY[0]}} a {{NAME}} b {{JOB}}.",
            "{{HOBBY[0]}} a {{JOB}} b {{NAME}}.",
            "{{NAME}} makes {{HOBBY[0]}} quirky, then {{HOBBY[1]}} even quirkier as a {{JOB}}.",
            "{{NAME}} repeats {{HOBBY[0]}} and {{HOBBY[0]}}.",
            "{{NAME}} gives {{HOBBY[0]}} a quirky spin.",
        ).forEach { template ->
            assertTrue(placeholderPattern.matches(template), template)
        }
        listOf(
            "{{NAME}} gives {{HOBBY[0]}} a quirky spin as a {{JOB}} with {{UNKNOWN}}.",
            "{{NAME}} gives {{HOBBY}} a quirky spin as a {{JOB}}.",
            "{{NAME}} gives {{HOBBY[10]}} a quirky spin as a {{JOB}}.",
            "{{NAME}} gives {{HOBBY[01]}} a quirky spin as a {{JOB}}.",
        ).forEach { template ->
            assertFalse(placeholderPattern.matches(template), template)
        }
    }

    @Test
    fun `OpenAI GPT 5_6 disables reasoning for the bounded structured prose task`() {
        val transport = RecordingTransport(validOpenAiResponse())

        val result =
            OpenAiModelProviderClient(
                apiKey = "openai-test-credential",
                model = "gpt-5.6-luna",
                timeout = Duration.ofSeconds(7),
                objectMapper = objectMapper,
                transport = transport,
            ).generate(request)

        assertEquals(ModelProviderResult.Generated(VALID_PROSE_OUTPUT), result)
        val body = objectMapper.readTree(requireNotNull(transport.request).body)
        assertEquals("none", body.path("reasoning").path("effort").stringValue())
    }

    @Test
    fun `OpenAI client fails closed before transport when the canonical bio schema is missing`() {
        val transport = RecordingTransport(validOpenAiResponse())
        val malformedSchemaRequest =
            ModelGenerationRequest(
                instructions = request.instructions,
                inputJson = request.inputJson,
                outputSchemaJson = """{"type":"object","properties":{}}""",
                maxOutputTokens = request.maxOutputTokens,
            )

        assertEquals(
            ModelProviderResult.Failure(BioGenerationFailure.UNAVAILABLE),
            openAiClient(transport).generate(malformedSchemaRequest),
        )
        assertEquals(null, transport.request)
    }

    @Test
    fun `OpenAI client ignores reasoning items and extracts the single final structured message`() {
        val response =
            ProviderHttpResponse(
                200,
                """
                {
                  "object": "response",
                  "status": "completed",
                  "output": [
                    {"type": "reasoning", "id": "reasoning-item"},
                    {
                      "type": "message",
                      "role": "assistant",
                      "status": "completed",
                      "content": [{
                        "type": "output_text",
                        "text": "{\"bio_template\":\"{{NAME}} gives {{HOBBY[0]}} a quirky spin after work as a {{JOB}}.\"}"
                      }]
                    }
                  ]
                }
                """.trimIndent(),
            )

        assertEquals(
            ModelProviderResult.Generated(VALID_PROSE_OUTPUT),
            openAiClient(ProviderHttpTransport { response }).generate(request),
        )
    }

    @Test
    fun `OpenAI client rejects malformed completed response envelopes`() {
        val malformedResponses =
            listOf(
                """{"status":"completed","output":[{"type":"message","role":"assistant","status":"completed","content":[{"type":"output_text","text":"safe output"}]}]}""",
                """{"object":"response","status":"completed","output":[{"type":"message","role":"user","status":"completed","content":[{"type":"output_text","text":"safe output"}]}]}""",
                """{"object":"response","status":"completed","output":[{"type":"message","role":"assistant","status":"in_progress","content":[{"type":"output_text","text":"safe output"}]}]}""",
                """{"object":"response","status":"completed","output":{"type":"message","role":"assistant","status":"completed","content":[{"type":"output_text","text":"safe output"}]}}""",
                """{"object":"response","status":"completed","output":[{"type":"message","role":"assistant","status":"completed","content":{"type":"output_text","text":"safe output"}}]}""",
            )

        malformedResponses.forEach { body ->
            assertEquals(
                ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT),
                openAiClient(
                    ProviderHttpTransport {
                        ProviderHttpResponse(200, body)
                    },
                ).generate(request),
            )
        }
    }

    @Test
    fun `Gemini client uses generateContent structured output without putting credential in body`() {
        val transport =
            RecordingTransport(
                ProviderHttpResponse(
                    200,
                    """
                    {
                      "candidates": [{
                        "finishReason": "STOP",
                        "content": {
                          "parts": [{
                            "text": "{\"bio_template\":\"{{NAME}} gives {{HOBBY[0]}} a quirky spin after work as a {{JOB}}.\"}"
                          }]
                        }
                      }]
                    }
                    """.trimIndent(),
                ),
            )
        val result =
            GeminiModelProviderClient(
                apiKey = "gemini-test-credential",
                model = "gemini/test model",
                timeout = Duration.ofSeconds(8),
                objectMapper = objectMapper,
                transport = transport,
            ).generate(request)

        assertEquals(
            ModelProviderResult.Generated(
                VALID_PROSE_OUTPUT,
            ),
            result,
        )
        val sent = requireNotNull(transport.request)
        assertEquals("POST", sent.method)
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini%2Ftest%20model:generateContent",
            sent.uri.toString(),
        )
        assertEquals(null, sent.uri.query)
        assertEquals(setOf("x-goog-api-key", "Content-Type"), sent.headers.keys)
        assertEquals("gemini-test-credential", sent.headers["x-goog-api-key"])
        assertEquals(Duration.ofSeconds(8), sent.timeout)
        assertFalse(sent.body.contains("gemini-test-credential"))
        val body = objectMapper.readTree(sent.body)
        assertEquals(
            setOf("systemInstruction", "contents", "generationConfig"),
            body.propertyNames().toSet(),
        )
        assertEquals(
            request.instructions,
            body.path("systemInstruction").path("parts").path(0).path("text").stringValue(),
        )
        assertEquals(
            request.inputJson,
            body.path("contents").path(0).path("parts").path(0).path("text").stringValue(),
        )
        assertEquals(
            "application/json",
            body.path("generationConfig").path("responseMimeType").stringValue(),
        )
        assertFalse(
            body.path("generationConfig").path("responseJsonSchema")
                .path("additionalProperties")
                .asBoolean(),
        )
    }

    @Test
    fun `Anthropic client uses Messages structured output without putting credential in body`() {
        val transport =
            RecordingTransport(
                ProviderHttpResponse(
                    200,
                    """
                    {
                      "type": "message",
                      "role": "assistant",
                      "stop_reason": "end_turn",
                      "content": [{
                        "type": "text",
                        "text": "{\"bio_template\":\"{{NAME}} gives {{HOBBY[0]}} a quirky spin after work as a {{JOB}}.\"}"
                      }]
                    }
                    """.trimIndent(),
                ),
            )
        val result =
            AnthropicModelProviderClient(
                apiKey = "anthropic-test-credential",
                model = "test-model",
                timeout = Duration.ofSeconds(9),
                objectMapper = objectMapper,
                transport = transport,
            ).generate(request)

        assertEquals(
            ModelProviderResult.Generated(
                VALID_PROSE_OUTPUT,
            ),
            result,
        )
        val sent = requireNotNull(transport.request)
        assertEquals("POST", sent.method)
        assertEquals("https://api.anthropic.com/v1/messages", sent.uri.toString())
        assertEquals(null, sent.uri.query)
        assertEquals(setOf("x-api-key", "anthropic-version", "Content-Type"), sent.headers.keys)
        assertEquals("anthropic-test-credential", sent.headers["x-api-key"])
        assertEquals("2023-06-01", sent.headers["anthropic-version"])
        assertEquals(Duration.ofSeconds(9), sent.timeout)
        assertFalse(sent.body.contains("anthropic-test-credential"))
        val body = objectMapper.readTree(sent.body)
        assertEquals(
            setOf("model", "max_tokens", "system", "messages", "output_config"),
            body.propertyNames().toSet(),
        )
        assertEquals("test-model", body.path("model").stringValue())
        assertEquals(256, body.path("max_tokens").asInt())
        assertEquals(request.instructions, body.path("system").stringValue())
        assertEquals(
            request.inputJson,
            body.path("messages").path(0).path("content").stringValue(),
        )
        assertEquals(
            "json_schema",
            body.path("output_config").path("format").path("type").stringValue(),
        )
        assertFalse(
            body.path("output_config").path("format").path("schema")
                .path("additionalProperties")
                .asBoolean(),
        )
    }

    @Test
    fun `provider clients classify timeout rate limit refusal and safety without exposing payloads`() {
        val timeoutTransport =
            ProviderHttpTransport {
                throw HttpTimeoutException("timed out")
            }
        assertEquals(
            ModelProviderResult.Failure(BioGenerationFailure.TIMEOUT),
            openAiClient(timeoutTransport).generate(request),
        )

        assertEquals(
            ModelProviderResult.Failure(BioGenerationFailure.RATE_LIMITED),
            openAiClient(ProviderHttpTransport { ProviderHttpResponse(429, "provider detail") })
                .generate(request),
        )
        assertEquals(
            ModelProviderResult.Failure(BioGenerationFailure.POLICY_REJECTED),
            openAiClient(
                ProviderHttpTransport {
                    ProviderHttpResponse(
                        200,
                        """{"object":"response","status":"completed","output":[{"type":"message","role":"assistant","status":"completed","content":[{"type":"refusal","refusal":"not allowed"}]}]}""",
                    )
                },
            ).generate(request),
        )
        assertEquals(
            ModelProviderResult.Failure(BioGenerationFailure.POLICY_REJECTED),
            geminiClient(
                ProviderHttpTransport {
                    ProviderHttpResponse(
                        200,
                        """{"promptFeedback":{"blockReason":"SAFETY"}}""",
                    )
                },
            ).generate(request),
        )
        assertEquals(
            ModelProviderResult.Failure(BioGenerationFailure.POLICY_REJECTED),
            anthropicClient(
                ProviderHttpTransport {
                    ProviderHttpResponse(
                        200,
                        """{"type":"message","role":"assistant","stop_reason":"refusal","content":[{"type":"text","text":"not allowed"}]}""",
                    )
                },
            ).generate(request),
        )

        listOf(
            openAiClient(oversizedTransport()),
            geminiClient(oversizedTransport()),
            anthropicClient(oversizedTransport()),
        ).forEach { client ->
            assertEquals(
                ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT),
                client.generate(request),
            )
        }
    }

    @Test
    fun `provider truncation signals normalize to invalid output`() {
        listOf(
            "OpenAI" to
                openAiClient(
                    ProviderHttpTransport {
                        ProviderHttpResponse(200, """{"status":"incomplete"}""")
                    },
                ),
            "Gemini" to
                geminiClient(
                    ProviderHttpTransport {
                        ProviderHttpResponse(
                            200,
                            """{"candidates":[{"finishReason":"MAX_TOKENS"}]}""",
                        )
                    },
                ),
            "Anthropic" to
                anthropicClient(
                    ProviderHttpTransport {
                        ProviderHttpResponse(
                            200,
                            """{"type":"message","role":"assistant","stop_reason":"max_tokens","content":[]}""",
                        )
                    },
                ),
        ).forEach { (provider, client) ->
            assertEquals(
                ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT),
                client.generate(request),
                provider,
            )
        }
    }

    @Test
    fun `transport request and response diagnostics omit headers and bodies`() {
        val providerRequest =
            ProviderHttpRequest(
                uri = java.net.URI.create("https://example.test"),
                headers = mapOf("Authorization" to "Bearer secret"),
                body = "customer payload",
                timeout = Duration.ofSeconds(2),
            )
        val providerResponse = ProviderHttpResponse(200, "generated content")

        assertFalse(providerRequest.toString().contains("secret"))
        assertFalse(providerRequest.toString().contains("customer payload"))
        assertFalse(providerResponse.toString().contains("generated content"))
        assertEquals(
            setOf("method", "uri", "headers", "body", "timeout"),
            ProviderHttpRequest::class.memberProperties.map { it.name }.toSet(),
        )
    }

    @Test
    fun `application generator diagnostics contain metadata only`() {
        val canonicalRequest =
            BioTemplateRequest(
                jobCategory = SafeJobCode.TECHNOLOGY_ENGINEERING,
                interests = listOf(SafeInterestCode.OUTDOORS_NATURE),
            )
        val providerRequest =
            ModelGenerationRequest(
                instructions = "synthetic secret prompt",
                inputJson = """{"synthetic":"customer context"}""",
                outputSchemaJson = """{"synthetic":"schema"}""",
                maxOutputTokens = 64,
            )
        val providerResult = ModelProviderResult.Generated("synthetic provider output")
        val applicationResult =
            BioGenerationResult.Template(
                GeneratedBioTemplate.fromCatalog(BioTemplateId.QUIRKY_SIDE_QUEST),
            )

        val diagnostics =
            listOf(
                canonicalRequest.toString(),
                providerRequest.toString(),
                providerResult.toString(),
                applicationResult.toString(),
            ).joinToString()
        listOf(
            "{{NAME}}",
            "en-NZ",
            "technology_engineering",
            "outdoors_nature",
            "synthetic secret prompt",
            "customer context",
            "synthetic provider output",
            "{{JOB}}",
            "{{HOBBY[0]}}",
        ).forEach { forbidden ->
            assertFalse(diagnostics.contains(forbidden), forbidden)
        }
    }

    @Test
    fun `application-owned requests have no customer correlation tracing baggage tools or telemetry fields`() {
        val sentRequests =
            listOf(
                RecordingTransport(validOpenAiResponse()).also { openAiClient(it).generate(request) },
                RecordingTransport(validGeminiResponse()).also { geminiClient(it).generate(request) },
                RecordingTransport(validAnthropicResponse()).also { anthropicClient(it).generate(request) },
            ).map { requireNotNull(it.request) }

        sentRequests.forEach { sent ->
            val completeRequest =
                listOf(
                    sent.method,
                    sent.uri.toString(),
                    sent.headers.entries.joinToString(),
                    sent.body,
                ).joinToString("|")
            listOf(
                "request_id",
                "request-id",
                "idempotency",
                "traceparent",
                "tracestate",
                "baggage",
                "session",
                "user_id",
                "person_id",
                "metadata",
                "telemetry",
                "tools",
                "tool_choice",
                "previous_response_id",
            ).forEach { forbidden ->
                assertFalse(completeRequest.contains(forbidden, ignoreCase = true), forbidden)
            }
        }
        val openAiBody = objectMapper.readTree(sentRequests[0].body)
        assertFalse(openAiBody.path("store").asBoolean())
        assertEquals(
            OPENAI_BIO_TEMPLATE_PLACEHOLDER_PATTERN,
            openAiBody.path("text").path("format").path("schema")
                .path("properties").path("bio_template").path("pattern").stringValue(),
        )
        assertFalse(sentRequests[1].body.contains("\"pattern\""))
        assertFalse(sentRequests[2].body.contains("\"pattern\""))
        assertFalse(sentRequests[1].body.contains("cachedContent"))
        assertFalse(sentRequests[2].body.contains("metadata"))
    }

    @Test
    fun `deadline clamps adapter timeout and expiry causes no transport call`() {
        var now = 100L
        val context = BioGenerationContext.start(Duration.ofSeconds(2)) { now }
        val requestWithDeadline =
            request.copyWithContext(context)
        val recording = RecordingTransport(validOpenAiResponse())
        openAiClient(recording).generate(requestWithDeadline)
        assertEquals(Duration.ofSeconds(2), requireNotNull(recording.request).timeout)

        now += Duration.ofSeconds(2).toNanos()
        val neverCalled =
            RecordingTransport(validOpenAiResponse())
        assertEquals(
            ModelProviderResult.Failure(BioGenerationFailure.TIMEOUT),
            openAiClient(neverCalled).generate(requestWithDeadline),
        )
        assertEquals(null, neverCalled.request)
    }

    @Test
    fun `cancellation propagates and is never normalized or retried`() {
        var invocations = 0
        val cancelling =
            ProviderHttpTransport {
                invocations++
                throw CancellationException("synthetic cancellation")
            }

        assertThrows(CancellationException::class.java) {
            openAiClient(cancelling).generate(request)
        }
        assertEquals(1, invocations)
    }

    @Test
    fun `malformed or structurally incomplete provider success responses normalize to invalid output`() {
        listOf(
            openAiClient(ProviderHttpTransport { ProviderHttpResponse(200, "{") }),
            geminiClient(ProviderHttpTransport { ProviderHttpResponse(200, "{") }),
            anthropicClient(ProviderHttpTransport { ProviderHttpResponse(200, "{") }),
            openAiClient(ProviderHttpTransport { ProviderHttpResponse(200, "{}") }),
            geminiClient(ProviderHttpTransport { ProviderHttpResponse(200, "{}") }),
            anthropicClient(ProviderHttpTransport { ProviderHttpResponse(200, "{}") }),
        ).forEach { client ->
            assertEquals(
                ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT),
                client.generate(request),
            )
        }
    }

    private fun ModelGenerationRequest.copyWithContext(context: BioGenerationContext) =
        ModelGenerationRequest(
            instructions = instructions,
            inputJson = inputJson,
            outputSchemaJson = outputSchemaJson,
            maxOutputTokens = maxOutputTokens,
            context = context,
        )

    private fun validOpenAiResponse() =
        ProviderHttpResponse(
            200,
            """{"object":"response","status":"completed","output":[{"type":"message","role":"assistant","status":"completed","content":[{"type":"output_text","text":"{\"bio_template\":\"{{NAME}} gives {{HOBBY[0]}} a quirky spin after work as a {{JOB}}.\"}"}]}]}""",
        )

    private fun validGeminiResponse() =
        ProviderHttpResponse(
            200,
            """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"{\"bio_template\":\"{{NAME}} gives {{HOBBY[0]}} a quirky spin after work as a {{JOB}}.\"}"}]}}]}""",
        )

    private fun validAnthropicResponse() =
        ProviderHttpResponse(
            200,
            """{"type":"message","role":"assistant","stop_reason":"end_turn","content":[{"type":"text","text":"{\"bio_template\":\"{{NAME}} gives {{HOBBY[0]}} a quirky spin after work as a {{JOB}}.\"}"}]}""",
        )

    private fun openAiClient(transport: ProviderHttpTransport) =
        OpenAiModelProviderClient(
            apiKey = "openai-test-credential",
            model = "test-model",
            timeout = Duration.ofSeconds(5),
            objectMapper = objectMapper,
            transport = transport,
        )

    private fun geminiClient(transport: ProviderHttpTransport) =
        GeminiModelProviderClient(
            apiKey = "gemini-test-credential",
            model = "test-model",
            timeout = Duration.ofSeconds(5),
            objectMapper = objectMapper,
            transport = transport,
        )

    private fun anthropicClient(transport: ProviderHttpTransport) =
        AnthropicModelProviderClient(
            apiKey = "anthropic-test-credential",
            model = "test-model",
            timeout = Duration.ofSeconds(5),
            objectMapper = objectMapper,
            transport = transport,
        )

    private fun oversizedTransport() =
        ProviderHttpTransport {
            ProviderHttpResponse(
                statusCode = 200,
                body = "",
                bodyTooLarge = true,
            )
        }

    private companion object {
        const val VALID_PROSE_OUTPUT =
            """{"bio_template":"{{NAME}} gives {{HOBBY[0]}} a quirky spin after work as a {{JOB}}."}"""
    }

    private class RecordingTransport(
        private val response: ProviderHttpResponse,
    ) : ProviderHttpTransport {
        var request: ProviderHttpRequest? = null

        override fun send(request: ProviderHttpRequest): ProviderHttpResponse {
            this.request = request
            return response
        }
    }
}
