package com.persons.finder.person.bio.remote.eval

import com.persons.finder.person.bio.BIO_GENERATION_DEADLINE
import com.persons.finder.person.bio.BioGenerationFailure
import com.persons.finder.person.bio.BioGenerationResult
import com.persons.finder.person.bio.BioTemplateRequest
import com.persons.finder.person.bio.SafeInterestCode
import com.persons.finder.person.bio.SafeJobCode
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
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.JsonNode
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
        val model = requireValue(environment, provider.modelEnvironmentName)
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
                    "pacing_strategy" to plan.pacingStrategy,
                    "minimum_call_interval_millis" to plan.minimumCallIntervalMillis,
                    "configured_minimum_call_start_span_millis" to
                        plan.configuredMinimumCallStartSpanMillis,
                    "maximum_one_sided_95_percent_wilson_upper_failure_bound" to
                        maximumFailureUpperBound,
                    "prompt_sha256" to fingerprint.promptSha256,
                    "output_schema_sha256" to fingerprint.outputSchemaSha256,
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

        // Read only the selected provider credential, after every non-secret preflight check.
        val credential = requireValue(environment, provider.credentialEnvironmentName)
        val transport =
            InspectingProviderHttpTransport(
                delegate = JdkProviderHttpTransport(),
                provider = provider,
                exactModelId = model,
                maxCalls = plan.plannedCalls,
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
        val report =
            runner.run(
                corpus = corpus,
                configuration = configuration,
                generator = RemoteBioGenerator(inspectedClient, objectMapper),
            )
        val boundaryViolations =
            mergeCounts(
                inspectedClient.violationCounts,
                transport.violationCounts,
            )
        val boundaryViolationCount = boundaryViolations.values.sum()
        val harnessErrorCount =
            report.overall.resultCounts.getValue(BioEvalOutcome.HARNESS_ERROR)
        val reliabilityPassed =
            maximumFailureUpperBound == null ||
                report.overall.oneSided95WilsonUpperFailureBound <= maximumFailureUpperBound
        val passed =
            boundaryViolationCount == 0 &&
                harnessErrorCount == 0 &&
                reliabilityPassed
        val sanitizedReport =
            linkedMapOf<String, Any>().apply {
                putAll(report.toSanitizedMap())
                put(
                    "execution",
                    linkedMapOf(
                        "model_provider_client_invocations" to
                            inspectedClient.providerClientInvocations,
                        "delegated_http_send_attempts" to
                            transport.delegatedHttpSendAttempts,
                        "working_tree_clean" to revision.workingTreeClean,
                        "hard_boundary_violation_count" to boundaryViolationCount,
                        "hard_boundary_violation_counts" to boundaryViolations,
                    ),
                )
                put(
                    "gate",
                    linkedMapOf<String, Any>(
                        "reliability_gate_configured" to
                            (maximumFailureUpperBound != null),
                        "synthetic_retention_and_data_use_approved" to true,
                        "hard_boundary_violations" to boundaryViolationCount,
                        "harness_errors" to harnessErrorCount,
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
        val reportPath = writeReport(objectMapper, sanitizedReport)

        println(
            "Live AI evaluation completed: attempts=${report.overall.attempts}, " +
                "failures=${report.overall.failureCount}, " +
                "failureUpper95=${report.overall.oneSided95WilsonUpperFailureBound}, " +
                "httpSendAttempts=${transport.delegatedHttpSendAttempts}, report=$reportPath",
        )
        check(passed) {
            "Live AI evaluation gate failed; inspect the sanitized report"
        }
    }

    private fun writeReport(
        objectMapper: JsonMapper,
        report: Map<String, Any>,
    ): Path {
        val reportDirectory =
            Path.of(
                System.getProperty(
                    "liveAiEval.reportDir",
                    "build/reports/live-ai-eval",
                ),
            )
        Files.createDirectories(reportDirectory)
        val reportPath = reportDirectory.resolve("report.json")
        val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report)
        Files.writeString(reportPath, "$json\n", StandardCharsets.UTF_8)
        return reportPath.fileName
    }

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
}

internal enum class LiveBioEvalProvider(
    val wireValue: String,
    val credentialEnvironmentName: String,
    val modelEnvironmentName: String,
    val expectedHost: String,
    val expectedHeaderNames: Set<String>,
) {
    OPENAI(
        wireValue = "openai",
        credentialEnvironmentName = "OPENAI_API_KEY",
        modelEnvironmentName = "OPENAI_LIVE_MODEL",
        expectedHost = "api.openai.com",
        expectedHeaderNames = setOf("Authorization", "Content-Type"),
    ),
    GEMINI(
        wireValue = "gemini",
        credentialEnvironmentName = "GEMINI_API_KEY",
        modelEnvironmentName = "GEMINI_LIVE_MODEL",
        expectedHost = "generativelanguage.googleapis.com",
        expectedHeaderNames = setOf("x-goog-api-key", "Content-Type"),
    ),
    ANTHROPIC(
        wireValue = "anthropic",
        credentialEnvironmentName = "ANTHROPIC_API_KEY",
        modelEnvironmentName = "ANTHROPIC_LIVE_MODEL",
        expectedHost = "api.anthropic.com",
        expectedHeaderNames = setOf("x-api-key", "anthropic-version", "Content-Type"),
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
            if (!hasApprovedPayload(request.inputJson)) {
                add("application_input_allowlist")
            }
        }

    private fun hasApprovedPayload(inputJson: String): Boolean =
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

    private fun recordViolation(code: String) {
        mutableViolationCounts[code] = mutableViolationCounts.getOrDefault(code, 0) + 1
    }

    private companion object {
        val APPROVED_INPUT_FIELDS =
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
        val APPROVED_JOB_CODES = SafeJobCode.entries.mapTo(mutableSetOf(), SafeJobCode::wireValue)
        val APPROVED_INTEREST_CODES =
            SafeInterestCode.entries.mapTo(mutableSetOf(), SafeInterestCode::wireValue)
    }
}

internal class InspectingProviderHttpTransport(
    private val delegate: ProviderHttpTransport,
    private val provider: LiveBioEvalProvider,
    private val exactModelId: String,
    private val maxCalls: Int,
) : ProviderHttpTransport {
    private val mutableViolationCounts = linkedMapOf<String, Int>()
    var delegatedHttpSendAttempts: Int = 0
        private set

    val violationCounts: Map<String, Int>
        get() = mutableViolationCounts.toMap()

    override fun send(request: ProviderHttpRequest): ProviderHttpResponse {
        val violations = inspect(request).toMutableSet()
        if (delegatedHttpSendAttempts >= maxCalls) {
            violations += "http_call_budget"
        }
        if (violations.isNotEmpty()) {
            violations.forEach(::recordViolation)
            throw IllegalStateException("Live AI request blocked by evaluation boundary")
        }
        delegatedHttpSendAttempts++
        return delegate.send(request)
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

    private companion object {
        val FORBIDDEN_BODY_PATTERN =
            Regex(
                """(?i)"(?:metadata|tools|cachedContent)"|"store"\s*:\s*true""",
            )
    }
}

private fun mergeCounts(
    first: Map<String, Int>,
    second: Map<String, Int>,
): Map<String, Int> =
    (first.keys + second.keys)
        .sorted()
        .associateWith { key -> first.getOrDefault(key, 0) + second.getOrDefault(key, 0) }
