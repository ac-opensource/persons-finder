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
    val finalGroundedSizeReportedCount: Int,
    val maximumFinalGroundedCodePoints: Int,
    val validResultsWithoutGroundedMeasurement: Int,
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
        require(finalGroundedSizeReportedCount in 0..validProseCount)
        require(
            finalGroundedSizeReportedCount + validResultsWithoutGroundedMeasurement ==
                validProseCount,
        )
        require(maximumFinalGroundedCodePoints >= 0)
        require(
            (finalGroundedSizeReportedCount == 0) ==
                (maximumFinalGroundedCodePoints == 0),
        )
        require(resultCounts.values.sum() == attempts)
    }

    internal fun toSanitizedMap(): Map<String, Any> =
        linkedMapOf(
            "attempts" to attempts,
            "valid_prose_count" to validProseCount,
            "distinct_valid_prose_count" to distinctValidProseCount,
            "deterministic_catalog_match_count" to deterministicCatalogMatchCount,
            "final_grounded_size_reported_count" to finalGroundedSizeReportedCount,
            "maximum_final_grounded_code_points" to maximumFinalGroundedCodePoints,
            "valid_results_without_grounded_measurement" to
                validResultsWithoutGroundedMeasurement,
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

internal data class BioEvalAttemptEvidence(
    val attemptIndex: Int,
    val caseId: String,
    val outcome: BioEvalOutcome,
    val finalGroundedCodePoints: Int?,
) {
    init {
        require(attemptIndex > 0)
        require(caseId.matches(Regex("[a-z0-9_-]{1,64}")))
        require(
            (outcome == BioEvalOutcome.VALID_PROSE) ==
                (finalGroundedCodePoints != null),
        )
        finalGroundedCodePoints?.let { codePoints ->
            require(codePoints > 0)
        }
    }

    internal fun toSanitizedMap(): Map<String, Any?> =
        linkedMapOf(
            "attempt_index" to attemptIndex,
            "case_id" to caseId,
            "normalized_result" to outcome.wireValue,
            "final_grounded_code_points" to finalGroundedCodePoints,
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
    val maxOutputTokens: Int,
    val modelAuthoredCodePointLimit: Int,
    val maximumGroundingSourceCodePoints: Int,
    val finalGroundedCodePointLimit: Int,
    val groundingStrategy: String,
    val caseOrderStrategy: String,
    val repetitions: Int,
    val plannedCalls: Int,
    val pacingStrategy: String,
    val minimumCallIntervalMillis: Long,
    val configuredMinimumCallStartSpanMillis: Long,
)

internal data class LiveBioEvalReport(
    val reportSchemaVersion: Int = 5,
    val startedAt: Instant,
    val completedAt: Instant,
    val provenance: BioEvalProvenance,
    val pacing: LiveBioEvalPacingSnapshot,
    val attemptEvidence: List<BioEvalAttemptEvidence>,
    val overall: BioEvalMetrics,
    val byCase: Map<String, BioEvalMetrics>,
    val bySlice: Map<String, BioEvalMetrics>,
) {
    /**
     * Deliberately content-free. Attempt evidence contains only a sequence
     * number, validated corpus case ID, normalized outcome, and optional numeric
     * grounded length. It contains no case request, provider request, provider
     * response, exception message, credential, or source profile value.
     */
    fun toSanitizedMap(): Map<String, Any> =
        linkedMapOf(
            "report_schema_version" to reportSchemaVersion,
            "data_policy" to "sanitized_metrics_no_request_or_response_content",
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
                    "max_output_tokens" to provenance.maxOutputTokens,
                    "model_authored_code_point_limit" to
                        provenance.modelAuthoredCodePointLimit,
                    "maximum_grounding_source_code_points" to
                        provenance.maximumGroundingSourceCodePoints,
                    "final_grounded_code_point_limit" to
                        provenance.finalGroundedCodePointLimit,
                    "grounding_strategy" to provenance.groundingStrategy,
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
            "attempt_evidence" to
                attemptEvidence.map(BioEvalAttemptEvidence::toSanitizedMap),
            "overall" to overall.toSanitizedMap(),
            "by_case" to byCase.mapValues { entry -> entry.value.toSanitizedMap() },
            "by_slice" to bySlice.mapValues { entry -> entry.value.toSanitizedMap() },
        )
}
