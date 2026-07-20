package com.persons.finder.person.bio.remote.eval

import com.persons.finder.person.bio.BIO_GENERATION_DEADLINE
import com.persons.finder.person.bio.BioTemplateRequest
import com.persons.finder.person.bio.SafeInterestCode
import com.persons.finder.person.bio.SafeJobCode
import com.persons.finder.person.bio.remote.ProviderHttpRequest
import java.nio.charset.StandardCharsets
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

internal data class SanitizedProviderRequestEvidence(
    val requestBodyBytes: Int,
    val timeoutMillis: Long,
    val methodMatched: Boolean,
    val destinationMatched: Boolean,
    val headersMatched: Boolean,
    val approvedHeaderNamesPresent: List<String>,
    val unexpectedHeaderNameCount: Int,
    val timeoutWithinApplicationDeadline: Boolean,
    val jsonObjectParsed: Boolean,
    val topLevelShapeMatched: Boolean,
    val unexpectedConfigurationFieldCount: Int,
    val requestedModelMatched: Boolean?,
    val promptFingerprintMatched: Boolean?,
    val inputAllowlistMatched: Boolean?,
    val systemInstructionUtf8Bytes: Int?,
    val inputPayloadUtf8Bytes: Int?,
    val outputSchemaUtf8Bytes: Int?,
    val maxOutputTokens: Int?,
    val structuredOutputMode: String?,
    val structuredOutputStrict: Boolean?,
    val outputSchemaPresent: Boolean,
    val store: Boolean?,
    val reasoningEffort: String?,
    val thinkingMode: String?,
    val thinkingBudgetTokens: Long?,
    val includeThoughts: Boolean?,
    val truncationMode: String?,
    val serviceTier: String?,
    val candidateCount: Int?,
    val messageCount: Int?,
    val contentPartCount: Int?,
    val temperature: Double?,
    val topP: Double?,
    val topK: Long?,
    val seed: Long?,
    val stopSequenceCount: Int?,
    val expectedConfigurationMatched: Boolean,
)

internal class LiveProviderRequestEvidenceAccumulator(
    private val provider: LiveBioEvalProvider,
    private val exactModelId: String,
    private val expectedHeaderValueFingerprints: Map<String, String>,
    private val expectedFingerprint: ApplicationRequestFingerprint,
    private val objectMapper: JsonMapper,
) {
    private val requests = mutableListOf<SanitizedProviderRequestEvidence>()

    fun record(request: ProviderHttpRequest): SanitizedProviderRequestEvidence =
        sanitize(request).also(requests::add)

    fun summary(): Map<String, Any> =
        linkedMapOf(
            "request_count" to requests.size,
            "requests" to
                requests.mapIndexed { index, request ->
                    linkedMapOf(
                        "request_index" to index + 1,
                        "request_body_bytes" to request.requestBodyBytes,
                        "timeout_millis" to request.timeoutMillis,
                        "method_matched" to request.methodMatched,
                        "destination_matched" to request.destinationMatched,
                        "headers_matched" to request.headersMatched,
                        "approved_header_names_present" to
                            request.approvedHeaderNamesPresent,
                        "unexpected_header_name_count" to
                            request.unexpectedHeaderNameCount,
                        "timeout_within_application_deadline" to
                            request.timeoutWithinApplicationDeadline,
                        "json_object_parsed" to request.jsonObjectParsed,
                        "top_level_shape_matched" to request.topLevelShapeMatched,
                        "unexpected_configuration_field_count" to
                            request.unexpectedConfigurationFieldCount,
                        "requested_model_matched" to request.requestedModelMatched,
                        "prompt_fingerprint_matched" to
                            request.promptFingerprintMatched,
                        "input_allowlist_matched" to request.inputAllowlistMatched,
                        "system_instruction_utf8_bytes" to
                            request.systemInstructionUtf8Bytes,
                        "input_payload_utf8_bytes" to request.inputPayloadUtf8Bytes,
                        "output_schema_utf8_bytes" to request.outputSchemaUtf8Bytes,
                        "max_output_tokens" to request.maxOutputTokens,
                        "structured_output_mode" to request.structuredOutputMode,
                        "structured_output_strict" to request.structuredOutputStrict,
                        "output_schema_present" to request.outputSchemaPresent,
                        "store" to request.store,
                        "reasoning_effort" to request.reasoningEffort,
                        "thinking_mode" to request.thinkingMode,
                        "thinking_budget_tokens" to request.thinkingBudgetTokens,
                        "include_thoughts" to request.includeThoughts,
                        "truncation_mode" to request.truncationMode,
                        "service_tier" to request.serviceTier,
                        "candidate_count" to request.candidateCount,
                        "message_count" to request.messageCount,
                        "content_part_count" to request.contentPartCount,
                        "temperature" to request.temperature,
                        "top_p" to request.topP,
                        "top_k" to request.topK,
                        "seed" to request.seed,
                        "stop_sequence_count" to request.stopSequenceCount,
                        "expected_configuration_matched" to
                            request.expectedConfigurationMatched,
                    )
                },
            "request_body_bytes" to requests.sumOf { it.requestBodyBytes },
            "maximum_request_body_bytes" to
                (requests.maxOfOrNull { it.requestBodyBytes } ?: 0),
            "maximum_timeout_millis" to
                (requests.maxOfOrNull { it.timeoutMillis } ?: 0L),
            "maximum_system_instruction_utf8_bytes" to
                (requests.mapNotNull { it.systemInstructionUtf8Bytes }.maxOrNull() ?: 0),
            "maximum_input_payload_utf8_bytes" to
                (requests.mapNotNull { it.inputPayloadUtf8Bytes }.maxOrNull() ?: 0),
            "maximum_output_schema_utf8_bytes" to
                (requests.mapNotNull { it.outputSchemaUtf8Bytes }.maxOrNull() ?: 0),
            "max_output_token_values" to
                requests.mapNotNull { it.maxOutputTokens }.distinct().sorted(),
            "reasoning_effort_counts" to
                requests.mapNotNull { it.reasoningEffort }.countValues(),
            "thinking_mode_counts" to
                requests.mapNotNull { it.thinkingMode }.countValues(),
            "structured_output_mode_counts" to
                requests.mapNotNull { it.structuredOutputMode }.countValues(),
            "requests_with_explicit_sampling_configuration" to
                requests.count {
                    it.temperature != null ||
                        it.topP != null ||
                        it.topK != null ||
                        it.seed != null
                },
            "requests_with_stop_sequences" to
                requests.count { (it.stopSequenceCount ?: 0) > 0 },
            "unexpected_header_name_count" to
                requests.sumOf { it.unexpectedHeaderNameCount },
            "unexpected_configuration_field_count" to
                requests.sumOf { it.unexpectedConfigurationFieldCount },
            "expected_configuration_match_count" to
                requests.count(SanitizedProviderRequestEvidence::expectedConfigurationMatched),
            "all_requests_match_expected_configuration" to
                (
                    requests.isNotEmpty() &&
                        requests.all(SanitizedProviderRequestEvidence::expectedConfigurationMatched)
                ),
        )

    private fun sanitize(request: ProviderHttpRequest): SanitizedProviderRequestEvidence {
        val methodMatched = request.method == "POST"
        val destinationMatched =
            request.uri.scheme == "https" &&
                request.uri.host == provider.expectedHost &&
                request.uri.port == -1 &&
                request.uri.rawPath == provider.expectedRawPath(exactModelId) &&
                request.uri.rawQuery == null &&
                request.uri.userInfo == null &&
                request.uri.fragment == null
        val expectedHeaders = expectedHeaderValueFingerprints.keys
        val actualHeaders = request.headers.keys
        val headersMatched =
            actualHeaders == expectedHeaders &&
                request.headers.all { (name, value) ->
                    BioEvalHash.sha256(value) == expectedHeaderValueFingerprints[name]
                }
        val approvedHeaderNamesPresent =
            expectedHeaders.filter(actualHeaders::contains).sorted()
        val unexpectedHeaderNameCount = (actualHeaders - expectedHeaders).size
        val timeoutWithinApplicationDeadline =
            request.timeout > java.time.Duration.ZERO &&
                request.timeout <= BIO_GENERATION_DEADLINE
        val body =
            try {
                objectMapper.reader()
                    .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .readTree(request.body)
            } catch (_: RuntimeException) {
                null
            }
        if (body == null || !body.isObject) {
            return empty(
                request = request,
                methodMatched = methodMatched,
                destinationMatched = destinationMatched,
                headersMatched = headersMatched,
                approvedHeaderNamesPresent = approvedHeaderNamesPresent,
                unexpectedHeaderNameCount = unexpectedHeaderNameCount,
                timeoutWithinApplicationDeadline = timeoutWithinApplicationDeadline,
            )
        }
        return when (provider) {
            LiveBioEvalProvider.OPENAI ->
                sanitizeOpenAi(
                    request,
                    body,
                    methodMatched,
                    destinationMatched,
                    headersMatched,
                    approvedHeaderNamesPresent,
                    unexpectedHeaderNameCount,
                    timeoutWithinApplicationDeadline,
                )

            LiveBioEvalProvider.GEMINI ->
                sanitizeGemini(
                    request,
                    body,
                    methodMatched,
                    destinationMatched,
                    headersMatched,
                    approvedHeaderNamesPresent,
                    unexpectedHeaderNameCount,
                    timeoutWithinApplicationDeadline,
                )

            LiveBioEvalProvider.ANTHROPIC ->
                sanitizeAnthropic(
                    request,
                    body,
                    methodMatched,
                    destinationMatched,
                    headersMatched,
                    approvedHeaderNamesPresent,
                    unexpectedHeaderNameCount,
                    timeoutWithinApplicationDeadline,
                )
        }
    }

    private fun sanitizeOpenAi(
        request: ProviderHttpRequest,
        body: JsonNode,
        methodMatched: Boolean,
        destinationMatched: Boolean,
        headersMatched: Boolean,
        approvedHeaderNamesPresent: List<String>,
        unexpectedHeaderNameCount: Int,
        timeoutWithinApplicationDeadline: Boolean,
    ): SanitizedProviderRequestEvidence {
        val format = body.path("text").path("format")
        val reasoning = body.path("reasoning")
        val expectedReasoning =
            if (exactModelId == "gpt-5.6" || exactModelId.startsWith("gpt-5.6-")) {
                "none"
            } else {
                null
            }
        val expectedTopFields =
            OPENAI_TOP_LEVEL_FIELDS +
                if (expectedReasoning == null) emptySet() else setOf("reasoning")
        val unexpectedConfigurationFieldCount =
            body.unexpectedFieldCount(expectedTopFields) +
                body.path("text").unexpectedFieldCount(setOf("format")) +
                format.unexpectedFieldCount(setOf("type", "name", "strict", "schema")) +
                if (reasoning.isMissingNode) {
                    0
                } else {
                    reasoning.unexpectedFieldCount(setOf("effort"))
                }
        val topLevelShapeMatched = body.hasExactFields(expectedTopFields)
        val instructions = body.text("instructions")
        val input = body.text("input")
        val schema = format.get("schema")
        val modelMatched = body.text("model") == exactModelId
        val maxOutputTokens = body.int("max_output_tokens")
        val structuredOutputMode = format.closedText("type", setOf("json_schema"))
        val structuredOutputStrict = format.boolean("strict")
        val outputSchemaPresent = schema?.isObject == true
        val promptFingerprintMatched =
            instructions?.let(BioEvalHash::sha256) == expectedFingerprint.promptSha256
        val outputSchemaFingerprintMatched =
            schema
                ?.takeIf(JsonNode::isObject)
                ?.toString()
                ?.let(BioEvalHash::sha256) == expectedFingerprint.outputSchemaSha256
        val inputAllowlistMatched =
            input?.let { hasApprovedLiveBioPayload(objectMapper, it) } == true
        val store = body.boolean("store")
        val reasoningEffort =
            reasoning.closedText(
                "effort",
                setOf("none", "minimal", "low", "medium", "high", "xhigh", "max"),
            )
        val truncationMode = body.closedText("truncation", setOf("auto", "disabled"))
        val serviceTier =
            body.closedText(
                "service_tier",
                setOf("auto", "default", "flex", "priority", "scale", "batch"),
            )
        val generalMatched =
            methodMatched &&
                destinationMatched &&
                headersMatched &&
                timeoutWithinApplicationDeadline
        val nestedShapeMatched =
            body.path("text").hasExactFields(setOf("format")) &&
                format.hasExactFields(setOf("type", "name", "strict", "schema")) &&
                (
                    (expectedReasoning == null && reasoning.isMissingNode) ||
                        reasoning.hasExactFields(setOf("effort"))
                )
        return base(
            request = request,
            methodMatched = methodMatched,
            destinationMatched = destinationMatched,
            headersMatched = headersMatched,
            approvedHeaderNamesPresent = approvedHeaderNamesPresent,
            unexpectedHeaderNameCount = unexpectedHeaderNameCount,
            timeoutWithinApplicationDeadline = timeoutWithinApplicationDeadline,
            topLevelShapeMatched = topLevelShapeMatched,
            unexpectedConfigurationFieldCount = unexpectedConfigurationFieldCount,
            requestedModelMatched = modelMatched,
            promptFingerprintMatched = promptFingerprintMatched,
            inputAllowlistMatched = inputAllowlistMatched,
            systemInstruction = instructions,
            inputPayload = input,
            outputSchema = schema,
            maxOutputTokens = maxOutputTokens,
            structuredOutputMode = structuredOutputMode,
            structuredOutputStrict = structuredOutputStrict,
            outputSchemaPresent = outputSchemaPresent,
            store = store,
            reasoningEffort = reasoningEffort,
            thinkingMode = null,
            thinkingBudgetTokens = null,
            includeThoughts = null,
            truncationMode = truncationMode,
            serviceTier = serviceTier,
            candidateCount = null,
            messageCount = null,
            contentPartCount = null,
            temperature = body.double("temperature"),
            topP = body.double("top_p"),
            topK = null,
            seed = body.long("seed"),
            stopSequenceCount = body.stopSequenceCount("stop"),
            expectedConfigurationMatched =
                generalMatched &&
                    topLevelShapeMatched &&
                    nestedShapeMatched &&
                    unexpectedConfigurationFieldCount == 0 &&
                    modelMatched &&
                    promptFingerprintMatched &&
                    inputAllowlistMatched &&
                    outputSchemaFingerprintMatched &&
                    maxOutputTokens == expectedFingerprint.maxOutputTokens &&
                    structuredOutputMode == "json_schema" &&
                    structuredOutputStrict == true &&
                    outputSchemaPresent &&
                    store == false &&
                    reasoningEffort == expectedReasoning,
        )
    }

    private fun sanitizeGemini(
        request: ProviderHttpRequest,
        body: JsonNode,
        methodMatched: Boolean,
        destinationMatched: Boolean,
        headersMatched: Boolean,
        approvedHeaderNamesPresent: List<String>,
        unexpectedHeaderNameCount: Int,
        timeoutWithinApplicationDeadline: Boolean,
    ): SanitizedProviderRequestEvidence {
        val systemInstruction = body.path("systemInstruction")
        val systemParts = systemInstruction.path("parts")
        val systemPart = systemParts.firstOrNull()
        val contents = body.path("contents")
        val message = contents.firstOrNull()
        val contentParts = message?.path("parts")
        val contentPart = contentParts?.firstOrNull()
        val generation = body.path("generationConfig")
        val thinkingConfig = generation.path("thinkingConfig")
        val schema = generation.get("responseJsonSchema")
        val instructions = systemPart?.text("text")
        val input = contentPart?.text("text")
        val expectedGenerationFields =
            setOf(
                "responseMimeType",
                "responseJsonSchema",
                "candidateCount",
                "maxOutputTokens",
            )
        val unexpectedConfigurationFieldCount =
            body.unexpectedFieldCount(GEMINI_TOP_LEVEL_FIELDS) +
                systemInstruction.unexpectedFieldCount(setOf("parts")) +
                (systemPart?.unexpectedFieldCount(setOf("text")) ?: 1) +
                (message?.unexpectedFieldCount(setOf("role", "parts")) ?: 1) +
                (contentPart?.unexpectedFieldCount(setOf("text")) ?: 1) +
                generation.unexpectedFieldCount(expectedGenerationFields)
        val topLevelShapeMatched = body.hasExactFields(GEMINI_TOP_LEVEL_FIELDS)
        val nestedShapeMatched =
            systemInstruction.hasExactFields(setOf("parts")) &&
                systemParts.isArray &&
                systemParts.size() == 1 &&
                systemPart?.hasExactFields(setOf("text")) == true &&
                contents.isArray &&
                contents.size() == 1 &&
                message?.hasExactFields(setOf("role", "parts")) == true &&
                message.text("role") == "user" &&
                contentParts?.isArray == true &&
                contentParts.size() == 1 &&
                contentPart?.hasExactFields(setOf("text")) == true &&
                generation.hasExactFields(expectedGenerationFields)
        val modelMatched =
            request.uri.rawPath == provider.expectedRawPath(exactModelId)
        val maxOutputTokens = generation.int("maxOutputTokens")
        val structuredOutputMode =
            generation.closedText("responseMimeType", setOf("application/json"))
        val outputSchemaPresent = schema?.isObject == true
        val promptFingerprintMatched =
            instructions?.let(BioEvalHash::sha256) == expectedFingerprint.promptSha256
        val outputSchemaFingerprintMatched =
            schema
                ?.takeIf(JsonNode::isObject)
                ?.toString()
                ?.let(BioEvalHash::sha256) == expectedFingerprint.outputSchemaSha256
        val inputAllowlistMatched =
            input?.let { hasApprovedLiveBioPayload(objectMapper, it) } == true
        val candidateCount = generation.int("candidateCount")
        val generalMatched =
            methodMatched &&
                destinationMatched &&
                headersMatched &&
                timeoutWithinApplicationDeadline
        return base(
            request = request,
            methodMatched = methodMatched,
            destinationMatched = destinationMatched,
            headersMatched = headersMatched,
            approvedHeaderNamesPresent = approvedHeaderNamesPresent,
            unexpectedHeaderNameCount = unexpectedHeaderNameCount,
            timeoutWithinApplicationDeadline = timeoutWithinApplicationDeadline,
            topLevelShapeMatched = topLevelShapeMatched,
            unexpectedConfigurationFieldCount = unexpectedConfigurationFieldCount,
            requestedModelMatched = modelMatched,
            promptFingerprintMatched = promptFingerprintMatched,
            inputAllowlistMatched = inputAllowlistMatched,
            systemInstruction = instructions,
            inputPayload = input,
            outputSchema = schema,
            maxOutputTokens = maxOutputTokens,
            structuredOutputMode = structuredOutputMode,
            structuredOutputStrict = null,
            outputSchemaPresent = outputSchemaPresent,
            store = null,
            reasoningEffort = null,
            thinkingMode =
                thinkingConfig.closedText(
                    "thinkingMode",
                    setOf("dynamic", "disabled", "manual"),
                ),
            thinkingBudgetTokens = thinkingConfig.long("thinkingBudget"),
            includeThoughts = thinkingConfig.boolean("includeThoughts"),
            truncationMode = null,
            serviceTier = null,
            candidateCount = candidateCount,
            messageCount = contents.size().takeIf { contents.isArray },
            contentPartCount = contentParts?.size()?.takeIf { contentParts.isArray },
            temperature = generation.double("temperature"),
            topP = generation.double("topP"),
            topK = generation.long("topK"),
            seed = generation.long("seed"),
            stopSequenceCount = generation.path("stopSequences").arraySizeOrNull(),
            expectedConfigurationMatched =
                generalMatched &&
                    topLevelShapeMatched &&
                    nestedShapeMatched &&
                    unexpectedConfigurationFieldCount == 0 &&
                    modelMatched &&
                    promptFingerprintMatched &&
                    inputAllowlistMatched &&
                    outputSchemaFingerprintMatched &&
                    maxOutputTokens == expectedFingerprint.maxOutputTokens &&
                    structuredOutputMode == "application/json" &&
                    outputSchemaPresent &&
                    candidateCount == 1,
        )
    }

    private fun sanitizeAnthropic(
        request: ProviderHttpRequest,
        body: JsonNode,
        methodMatched: Boolean,
        destinationMatched: Boolean,
        headersMatched: Boolean,
        approvedHeaderNamesPresent: List<String>,
        unexpectedHeaderNameCount: Int,
        timeoutWithinApplicationDeadline: Boolean,
    ): SanitizedProviderRequestEvidence {
        val messages = body.path("messages")
        val message = messages.firstOrNull()
        val outputConfig = body.path("output_config")
        val format = outputConfig.path("format")
        val thinking = body.path("thinking")
        val schema = format.get("schema")
        val instructions = body.text("system")
        val input = message?.text("content")
        val unexpectedConfigurationFieldCount =
            body.unexpectedFieldCount(ANTHROPIC_TOP_LEVEL_FIELDS) +
                (message?.unexpectedFieldCount(setOf("role", "content")) ?: 1) +
                outputConfig.unexpectedFieldCount(setOf("format")) +
                format.unexpectedFieldCount(setOf("type", "schema"))
        val topLevelShapeMatched = body.hasExactFields(ANTHROPIC_TOP_LEVEL_FIELDS)
        val nestedShapeMatched =
            messages.isArray &&
                messages.size() == 1 &&
                message?.hasExactFields(setOf("role", "content")) == true &&
                message.text("role") == "user" &&
                outputConfig.hasExactFields(setOf("format")) &&
                format.hasExactFields(setOf("type", "schema"))
        val modelMatched = body.text("model") == exactModelId
        val maxOutputTokens = body.int("max_tokens")
        val structuredOutputMode = format.closedText("type", setOf("json_schema"))
        val outputSchemaPresent = schema?.isObject == true
        val promptFingerprintMatched =
            instructions?.let(BioEvalHash::sha256) == expectedFingerprint.promptSha256
        val outputSchemaFingerprintMatched =
            schema
                ?.takeIf(JsonNode::isObject)
                ?.toString()
                ?.let(BioEvalHash::sha256) == expectedFingerprint.outputSchemaSha256
        val inputAllowlistMatched =
            input?.let { hasApprovedLiveBioPayload(objectMapper, it) } == true
        val generalMatched =
            methodMatched &&
                destinationMatched &&
                headersMatched &&
                timeoutWithinApplicationDeadline
        return base(
            request = request,
            methodMatched = methodMatched,
            destinationMatched = destinationMatched,
            headersMatched = headersMatched,
            approvedHeaderNamesPresent = approvedHeaderNamesPresent,
            unexpectedHeaderNameCount = unexpectedHeaderNameCount,
            timeoutWithinApplicationDeadline = timeoutWithinApplicationDeadline,
            topLevelShapeMatched = topLevelShapeMatched,
            unexpectedConfigurationFieldCount = unexpectedConfigurationFieldCount,
            requestedModelMatched = modelMatched,
            promptFingerprintMatched = promptFingerprintMatched,
            inputAllowlistMatched = inputAllowlistMatched,
            systemInstruction = instructions,
            inputPayload = input,
            outputSchema = schema,
            maxOutputTokens = maxOutputTokens,
            structuredOutputMode = structuredOutputMode,
            structuredOutputStrict = null,
            outputSchemaPresent = outputSchemaPresent,
            store = null,
            reasoningEffort = null,
            thinkingMode =
                thinking.closedText(
                    "type",
                    setOf("enabled", "disabled", "adaptive"),
                ),
            thinkingBudgetTokens = thinking.long("budget_tokens"),
            includeThoughts = null,
            truncationMode = null,
            serviceTier = body.closedText("service_tier", setOf("auto", "standard_only")),
            candidateCount = null,
            messageCount = messages.size().takeIf { messages.isArray },
            contentPartCount = null,
            temperature = body.double("temperature"),
            topP = body.double("top_p"),
            topK = body.long("top_k"),
            seed = null,
            stopSequenceCount = body.path("stop_sequences").arraySizeOrNull(),
            expectedConfigurationMatched =
                generalMatched &&
                    topLevelShapeMatched &&
                    nestedShapeMatched &&
                    unexpectedConfigurationFieldCount == 0 &&
                    modelMatched &&
                    promptFingerprintMatched &&
                    inputAllowlistMatched &&
                    outputSchemaFingerprintMatched &&
                    maxOutputTokens == expectedFingerprint.maxOutputTokens &&
                    structuredOutputMode == "json_schema" &&
                    outputSchemaPresent,
        )
    }

    private fun base(
        request: ProviderHttpRequest,
        methodMatched: Boolean,
        destinationMatched: Boolean,
        headersMatched: Boolean,
        approvedHeaderNamesPresent: List<String>,
        unexpectedHeaderNameCount: Int,
        timeoutWithinApplicationDeadline: Boolean,
        topLevelShapeMatched: Boolean,
        unexpectedConfigurationFieldCount: Int,
        requestedModelMatched: Boolean?,
        promptFingerprintMatched: Boolean?,
        inputAllowlistMatched: Boolean?,
        systemInstruction: String?,
        inputPayload: String?,
        outputSchema: JsonNode?,
        maxOutputTokens: Int?,
        structuredOutputMode: String?,
        structuredOutputStrict: Boolean?,
        outputSchemaPresent: Boolean,
        store: Boolean?,
        reasoningEffort: String?,
        thinkingMode: String?,
        thinkingBudgetTokens: Long?,
        includeThoughts: Boolean?,
        truncationMode: String?,
        serviceTier: String?,
        candidateCount: Int?,
        messageCount: Int?,
        contentPartCount: Int?,
        temperature: Double?,
        topP: Double?,
        topK: Long?,
        seed: Long?,
        stopSequenceCount: Int?,
        expectedConfigurationMatched: Boolean,
    ): SanitizedProviderRequestEvidence =
        SanitizedProviderRequestEvidence(
            requestBodyBytes = request.body.toByteArray(StandardCharsets.UTF_8).size,
            timeoutMillis = request.timeout.toMillis(),
            methodMatched = methodMatched,
            destinationMatched = destinationMatched,
            headersMatched = headersMatched,
            approvedHeaderNamesPresent = approvedHeaderNamesPresent,
            unexpectedHeaderNameCount = unexpectedHeaderNameCount,
            timeoutWithinApplicationDeadline = timeoutWithinApplicationDeadline,
            jsonObjectParsed = true,
            topLevelShapeMatched = topLevelShapeMatched,
            unexpectedConfigurationFieldCount = unexpectedConfigurationFieldCount,
            requestedModelMatched = requestedModelMatched,
            promptFingerprintMatched = promptFingerprintMatched,
            inputAllowlistMatched = inputAllowlistMatched,
            systemInstructionUtf8Bytes = systemInstruction?.utf8Bytes(),
            inputPayloadUtf8Bytes = inputPayload?.utf8Bytes(),
            outputSchemaUtf8Bytes = outputSchema?.toString()?.utf8Bytes(),
            maxOutputTokens = maxOutputTokens,
            structuredOutputMode = structuredOutputMode,
            structuredOutputStrict = structuredOutputStrict,
            outputSchemaPresent = outputSchemaPresent,
            store = store,
            reasoningEffort = reasoningEffort,
            thinkingMode = thinkingMode,
            thinkingBudgetTokens = thinkingBudgetTokens,
            includeThoughts = includeThoughts,
            truncationMode = truncationMode,
            serviceTier = serviceTier,
            candidateCount = candidateCount,
            messageCount = messageCount,
            contentPartCount = contentPartCount,
            temperature = temperature,
            topP = topP,
            topK = topK,
            seed = seed,
            stopSequenceCount = stopSequenceCount,
            expectedConfigurationMatched = expectedConfigurationMatched,
        )

    private fun empty(
        request: ProviderHttpRequest,
        methodMatched: Boolean,
        destinationMatched: Boolean,
        headersMatched: Boolean,
        approvedHeaderNamesPresent: List<String>,
        unexpectedHeaderNameCount: Int,
        timeoutWithinApplicationDeadline: Boolean,
    ): SanitizedProviderRequestEvidence =
        SanitizedProviderRequestEvidence(
            requestBodyBytes = request.body.toByteArray(StandardCharsets.UTF_8).size,
            timeoutMillis = request.timeout.toMillis(),
            methodMatched = methodMatched,
            destinationMatched = destinationMatched,
            headersMatched = headersMatched,
            approvedHeaderNamesPresent = approvedHeaderNamesPresent,
            unexpectedHeaderNameCount = unexpectedHeaderNameCount,
            timeoutWithinApplicationDeadline = timeoutWithinApplicationDeadline,
            jsonObjectParsed = false,
            topLevelShapeMatched = false,
            unexpectedConfigurationFieldCount = 0,
            requestedModelMatched = null,
            promptFingerprintMatched = null,
            inputAllowlistMatched = null,
            systemInstructionUtf8Bytes = null,
            inputPayloadUtf8Bytes = null,
            outputSchemaUtf8Bytes = null,
            maxOutputTokens = null,
            structuredOutputMode = null,
            structuredOutputStrict = null,
            outputSchemaPresent = false,
            store = null,
            reasoningEffort = null,
            thinkingMode = null,
            thinkingBudgetTokens = null,
            includeThoughts = null,
            truncationMode = null,
            serviceTier = null,
            candidateCount = null,
            messageCount = null,
            contentPartCount = null,
            temperature = null,
            topP = null,
            topK = null,
            seed = null,
            stopSequenceCount = null,
            expectedConfigurationMatched = false,
        )

    private fun JsonNode.hasExactFields(expected: Set<String>): Boolean =
        isObject && propertyNames().asSequence().toSet() == expected

    private fun JsonNode.unexpectedFieldCount(expected: Set<String>): Int =
        if (isObject) {
            (propertyNames().asSequence().toSet() - expected).size
        } else {
            1
        }

    private fun JsonNode.text(fieldName: String): String? =
        get(fieldName)?.takeIf(JsonNode::isString)?.stringValue()

    private fun JsonNode.int(fieldName: String): Int? =
        get(fieldName)?.takeIf(JsonNode::isIntegralNumber)?.intValue()

    private fun JsonNode.long(fieldName: String): Long? =
        get(fieldName)?.takeIf(JsonNode::isIntegralNumber)?.longValue()

    private fun JsonNode.double(fieldName: String): Double? =
        get(fieldName)?.takeIf(JsonNode::isNumber)?.doubleValue()

    private fun JsonNode.boolean(fieldName: String): Boolean? =
        get(fieldName)?.takeIf(JsonNode::isBoolean)?.booleanValue()

    private fun JsonNode.closedText(
        fieldName: String,
        allowedValues: Set<String>,
    ): String? =
        text(fieldName)?.let { value -> if (value in allowedValues) value else "other" }

    private fun JsonNode.arraySizeOrNull(): Int? =
        size().takeIf { isArray }

    private fun JsonNode.stopSequenceCount(fieldName: String): Int? {
        val node = get(fieldName) ?: return null
        return when {
            node.isString -> 1
            node.isArray -> node.size()
            else -> null
        }
    }

    private fun String.utf8Bytes(): Int =
        toByteArray(StandardCharsets.UTF_8).size

    private fun List<String>.countValues(): Map<String, Int> =
        groupingBy { it }.eachCount().toSortedMap()

    private companion object {
        val OPENAI_TOP_LEVEL_FIELDS =
            setOf(
                "model",
                "instructions",
                "input",
                "store",
                "max_output_tokens",
                "text",
            )
        val GEMINI_TOP_LEVEL_FIELDS =
            setOf("systemInstruction", "contents", "generationConfig")
        val ANTHROPIC_TOP_LEVEL_FIELDS =
            setOf("model", "max_tokens", "system", "messages", "output_config")
    }
}

internal fun hasApprovedLiveBioPayload(
    objectMapper: JsonMapper,
    inputJson: String,
): Boolean =
    try {
        val payload =
            objectMapper.reader()
                .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .readTree(inputJson)
        payload.isObject &&
            payload.propertyNames().asSequence().toSet() == APPROVED_INPUT_FIELDS &&
            payload.text("display_name") == BioTemplateRequest.DISPLAY_NAME_TOKEN &&
            payload.text("locale") == BioTemplateRequest.DEPLOYMENT_LOCALE &&
            payload.text("country_code") == BioTemplateRequest.DEPLOYMENT_COUNTRY_CODE &&
            payload.text("job_category") in APPROVED_JOB_CODES &&
            payload.text("job_category_mapping_version") ==
            BioTemplateRequest.JOB_MAPPING_VERSION &&
            payload.approvedInterests() &&
            payload.text("interest_category_mapping_version") ==
            BioTemplateRequest.INTEREST_MAPPING_VERSION &&
            payload.text("tone") == "quirky"
    } catch (_: RuntimeException) {
        false
    }

private fun JsonNode.text(fieldName: String): String? =
    get(fieldName)?.takeIf(JsonNode::isString)?.stringValue()

private fun JsonNode.approvedInterests(): Boolean {
    val nodes =
        get("interests")
            ?.takeIf(JsonNode::isArray)
            ?.toList()
            ?: return false
    if (nodes.isEmpty() || nodes.any { node -> !node.isString }) {
        return false
    }
    val values = nodes.map(JsonNode::stringValue)
    return values.all { value -> value in APPROVED_INTEREST_CODES } &&
        values.distinct() == values
}

private val APPROVED_INPUT_FIELDS =
    setOf(
        "display_name",
        "locale",
        "country_code",
        "job_category",
        "job_category_mapping_version",
        "interests",
        "interest_category_mapping_version",
        "tone",
    )
private val APPROVED_JOB_CODES =
    SafeJobCode.entries.mapTo(mutableSetOf(), SafeJobCode::wireValue)
private val APPROVED_INTEREST_CODES =
    SafeInterestCode.entries.mapTo(mutableSetOf(), SafeInterestCode::wireValue)
