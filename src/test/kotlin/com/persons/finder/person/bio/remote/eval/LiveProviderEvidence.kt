package com.persons.finder.person.bio.remote.eval

import com.persons.finder.person.bio.remote.ProviderHttpResponse
import com.persons.finder.person.bio.remote.GeminiFinishDisposition
import com.persons.finder.person.bio.remote.classifyGeminiFinishReason
import java.io.IOException
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CancellationException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

internal data class SanitizedProviderResponseEvidence(
    val httpStatusCode: Int,
    val httpStatusClass: String,
    val responseBodyBytes: Int?,
    val transportLatencyMillis: Long,
    val safeMetadataHeaders: Map<String, String>,
    val bodyTooLarge: Boolean,
    val jsonObjectParsed: Boolean,
    val providerEnvelopeValid: Boolean,
    val providerModelMatched: Boolean?,
    val providerStatus: String?,
    val finishReason: String?,
    val incompleteReason: String?,
    val providerSafetyReason: String?,
    val providerErrorCategory: String?,
    val providerErrorCode: String?,
    val providerErrorType: String?,
    val providerErrorParameter: String?,
    val terminalProviderFailureCategory: String?,
    val serviceTier: String?,
    val providerCreatedAtEpochSeconds: Long?,
    val diagnostic: String,
    val providerRequestIdSha256: String?,
    val providerResponseIdSha256: String?,
    val inputTokens: Long?,
    val outputTokens: Long?,
    val totalTokens: Long?,
    val effectiveInputTokens: Long?,
    val totalTokensDerived: Boolean,
    val cachedInputTokens: Long?,
    val cacheWriteTokens: Long?,
    val cacheCreationInputTokens: Long?,
    val cacheCreationFiveMinuteInputTokens: Long?,
    val cacheCreationOneHourInputTokens: Long?,
    val reasoningOrThinkingTokens: Long?,
    val toolUsePromptTokens: Long?,
    val providerOutputItemCount: Int,
    val reasoningOutputItemCount: Int,
    val outputTextItemCount: Int,
    val invalidOutputTextItemCount: Int,
    val providerOutputTextUtf8Bytes: Int,
    val providerOutputTextCodePoints: Int,
    val refusalItemCount: Int,
    val safetyRatingCount: Int,
    val usageEnvelopePresent: Boolean,
)

internal data class SanitizedTransportFailureEvidence(
    val category: String,
    val transportLatencyMillis: Long,
)

private sealed interface SanitizedProviderAttemptEvidence {
    data class Response(
        val evidence: SanitizedProviderResponseEvidence,
    ) : SanitizedProviderAttemptEvidence

    data class TransportFailure(
        val evidence: SanitizedTransportFailureEvidence,
    ) : SanitizedProviderAttemptEvidence
}

private data class SanitizedProviderErrorEvidence(
    val category: String?,
    val code: String?,
    val type: String?,
    val parameter: String?,
)

internal class LiveProviderEvidenceAccumulator(
    private val provider: LiveBioEvalProvider,
    private val exactModelId: String,
    private val objectMapper: JsonMapper,
) {
    private val attempts = mutableListOf<SanitizedProviderAttemptEvidence>()

    val latestTerminalProviderFailureCategory: String?
        get() =
            (attempts.lastOrNull() as? SanitizedProviderAttemptEvidence.Response)
                ?.evidence
                ?.terminalProviderFailureCategory

    fun record(
        response: ProviderHttpResponse,
        elapsedNanos: Long = 0L,
    ) {
        attempts += SanitizedProviderAttemptEvidence.Response(sanitize(response, elapsedNanos))
    }

    fun recordTransportFailure(
        failure: Throwable,
        elapsedNanos: Long,
    ) {
        attempts +=
            SanitizedProviderAttemptEvidence.TransportFailure(
                SanitizedTransportFailureEvidence(
                    category = failure.transportFailureCategory(),
                    transportLatencyMillis = elapsedNanos.toElapsedMillis(),
                ),
            )
    }

    fun summary(): Map<String, Any> {
        val responses =
            attempts.mapNotNull { attempt ->
                (attempt as? SanitizedProviderAttemptEvidence.Response)?.evidence
            }
        val transportFailures =
            attempts.mapNotNull { attempt ->
                (attempt as? SanitizedProviderAttemptEvidence.TransportFailure)?.evidence
            }
        val requestIdHashes = responses.mapNotNull { it.providerRequestIdSha256 }
        val responseIdHashes = responses.mapNotNull { it.providerResponseIdSha256 }
        val usageResponses =
            responses.filter {
                it.inputTokens != null ||
                    it.outputTokens != null ||
                    it.totalTokens != null
            }
        val inputTokens = responses.mapNotNull { it.inputTokens }
        val outputTokens = responses.mapNotNull { it.outputTokens }
        val totalTokens = responses.mapNotNull { it.totalTokens }
        val effectiveInputTokens = responses.mapNotNull { it.effectiveInputTokens }
        val cachedInputTokens = responses.mapNotNull { it.cachedInputTokens }
        val cacheWriteTokens = responses.mapNotNull { it.cacheWriteTokens }
        val cacheCreationInputTokens = responses.mapNotNull { it.cacheCreationInputTokens }
        val cacheCreationFiveMinuteInputTokens =
            responses.mapNotNull { it.cacheCreationFiveMinuteInputTokens }
        val cacheCreationOneHourInputTokens =
            responses.mapNotNull { it.cacheCreationOneHourInputTokens }
        val reasoningOrThinkingTokens =
            responses.mapNotNull { it.reasoningOrThinkingTokens }
        val toolUsePromptTokens = responses.mapNotNull { it.toolUsePromptTokens }
        val modelMatchCounts =
            responses
                .map {
                    when (it.providerModelMatched) {
                        true -> "matched"
                        false -> "mismatched"
                        null -> "unavailable"
                    }
                }.countValues()
        return linkedMapOf<String, Any>(
            "attempt_count" to attempts.size,
            "attempts" to
                attempts.mapIndexed { index, attempt ->
                    when (attempt) {
                        is SanitizedProviderAttemptEvidence.Response ->
                            attempt.evidence.toSanitizedMap(index + 1)

                        is SanitizedProviderAttemptEvidence.TransportFailure ->
                            attempt.evidence.toSanitizedMap(index + 1)
                    }
                },
            "response_count" to responses.size,
            "responses" to
                attempts.mapIndexedNotNull { index, attempt ->
                    (attempt as? SanitizedProviderAttemptEvidence.Response)
                        ?.evidence
                        ?.toSanitizedMap(index + 1)
                },
            "http_status_code_counts" to responses.map { it.httpStatusCode.toString() }.countValues(),
            "http_status_class_counts" to responses.countBy(SanitizedProviderResponseEvidence::httpStatusClass),
            "response_body_bytes" to responses.mapNotNull { it.responseBodyBytes }.sum(),
            "maximum_response_body_bytes" to (responses.mapNotNull { it.responseBodyBytes }.maxOrNull() ?: 0),
            "transport_latency_millis" to responses.sumOf { it.transportLatencyMillis },
            "maximum_transport_latency_millis" to
                (responses.maxOfOrNull { it.transportLatencyMillis } ?: 0L),
            "transport_failure_count" to transportFailures.size,
            "transport_failures" to
                attempts.mapIndexedNotNull { index, attempt ->
                    (attempt as? SanitizedProviderAttemptEvidence.TransportFailure)
                        ?.evidence
                        ?.toSanitizedMap(index + 1)
                },
            "transport_failure_category_counts" to
                transportFailures.map(SanitizedTransportFailureEvidence::category).countValues(),
            "body_too_large_count" to responses.count(SanitizedProviderResponseEvidence::bodyTooLarge),
            "invalid_json_object_count" to responses.count { !it.jsonObjectParsed },
            "invalid_provider_envelope_count" to responses.count { !it.providerEnvelopeValid },
            "provider_model_match_counts" to modelMatchCounts,
            "provider_status_counts" to responses.mapNotNull { it.providerStatus }.countValues(),
            "finish_reason_counts" to responses.mapNotNull { it.finishReason }.countValues(),
            "incomplete_reason_counts" to responses.mapNotNull { it.incompleteReason }.countValues(),
            "provider_safety_reason_counts" to
                responses.mapNotNull { it.providerSafetyReason }.countValues(),
            "provider_error_category_counts" to
                responses.mapNotNull { it.providerErrorCategory }.countValues(),
            "provider_error_code_counts" to
                responses.mapNotNull { it.providerErrorCode }.countValues(),
            "provider_error_type_counts" to
                responses.mapNotNull { it.providerErrorType }.countValues(),
            "provider_error_parameter_counts" to
                responses.mapNotNull { it.providerErrorParameter }.countValues(),
            "terminal_provider_failure_category_counts" to
                responses.mapNotNull { it.terminalProviderFailureCategory }.countValues(),
            "service_tier_counts" to responses.mapNotNull { it.serviceTier }.countValues(),
            "safe_metadata_header_presence_counts" to
                responses
                    .flatMap { it.safeMetadataHeaders.keys }
                    .countValues(),
            "diagnostic_counts" to responses.map(SanitizedProviderResponseEvidence::diagnostic).countValues(),
            "provider_request_id_count" to requestIdHashes.size,
            "unique_provider_request_id_count" to requestIdHashes.distinct().size,
            "provider_request_id_sequence_sha256" to
                (sequenceHash(requestIdHashes) ?: "unavailable"),
            "provider_response_id_count" to responseIdHashes.size,
            "unique_provider_response_id_count" to responseIdHashes.distinct().size,
            "provider_response_id_sequence_sha256" to
                (sequenceHash(responseIdHashes) ?: "unavailable"),
            "usage_reported_response_count" to usageResponses.size,
            "input_tokens_reported_response_count" to inputTokens.size,
            "input_tokens" to inputTokens.sum(),
            "maximum_input_tokens_per_response" to (inputTokens.maxOrNull() ?: 0L),
            "output_tokens_reported_response_count" to outputTokens.size,
            "output_tokens" to outputTokens.sum(),
            "maximum_output_tokens_per_response" to (outputTokens.maxOrNull() ?: 0L),
            "total_tokens_reported_or_derived_response_count" to totalTokens.size,
            "total_tokens" to totalTokens.sum(),
            "maximum_total_tokens_per_response" to (totalTokens.maxOrNull() ?: 0L),
            "derived_total_tokens_response_count" to
                responses.count(SanitizedProviderResponseEvidence::totalTokensDerived),
            "effective_input_tokens_reported_or_derived_response_count" to
                effectiveInputTokens.size,
            "effective_input_tokens" to effectiveInputTokens.sum(),
            "maximum_effective_input_tokens_per_response" to
                (effectiveInputTokens.maxOrNull() ?: 0L),
            "cached_input_tokens_reported_response_count" to cachedInputTokens.size,
            "cached_input_tokens" to cachedInputTokens.sum(),
            "maximum_cached_input_tokens_per_response" to
                (cachedInputTokens.maxOrNull() ?: 0L),
            "cache_write_tokens_reported_response_count" to cacheWriteTokens.size,
            "cache_write_tokens" to cacheWriteTokens.sum(),
            "maximum_cache_write_tokens_per_response" to
                (cacheWriteTokens.maxOrNull() ?: 0L),
            "cache_creation_input_tokens_reported_response_count" to
                cacheCreationInputTokens.size,
            "cache_creation_input_tokens" to cacheCreationInputTokens.sum(),
            "maximum_cache_creation_input_tokens_per_response" to
                (cacheCreationInputTokens.maxOrNull() ?: 0L),
            "cache_creation_five_minute_input_tokens_reported_response_count" to
                cacheCreationFiveMinuteInputTokens.size,
            "cache_creation_five_minute_input_tokens" to
                cacheCreationFiveMinuteInputTokens.sum(),
            "cache_creation_one_hour_input_tokens_reported_response_count" to
                cacheCreationOneHourInputTokens.size,
            "cache_creation_one_hour_input_tokens" to
                cacheCreationOneHourInputTokens.sum(),
            "reasoning_or_thinking_tokens_reported_response_count" to
                reasoningOrThinkingTokens.size,
            "reasoning_or_thinking_tokens" to reasoningOrThinkingTokens.sum(),
            "maximum_reasoning_or_thinking_tokens_per_response" to
                (reasoningOrThinkingTokens.maxOrNull() ?: 0L),
            "tool_use_prompt_tokens_reported_response_count" to toolUsePromptTokens.size,
            "tool_use_prompt_tokens" to toolUsePromptTokens.sum(),
            "maximum_tool_use_prompt_tokens_per_response" to
                (toolUsePromptTokens.maxOrNull() ?: 0L),
            "provider_output_item_count" to
                responses.sumOf(SanitizedProviderResponseEvidence::providerOutputItemCount),
            "reasoning_output_item_count" to
                responses.sumOf(SanitizedProviderResponseEvidence::reasoningOutputItemCount),
            "output_text_item_count" to responses.sumOf(SanitizedProviderResponseEvidence::outputTextItemCount),
            "invalid_output_text_item_count" to
                responses.sumOf(SanitizedProviderResponseEvidence::invalidOutputTextItemCount),
            "provider_output_text_utf8_bytes" to
                responses.sumOf(SanitizedProviderResponseEvidence::providerOutputTextUtf8Bytes),
            "maximum_provider_output_text_utf8_bytes_per_response" to
                (
                    responses.maxOfOrNull(
                        SanitizedProviderResponseEvidence::providerOutputTextUtf8Bytes,
                    ) ?: 0
                ),
            "provider_output_text_code_points" to
                responses.sumOf(SanitizedProviderResponseEvidence::providerOutputTextCodePoints),
            "maximum_provider_output_text_code_points_per_response" to
                (
                    responses.maxOfOrNull(
                        SanitizedProviderResponseEvidence::providerOutputTextCodePoints,
                    ) ?: 0
                ),
            "refusal_item_count" to responses.sumOf(SanitizedProviderResponseEvidence::refusalItemCount),
            "safety_rating_count" to responses.sumOf(SanitizedProviderResponseEvidence::safetyRatingCount),
            "usage_envelope_present_response_count" to
                responses.count(SanitizedProviderResponseEvidence::usageEnvelopePresent),
        )
    }

    private fun sanitize(
        response: ProviderHttpResponse,
        elapsedNanos: Long,
    ): SanitizedProviderResponseEvidence {
        val requestIdHash =
            response.providerRequestId
                ?.takeIf(SAFE_PROVIDER_ID::matches)
                ?.let(BioEvalHash::sha256)
        if (response.bodyTooLarge) {
            return emptyEvidence(
                response = response,
                requestIdHash = requestIdHash,
                elapsedNanos = elapsedNanos,
                diagnostic = "provider_response_too_large",
            )
        }
        val body =
            try {
                objectMapper.readTree(response.body)
            } catch (_: RuntimeException) {
                null
            }
        if (body == null || !body.isObject) {
            return emptyEvidence(
                response = response,
                requestIdHash = requestIdHash,
                elapsedNanos = elapsedNanos,
                diagnostic = "provider_envelope_invalid",
            )
        }
        return when (provider) {
            LiveBioEvalProvider.OPENAI -> openAiEvidence(response, body, requestIdHash, elapsedNanos)
            LiveBioEvalProvider.GEMINI -> geminiEvidence(response, body, requestIdHash, elapsedNanos)
            LiveBioEvalProvider.ANTHROPIC -> anthropicEvidence(response, body, requestIdHash, elapsedNanos)
        }
    }

    private fun openAiEvidence(
        response: ProviderHttpResponse,
        body: JsonNode,
        requestIdHash: String?,
        elapsedNanos: Long,
    ): SanitizedProviderResponseEvidence {
        val status = body.safeValue("status", OPENAI_STATUSES)
        val incompleteReason =
            body.path("incomplete_details").safeValue("reason", OPENAI_INCOMPLETE_REASONS)
        val output = body.path("output").toList()
        val messages = output.filter { item -> item.text("type") == "message" }
        val completedMessage =
            messages.singleOrNull()?.takeIf { message ->
                message.text("role") == "assistant" &&
                    message.text("status") == "completed"
            }
        val content =
            messages
                .flatMap { item -> item.path("content").toList() }
        val outputTextItems = content.filter { it.text("type") == "output_text" }
        val outputTexts =
            outputTextItems.mapNotNull { item ->
                item.get("text")?.takeIf(JsonNode::isString)?.stringValue()
            }
        val outputTextCount = outputTexts.size
        val invalidOutputTextCount = outputTextItems.size - outputTextCount
        val refusalCount = content.count { it.text("type") == "refusal" }
        val usage = body.path("usage")
        val inputTokens = usage.long("input_tokens")
        val outputTokens = usage.long("output_tokens")
        val reportedTotalTokens = usage.long("total_tokens")
        val providerError = body.openAiErrorEvidence()
        val responseShapeValid =
            body.text("object") == "response" &&
                body.path("output").isArray
        val completedMessageShapeValid =
            completedMessage != null &&
                completedMessage.path("content").isArray
        val providerEnvelopeValid =
            if (response.statusCode in 200..299) {
                responseShapeValid &&
                    when (status) {
                        "completed" ->
                            completedMessageShapeValid &&
                                (
                                    (outputTextCount == 1 && refusalCount == 0) ||
                                        (outputTextCount == 0 && refusalCount == 1)
                                )

                        "incomplete" -> incompleteReason != null
                        "failed", "cancelled" -> true
                        else -> false
                    }
            } else {
                body.path("error").isObject
            }
        return evidence(
            response = response,
            requestIdHash = requestIdHash,
            elapsedNanos = elapsedNanos,
            body = body,
            providerStatus = status,
            finishReason = null,
            incompleteReason = incompleteReason,
            providerSafetyReason =
                when {
                    refusalCount > 0 -> "refusal"
                    incompleteReason == "content_filter" -> "content_filter"
                    else -> null
                },
            providerErrorCategory = providerError.category,
            providerErrorCode = providerError.code,
            providerErrorType = providerError.type,
            providerErrorParameter = providerError.parameter,
            providerEnvelopeValid = providerEnvelopeValid,
            diagnostic =
                when {
                    response.statusCode !in 200..299 -> "provider_http_non_success"
                    !responseShapeValid ||
                        (status == "completed" && !completedMessageShapeValid) ->
                        "provider_envelope_unexpected"

                    refusalCount > 0 -> "provider_refusal"
                    status == "incomplete" && incompleteReason == "max_output_tokens" ->
                        "incomplete_max_output_tokens"

                    status == "incomplete" && incompleteReason == "content_filter" ->
                        "incomplete_content_filter"

                    status == "incomplete" -> "provider_incomplete_other"
                    status != "completed" -> "provider_envelope_unexpected"
                    outputTextCount != 1 -> "structured_output_missing"
                    else -> "completed_structured_output"
                },
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens =
                reportedTotalTokens ?: inputTokens.plusIfBothPresent(outputTokens),
            effectiveInputTokens = inputTokens,
            totalTokensDerived =
                reportedTotalTokens == null && inputTokens != null && outputTokens != null,
            cachedInputTokens = usage.path("input_tokens_details").long("cached_tokens"),
            cacheWriteTokens =
                usage.path("input_tokens_details").long("cache_write_tokens"),
            cacheCreationInputTokens = null,
            cacheCreationFiveMinuteInputTokens = null,
            cacheCreationOneHourInputTokens = null,
            reasoningOrThinkingTokens =
                usage.path("output_tokens_details").long("reasoning_tokens"),
            toolUsePromptTokens = null,
            providerOutputItemCount = output.size,
            reasoningOutputItemCount = output.count { it.text("type") == "reasoning" },
            outputTextCount = outputTextCount,
            invalidOutputTextCount = invalidOutputTextCount,
            providerOutputTextUtf8Bytes = outputTexts.sumOf { it.utf8Bytes() },
            providerOutputTextCodePoints = outputTexts.sumOf { it.unicodeCodePointCount() },
            refusalCount = refusalCount,
            safetyRatingCount = 0,
            usageEnvelopePresent = body.get("usage")?.isObject == true,
        )
    }

    private fun geminiEvidence(
        response: ProviderHttpResponse,
        body: JsonNode,
        requestIdHash: String?,
        elapsedNanos: Long,
    ): SanitizedProviderResponseEvidence {
        val candidates = body.path("candidates").toList()
        val rawFinishReason = candidates.firstOrNull()?.text("finishReason")
        val finishReason =
            rawFinishReason?.let { value ->
                if (value in GEMINI_FINISH_REASONS) value else "other"
            }
        val finishDisposition = classifyGeminiFinishReason(rawFinishReason)
        val outputTexts =
            candidates.flatMap { candidate ->
                candidate.path("content").path("parts")
                    .mapNotNull { part ->
                        part.get("text")?.takeIf(JsonNode::isString)?.stringValue()
                    }
            }
        val outputTextCount = outputTexts.size
        val invalidOutputTextCount =
            candidates.sumOf { candidate ->
                candidate.path("content").path("parts").count { part ->
                    part.has("text") && part.get("text")?.isString != true
                }
            }
        val promptFeedback = body.path("promptFeedback")
        val promptBlocked = promptFeedback.hasNonNull("blockReason")
        val blocked = promptFeedback.safeValue("blockReason", GEMINI_BLOCK_REASONS)
        val usage = body.path("usageMetadata")
        val inputTokens = usage.long("promptTokenCount")
        val outputTokens = usage.long("candidatesTokenCount")
        val reportedTotalTokens = usage.long("totalTokenCount")
        val safetyRatingCount =
            body.path("promptFeedback").path("safetyRatings").size() +
                candidates.sumOf { candidate -> candidate.path("safetyRatings").size() }
        val providerError = body.geminiErrorEvidence()
        val providerEnvelopeValid =
            if (response.statusCode in 200..299) {
                promptBlocked ||
                    (
                        candidates.isNotEmpty() &&
                            when (finishDisposition) {
                                GeminiFinishDisposition.COMPLETED -> outputTextCount == 1
                                GeminiFinishDisposition.POLICY_REJECTED,
                                GeminiFinishDisposition.MAX_TOKENS,
                                -> true

                                GeminiFinishDisposition.INVALID -> false
                            }
                    )
            } else {
                body.path("error").isObject
            }
        return evidence(
            response = response,
            requestIdHash = requestIdHash,
            elapsedNanos = elapsedNanos,
            body = body,
            providerStatus =
                if (response.statusCode !in 200..299) {
                    null
                } else {
                    when {
                        promptBlocked ||
                            finishDisposition == GeminiFinishDisposition.POLICY_REJECTED ->
                            "refused"

                        finishDisposition == GeminiFinishDisposition.MAX_TOKENS ->
                            "incomplete"

                        finishDisposition == GeminiFinishDisposition.COMPLETED ->
                            "completed"

                        else -> null
                    }
                },
            finishReason = finishReason,
            incompleteReason = null,
            providerSafetyReason =
                blocked
                    ?: finishReason?.takeIf {
                        finishDisposition == GeminiFinishDisposition.POLICY_REJECTED
                    },
            providerErrorCategory = providerError.category,
            providerErrorCode = providerError.code,
            providerErrorType = providerError.type,
            providerErrorParameter = providerError.parameter,
            providerEnvelopeValid = providerEnvelopeValid,
            diagnostic =
                when {
                    response.statusCode !in 200..299 -> "provider_http_non_success"
                    promptBlocked || finishDisposition == GeminiFinishDisposition.POLICY_REJECTED ->
                        "provider_refusal"

                    finishDisposition == GeminiFinishDisposition.MAX_TOKENS ->
                        "incomplete_max_output_tokens"

                    finishDisposition != GeminiFinishDisposition.COMPLETED ->
                        "provider_envelope_unexpected"

                    outputTextCount != 1 -> "structured_output_missing"
                    else -> "completed_structured_output"
                },
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens =
                reportedTotalTokens ?: inputTokens.plusIfBothPresent(outputTokens),
            effectiveInputTokens = inputTokens,
            totalTokensDerived =
                reportedTotalTokens == null && inputTokens != null && outputTokens != null,
            cachedInputTokens = usage.long("cachedContentTokenCount"),
            cacheWriteTokens = null,
            cacheCreationInputTokens = null,
            cacheCreationFiveMinuteInputTokens = null,
            cacheCreationOneHourInputTokens = null,
            reasoningOrThinkingTokens = usage.long("thoughtsTokenCount"),
            toolUsePromptTokens = usage.long("toolUsePromptTokenCount"),
            providerOutputItemCount = candidates.size,
            reasoningOutputItemCount =
                candidates.sumOf { candidate ->
                    candidate.path("content").path("parts").count { part ->
                        part.get("thought")?.takeIf(JsonNode::isBoolean)?.booleanValue() == true
                    }
                },
            outputTextCount = outputTextCount,
            invalidOutputTextCount = invalidOutputTextCount,
            providerOutputTextUtf8Bytes = outputTexts.sumOf { it.utf8Bytes() },
            providerOutputTextCodePoints = outputTexts.sumOf { it.unicodeCodePointCount() },
            refusalCount =
                if (
                    promptBlocked ||
                    finishDisposition == GeminiFinishDisposition.POLICY_REJECTED
                ) {
                    1
                } else {
                    0
                },
            safetyRatingCount = safetyRatingCount,
            usageEnvelopePresent = body.get("usageMetadata")?.isObject == true,
        )
    }

    private fun anthropicEvidence(
        response: ProviderHttpResponse,
        body: JsonNode,
        requestIdHash: String?,
        elapsedNanos: Long,
    ): SanitizedProviderResponseEvidence {
        val finishReason = body.safeValue("stop_reason", ANTHROPIC_STOP_REASONS)
        val envelopeMatches =
            body.text("type") == "message" && body.text("role") == "assistant"
        val textItems = body.path("content").filter { it.text("type") == "text" }
        val outputTexts =
            textItems.mapNotNull { item ->
                item.get("text")?.takeIf(JsonNode::isString)?.stringValue()
            }
        val outputTextCount = outputTexts.size
        val invalidOutputTextCount = textItems.size - outputTextCount
        val usage = body.path("usage")
        val inputTokens = usage.long("input_tokens")
        val outputTokens = usage.long("output_tokens")
        val cachedInputTokens = usage.long("cache_read_input_tokens")
        val cacheCreationInputTokens = usage.long("cache_creation_input_tokens")
        val effectiveInputTokens =
            inputTokens?.let {
                it + (cachedInputTokens ?: 0L) + (cacheCreationInputTokens ?: 0L)
            }
        val providerError = body.anthropicErrorEvidence()
        val providerEnvelopeValid =
            if (response.statusCode in 200..299) {
                envelopeMatches &&
                    when (finishReason) {
                        "end_turn" -> outputTextCount == 1
                        "max_tokens", "refusal" -> true
                        else -> false
                    }
            } else {
                body.path("error").isObject
            }
        return evidence(
            response = response,
            requestIdHash = requestIdHash,
            elapsedNanos = elapsedNanos,
            body = body,
            providerStatus =
                if (response.statusCode !in 200..299) {
                    null
                } else {
                    when (finishReason) {
                        "end_turn" -> "completed"
                        "max_tokens" -> "incomplete"
                        "refusal" -> "refused"
                        else -> null
                    }
                },
            finishReason = finishReason,
            incompleteReason = null,
            providerSafetyReason = finishReason?.takeIf { it == "refusal" },
            providerErrorCategory = providerError.category,
            providerErrorCode = providerError.code,
            providerErrorType = providerError.type,
            providerErrorParameter = providerError.parameter,
            providerEnvelopeValid = providerEnvelopeValid,
            diagnostic =
                when {
                    response.statusCode !in 200..299 -> "provider_http_non_success"
                    !envelopeMatches -> "provider_envelope_unexpected"
                    finishReason == "refusal" -> "provider_refusal"
                    finishReason == "max_tokens" -> "incomplete_max_output_tokens"
                    finishReason != "end_turn" -> "provider_envelope_unexpected"
                    outputTextCount != 1 -> "structured_output_missing"
                    else -> "completed_structured_output"
                },
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = effectiveInputTokens.plusIfBothPresent(outputTokens),
            effectiveInputTokens = effectiveInputTokens,
            totalTokensDerived = effectiveInputTokens != null && outputTokens != null,
            cachedInputTokens = cachedInputTokens,
            cacheWriteTokens = null,
            cacheCreationInputTokens = cacheCreationInputTokens,
            cacheCreationFiveMinuteInputTokens =
                usage.path("cache_creation").long("ephemeral_5m_input_tokens"),
            cacheCreationOneHourInputTokens =
                usage.path("cache_creation").long("ephemeral_1h_input_tokens"),
            reasoningOrThinkingTokens =
                usage.path("output_tokens_details").long("thinking_tokens"),
            toolUsePromptTokens = null,
            providerOutputItemCount = body.path("content").size(),
            reasoningOutputItemCount =
                body.path("content").count {
                    it.text("type") == "thinking" || it.text("type") == "redacted_thinking"
                },
            outputTextCount = outputTextCount,
            invalidOutputTextCount = invalidOutputTextCount,
            providerOutputTextUtf8Bytes = outputTexts.sumOf { it.utf8Bytes() },
            providerOutputTextCodePoints = outputTexts.sumOf { it.unicodeCodePointCount() },
            refusalCount = if (finishReason == "refusal") 1 else 0,
            safetyRatingCount = 0,
            usageEnvelopePresent = body.get("usage")?.isObject == true,
        )
    }

    private fun evidence(
        response: ProviderHttpResponse,
        requestIdHash: String?,
        elapsedNanos: Long,
        body: JsonNode,
        providerStatus: String?,
        finishReason: String?,
        incompleteReason: String?,
        providerSafetyReason: String?,
        providerErrorCategory: String?,
        providerErrorCode: String?,
        providerErrorType: String?,
        providerErrorParameter: String?,
        providerEnvelopeValid: Boolean,
        diagnostic: String,
        inputTokens: Long?,
        outputTokens: Long?,
        totalTokens: Long?,
        effectiveInputTokens: Long?,
        totalTokensDerived: Boolean,
        cachedInputTokens: Long?,
        cacheWriteTokens: Long?,
        cacheCreationInputTokens: Long?,
        cacheCreationFiveMinuteInputTokens: Long?,
        cacheCreationOneHourInputTokens: Long?,
        reasoningOrThinkingTokens: Long?,
        toolUsePromptTokens: Long?,
        providerOutputItemCount: Int,
        reasoningOutputItemCount: Int,
        outputTextCount: Int,
        invalidOutputTextCount: Int,
        providerOutputTextUtf8Bytes: Int,
        providerOutputTextCodePoints: Int,
        refusalCount: Int,
        safetyRatingCount: Int,
        usageEnvelopePresent: Boolean,
    ): SanitizedProviderResponseEvidence {
        val responseIdHash =
            (body.text("id") ?: body.text("responseId"))
                ?.takeIf(SAFE_PROVIDER_ID::matches)
                ?.let(BioEvalHash::sha256)
        val echoedModel = body.text("model") ?: body.text("modelVersion")
        return SanitizedProviderResponseEvidence(
            httpStatusCode = response.statusCode,
            httpStatusClass = response.statusCode.statusClass(),
            responseBodyBytes = response.body.toByteArray(StandardCharsets.UTF_8).size,
            transportLatencyMillis = elapsedNanos.toElapsedMillis(),
            safeMetadataHeaders = response.safeMetadataHeaders.toSortedMap(),
            bodyTooLarge = false,
            jsonObjectParsed = true,
            providerEnvelopeValid = providerEnvelopeValid,
            providerModelMatched = echoedModel?.let { it == exactModelId },
            providerStatus = providerStatus,
            finishReason = finishReason,
            incompleteReason = incompleteReason,
            providerSafetyReason = providerSafetyReason,
            providerErrorCategory = providerErrorCategory,
            providerErrorCode = providerErrorCode,
            providerErrorType = providerErrorType,
            providerErrorParameter = providerErrorParameter,
            terminalProviderFailureCategory =
                terminalProviderFailureCategory(
                    statusCode = response.statusCode,
                    errorCategory = providerErrorCategory,
                ),
            serviceTier =
                body.safeValue(
                    "service_tier",
                    setOf("auto", "default", "flex", "priority", "scale", "batch"),
                ),
            providerCreatedAtEpochSeconds = body.long("created_at"),
            diagnostic = diagnostic,
            providerRequestIdSha256 = requestIdHash,
            providerResponseIdSha256 = responseIdHash,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = totalTokens,
            effectiveInputTokens = effectiveInputTokens,
            totalTokensDerived = totalTokensDerived,
            cachedInputTokens = cachedInputTokens,
            cacheWriteTokens = cacheWriteTokens,
            cacheCreationInputTokens = cacheCreationInputTokens,
            cacheCreationFiveMinuteInputTokens =
                cacheCreationFiveMinuteInputTokens,
            cacheCreationOneHourInputTokens =
                cacheCreationOneHourInputTokens,
            reasoningOrThinkingTokens = reasoningOrThinkingTokens,
            toolUsePromptTokens = toolUsePromptTokens,
            providerOutputItemCount = providerOutputItemCount,
            reasoningOutputItemCount = reasoningOutputItemCount,
            outputTextItemCount = outputTextCount,
            invalidOutputTextItemCount = invalidOutputTextCount,
            providerOutputTextUtf8Bytes = providerOutputTextUtf8Bytes,
            providerOutputTextCodePoints = providerOutputTextCodePoints,
            refusalItemCount = refusalCount,
            safetyRatingCount = safetyRatingCount,
            usageEnvelopePresent = usageEnvelopePresent,
        )
    }

    private fun emptyEvidence(
        response: ProviderHttpResponse,
        requestIdHash: String?,
        elapsedNanos: Long,
        diagnostic: String,
    ) = SanitizedProviderResponseEvidence(
        httpStatusCode = response.statusCode,
        httpStatusClass = response.statusCode.statusClass(),
        responseBodyBytes =
            if (response.bodyTooLarge) {
                null
            } else {
                response.body.toByteArray(StandardCharsets.UTF_8).size
            },
        transportLatencyMillis = elapsedNanos.toElapsedMillis(),
        safeMetadataHeaders = response.safeMetadataHeaders.toSortedMap(),
        bodyTooLarge = response.bodyTooLarge,
        jsonObjectParsed = false,
        providerEnvelopeValid = false,
        providerModelMatched = null,
        providerStatus = null,
        finishReason = null,
        incompleteReason = null,
        providerSafetyReason = null,
        providerErrorCategory = null,
        providerErrorCode = null,
        providerErrorType = null,
        providerErrorParameter = null,
        terminalProviderFailureCategory =
            terminalProviderFailureCategory(
                statusCode = response.statusCode,
                errorCategory = null,
            ),
        serviceTier = null,
        providerCreatedAtEpochSeconds = null,
        diagnostic = diagnostic,
        providerRequestIdSha256 = requestIdHash,
        providerResponseIdSha256 = null,
        inputTokens = null,
        outputTokens = null,
        totalTokens = null,
        effectiveInputTokens = null,
        totalTokensDerived = false,
        cachedInputTokens = null,
        cacheWriteTokens = null,
        cacheCreationInputTokens = null,
        cacheCreationFiveMinuteInputTokens = null,
        cacheCreationOneHourInputTokens = null,
        reasoningOrThinkingTokens = null,
        toolUsePromptTokens = null,
        providerOutputItemCount = 0,
        reasoningOutputItemCount = 0,
        outputTextItemCount = 0,
        invalidOutputTextItemCount = 0,
        providerOutputTextUtf8Bytes = 0,
        providerOutputTextCodePoints = 0,
        refusalItemCount = 0,
        safetyRatingCount = 0,
        usageEnvelopePresent = false,
    )

    private fun SanitizedProviderResponseEvidence.toSanitizedMap(
        attemptIndex: Int,
    ): Map<String, Any?> =
        linkedMapOf(
            "attempt_index" to attemptIndex,
            "kind" to "response",
            "http_status_code" to httpStatusCode,
            "http_status_class" to httpStatusClass,
            "response_body_bytes" to responseBodyBytes,
            "transport_latency_millis" to transportLatencyMillis,
            "safe_metadata_headers" to safeMetadataHeaders,
            "body_too_large" to bodyTooLarge,
            "json_object_parsed" to jsonObjectParsed,
            "provider_envelope_valid" to providerEnvelopeValid,
            "provider_model_matched" to providerModelMatched,
            "provider_status" to providerStatus,
            "finish_reason" to finishReason,
            "incomplete_reason" to incompleteReason,
            "provider_safety_reason" to providerSafetyReason,
            "provider_error_category" to providerErrorCategory,
            "provider_error_code" to providerErrorCode,
            "provider_error_type" to providerErrorType,
            "provider_error_parameter" to providerErrorParameter,
            "terminal_provider_failure_category" to terminalProviderFailureCategory,
            "service_tier" to serviceTier,
            "provider_created_at_epoch_seconds" to providerCreatedAtEpochSeconds,
            "diagnostic" to diagnostic,
            "provider_request_id_sha256" to providerRequestIdSha256,
            "provider_response_id_sha256" to providerResponseIdSha256,
            "input_tokens" to inputTokens,
            "output_tokens" to outputTokens,
            "total_tokens" to totalTokens,
            "effective_input_tokens" to effectiveInputTokens,
            "total_tokens_derived" to totalTokensDerived,
            "cached_input_tokens" to cachedInputTokens,
            "cache_write_tokens" to cacheWriteTokens,
            "cache_creation_input_tokens" to cacheCreationInputTokens,
            "cache_creation_five_minute_input_tokens" to
                cacheCreationFiveMinuteInputTokens,
            "cache_creation_one_hour_input_tokens" to
                cacheCreationOneHourInputTokens,
            "reasoning_or_thinking_tokens" to reasoningOrThinkingTokens,
            "tool_use_prompt_tokens" to toolUsePromptTokens,
            "provider_output_item_count" to providerOutputItemCount,
            "reasoning_output_item_count" to reasoningOutputItemCount,
            "output_text_item_count" to outputTextItemCount,
            "invalid_output_text_item_count" to invalidOutputTextItemCount,
            "provider_output_text_utf8_bytes" to providerOutputTextUtf8Bytes,
            "provider_output_text_code_points" to providerOutputTextCodePoints,
            "refusal_item_count" to refusalItemCount,
            "safety_rating_count" to safetyRatingCount,
            "usage_envelope_present" to usageEnvelopePresent,
        )

    private fun SanitizedTransportFailureEvidence.toSanitizedMap(
        attemptIndex: Int,
    ): Map<String, Any> =
        linkedMapOf(
            "attempt_index" to attemptIndex,
            "kind" to "transport_failure",
            "category" to category,
            "transport_latency_millis" to transportLatencyMillis,
        )

    private fun JsonNode.openAiErrorEvidence(): SanitizedProviderErrorEvidence {
        val error =
            path("error").takeIf(JsonNode::isObject)
                ?: return SanitizedProviderErrorEvidence(null, null, null, null)
        val rawCode = error.text("code")
        val rawType = error.text("type")
        return SanitizedProviderErrorEvidence(
            category =
                when {
                    rawCode in OPENAI_AUTH_ERROR_VALUES ||
                        rawType in OPENAI_AUTH_ERROR_VALUES ->
                        "authentication"

                    rawCode in OPENAI_QUOTA_ERROR_VALUES ||
                        rawType in OPENAI_QUOTA_ERROR_VALUES ->
                        "quota"

                    rawCode in OPENAI_RATE_LIMIT_ERROR_VALUES ||
                        rawType in OPENAI_RATE_LIMIT_ERROR_VALUES ->
                        "rate_limit"

                    rawCode in OPENAI_CONTENT_POLICY_ERROR_VALUES ||
                        rawType in OPENAI_CONTENT_POLICY_ERROR_VALUES ->
                        "content_policy"

                    rawCode in OPENAI_SERVER_ERROR_VALUES ||
                        rawType in OPENAI_SERVER_ERROR_VALUES ->
                        "server"

                    rawCode in OPENAI_INVALID_REQUEST_ERROR_VALUES ||
                        rawType in OPENAI_INVALID_REQUEST_ERROR_VALUES ->
                        "invalid_request"

                    else -> "other"
                },
            code = rawCode.closedValue(OPENAI_ERROR_CODES),
            type = rawType.closedValue(OPENAI_ERROR_TYPES),
            parameter =
                error.text("param").closedValue(
                    setOf(
                        "model",
                        "max_output_tokens",
                        "reasoning",
                        "reasoning.effort",
                        "text",
                        "text.format",
                        "store",
                        "input",
                        "instructions",
                    ),
                ),
        )
    }

    private fun JsonNode.geminiErrorEvidence(): SanitizedProviderErrorEvidence {
        val error =
            path("error").takeIf(JsonNode::isObject)
                ?: return SanitizedProviderErrorEvidence(null, null, null, null)
        val status = error.text("status")
        return SanitizedProviderErrorEvidence(
            category =
                when (status) {
                    "UNAUTHENTICATED" -> "authentication"
                    "PERMISSION_DENIED" -> "permission"
                    "RESOURCE_EXHAUSTED" -> "rate_limit_or_quota"
                    "INVALID_ARGUMENT", "FAILED_PRECONDITION", "NOT_FOUND" ->
                        "invalid_request"

                    "INTERNAL", "UNAVAILABLE", "DEADLINE_EXCEEDED" -> "server"
                    else -> "other"
                },
            code = status.closedValue(GEMINI_ERROR_STATUSES),
            type = null,
            parameter = null,
        )
    }

    private fun JsonNode.anthropicErrorEvidence(): SanitizedProviderErrorEvidence {
        val error =
            path("error").takeIf(JsonNode::isObject)
                ?: return SanitizedProviderErrorEvidence(null, null, null, null)
        val type = error.text("type")
        return SanitizedProviderErrorEvidence(
            category =
                when (type) {
                    "authentication_error" -> "authentication"
                    "permission_error" -> "permission"
                    "rate_limit_error" -> "rate_limit"
                    "billing_error" -> "quota"
                    "invalid_request_error", "not_found_error", "request_too_large" ->
                        "invalid_request"

                    "api_error", "overloaded_error" -> "server"
                    else -> "other"
                },
            code = null,
            type = type.closedValue(ANTHROPIC_ERROR_TYPES),
            parameter = null,
        )
    }

    private fun terminalProviderFailureCategory(
        statusCode: Int,
        errorCategory: String?,
    ): String? =
        when {
            statusCode == 401 || errorCategory == "authentication" -> "authentication"
            statusCode == 402 || errorCategory == "quota" -> "billing"
            statusCode == 403 || errorCategory == "permission" -> "permission"
            statusCode == 404 -> "not_found"
            statusCode in setOf(400, 409, 422) || errorCategory == "invalid_request" ->
                "invalid_request"

            else -> null
        }

    private fun sequenceHash(values: List<String>): String? =
        values.takeIf(List<String>::isNotEmpty)?.joinToString("\n")?.let(BioEvalHash::sha256)

    private fun Throwable.transportFailureCategory(): String =
        when (this) {
            is HttpTimeoutException -> "timeout"
            is IOException -> "io"
            is InterruptedException, is CancellationException -> "cancelled"
            is RuntimeException -> "runtime"
            else -> "other"
        }

    private fun Long.toElapsedMillis(): Long =
        coerceAtLeast(0L) / NANOS_PER_MILLISECOND

    private fun Int.statusClass(): String =
        when (this) {
            in 100..199 -> "1xx"
            in 200..299 -> "2xx"
            in 300..399 -> "3xx"
            in 400..499 -> "4xx"
            in 500..599 -> "5xx"
            else -> "other"
        }

    private fun JsonNode.text(fieldName: String): String? =
        get(fieldName)?.takeIf(JsonNode::isString)?.stringValue()

    private fun JsonNode.long(fieldName: String): Long? =
        get(fieldName)?.takeIf(JsonNode::isIntegralNumber)?.longValue()

    private fun Long?.plusIfBothPresent(other: Long?): Long? =
        if (this != null && other != null) {
            this + other
        } else {
            null
        }

    private fun JsonNode.safeValue(
        fieldName: String,
        allowedValues: Set<String>,
    ): String? =
        text(fieldName)?.let { value -> if (value in allowedValues) value else "other" }

    private fun String?.closedValue(allowedValues: Set<String>): String? =
        this?.let { value -> if (value in allowedValues) value else "other" }

    private fun String.utf8Bytes(): Int =
        toByteArray(StandardCharsets.UTF_8).size

    private fun String.unicodeCodePointCount(): Int =
        codePointCount(0, length)

    private fun <T> List<T>.countBy(selector: (T) -> String): Map<String, Int> =
        map(selector).countValues()

    private fun List<String>.countValues(): Map<String, Int> =
        groupingBy { it }.eachCount().toSortedMap()

    private companion object {
        val SAFE_PROVIDER_ID = Regex("[A-Za-z0-9._:-]{1,256}")
        val OPENAI_STATUSES = setOf("completed", "incomplete", "failed", "cancelled")
        val OPENAI_INCOMPLETE_REASONS = setOf("max_output_tokens", "content_filter")
        val OPENAI_AUTH_ERROR_VALUES =
            setOf("invalid_api_key", "authentication_error")
        val OPENAI_QUOTA_ERROR_VALUES =
            setOf("insufficient_quota", "billing_hard_limit_reached")
        val OPENAI_RATE_LIMIT_ERROR_VALUES =
            setOf("rate_limit_exceeded", "rate_limit_error")
        val OPENAI_CONTENT_POLICY_ERROR_VALUES =
            setOf("content_policy_violation")
        val OPENAI_SERVER_ERROR_VALUES =
            setOf("server_error", "api_error", "overloaded_error")
        val OPENAI_INVALID_REQUEST_ERROR_VALUES =
            setOf(
                "invalid_request",
                "invalid_request_error",
                "unsupported_parameter",
                "unsupported_value",
                "model_not_found",
            )
        val OPENAI_ERROR_CODES =
            OPENAI_AUTH_ERROR_VALUES +
                OPENAI_QUOTA_ERROR_VALUES +
                OPENAI_RATE_LIMIT_ERROR_VALUES +
                OPENAI_CONTENT_POLICY_ERROR_VALUES +
                OPENAI_SERVER_ERROR_VALUES +
                OPENAI_INVALID_REQUEST_ERROR_VALUES
        val OPENAI_ERROR_TYPES =
            setOf(
                "authentication_error",
                "permission_error",
                "invalid_request_error",
                "rate_limit_error",
                "api_error",
                "overloaded_error",
                "server_error",
            )
        val GEMINI_FINISH_REASONS =
            setOf(
                "STOP",
                "MAX_TOKENS",
                "SAFETY",
                "RECITATION",
                "LANGUAGE",
                "OTHER",
                "BLOCKLIST",
                "PROHIBITED_CONTENT",
                "SPII",
                "MALFORMED_FUNCTION_CALL",
                "IMAGE_SAFETY",
                "IMAGE_PROHIBITED_CONTENT",
                "IMAGE_RECITATION",
                "IMAGE_OTHER",
                "NO_IMAGE",
                "IMAGE_TOO_MANY",
                "UNEXPECTED_TOOL_CALL",
                "TOO_MANY_TOOL_CALLS",
            )
        val GEMINI_BLOCK_REASONS =
            setOf(
                "SAFETY",
                "BLOCKLIST",
                "PROHIBITED_CONTENT",
                "OTHER",
                "BLOCK_REASON_UNSPECIFIED",
            )
        val GEMINI_ERROR_STATUSES =
            setOf(
                "UNAUTHENTICATED",
                "PERMISSION_DENIED",
                "RESOURCE_EXHAUSTED",
                "INVALID_ARGUMENT",
                "FAILED_PRECONDITION",
                "NOT_FOUND",
                "INTERNAL",
                "UNAVAILABLE",
                "DEADLINE_EXCEEDED",
            )
        val ANTHROPIC_STOP_REASONS = setOf("end_turn", "max_tokens", "refusal", "stop_sequence")
        val ANTHROPIC_ERROR_TYPES =
            setOf(
                "authentication_error",
                "permission_error",
                "rate_limit_error",
                "billing_error",
                "invalid_request_error",
                "not_found_error",
                "request_too_large",
                "api_error",
                "overloaded_error",
            )
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
