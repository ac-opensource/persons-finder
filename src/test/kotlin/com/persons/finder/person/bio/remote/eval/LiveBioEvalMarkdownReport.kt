package com.persons.finder.person.bio.remote.eval

import java.nio.file.Path
import java.time.Instant
import java.util.Locale
import kotlin.math.ceil
import tools.jackson.databind.json.JsonMapper

/**
 * Renders the sanitized machine checkpoint into the tracked, human-readable
 * evidence format. It never receives request/response bodies or model prose.
 */
internal object LiveBioEvalMarkdownReport {
    fun render(report: Map<String, Any>): String {
        val provenance = report.requiredMap("provenance")
        val overall = report.requiredMap("overall")
        val execution = report.requiredMap("execution")
        val gate = report.requiredMap("gate")
        val billing = report.requiredMap("billing")
        val requestEvidence = execution.requiredMap("provider_request_evidence")
        val responseEvidence = execution.requiredMap("provider_response_evidence")
        val applicationEvidence =
            execution.requiredMap("application_generation_diagnostics")
        val attempts = report.requiredMapList("attempt_evidence")
        val attemptIndices = attempts.requireContiguousIndices("attempt_index")
        val requests =
            requestEvidence.optionalMapList("requests")
                .indexBy("request_index", attemptIndices)
        val providerAttempts =
            responseEvidence.optionalMapList("attempts")
                .indexBy("attempt_index", attemptIndices)
        val applicationEvents =
            applicationEvidence.optionalMapList("events")
                .indexBy("invocation_index", attemptIndices)
        val passed = gate.boolean("passed") == true

        val repetitions = provenance.int("repetitions")
        val plannedCalls = provenance.int("planned_calls")
        require(repetitions > 0 && plannedCalls > 0 && plannedCalls % repetitions == 0)
        val caseCount = plannedCalls / repetitions
        attempts.forEach { attempt ->
            val index = attempt.int("attempt_index")
            require(attempt.int("round") == ((index - 1) / caseCount) + 1)
            require(attempt.int("slot") == ((index - 1) % caseCount) + 1)
            val slices = attempt["slice_ids"] as? List<*>
                ?: throw IllegalArgumentException("slice_ids must be a sanitized list")
            require(slices.isNotEmpty() && slices.all { slice -> slice is String }) {
                "slice_ids must contain safe identifiers"
            }
            val outcome = attempt["normalized_result"] as? String
                ?: throw IllegalArgumentException("normalized_result must be text")
            require(outcome in BioEvalOutcome.entries.map(BioEvalOutcome::wireValue))
            val outputFields =
                listOf(
                    attempt["output_equivalence_id"],
                    attempt["model_authored_code_points"],
                    attempt["sentence_count"],
                    attempt["deterministic_catalog_match"],
                    attempt["final_grounded_code_points"],
                )
            require(
                (outcome == BioEvalOutcome.VALID_PROSE.wireValue) ==
                    outputFields.all { value -> value != null },
            ) {
                "Attempt $index has contradictory output evidence"
            }
            if (outcome != BioEvalOutcome.VALID_PROSE.wireValue) {
                require(outputFields.all { value -> value == null })
            }
        }

        val completeJoins =
            requests.keys == attemptIndices.toSet() &&
                providerAttempts.keys == attemptIndices.toSet() &&
                applicationEvents.keys == attemptIndices.toSet()
        if (passed) {
            require(gate.boolean("execution_finalized") == true)
            require(gate.boolean("all_planned_calls_completed") == true)
            require(attempts.size == plannedCalls)
            require(requestEvidence.int("request_count") == requests.size) {
                "request_count contradicts the request records"
            }
            require(responseEvidence.int("attempt_count") == providerAttempts.size) {
                "attempt_count contradicts the provider-attempt records"
            }
            require(applicationEvidence.int("diagnostic_count") == applicationEvents.size) {
                "diagnostic_count contradicts the application records"
            }
            require(gate.boolean("evidence_complete") == true && completeJoins) {
                "A passing report requires complete attempt joins"
            }
            require(requests.keys == attemptIndices.toSet()) {
                "A passing report requires one request record per attempt"
            }
            require(providerAttempts.keys == attemptIndices.toSet()) {
                "A passing report requires one provider record per attempt"
            }
            require(applicationEvents.keys == attemptIndices.toSet()) {
                "A passing report requires one application record per attempt"
            }
        }

        val markdown = StringBuilder()
        markdown.appendLine("# OpenAI live bio evaluation evidence")
        markdown.appendLine()
        markdown.appendLine("> Result: **${if (passed) "PASS" else "FAIL / INCOMPLETE"}**")
        markdown.appendLine()
        markdown.appendLine("## What this evidence establishes")
        markdown.appendLine()
        markdown.appendLine(
            "This is a fixed-corpus test of the application-owned structural and " +
                "security contract for a nondeterministic provider response. It " +
                "does not assert exact wording, subjective prose quality, or " +
                "production-wide reliability.",
        )
        markdown.appendLine()
        markdown.appendLine(
            "The report is content-safe: it contains no prompt text, schema text, " +
                "request or response body, model prose, profile value, credential, " +
                "exception message, endpoint, or raw provider identifier.",
        )
        markdown.appendLine()

        appendKeyValueTable(
            markdown,
            "Test identity",
            linkedMapOf(
                "Report schema" to report["report_schema_version"],
                "Data policy" to report["data_policy"],
                "Provider" to provenance["provider"],
                "Exact model" to provenance["exact_model_id"],
                "Clean code revision" to provenance["code_revision"],
                "Started (UTC)" to report["started_at"],
                "Completed (UTC)" to report["completed_at"],
                "Corpus ID" to provenance["corpus_id"],
                "Corpus schema" to provenance["corpus_schema_version"],
                "Corpus SHA-256" to provenance["corpus_sha256"],
                "Prompt SHA-256" to provenance["prompt_sha256"],
                "Output schema SHA-256" to provenance["output_schema_sha256"],
                "Cases per round" to caseCount,
                "Rounds" to provenance["repetitions"],
                "Planned calls" to provenance["planned_calls"],
                "Case order" to provenance["case_order_strategy"],
                "Pacing strategy" to provenance["pacing_strategy"],
                "Minimum call interval (ms)" to
                    provenance["minimum_call_interval_millis"],
                "Maximum provider output tokens" to provenance["max_output_tokens"],
                "Model-authored limit (code points)" to
                    provenance["model_authored_code_point_limit"],
                "Maximum source grounding (code points)" to
                    provenance["maximum_grounding_source_code_points"],
                "Final grounded limit (code points)" to
                    provenance["final_grounded_code_point_limit"],
            ),
        )
        appendScalarSection(markdown, "Acceptance gate", gate)
        appendScalarSection(markdown, "Aggregate result", overall)
        appendScalarSection(markdown, "Pacing", report.requiredMap("pacing"))
        appendScalarSection(markdown, "Execution accounting", execution, EXECUTION_EXCLUSIONS)
        appendScalarSection(markdown, "Billing evidence", billing)
        appendCostEstimate(markdown, provenance, responseEvidence)
        appendScalarSection(
            markdown,
            "Request-boundary summary",
            requestEvidence,
            setOf("requests"),
        )
        appendScalarSection(
            markdown,
            "Provider response and usage summary",
            responseEvidence,
            setOf("attempts", "responses", "transport_failures"),
        )
        appendScalarSection(
            markdown,
            "Application validation summary",
            applicationEvidence,
            setOf("events", "diagnostic_sequence"),
        )

        appendRoundTable(
            markdown = markdown,
            attempts = attempts,
            providerAttempts = providerAttempts,
            caseCount = caseCount,
            metricsByRound = report.requiredMap("by_round"),
        )
        appendMetricsTable(markdown, "Per-case results", "Case", report.requiredMap("by_case"))
        appendMetricsTable(markdown, "Per-slice results", "Slice", report.requiredMap("by_slice"))

        markdown.appendLine("## Per-attempt validation ledger")
        markdown.appendLine()
        markdown.appendLine("<details>")
        markdown.appendLine("<summary>Show all ${attempts.size} content-free attempt records</summary>")
        markdown.appendLine()
        markdown.appendLine(
                "| Call | Round | Slot | Case | Slices | Result | Output class | Authored cp | " +
                    "Grounded cp | Sentences | Catalog | App diagnostic | N/J/H counts | " +
                    "ASCII | Request checks |",
        )
        markdown.appendLine(
            "|---:|---:|---:|---|---|---|---|---:|---:|---:|---|---|---|---|---|",
        )
        attempts.forEach { attempt ->
            val index = attempt.int("attempt_index")
            val request = requests[index]
            val application = applicationEvents[index]
            markdown.appendLine(
                row(
                    index,
                    attempt["round"],
                    attempt["slot"],
                    attempt["case_id"],
                    attempt["slice_ids"].toCompactList(),
                    attempt["normalized_result"],
                    attempt["output_equivalence_id"],
                    attempt["model_authored_code_points"],
                    attempt["final_grounded_code_points"],
                    attempt["sentence_count"],
                    attempt["deterministic_catalog_match"],
                    application?.get("diagnostic"),
                    placeholderCounts(application),
                    application?.get("printable_ascii"),
                    request?.get("expected_configuration_matched"),
                ),
            )
        }
        markdown.appendLine()
        markdown.appendLine("</details>")
        markdown.appendLine()

        markdown.appendLine("## Per-attempt transport and metering ledger")
        markdown.appendLine()
        markdown.appendLine("<details>")
        markdown.appendLine("<summary>Show all ${attempts.size} provider-attempt records</summary>")
        markdown.appendLine()
        markdown.appendLine(
            "| Call | HTTP | Provider status | Diagnostic | Response bytes | " +
                "Transport ms | Input | Output | Total | Cached | Reasoning | Tool | " +
                "Output text cp | Refusals | Safety | Safe provider metadata |",
        )
        markdown.appendLine(
            "|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|",
        )
        attempts.forEach { attempt ->
            val index = attempt.int("attempt_index")
            val providerAttempt = providerAttempts[index]
            markdown.appendLine(
                row(
                    index,
                    providerAttempt?.get("http_status_code"),
                    providerAttempt?.get("provider_status"),
                    providerAttempt?.get("diagnostic")
                        ?: providerAttempt?.get("transport_failure_category"),
                    providerAttempt?.get("response_body_bytes"),
                    providerAttempt?.get("transport_latency_millis"),
                    providerAttempt?.get("input_tokens"),
                    providerAttempt?.get("output_tokens"),
                    providerAttempt?.get("total_tokens"),
                    providerAttempt?.get("cached_input_tokens"),
                    providerAttempt?.get("reasoning_or_thinking_tokens"),
                    providerAttempt?.get("tool_use_prompt_tokens"),
                    providerAttempt?.get("provider_output_text_code_points"),
                    providerAttempt?.get("refusal_item_count"),
                    providerAttempt?.get("safety_rating_count"),
                    providerAttempt?.get("safe_metadata_headers").toCompactMap(),
                ),
            )
        }
        markdown.appendLine()
        markdown.appendLine("</details>")
        markdown.appendLine()

        appendCompleteAttemptFacts(
            markdown = markdown,
            attempts = attempts,
            requests = requests,
            providerAttempts = providerAttempts,
            applicationEvents = applicationEvents,
        )
        appendReproduction(markdown, provenance, gate)
        markdown.appendLine("## Interpretation limits")
        markdown.appendLine()
        markdown.appendLine(
            "- The Wilson result is an overall result for this fixed, equally " +
                "weighted synthetic corpus and the provider conditions during this run.",
        )
        markdown.appendLine(
            "- Per-case samples contain fewer observations and must not be read as " +
                "simultaneous 95% guarantees.",
        )
        markdown.appendLine(
            "- Passing measures structural validity, local grounding, request-boundary " +
                "compliance, and metering evidence; it does not grade creativity or relevance.",
        )
        markdown.appendLine(
            "- Provider usage is evidence of metered processing. Actual billed cost " +
                "requires the provider billing export.",
        )
        return markdown.toString()
    }

    private fun appendRoundTable(
        markdown: StringBuilder,
        attempts: List<Map<String, Any?>>,
        providerAttempts: Map<Int, Map<String, Any?>>,
        caseCount: Int,
        metricsByRound: Map<String, Any?>,
    ) {
        markdown.appendLine("## Results by round")
        markdown.appendLine()
        markdown.appendLine(
            "| Round | Rotation | Calls | Valid | Failures | Distinct | Input | Output | " +
                "Max output | Max authored cp | Max grounded cp | Max sentences | " +
                "Wilson upper | Transport p95 ms |",
        )
        markdown.appendLine(
            "|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|",
        )
        attempts.groupBy { attempt -> attempt.int("round") }
            .toSortedMap()
            .forEach { (round, roundAttempts) ->
                val providerRows =
                    roundAttempts.mapNotNull { attempt ->
                        providerAttempts[attempt.int("attempt_index")]
                    }
                val valid =
                    roundAttempts.count { attempt ->
                        attempt["normalized_result"] == BioEvalOutcome.VALID_PROSE.wireValue
                    }
                val roundMetrics =
                    metricsByRound[round.toString()] as? Map<*, *>
                        ?: throw IllegalArgumentException(
                            "by_round is missing round $round",
                        )
                require(roundMetrics.int("attempts") == roundAttempts.size)
                markdown.appendLine(
                    row(
                        round,
                        roundAttempts.joinToString(" -> ") { attempt ->
                            attempt["case_id"].display()
                        },
                        roundAttempts.size,
                        valid,
                        roundAttempts.size - valid,
                        roundAttempts.mapNotNull { it["output_equivalence_id"] }.distinct().size,
                        providerRows.sumNullableLong("input_tokens"),
                        providerRows.sumNullableLong("output_tokens"),
                        providerRows.maxNullableLong("output_tokens"),
                        roundAttempts.maxNullableLong("model_authored_code_points"),
                        roundAttempts.maxNullableLong("final_grounded_code_points"),
                        roundAttempts.maxNullableLong("sentence_count"),
                        roundMetrics["one_sided_95_percent_wilson_upper_failure_bound"],
                        providerRows.nearestRank95("transport_latency_millis"),
                    ),
                )
                require(roundAttempts.size <= caseCount) {
                    "A round cannot contain more attempts than the corpus"
                }
            }
        markdown.appendLine()
    }

    private fun appendCompleteAttemptFacts(
        markdown: StringBuilder,
        attempts: List<Map<String, Any?>>,
        requests: Map<Int, Map<String, Any?>>,
        providerAttempts: Map<Int, Map<String, Any?>>,
        applicationEvents: Map<Int, Map<String, Any?>>,
    ) {
        markdown.appendLine("## Complete sanitized per-attempt facts")
        markdown.appendLine()
        markdown.appendLine(
            "This appendix preserves every scalar/list metric from the four " +
                "content-safe per-attempt records. Repeated aggregate-only arrays are " +
                "not duplicated.",
        )
        markdown.appendLine()
        markdown.appendLine("<details>")
        markdown.appendLine("<summary>Show complete sanitized fact matrix</summary>")
        markdown.appendLine()
        markdown.appendLine("| Call | Source | Metric | Value |")
        markdown.appendLine("|---:|---|---|---|")
        attempts.forEach { attempt ->
            val index = attempt.int("attempt_index")
            appendCompleteRecord(markdown, index, "evaluation", attempt)
            appendCompleteRecord(markdown, index, "request", requests[index])
            appendCompleteRecord(markdown, index, "provider", providerAttempts[index])
            appendCompleteRecord(markdown, index, "application", applicationEvents[index])
        }
        markdown.appendLine()
        markdown.appendLine("</details>")
        markdown.appendLine()
    }

    private fun appendCompleteRecord(
        markdown: StringBuilder,
        attemptIndex: Int,
        source: String,
        record: Map<String, Any?>?,
    ) {
        if (record == null) {
            markdown.appendLine(row(attemptIndex, source, "record", null))
            return
        }
        val flattened = linkedMapOf<String, Any?>()
        record.forEach { (key, value) -> flatten(key, value, flattened) }
        if (flattened.isEmpty()) {
            markdown.appendLine(row(attemptIndex, source, "record", null))
        } else {
            flattened.forEach { (metric, value) ->
                markdown.appendLine(row(attemptIndex, source, metric, value))
            }
        }
    }

    private fun appendMetricsTable(
        markdown: StringBuilder,
        title: String,
        label: String,
        metricsByKey: Map<String, Any?>,
    ) {
        markdown.appendLine("## $title")
        markdown.appendLine()
        markdown.appendLine(
            "| $label | Attempts | Valid | Failures | Distinct | Catalog | Max grounded cp | " +
                "Missing grounded | Wilson upper | p50 ms | p95 ms | Max ms |",
        )
        markdown.appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
        metricsByKey.toSortedMap().forEach { (key, value) ->
            val metrics = value as? Map<*, *>
                ?: throw IllegalArgumentException("$title contains a non-object metric")
            val latency = metrics.requiredMap("latency_nanos")
            markdown.appendLine(
                row(
                    key,
                    metrics["attempts"],
                    metrics["valid_prose_count"],
                    metrics["failure_count"],
                    metrics["distinct_valid_prose_count"],
                    metrics["deterministic_catalog_match_count"],
                    metrics["maximum_final_grounded_code_points"],
                    metrics["valid_results_without_grounded_measurement"],
                    metrics["one_sided_95_percent_wilson_upper_failure_bound"],
                    latency.longOrNull("p50")?.div(NANOS_PER_MILLISECOND),
                    latency.longOrNull("p95")?.div(NANOS_PER_MILLISECOND),
                    latency.longOrNull("max")?.div(NANOS_PER_MILLISECOND),
                ),
            )
        }
        markdown.appendLine()
    }

    private fun appendCostEstimate(
        markdown: StringBuilder,
        provenance: Map<String, Any?>,
        responseEvidence: Map<String, Any?>,
    ) {
        val inputTokens = responseEvidence.longOrNull("input_tokens")
        val outputTokens = responseEvidence.longOrNull("output_tokens")
        if (
            provenance["provider"] == "openai" &&
            inputTokens != null &&
            outputTokens != null
        ) {
            val estimate =
                (
                    inputTokens * OPENAI_INPUT_USD_PER_MILLION +
                        outputTokens * OPENAI_OUTPUT_USD_PER_MILLION
                ) / 1_000_000.0
            appendKeyValueTable(
                markdown,
                "Usage-cost estimate",
                linkedMapOf(
                    "Published input rate used (USD / 1M tokens)" to
                        OPENAI_INPUT_USD_PER_MILLION,
                    "Published output rate used (USD / 1M tokens)" to
                        OPENAI_OUTPUT_USD_PER_MILLION,
                    "Formula" to
                        "($inputTokens x $OPENAI_INPUT_USD_PER_MILLION + " +
                        "$outputTokens x $OPENAI_OUTPUT_USD_PER_MILLION) / 1,000,000",
                    "Estimated usage (USD)" to
                        String.format(Locale.ROOT, "%.6f", estimate),
                    "Actual billed cost" to "Unavailable without billing export",
                    "Pricing basis" to
                        "[OpenAI published standard API rates](" +
                            "https://developers.openai.com/api/docs/pricing) " +
                            "reviewed 2026-07-20",
                    "Estimate limitation" to
                        "Simple full-input/output-token estimate; actual billing can " +
                            "differ with caching, service tier, regional processing, " +
                            "or account adjustments",
                ),
            )
        }
    }

    private fun appendReproduction(
        markdown: StringBuilder,
        provenance: Map<String, Any?>,
        gate: Map<String, Any?>,
    ) {
        val provider = provenance["provider"].display()
        val model = provenance["exact_model_id"].display()
        val repetitions = provenance["repetitions"].display()
        val plannedCalls = provenance["planned_calls"].display()
        val interval = provenance["minimum_call_interval_millis"].display()
        val failureBound =
            gate["maximum_one_sided_95_percent_wilson_upper_failure_bound"]
                .display()
        val providerUpper = provider.uppercase(Locale.ROOT)
        markdown.appendLine("## Reproduce")
        markdown.appendLine()
        markdown.appendLine("Credential-free plan:")
        markdown.appendLine()
        markdown.appendLine("```bash")
        markdown.appendLine("LIVE_AI_PROVIDER=$provider \\")
        markdown.appendLine("LIVE_AI_EVAL_REPETITIONS=$repetitions \\")
        markdown.appendLine("LIVE_AI_EVAL_MAX_CALLS=$plannedCalls \\")
        markdown.appendLine("LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS=$interval \\")
        markdown.appendLine("LIVE_AI_EVAL_MAX_FAILURE_UPPER_BOUND=$failureBound \\")
        markdown.appendLine("${providerUpper}_LIVE_MODEL='$model' \\")
        markdown.appendLine("./gradlew --no-daemon liveAiEval --args='--plan'")
        markdown.appendLine("```")
        markdown.appendLine()
        markdown.appendLine("Authorized paid run (supply the credential only through the environment):")
        markdown.appendLine()
        markdown.appendLine("```bash")
        markdown.appendLine("LIVE_AI_PROVIDER=$provider \\")
        markdown.appendLine("LIVE_AI_EVAL_REPETITIONS=$repetitions \\")
        markdown.appendLine("LIVE_AI_EVAL_MAX_CALLS=$plannedCalls \\")
        markdown.appendLine("LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS=$interval \\")
        if (failureBound != EM_DASH) {
            markdown.appendLine("LIVE_AI_EVAL_MAX_FAILURE_UPPER_BOUND=$failureBound \\")
        }
        markdown.appendLine("RUN_LIVE_AI_TESTS=true \\")
        markdown.appendLine("${providerUpper}_LIVE_SYNTHETIC_RETENTION_AND_DATA_USE_APPROVED=true \\")
        markdown.appendLine("LIVE_AI_AUTOMATIC_TELEMETRY_DISABLED_CONFIRMED=true \\")
        markdown.appendLine("LIVE_AI_APPLICATION_REQUEST_INSPECTION_CONFIRMED=true \\")
        markdown.appendLine("${providerUpper}_LIVE_MODEL='$model' \\")
        markdown.appendLine(
            "${providerUpper}_API_KEY=\"\$(<REPLACE_WITH_APPROVED_SECRET_FILE)\" \\",
        )
        markdown.appendLine("./gradlew --no-daemon liveAiEval")
        markdown.appendLine("```")
        markdown.appendLine()
        markdown.appendLine(
            "`REPLACE_WITH_APPROVED_SECRET_FILE` is a placeholder; use an approved " +
                "local secret source without printing or committing the credential.",
        )
        markdown.appendLine()
    }

    private fun appendScalarSection(
        markdown: StringBuilder,
        title: String,
        value: Map<String, Any?>,
        excluded: Set<String> = emptySet(),
    ) {
        val flattened = linkedMapOf<String, Any?>()
        value.forEach { (key, fieldValue) ->
            if (key !in excluded) {
                flatten(key, fieldValue, flattened)
            }
        }
        appendKeyValueTable(markdown, title, flattened)
    }

    private fun appendKeyValueTable(
        markdown: StringBuilder,
        title: String,
        values: Map<String, Any?>,
    ) {
        markdown.appendLine("## $title")
        markdown.appendLine()
        markdown.appendLine("| Metric | Value |")
        markdown.appendLine("|---|---|")
        values.forEach { (key, value) ->
            markdown.appendLine(row(key, value))
        }
        markdown.appendLine()
    }

    private fun flatten(
        prefix: String,
        value: Any?,
        destination: MutableMap<String, Any?>,
    ) {
        when (value) {
            is Map<*, *> ->
                value.forEach { (key, nested) ->
                    require(key is String) { "Sanitized report keys must be strings" }
                    flatten("$prefix.$key", nested, destination)
                }

            is List<*> -> {
                if (value.none { element -> element is Map<*, *> || element is List<*> }) {
                    destination[prefix] = value.joinToString(", ") { element -> element.display() }
                }
            }

            else -> destination[prefix] = value
        }
    }

    private fun List<Map<String, Any?>>.requireContiguousIndices(field: String): List<Int> {
        val indices = map { record -> record.int(field) }
        require(indices == (1..size).toList()) {
            "$field values must be unique, contiguous, and one-based"
        }
        return indices
    }

    private fun List<Map<String, Any?>>.indexBy(
        field: String,
        allowedIndices: List<Int>,
    ): Map<Int, Map<String, Any?>> {
        val indexed = associateBy { record -> record.int(field) }
        require(indexed.size == size) { "$field values must be unique" }
        require(indexed.keys.all { index -> index in allowedIndices }) {
            "$field contains an orphan or out-of-range record"
        }
        return indexed
    }

    private fun List<Map<String, Any?>>.sumNullableLong(field: String): Any {
        val values = mapNotNull { record -> record.longOrNull(field) }
        return if (values.isEmpty()) EM_DASH else values.sum()
    }

    private fun List<Map<String, Any?>>.maxNullableLong(field: String): Any =
        mapNotNull { record -> record.longOrNull(field) }.maxOrNull() ?: EM_DASH

    private fun List<Map<String, Any?>>.nearestRank95(field: String): Any {
        val values = mapNotNull { record -> record.longOrNull(field) }.sorted()
        if (values.isEmpty()) {
            return EM_DASH
        }
        val index = ceil(values.size * 0.95).toInt().coerceAtLeast(1) - 1
        return values[index]
    }

    private fun placeholderCounts(event: Map<String, Any?>?): Any =
        if (event == null) {
            EM_DASH
        } else {
            listOf(
                event["name_placeholder_count"],
                event["job_placeholder_count"],
                event["hobby_placeholder_count"],
            ).joinToString("/")
        }

    private fun row(vararg values: Any?): String =
        values.joinToString(prefix = "| ", postfix = " |", separator = " | ") { value ->
            value.display()
        }

    private fun Any?.display(): String =
        when (this) {
            null -> EM_DASH
            is Double ->
                if (isFinite()) {
                    String.format(Locale.ROOT, "%.9f", this).trimEnd('0').trimEnd('.')
                } else {
                    EM_DASH
                }

            else ->
                toString()
                    .replace("\r", " ")
                    .replace("\n", " ")
                    .replace("|", "\\|")
                    .ifBlank { EM_DASH }
        }

    private fun Any?.toCompactMap(): Any =
        when (this) {
            is Map<*, *> ->
                entries.joinToString("; ") { (key, value) ->
                    "${key.display()}=${value.display()}"
                }.ifBlank { EM_DASH }

            else -> EM_DASH
        }

    private fun Any?.toCompactList(): Any =
        when (this) {
            is List<*> -> joinToString(", ") { value -> value.display() }.ifBlank { EM_DASH }
            else -> EM_DASH
        }

    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.requiredMap(key: String): Map<String, Any?> =
        this[key] as? Map<String, Any?>
            ?: throw IllegalArgumentException("$key must be a sanitized object")

    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.requiredMapList(key: String): List<Map<String, Any?>> =
        this[key] as? List<Map<String, Any?>>
            ?: throw IllegalArgumentException("$key must be a sanitized object list")

    @Suppress("UNCHECKED_CAST")
    private fun Map<*, *>.optionalMapList(key: String): List<Map<String, Any?>> =
        (this[key] as? List<Map<String, Any?>>).orEmpty()

    private fun Map<*, *>.int(key: String): Int =
        (this[key] as? Number)?.toInt()
            ?: throw IllegalArgumentException("$key must be an integer")

    private fun Map<*, *>.longOrNull(key: String): Long? =
        (this[key] as? Number)?.toLong()

    private fun Map<*, *>.boolean(key: String): Boolean? = this[key] as? Boolean

    private const val EM_DASH = "—"
    private const val NANOS_PER_MILLISECOND = 1_000_000L
    private const val OPENAI_INPUT_USD_PER_MILLION = 1.0
    private const val OPENAI_OUTPUT_USD_PER_MILLION = 6.0
    private val EXECUTION_EXCLUSIONS =
        setOf(
            "provider_request_evidence",
            "provider_response_evidence",
            "application_generation_diagnostics",
        )
}

internal data class LiveBioEvalEvidencePaths(
    val json: LiveEvidencePaths,
    val markdown: LiveEvidencePaths,
)

internal fun writeLiveBioEvalEvidenceCopies(
    objectMapper: JsonMapper,
    report: Map<String, Any>,
    scratchJsonPath: Path,
    durableReportDirectory: Path,
    codeRevision: String,
    provider: String,
    exactModelId: String,
    startedAt: Instant,
): LiveBioEvalEvidencePaths {
    val jsonPaths =
        writeSanitizedEvidenceCopies(
            objectMapper = objectMapper,
            report = report,
            scratchPath = scratchJsonPath,
            durableReportDirectory = durableReportDirectory,
            codeRevision = codeRevision,
            provider = provider,
            exactModelId = exactModelId,
            startedAt = startedAt,
        )
    val markdownPaths =
        LiveEvidencePaths(
            scratchPath = scratchJsonPath.resolveSibling("report.md"),
            durablePath =
                jsonPaths.durablePath.resolveSibling(
                    jsonPaths.durablePath.fileName.toString()
                        .removeSuffix(".json") + ".md",
                ),
        )
    val markdown = LiveBioEvalMarkdownReport.render(report)
    writeAtomically(markdownPaths.durablePath, markdown)
    writeAtomically(markdownPaths.scratchPath, markdown)
    return LiveBioEvalEvidencePaths(json = jsonPaths, markdown = markdownPaths)
}
