package com.persons.finder.person.bio.remote.eval

import java.time.Instant

internal enum class BioEvalOutcome(val wireValue: String) {
    VALID_PROSE("valid_prose"),
    TIMEOUT("timeout"),
    RATE_LIMITED("rate_limited"),
    UNAVAILABLE("unavailable"),
    INVALID_OUTPUT("invalid_output"),
    POLICY_REJECTED("policy_rejected"),
    HARNESS_ERROR("harness_error"),
}

internal data class BioEvalMetrics(
    val attempts: Int,
    val validProseCount: Int,
    val distinctValidProseCount: Int,
    val deterministicCatalogMatchCount: Int,
    val failureCount: Int,
    val observedFailureRate: Double,
    val oneSided95WilsonUpperFailureBound: Double,
    val resultCounts: Map<BioEvalOutcome, Int>,
    val latency: BioEvalLatencySummary,
) {
    init {
        require(attempts > 0)
        require(validProseCount + failureCount == attempts)
        require(distinctValidProseCount in 0..validProseCount)
        require(deterministicCatalogMatchCount in 0..validProseCount)
        require(resultCounts.values.sum() == attempts)
    }

    internal fun toSanitizedMap(): Map<String, Any> =
        linkedMapOf(
            "attempts" to attempts,
            "valid_prose_count" to validProseCount,
            "distinct_valid_prose_count" to distinctValidProseCount,
            "deterministic_catalog_match_count" to deterministicCatalogMatchCount,
            "failure_count" to failureCount,
            "observed_failure_rate" to observedFailureRate,
            "one_sided_95_percent_wilson_upper_failure_bound" to
                oneSided95WilsonUpperFailureBound,
            "result_counts" to
                resultCounts.mapKeys { entry -> entry.key.wireValue },
            "latency_nanos" to
                linkedMapOf(
                    "p50" to latency.p50Nanos,
                    "p95" to latency.p95Nanos,
                    "max" to latency.maxNanos,
                ),
        )
}

internal data class BioEvalProvenance(
    val provider: String,
    val exactModelId: String,
    val codeRevision: String,
    val corpusId: String,
    val corpusSchemaVersion: Int,
    val corpusSha256: String,
    val promptSha256: String,
    val outputSchemaSha256: String,
    val caseOrderStrategy: String,
    val repetitions: Int,
    val plannedCalls: Int,
    val pacingStrategy: String,
    val minimumCallIntervalMillis: Long,
    val configuredMinimumCallStartSpanMillis: Long,
)

internal data class LiveBioEvalReport(
    val reportSchemaVersion: Int = 3,
    val startedAt: Instant,
    val completedAt: Instant,
    val provenance: BioEvalProvenance,
    val pacing: LiveBioEvalPacingSnapshot,
    val overall: BioEvalMetrics,
    val byCase: Map<String, BioEvalMetrics>,
    val bySlice: Map<String, BioEvalMetrics>,
) {
    /**
     * Deliberately aggregate-only. Case keys are validated corpus case IDs; this
     * contains no case request, provider request, provider response, exception
     * message, credential, or source profile value.
     */
    fun toSanitizedMap(): Map<String, Any> =
        linkedMapOf(
            "report_schema_version" to reportSchemaVersion,
            "data_policy" to "aggregate_only_no_request_or_response_content",
            "started_at" to startedAt.toString(),
            "completed_at" to completedAt.toString(),
            "provenance" to
                linkedMapOf(
                    "provider" to provenance.provider,
                    "exact_model_id" to provenance.exactModelId,
                    "code_revision" to provenance.codeRevision,
                    "corpus_id" to provenance.corpusId,
                    "corpus_schema_version" to provenance.corpusSchemaVersion,
                    "corpus_sha256" to provenance.corpusSha256,
                    "prompt_sha256" to provenance.promptSha256,
                    "output_schema_sha256" to provenance.outputSchemaSha256,
                    "case_order_strategy" to provenance.caseOrderStrategy,
                    "repetitions" to provenance.repetitions,
                    "planned_calls" to provenance.plannedCalls,
                    "pacing_strategy" to provenance.pacingStrategy,
                    "minimum_call_interval_millis" to
                        provenance.minimumCallIntervalMillis,
                    "configured_minimum_call_start_span_millis" to
                        provenance.configuredMinimumCallStartSpanMillis,
                ),
            "pacing" to
                linkedMapOf(
                    "wait_event_count" to pacing.waitEventCount,
                    "actual_wait_nanos" to pacing.actualWaitNanos,
                ),
            "overall" to overall.toSanitizedMap(),
            "by_case" to byCase.mapValues { entry -> entry.value.toSanitizedMap() },
            "by_slice" to bySlice.mapValues { entry -> entry.value.toSanitizedMap() },
        )
}
