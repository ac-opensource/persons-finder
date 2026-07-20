package com.persons.finder.person.bio.remote.eval

import com.persons.finder.person.bio.BIO_GENERATION_DEADLINE
import com.persons.finder.person.bio.BioGenerationFailure
import com.persons.finder.person.bio.BioGenerationResult
import com.persons.finder.person.bio.BioTemplateRequest
import com.persons.finder.person.bio.remote.AnthropicModelProviderClient
import com.persons.finder.person.bio.remote.GeminiModelProviderClient
import com.persons.finder.person.bio.remote.JdkProviderHttpTransport
import com.persons.finder.person.bio.remote.LiveAiTestAuthorization
import com.persons.finder.person.bio.remote.ModelGenerationRequest
import com.persons.finder.person.bio.remote.ModelProviderClient
import com.persons.finder.person.bio.remote.ModelProviderResult
import com.persons.finder.person.bio.remote.OpenAiModelProviderClient
import com.persons.finder.person.bio.remote.ProviderHttpRequest
import com.persons.finder.person.bio.remote.ProviderHttpResponse
import com.persons.finder.person.bio.remote.ProviderHttpTransport
import com.persons.finder.person.bio.remote.RemoteBioGenerator
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import tools.jackson.databind.json.JsonMapper

object LiveBioEvalMain {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.isEmpty() || args.contentEquals(arrayOf("--plan"))) {
            "The live AI evaluator accepts only the optional --plan argument"
        }
        val planOnly = args.contentEquals(arrayOf("--plan"))
        val environment: (String) -> String? = System::getenv
        val provider = LiveBioEvalProvider.fromWireValue(requireValue(environment, "LIVE_AI_PROVIDER"))
        val corpus = BioEvalCorpusLoader.load()
        val repetitions = requirePositiveInt(environment, "LIVE_AI_EVAL_REPETITIONS")
        val maxCalls = requirePositiveInt(environment, "LIVE_AI_EVAL_MAX_CALLS")
        val minimumCallInterval = requireLiveBioEvalMinimumCallInterval(environment)
        val model =
            provider.requireValidModelId(
                requireValue(environment, provider.modelEnvironmentName),
            )
        val revision = LiveBioEvalRevision.capture()
        val maximumFailureUpperBound =
            environment("LIVE_AI_EVAL_MAX_FAILURE_UPPER_BOUND")
                ?.takeIf(String::isNotBlank)
                ?.let(::parseFailureUpperBound)
        val objectMapper = JsonMapper.builder().build()
        val fingerprint =
            captureApplicationRequestFingerprint(
                objectMapper = objectMapper,
                request = corpus.cases.first().toRequest(),
            )
        val configuration =
            LiveBioEvalConfiguration(
                provider = provider.wireValue,
                exactModelId = model,
                codeRevision = revision.commit,
                promptSha256 = fingerprint.promptSha256,
                outputSchemaSha256 = fingerprint.outputSchemaSha256,
                maxOutputTokens = fingerprint.maxOutputTokens,
                repetitions = repetitions,
                maxCalls = maxCalls,
                minimumCallInterval = minimumCallInterval,
            )
        val runner = LiveBioEvalRunner()
        val plan = runner.planOnly(corpus, configuration)

        if (planOnly) {
            printJson(
                objectMapper,
                linkedMapOf<String, Any?>(
                    "mode" to "plan_only_no_provider_calls",
                    "provider" to provider.wireValue,
                    "exact_model_id" to model,
                    "code_revision" to revision.commit,
                    "working_tree_clean" to revision.workingTreeClean,
                    "corpus_id" to plan.corpusId,
                    "corpus_sha256" to plan.corpusSha256,
                    "case_count" to plan.caseCount,
                    "repetitions" to plan.repetitions,
                    "planned_calls" to plan.plannedCalls,
                    "maximum_authorized_calls" to plan.maxCalls,
                    "model_authored_code_point_limit" to
                        plan.modelAuthoredCodePointLimit,
                    "maximum_grounding_source_code_points" to
                        plan.maximumGroundingSourceCodePoints,
                    "final_grounded_code_point_limit" to
                        plan.finalGroundedCodePointLimit,
                    "grounding_strategy" to plan.groundingStrategy,
                    "pacing_strategy" to plan.pacingStrategy,
                    "minimum_call_interval_millis" to plan.minimumCallIntervalMillis,
                    "configured_minimum_call_start_span_millis" to
                        plan.configuredMinimumCallStartSpanMillis,
                    "maximum_one_sided_95_percent_wilson_upper_failure_bound" to
                        maximumFailureUpperBound,
                    "prompt_sha256" to fingerprint.promptSha256,
                    "output_schema_sha256" to fingerprint.outputSchemaSha256,
                    "max_output_tokens" to fingerprint.maxOutputTokens,
                ),
            )
            return
        }
        require(revision.workingTreeClean) {
            "A live AI evidence run requires a clean working tree"
        }

        LiveAiTestAuthorization.requirements(provider.name).forEach { requirement ->
            requireConfirmation(
                environment = environment,
                name = requirement.environmentName,
                reason = requirement.reason,
            )
        }
        requireEvidenceDirectoriesWritable(
            liveAiEvalReportDirectory(),
            liveAiEvalDurableReportDirectory().resolve(revision.commit),
        )

        // Read only the selected provider credential, after every non-secret preflight check.
        val credential = requireValue(environment, provider.credentialEnvironmentName)
        provider.requireCredentialDistinctFromModel(
            credential = credential,
            model = model,
        )
        val transport =
            InspectingProviderHttpTransport(
                delegate = JdkProviderHttpTransport(),
                provider = provider,
                exactModelId = model,
                expectedCredential = credential,
                maxCalls = plan.plannedCalls,
                expectedFingerprint = fingerprint,
                objectMapper = objectMapper,
            )
        val providerClient =
            provider.createClient(
                credential = credential,
                model = model,
                objectMapper = objectMapper,
                transport = transport,
            )
        val inspectedClient =
            InspectingModelProviderClient(
                delegate = providerClient,
                objectMapper = objectMapper,
                expectedFingerprint = fingerprint,
            )
        val applicationDiagnostics = LiveRemoteBioDiagnosticAccumulator()
        fun writeEvidenceSnapshot(
            report: LiveBioEvalReport,
            executionFinalized: Boolean,
        ): SanitizedLiveBioEval {
            val sanitized =
                buildSanitizedReport(
                    report = report,
                    plannedCalls = plan.plannedCalls,
                    revision = revision,
                    maximumFailureUpperBound = maximumFailureUpperBound,
                    inspectedClient = inspectedClient,
                    transport = transport,
                    applicationDiagnostics = applicationDiagnostics,
                    executionFinalized = executionFinalized,
                )
            writeReport(
                objectMapper = objectMapper,
                report = sanitized.value,
                codeRevision = revision.commit,
                provider = provider.wireValue,
                exactModelId = model,
                startedAt = report.startedAt,
            )
            return sanitized
        }
        val report =
            runner.run(
                corpus = corpus,
                configuration = configuration,
                generator =
                    RemoteBioGenerator(
                        inspectedClient,
                        objectMapper,
                        applicationDiagnostics,
                    ),
                stopAfterAttempt = {
                    inspectedClient.violationCounts.values.sum() > 0 ||
                        transport.violationCounts.values.sum() > 0 ||
                        transport.terminalProviderFailureCategory != null
                },
                afterAttempt = { partialReport ->
                    // Atomically overwrite the same durable run file after every completed
                    // paid attempt, so cancellation preserves all prior metered evidence.
                    writeEvidenceSnapshot(
                        report = partialReport,
                        executionFinalized = false,
                    )
                },
            )
        val finalSanitized = writeEvidenceSnapshot(report, executionFinalized = true)
        val reportPath = liveAiEvalReportDirectory().resolve("report.json")

        println(
            "Live AI evaluation completed: attempts=${report.overall.attempts}, " +
                "failures=${report.overall.failureCount}, " +
                "failureUpper95=${report.overall.oneSided95WilsonUpperFailureBound}, " +
                "httpSendAttempts=${transport.delegatedHttpSendAttempts}, " +
                "report=${reportPath.fileName}",
        )
        check(finalSanitized.passed) {
            "Live AI evaluation gate failed; inspect the sanitized report"
        }
    }

    private fun buildSanitizedReport(
        report: LiveBioEvalReport,
        plannedCalls: Int,
        revision: LiveBioEvalRevision,
        maximumFailureUpperBound: Double?,
        inspectedClient: InspectingModelProviderClient,
        transport: InspectingProviderHttpTransport,
        applicationDiagnostics: LiveRemoteBioDiagnosticAccumulator,
        executionFinalized: Boolean,
    ): SanitizedLiveBioEval {
        val boundaryViolations =
            mergeCounts(
                inspectedClient.violationCounts,
                transport.violationCounts,
            )
        val boundaryViolationCount = boundaryViolations.values.sum()
        val providerRequestEvidence = transport.providerRequestEvidence
        val providerResponseEvidence = transport.providerResponseEvidence
        val groundedEvidenceComplete =
            report.attemptEvidence.size == report.overall.attempts &&
                report.overall.validResultsWithoutGroundedMeasurement == 0
        val evidenceComplete =
            providerRequestEvidence["request_count"] == transport.delegatedHttpSendAttempts &&
                providerRequestEvidence["all_requests_match_expected_configuration"] == true &&
                providerResponseEvidence["attempt_count"] == transport.delegatedHttpSendAttempts &&
                groundedEvidenceComplete
        val allPlannedCallsCompleted = report.overall.attempts == plannedCalls
        val harnessErrorCount =
            report.overall.resultCounts.getValue(BioEvalOutcome.HARNESS_ERROR)
        val reliabilityPassed =
            maximumFailureUpperBound == null ||
                report.overall.oneSided95WilsonUpperFailureBound <= maximumFailureUpperBound
        val passed =
            executionFinalized &&
                boundaryViolationCount == 0 &&
                harnessErrorCount == 0 &&
                evidenceComplete &&
                allPlannedCallsCompleted &&
                transport.terminalProviderFailureCategory == null &&
                reliabilityPassed
        val stopReason =
            if (!executionFinalized) {
                "in_progress_checkpoint"
            } else {
                when {
                    boundaryViolationCount > 0 -> "security_boundary"
                    transport.terminalProviderFailureCategory != null ->
                        "terminal_provider_failure"

                    harnessErrorCount > 0 -> "harness_error"
                    allPlannedCallsCompleted -> "completed"
                    else -> "early_stop"
                }
            }
        val value =
            linkedMapOf<String, Any>().apply {
                putAll(report.toSanitizedMap())
                put(
                    "execution",
                    linkedMapOf(
                        "execution_finalized" to executionFinalized,
                        "model_provider_client_invocations" to
                            inspectedClient.providerClientInvocations,
                        "stop_reason" to stopReason,
                        "terminal_provider_failure_category" to
                            transport.terminalProviderFailureCategory,
                        "delegated_http_send_attempts" to
                            transport.delegatedHttpSendAttempts,
                        "working_tree_clean" to revision.workingTreeClean,
                        "hard_boundary_violation_count" to boundaryViolationCount,
                        "hard_boundary_violation_counts" to boundaryViolations,
                        "application_generation_diagnostics" to
                            applicationDiagnostics.summary(),
                        "provider_request_evidence" to providerRequestEvidence,
                        "provider_response_evidence" to providerResponseEvidence,
                    ),
                )
                val usageReportedResponseCount =
                    (providerResponseEvidence["usage_reported_response_count"] as? Number)
                        ?.toInt() ?: 0
                val providerResponseCount =
                    (providerResponseEvidence["response_count"] as? Number)?.toInt() ?: 0
                put(
                    "billing",
                    linkedMapOf(
                        "provider_usage_when_present_is_metering_evidence" to true,
                        "provider_response_received" to (providerResponseCount > 0),
                        "provider_usage_reported" to (usageReportedResponseCount > 0),
                        "usage_reported_response_count" to usageReportedResponseCount,
                        "metered_processing_evidenced" to
                            (usageReportedResponseCount > 0),
                        "actual_billed_usd" to null,
                        "actual_billing_requires_provider_billing_export" to true,
                    ),
                )
                put(
                    "gate",
                    linkedMapOf<String, Any>(
                        "execution_finalized" to executionFinalized,
                        "reliability_gate_configured" to
                            (maximumFailureUpperBound != null),
                        "synthetic_retention_and_data_use_approved" to true,
                        "hard_boundary_violations" to boundaryViolationCount,
                        "harness_errors" to harnessErrorCount,
                        "grounded_evidence_complete" to groundedEvidenceComplete,
                        "evidence_complete" to evidenceComplete,
                        "all_planned_calls_completed" to allPlannedCallsCompleted,
                        "passed" to passed,
                    ).apply {
                        maximumFailureUpperBound?.let { approvedBound ->
                            put(
                                "maximum_one_sided_95_percent_wilson_upper_failure_bound",
                                approvedBound,
                            )
                        }
                    },
                )
            }
        return SanitizedLiveBioEval(value = value, passed = passed)
    }

    private fun writeReport(
        objectMapper: JsonMapper,
        report: Map<String, Any>,
        codeRevision: String,
        provider: String,
        exactModelId: String,
        startedAt: java.time.Instant,
    ): LiveEvidencePaths {
        return writeSanitizedEvidenceCopies(
            objectMapper = objectMapper,
            report = report,
            scratchPath = liveAiEvalReportDirectory().resolve("report.json"),
            durableReportDirectory = liveAiEvalDurableReportDirectory(),
            codeRevision = codeRevision,
            provider = provider,
            exactModelId = exactModelId,
            startedAt = startedAt,
        )
    }

    private fun liveAiEvalReportDirectory(): Path =
        Path.of(
            System.getProperty(
                "liveAiEval.reportDir",
                "build/reports/live-ai-eval",
            ),
        )

    private fun liveAiEvalDurableReportDirectory(): Path =
        Path.of(
            System.getProperty(
                "liveAiEval.durableReportDir",
                ".agents/evidence/live-ai-eval",
            ),
        )

    private fun printJson(
        objectMapper: JsonMapper,
        value: Map<String, Any?>,
    ) {
        println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value))
    }

    private fun requireValue(
        environment: (String) -> String?,
        name: String,
    ): String {
        val value = environment(name)
        require(!value.isNullOrBlank() && value == value.trim()) {
            "$name is required and must not have surrounding whitespace"
        }
        return value
    }

    private fun requirePositiveInt(
        environment: (String) -> String?,
        name: String,
    ): Int {
        val value = requireValue(environment, name)
        return value.toIntOrNull()?.takeIf { parsed -> parsed > 0 }
            ?: throw IllegalArgumentException("$name must be a positive integer")
    }

    private fun requireConfirmation(
        environment: (String) -> String?,
        name: String,
        reason: String,
    ) {
        require(environment(name) == "true") { "$reason ($name=true)" }
    }

    private fun parseFailureUpperBound(value: String): Double =
        value.toDoubleOrNull()
            ?.takeIf { parsed -> parsed > 0.0 && parsed <= 1.0 }
            ?: throw IllegalArgumentException(
                "LIVE_AI_EVAL_MAX_FAILURE_UPPER_BOUND must be greater than zero and at most one",
            )

    private data class SanitizedLiveBioEval(
        val value: Map<String, Any>,
        val passed: Boolean,
    )
}

internal enum class LiveBioEvalProvider(
    val wireValue: String,
    val credentialEnvironmentName: String,
    val modelEnvironmentName: String,
    val expectedHost: String,
    val expectedHeaderNames: Set<String>,
    private val modelIdPattern: Regex,
) {
    OPENAI(
        wireValue = "openai",
        credentialEnvironmentName = "OPENAI_API_KEY",
        modelEnvironmentName = "OPENAI_LIVE_MODEL",
        expectedHost = "api.openai.com",
        expectedHeaderNames = setOf("Authorization", "Content-Type"),
        modelIdPattern = Regex("gpt-[A-Za-z0-9][A-Za-z0-9._-]{0,95}"),
    ),
    GEMINI(
        wireValue = "gemini",
        credentialEnvironmentName = "GEMINI_API_KEY",
        modelEnvironmentName = "GEMINI_LIVE_MODEL",
        expectedHost = "generativelanguage.googleapis.com",
        expectedHeaderNames = setOf("x-goog-api-key", "Content-Type"),
        modelIdPattern = Regex("gemini-[A-Za-z0-9][A-Za-z0-9._-]{0,95}"),
    ),
    ANTHROPIC(
        wireValue = "anthropic",
        credentialEnvironmentName = "ANTHROPIC_API_KEY",
        modelEnvironmentName = "ANTHROPIC_LIVE_MODEL",
        expectedHost = "api.anthropic.com",
        expectedHeaderNames = setOf("x-api-key", "anthropic-version", "Content-Type"),
        modelIdPattern = Regex("claude-[A-Za-z0-9][A-Za-z0-9._-]{0,95}"),
    ),
    ;

    fun expectedRawPath(model: String): String =
        when (this) {
            OPENAI -> "/v1/responses"
            GEMINI ->
                "/v1beta/models/" +
                    URLEncoder.encode(model, StandardCharsets.UTF_8).replace("+", "%20") +
                    ":generateContent"

            ANTHROPIC -> "/v1/messages"
        }

    fun requireValidModelId(value: String): String {
        require(modelIdPattern.matches(value)) {
            "$modelEnvironmentName must be a provider-specific model identifier"
        }
        return value
    }

    fun requireCredentialDistinctFromModel(
        credential: String,
        model: String,
    ) {
        require(credential != model) {
            "The selected credential and model identifier must be different"
        }
    }

    fun expectedHeaderValueFingerprints(credential: String): Map<String, String> =
        expectedHeaderValues(credential)
            .mapValues { (_, value) -> BioEvalHash.sha256(value) }

    private fun expectedHeaderValues(credential: String): Map<String, String> =
        when (this) {
            OPENAI ->
                mapOf(
                    "Authorization" to "Bearer $credential",
                    "Content-Type" to "application/json",
                )

            GEMINI ->
                mapOf(
                    "x-goog-api-key" to credential,
                    "Content-Type" to "application/json",
                )

            ANTHROPIC ->
                mapOf(
                    "x-api-key" to credential,
                    "anthropic-version" to "2023-06-01",
                    "Content-Type" to "application/json",
                )
        }

    fun createClient(
        credential: String,
        model: String,
        objectMapper: JsonMapper,
        transport: ProviderHttpTransport,
    ): ModelProviderClient =
        when (this) {
            OPENAI ->
                OpenAiModelProviderClient(
                    apiKey = credential,
                    model = model,
                    timeout = BIO_GENERATION_DEADLINE,
                    objectMapper = objectMapper,
                    transport = transport,
                )

            GEMINI ->
                GeminiModelProviderClient(
                    apiKey = credential,
                    model = model,
                    timeout = BIO_GENERATION_DEADLINE,
                    objectMapper = objectMapper,
                    transport = transport,
                )

            ANTHROPIC ->
                AnthropicModelProviderClient(
                    apiKey = credential,
                    model = model,
                    timeout = BIO_GENERATION_DEADLINE,
                    objectMapper = objectMapper,
                    transport = transport,
                )
        }

    companion object {
        fun fromWireValue(value: String): LiveBioEvalProvider =
            entries.firstOrNull { provider -> provider.wireValue == value }
                ?: throw IllegalArgumentException(
                    "LIVE_AI_PROVIDER must be one of openai, gemini, or anthropic",
                )
    }
}

internal data class LiveBioEvalRevision(
    val commit: String,
    val workingTreeClean: Boolean,
) {
    init {
        require(Regex("[a-f0-9]{40,64}").matches(commit)) {
            "Git revision is not a full commit identifier"
        }
    }

    companion object {
        fun capture(): LiveBioEvalRevision =
            LiveBioEvalRevision(
                commit = git("rev-parse", "HEAD"),
                workingTreeClean = git("status", "--porcelain=v1").isEmpty(),
            )

        private fun git(vararg arguments: String): String {
            val process =
                ProcessBuilder(listOf("git") + arguments)
                    .redirectErrorStream(true)
                    .start()
            val output = process.inputStream.bufferedReader().use { reader -> reader.readText() }
            require(process.waitFor() == 0) {
                "Unable to derive live AI evaluation revision from Git"
            }
            return output.trim()
        }
    }
}

internal data class ApplicationRequestFingerprint(
    val promptSha256: String,
    val outputSchemaSha256: String,
    val maxOutputTokens: Int,
)

internal fun captureApplicationRequestFingerprint(
    objectMapper: JsonMapper,
    request: BioTemplateRequest,
): ApplicationRequestFingerprint {
    var captured: ModelGenerationRequest? = null
    val generator =
        RemoteBioGenerator(
            providerClient =
                ModelProviderClient { providerRequest ->
                    captured = providerRequest
                    ModelProviderResult.Generated(
                        """{"bio_template":"{{NAME}} turns {{HOBBY}} into a quirky side quest after work as a {{JOB}}."}""",
                    )
                },
            objectMapper = objectMapper,
        )
    check(generator.generate(request) is BioGenerationResult.Template) {
        "Unable to capture the application-owned provider request"
    }
    val providerRequest = checkNotNull(captured)
    return ApplicationRequestFingerprint(
        promptSha256 = BioEvalHash.sha256(providerRequest.instructions),
        outputSchemaSha256 = BioEvalHash.sha256(providerRequest.outputSchemaJson),
        maxOutputTokens = providerRequest.maxOutputTokens,
    )
}

internal class InspectingModelProviderClient(
    private val delegate: ModelProviderClient,
    private val objectMapper: JsonMapper,
    private val expectedFingerprint: ApplicationRequestFingerprint,
) : ModelProviderClient {
    private val mutableViolationCounts = linkedMapOf<String, Int>()
    var providerClientInvocations: Int = 0
        private set

    val violationCounts: Map<String, Int>
        get() = mutableViolationCounts.toMap()

    override fun generate(request: ModelGenerationRequest): ModelProviderResult {
        val violations = inspect(request)
        if (violations.isNotEmpty()) {
            violations.forEach(::recordViolation)
            return ModelProviderResult.Failure(BioGenerationFailure.INVALID_OUTPUT)
        }
        providerClientInvocations++
        return delegate.generate(request)
    }

    private fun inspect(request: ModelGenerationRequest): Set<String> =
        buildSet {
            if (BioEvalHash.sha256(request.instructions) != expectedFingerprint.promptSha256) {
                add("application_prompt_fingerprint")
            }
            if (
                BioEvalHash.sha256(request.outputSchemaJson) !=
                expectedFingerprint.outputSchemaSha256
            ) {
                add("application_output_schema_fingerprint")
            }
            if (request.maxOutputTokens != expectedFingerprint.maxOutputTokens) {
                add("application_output_token_bound")
            }
            if (!hasApprovedLiveBioPayload(objectMapper, request.inputJson)) {
                add("application_input_allowlist")
            }
        }

    private fun recordViolation(code: String) {
        mutableViolationCounts[code] = mutableViolationCounts.getOrDefault(code, 0) + 1
    }

}

internal class InspectingProviderHttpTransport(
    private val delegate: ProviderHttpTransport,
    private val provider: LiveBioEvalProvider,
    private val exactModelId: String,
    expectedCredential: String,
    private val maxCalls: Int,
    expectedFingerprint: ApplicationRequestFingerprint,
    objectMapper: JsonMapper,
) : ProviderHttpTransport {
    private val mutableViolationCounts = linkedMapOf<String, Int>()
    private val expectedHeaderValueFingerprints =
        provider.expectedHeaderValueFingerprints(expectedCredential)
    private val evidence =
        LiveProviderEvidenceAccumulator(
            provider = provider,
            exactModelId = exactModelId,
            objectMapper = objectMapper,
        )
    private val requestEvidence =
        LiveProviderRequestEvidenceAccumulator(
            provider = provider,
            exactModelId = exactModelId,
            expectedHeaderValueFingerprints = expectedHeaderValueFingerprints,
            expectedFingerprint = expectedFingerprint,
            objectMapper = objectMapper,
        )
    var delegatedHttpSendAttempts: Int = 0
        private set

    val violationCounts: Map<String, Int>
        get() = mutableViolationCounts.toMap()
    val providerResponseEvidence: Map<String, Any>
        get() = evidence.summary()
    val providerRequestEvidence: Map<String, Any>
        get() = requestEvidence.summary()
    val terminalProviderFailureCategory: String?
        get() = evidence.latestTerminalProviderFailureCategory

    override fun send(request: ProviderHttpRequest): ProviderHttpResponse {
        val violations = inspect(request).toMutableSet()
        val sanitizedRequest = requestEvidence.record(request)
        if (!sanitizedRequest.expectedConfigurationMatched) {
            violations += "http_provider_configuration"
        }
        if (delegatedHttpSendAttempts >= maxCalls) {
            violations += "http_call_budget"
        }
        if (violations.isNotEmpty()) {
            violations.forEach(::recordViolation)
            throw IllegalStateException("Live AI request blocked by evaluation boundary")
        }
        delegatedHttpSendAttempts++
        val startedAtNanos = System.nanoTime()
        return try {
            delegate.send(request).also { response ->
                evidence.record(
                    response = response,
                    elapsedNanos = System.nanoTime() - startedAtNanos,
                )
            }
        } catch (failure: Exception) {
            evidence.recordTransportFailure(
                failure = failure,
                elapsedNanos = System.nanoTime() - startedAtNanos,
            )
            throw failure
        }
    }

    private fun inspect(request: ProviderHttpRequest): Set<String> =
        buildSet {
            if (request.method != "POST") add("http_method")
            if (request.uri.scheme != "https") add("http_scheme")
            if (request.uri.host != provider.expectedHost) add("http_host")
            if (request.uri.port != -1) add("http_port")
            if (request.uri.rawPath != provider.expectedRawPath(exactModelId)) add("http_path")
            if (request.uri.rawQuery != null) add("http_query")
            if (request.uri.userInfo != null) add("http_user_info")
            if (request.uri.fragment != null) add("http_fragment")
            if (request.headers.keys != provider.expectedHeaderNames) add("http_header_names")
            if (!request.headers.matchValueFingerprints(expectedHeaderValueFingerprints)) {
                add("http_header_values")
            }
            if (
                request.timeout <= Duration.ZERO ||
                request.timeout > BIO_GENERATION_DEADLINE
            ) {
                add("http_timeout")
            }
            if (FORBIDDEN_BODY_PATTERN.containsMatchIn(request.body)) {
                add("http_forbidden_body_field")
            }
        }

    private fun recordViolation(code: String) {
        mutableViolationCounts[code] = mutableViolationCounts.getOrDefault(code, 0) + 1
    }

    private fun Map<String, String>.matchValueFingerprints(
        expected: Map<String, String>,
    ): Boolean =
        keys == expected.keys &&
            all { (name, value) -> BioEvalHash.sha256(value) == expected[name] }

    private companion object {
        val FORBIDDEN_BODY_PATTERN =
            Regex(
                """(?i)"(?:metadata|tools|cachedContent)"|"store"\s*:\s*true""",
            )
    }
}

internal fun mergeCounts(
    first: Map<String, Int>,
    second: Map<String, Int>,
): Map<String, Int> =
    (first.keys + second.keys)
        .sorted()
        .associateWith { key -> first.getOrDefault(key, 0) + second.getOrDefault(key, 0) }
