package com.persons.finder.person.bio.remote.eval

import com.persons.finder.person.bio.remote.ProviderHttpResponse
import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper

class LiveProviderEvidenceTest {
    private val objectMapper = JsonMapper.builder().build()

    @Test
    fun `OpenAI evidence records metered response metadata without retaining content or identifiers`() {
        val evidence =
            LiveProviderEvidenceAccumulator(
                provider = LiveBioEvalProvider.OPENAI,
                exactModelId = "gpt-5.6-luna",
                objectMapper = objectMapper,
            )

        evidence.record(
            ProviderHttpResponse(
                statusCode = 200,
                providerRequestId = "req_provider_visible_123",
                body =
                    """
                    {
                      "object": "response",
                      "id": "resp_provider_visible_456",
                      "model": "gpt-5.6-luna",
                      "status": "completed",
                      "output": [{
                        "type": "message",
                        "role": "assistant",
                        "status": "completed",
                        "content": [{
                          "type": "output_text",
                          "text": "RAW-SECRET-PROVIDER-CONTENT"
                        }]
                      }],
                      "usage": {
                        "input_tokens": 211,
                        "output_tokens": 47,
                        "total_tokens": 258,
                        "input_tokens_details": {
                          "cached_tokens": 17,
                          "cache_write_tokens": 6
                        },
                        "output_tokens_details": {"reasoning_tokens": 9}
                      }
                    }
                    """.trimIndent(),
            ),
            elapsedNanos = 12_900_000L,
        )

        val summary = evidence.summary()
        val rendered = summary.toString()
        assertEquals(1, summary["response_count"])
        assertEquals(mapOf("2xx" to 1), summary["http_status_class_counts"])
        assertEquals(mapOf("completed_structured_output" to 1), summary["diagnostic_counts"])
        assertEquals(1, summary["provider_request_id_count"])
        assertEquals(1, summary["provider_response_id_count"])
        assertEquals(1, summary["usage_reported_response_count"])
        assertEquals(211L, summary["input_tokens"])
        assertEquals(47L, summary["output_tokens"])
        assertEquals(258L, summary["total_tokens"])
        assertEquals(211L, summary["maximum_input_tokens_per_response"])
        assertEquals(47L, summary["maximum_output_tokens_per_response"])
        assertEquals(258L, summary["maximum_total_tokens_per_response"])
        assertEquals(17L, summary["cached_input_tokens"])
        assertEquals(1, summary["cache_write_tokens_reported_response_count"])
        assertEquals(6L, summary["cache_write_tokens"])
        assertEquals(6L, summary["maximum_cache_write_tokens_per_response"])
        assertEquals(9L, summary["reasoning_or_thinking_tokens"])
        assertEquals(12L, summary["maximum_transport_latency_millis"])
        val responses = summary["responses"] as List<*>
        val response = responses.single() as Map<*, *>
        assertEquals(200, response["http_status_code"])
        assertEquals(12L, response["transport_latency_millis"])
        assertEquals(6L, response["cache_write_tokens"])
        assertEquals(9L, response["reasoning_or_thinking_tokens"])
        assertTrue((response["response_body_bytes"] as Int) > 0)
        assertFalse(rendered.contains("RAW-SECRET-PROVIDER-CONTENT"))
        assertFalse(rendered.contains("req_provider_visible_123"))
        assertFalse(rendered.contains("resp_provider_visible_456"))
        assertTrue(
            Regex("[a-f0-9]{64}").matches(
                summary["provider_request_id_sequence_sha256"] as String,
            ),
        )
        assertTrue(
            Regex("[a-f0-9]{64}").matches(
                summary["provider_response_id_sequence_sha256"] as String,
            ),
        )
    }

    @Test
    fun `OpenAI evidence distinguishes output-budget exhaustion from a parser failure`() {
        val evidence =
            LiveProviderEvidenceAccumulator(
                provider = LiveBioEvalProvider.OPENAI,
                exactModelId = "gpt-5.6-luna",
                objectMapper = objectMapper,
            )

        evidence.record(
            ProviderHttpResponse(
                statusCode = 200,
                body =
                    """
                    {
                      "object": "response",
                      "id": "resp_incomplete",
                      "model": "gpt-5.6-luna",
                      "status": "incomplete",
                      "incomplete_details": {"reason": "max_output_tokens"},
                      "output": [{"type": "reasoning"}],
                      "usage": {
                        "input_tokens": 200,
                        "output_tokens": 64,
                        "total_tokens": 264
                      }
                    }
                    """.trimIndent(),
            ),
        )
        evidence.record(ProviderHttpResponse(statusCode = 200, body = "{"))

        val summary = evidence.summary()
        assertEquals(
            mapOf(
                "incomplete_max_output_tokens" to 1,
                "provider_envelope_invalid" to 1,
            ),
            summary["diagnostic_counts"],
        )
        assertEquals(mapOf("max_output_tokens" to 1), summary["incomplete_reason_counts"])
        assertEquals(1, summary["invalid_json_object_count"])
        assertEquals(1, summary["usage_reported_response_count"])
        assertEquals(64L, summary["maximum_output_tokens_per_response"])
        assertEquals(1, summary["reasoning_output_item_count"])
    }

    @Test
    fun `OpenAI evidence rejects wrong object role and message status`() {
        val evidence =
            LiveProviderEvidenceAccumulator(
                provider = LiveBioEvalProvider.OPENAI,
                exactModelId = "gpt-5.6-luna",
                objectMapper = objectMapper,
            )
        listOf(
            """{"status":"completed","output":[{"type":"message","role":"assistant","status":"completed","content":[{"type":"output_text","text":"RAW-MISSING-OBJECT"}]}]}""",
            """{"object":"response","status":"completed","output":[{"type":"message","role":"user","status":"completed","content":[{"type":"output_text","text":"RAW-WRONG-ROLE"}]}]}""",
            """{"object":"response","status":"completed","output":[{"type":"message","role":"assistant","status":"in_progress","content":[{"type":"output_text","text":"RAW-WRONG-STATUS"}]}]}""",
            """{"object":"response","status":"completed","output":{"type":"message","role":"assistant","status":"completed","content":[{"type":"output_text","text":"RAW-WRONG-OUTPUT-SHAPE"}]}}""",
            """{"object":"response","status":"completed","output":[{"type":"message","role":"assistant","status":"completed","content":{"type":"output_text","text":"RAW-WRONG-CONTENT-SHAPE"}}]}""",
        ).forEach { body ->
            evidence.record(ProviderHttpResponse(200, body))
        }

        val summary = evidence.summary()
        assertEquals(5, summary["invalid_provider_envelope_count"])
        assertEquals(
            mapOf("provider_envelope_unexpected" to 5),
            summary["diagnostic_counts"],
        )
        assertFalse(summary.toString().contains("RAW-"))
    }

    @Test
    fun `Gemini and Anthropic usage shapes normalize into the same sanitized evidence`() {
        val gemini =
            LiveProviderEvidenceAccumulator(
                provider = LiveBioEvalProvider.GEMINI,
                exactModelId = "gemini-model",
                objectMapper = objectMapper,
            )
        gemini.record(
            ProviderHttpResponse(
                200,
                """
                {
                  "modelVersion": "gemini-model",
                  "responseId": "gemini-response",
                  "candidates": [{
                    "finishReason": "MAX_TOKENS",
                    "content": {"parts": [{"text": "RAW-GEMINI-CONTENT"}]}
                  }],
                  "usageMetadata": {
                    "promptTokenCount": 181,
                    "candidatesTokenCount": 64,
                    "totalTokenCount": 245,
                    "cachedContentTokenCount": 13,
                    "thoughtsTokenCount": 21,
                    "toolUsePromptTokenCount": 0
                  },
                  "promptFeedback": {
                    "safetyRatings": [{"category": "SAFE"}]
                  }
                }
                """.trimIndent(),
            ),
        )
        val anthropic =
            LiveProviderEvidenceAccumulator(
                provider = LiveBioEvalProvider.ANTHROPIC,
                exactModelId = "claude-model",
                objectMapper = objectMapper,
            )
        anthropic.record(
            ProviderHttpResponse(
                200,
                """
                {
                  "id": "anthropic-response",
                  "type": "message",
                  "role": "assistant",
                  "model": "claude-model",
                  "stop_reason": "end_turn",
                  "content": [{"type": "text", "text": "RAW-ANTHROPIC-CONTENT"}],
                  "usage": {
                    "input_tokens": 190,
                    "output_tokens": 41,
                    "cache_creation_input_tokens": 7,
                    "cache_read_input_tokens": 11,
                    "cache_creation": {
                      "ephemeral_5m_input_tokens": 3,
                      "ephemeral_1h_input_tokens": 4
                    },
                    "output_tokens_details": {"thinking_tokens": 5}
                  }
                }
                """.trimIndent(),
            ),
        )

        assertEquals(
            mapOf("incomplete_max_output_tokens" to 1),
            gemini.summary()["diagnostic_counts"],
        )
        assertEquals(245L, gemini.summary()["total_tokens"])
        assertEquals(21L, gemini.summary()["reasoning_or_thinking_tokens"])
        assertEquals(13L, gemini.summary()["cached_input_tokens"])
        assertEquals(1, gemini.summary()["safety_rating_count"])
        assertFalse(gemini.summary().toString().contains("RAW-GEMINI-CONTENT"))
        assertEquals(
            mapOf("completed_structured_output" to 1),
            anthropic.summary()["diagnostic_counts"],
        )
        assertEquals(249L, anthropic.summary()["total_tokens"])
        assertEquals(208L, anthropic.summary()["effective_input_tokens"])
        assertEquals(1, anthropic.summary()["derived_total_tokens_response_count"])
        assertEquals(11L, anthropic.summary()["cached_input_tokens"])
        assertEquals(7L, anthropic.summary()["cache_creation_input_tokens"])
        assertEquals(3L, anthropic.summary()["cache_creation_five_minute_input_tokens"])
        assertEquals(4L, anthropic.summary()["cache_creation_one_hour_input_tokens"])
        assertEquals(5L, anthropic.summary()["reasoning_or_thinking_tokens"])
        assertFalse(anthropic.summary().toString().contains("RAW-ANTHROPIC-CONTENT"))
    }

    @Test
    fun `extractor evidence rejects missing text and invalid Anthropic envelopes exactly like adapters`() {
        val openAi =
            LiveProviderEvidenceAccumulator(
                provider = LiveBioEvalProvider.OPENAI,
                exactModelId = "gpt-5.6-luna",
                objectMapper = objectMapper,
            )
        openAi.record(
            ProviderHttpResponse(
                200,
                """
                {
                  "object": "response",
                  "status": "completed",
                  "output": [{
                    "type": "reasoning",
                    "content": [{
                      "type": "output_text",
                      "text": "RAW-NON-MESSAGE-TEXT"
                    }]
                  }, {
                    "type": "message",
                    "role": "assistant",
                    "status": "completed",
                    "content": [{"type": "output_text", "text": null}]
                  }]
                }
                """.trimIndent(),
            ),
        )
        val anthropic =
            LiveProviderEvidenceAccumulator(
                provider = LiveBioEvalProvider.ANTHROPIC,
                exactModelId = "claude-model",
                objectMapper = objectMapper,
            )
        anthropic.record(
            ProviderHttpResponse(
                200,
                """
                {
                  "type": "not-a-message",
                  "role": "user",
                  "stop_reason": "end_turn",
                  "content": [{"type": "text", "text": 7}]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(
            mapOf("structured_output_missing" to 1),
            openAi.summary()["diagnostic_counts"],
        )
        assertEquals(0, openAi.summary()["output_text_item_count"])
        assertEquals(1, openAi.summary()["invalid_output_text_item_count"])
        assertFalse(openAi.summary().toString().contains("RAW-NON-MESSAGE-TEXT"))
        assertEquals(
            mapOf("provider_envelope_unexpected" to 1),
            anthropic.summary()["diagnostic_counts"],
        )
        assertEquals(1, anthropic.summary()["invalid_output_text_item_count"])
    }

    @Test
    fun `partial token usage retains output maximum without inventing unavailable metrics`() {
        val evidence =
            LiveProviderEvidenceAccumulator(
                provider = LiveBioEvalProvider.OPENAI,
                exactModelId = "gpt-5.6-luna",
                objectMapper = objectMapper,
            )
        evidence.record(
            ProviderHttpResponse(
                200,
                """
                {
                  "object": "response",
                  "status": "incomplete",
                  "incomplete_details": {"reason": "max_output_tokens"},
                  "output": [],
                  "usage": {"output_tokens": 777}
                }
                """.trimIndent(),
            ),
        )

        val summary = evidence.summary()
        assertEquals(1, summary["usage_reported_response_count"])
        assertEquals(0, summary["input_tokens_reported_response_count"])
        assertEquals(1, summary["output_tokens_reported_response_count"])
        assertEquals(777L, summary["maximum_output_tokens_per_response"])
        assertEquals(0, summary["total_tokens_reported_or_derived_response_count"])
        val response = (summary["responses"] as List<*>).single() as Map<*, *>
        assertEquals(null, response["input_tokens"])
        assertEquals(777L, response["output_tokens"])
        assertEquals(null, response["total_tokens"])
        assertEquals(null, response["cache_write_tokens"])
    }

    @Test
    fun `Gemini blocklist is policy while recitation remains an invalid provider envelope`() {
        val evidence =
            LiveProviderEvidenceAccumulator(
                provider = LiveBioEvalProvider.GEMINI,
                exactModelId = "gemini-model",
                objectMapper = objectMapper,
            )
        listOf("BLOCKLIST", "RECITATION").forEach { finishReason ->
            evidence.record(
                ProviderHttpResponse(
                    200,
                    """
                    {
                      "candidates": [{
                        "finishReason": "$finishReason",
                        "content": {"parts": []}
                      }]
                    }
                    """.trimIndent(),
                ),
            )
        }

        assertEquals(
            mapOf(
                "provider_envelope_unexpected" to 1,
                "provider_refusal" to 1,
            ),
            evidence.summary()["diagnostic_counts"],
        )
        assertEquals(
            mapOf("BLOCKLIST" to 1, "RECITATION" to 1),
            evidence.summary()["finish_reason_counts"],
        )
    }

    @Test
    fun `provider errors and transport failures retain categories and timing but not messages`() {
        val evidence =
            LiveProviderEvidenceAccumulator(
                provider = LiveBioEvalProvider.OPENAI,
                exactModelId = "gpt-5.6-luna",
                objectMapper = objectMapper,
            )
        evidence.record(
            response =
                ProviderHttpResponse(
                    statusCode = 429,
                    body =
                        """
                        {
                          "error": {
                            "code": "rate_limit_exceeded",
                            "message": "RAW-PROVIDER-ERROR-MESSAGE"
                          }
                        }
                        """.trimIndent(),
                ),
            elapsedNanos = 3_000_000L,
        )
        evidence.recordTransportFailure(
            failure = IOException("RAW-TRANSPORT-ERROR-MESSAGE"),
            elapsedNanos = 5_000_000L,
        )

        val summary = evidence.summary()
        assertEquals(mapOf("429" to 1), summary["http_status_code_counts"])
        assertEquals(mapOf("rate_limit" to 1), summary["provider_error_category_counts"])
        assertEquals(mapOf("io" to 1), summary["transport_failure_category_counts"])
        assertEquals(1, summary["transport_failure_count"])
        assertEquals(listOf("response", "transport_failure"), (summary["attempts"] as List<*>).map {
            (it as Map<*, *>)["kind"]
        })
        assertEquals(listOf(1, 2), (summary["attempts"] as List<*>).map {
            (it as Map<*, *>)["attempt_index"]
        })
        assertFalse(summary.toString().contains("RAW-PROVIDER-ERROR-MESSAGE"))
        assertFalse(summary.toString().contains("RAW-TRANSPORT-ERROR-MESSAGE"))
    }

    @Test
    fun `OpenAI terminal errors and safe response headers explain configuration failures`() {
        val evidence =
            LiveProviderEvidenceAccumulator(
                provider = LiveBioEvalProvider.OPENAI,
                exactModelId = "gpt-5.6-luna",
                objectMapper = objectMapper,
            )
        evidence.record(
            ProviderHttpResponse(
                statusCode = 400,
                safeMetadataHeaders =
                    mapOf(
                        "openai-processing-ms" to "83",
                        "x-ratelimit-limit-requests" to "500",
                        "x-ratelimit-remaining-requests" to "499",
                        "x-ratelimit-reset-requests" to "120ms",
                    ),
                body =
                    """
                    {
                      "error": {
                        "code": "unsupported_parameter",
                        "type": "invalid_request_error",
                        "param": "max_output_tokens",
                        "message": "RAW-OPENAI-ERROR-DETAIL"
                      }
                    }
                    """.trimIndent(),
            ),
            elapsedNanos = 91_000_000L,
        )

        val summary = evidence.summary()
        val response = (summary["responses"] as List<*>).single() as Map<*, *>
        assertEquals(mapOf("invalid_request" to 1), summary["provider_error_category_counts"])
        assertEquals(mapOf("unsupported_parameter" to 1), summary["provider_error_code_counts"])
        assertEquals(mapOf("invalid_request_error" to 1), summary["provider_error_type_counts"])
        assertEquals(mapOf("max_output_tokens" to 1), summary["provider_error_parameter_counts"])
        assertEquals(
            mapOf("invalid_request" to 1),
            summary["terminal_provider_failure_category_counts"],
        )
        assertEquals("invalid_request", evidence.latestTerminalProviderFailureCategory)
        assertEquals(true, response["provider_envelope_valid"])
        assertEquals(
            mapOf(
                "openai-processing-ms" to "83",
                "x-ratelimit-limit-requests" to "500",
                "x-ratelimit-remaining-requests" to "499",
                "x-ratelimit-reset-requests" to "120ms",
            ),
            response["safe_metadata_headers"],
        )
        assertFalse(summary.toString().contains("RAW-OPENAI-ERROR-DETAIL"))
    }

    @Test
    fun `OpenAI insufficient quota is a terminal billing failure despite HTTP 429`() {
        val evidence =
            LiveProviderEvidenceAccumulator(
                provider = LiveBioEvalProvider.OPENAI,
                exactModelId = "gpt-5.6-luna",
                objectMapper = objectMapper,
            )
        evidence.record(
            ProviderHttpResponse(
                statusCode = 429,
                body =
                    """
                    {
                      "error": {
                        "code": "insufficient_quota",
                        "type": "insufficient_quota",
                        "message": "RAW-BILLING-DETAIL"
                      }
                    }
                    """.trimIndent(),
            ),
        )

        val summary = evidence.summary()
        assertEquals(mapOf("quota" to 1), summary["provider_error_category_counts"])
        assertEquals(
            mapOf("billing" to 1),
            summary["terminal_provider_failure_category_counts"],
        )
        assertEquals("billing", evidence.latestTerminalProviderFailureCategory)
        assertFalse(summary.toString().contains("RAW-BILLING-DETAIL"))
    }

    @Test
    fun `provider response metadata rejects non allowlisted names and values`() {
        assertThrows<IllegalArgumentException> {
            ProviderHttpResponse(
                statusCode = 200,
                body = "{}",
                safeMetadataHeaders =
                    mapOf("x-organization-id" to "RAW-SECRET-ORGANIZATION"),
            )
        }
        assertThrows<IllegalArgumentException> {
            ProviderHttpResponse(
                statusCode = 200,
                body = "{}",
                safeMetadataHeaders =
                    mapOf("openai-processing-ms" to "RAW-SECRET-VALUE"),
            )
        }
    }
}
