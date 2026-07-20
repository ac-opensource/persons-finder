package com.persons.finder.person.bio.remote.eval

import java.time.Instant

internal enum class BioEvalOutcome(val wireValue: String) {
    VALID_PROSE("valid_prose"),
    TIMEOUT("timeout"),
    RATE_LIMITED("rate_limited"),
    UNAVAILABLE("unavailable"),
    INVALID_OUTPUT("invalid_output"),
    POLICY_REJECTED("policy_rejected"),
    CANCELLED("cancelled"),
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
    val round: Int,
    val slot: Int,
    val caseId: String,
    val sliceIds: Set<String>,
    val outcome: BioEvalOutcome,
    val outputEquivalenceId: String?,
    val modelAuthoredCodePoints: Int?,
    val sentenceCount: Int?,
    val deterministicCatalogMatch: Boolean?,
    val finalGroundedCodePoints: Int?,
) {
    init {
        require(attemptIndex > 0)
        require(round > 0)
        require(slot > 0)
        require(caseId.matches(Regex("[a-z0-9_-]{1,64}")))
        require(sliceIds.isNotEmpty())
        require(
            sliceIds.all { sliceId ->
                sliceId.matches(Regex("[a-z0-9_-]{1,64}"))
            },
        )
        outputEquivalenceId?.let { equivalenceId ->
            require(equivalenceId.matches(Regex("output-[0-9]{3,}")))
            require(equivalenceId.substringAfter("output-").toIntOrNull() != null)
            require(equivalenceId.substringAfter("output-").toInt() > 0)
        }
        val hasCompleteValidOutputEvidence =
            outputEquivalenceId != null &&
                modelAuthoredCodePoints != null &&
                sentenceCount != null &&
                deterministicCatalogMatch != null &&
                finalGroundedCodePoints != null
        require(
            (outcome == BioEvalOutcome.VALID_PROSE) ==
                hasCompleteValidOutputEvidence,
        )
        require(
            outcome == BioEvalOutcome.VALID_PROSE ||
                listOf(
                    outputEquivalenceId,
                    modelAuthoredCodePoints,
                    sentenceCount,
                    deterministicCatalogMatch,
                    finalGroundedCodePoints,
                ).all { value -> value == null },
        )
        modelAuthoredCodePoints?.let { codePoints ->
            require(codePoints > 0)
        }
        sentenceCount?.let { count ->
            require(count in 1..3)
        }
        finalGroundedCodePoints?.let { codePoints ->
            require(codePoints > 0)
        }
    }

    internal fun toSanitizedMap(): Map<String, Any?> =
        linkedMapOf(
            "attempt_index" to attemptIndex,
            "round" to round,
            "slot" to slot,
            "case_id" to caseId,
            "slice_ids" to sliceIds.sorted(),
            "normalized_result" to outcome.wireValue,
            "output_equivalence_id" to outputEquivalenceId,
            "model_authored_code_points" to modelAuthoredCodePoints,
            "sentence_count" to sentenceCount,
            "deterministic_catalog_match" to deterministicCatalogMatch,
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
    val reportSchemaVersion: Int = 6,
    val startedAt: Instant,
    val completedAt: Instant,
    val provenance: BioEvalProvenance,
    val pacing: LiveBioEvalPacingSnapshot,
    val attemptEvidence: List<BioEvalAttemptEvidence>,
    val overall: BioEvalMetrics,
    val byCase: Map<String, BioEvalMetrics>,
    val bySlice: Map<String, BioEvalMetrics>,
    val byRound: Map<Int, BioEvalMetrics>,
) {
    init {
        require(reportSchemaVersion == 6)
        require(!completedAt.isBefore(startedAt))
        require(provenance.repetitions > 0)
        require(provenance.plannedCalls > 0)
        require(provenance.plannedCalls % provenance.repetitions == 0)
        require(provenance.maxOutputTokens > 0)
        require(provenance.modelAuthoredCodePointLimit > 0)
        require(provenance.maximumGroundingSourceCodePoints > 0)
        require(
            provenance.finalGroundedCodePointLimit ==
                provenance.modelAuthoredCodePointLimit +
                provenance.maximumGroundingSourceCodePoints,
        )
        require(attemptEvidence.isNotEmpty())
        require(attemptEvidence.size <= provenance.plannedCalls)

        val casesPerRound = provenance.plannedCalls / provenance.repetitions
        require(
            attemptEvidence.map(BioEvalAttemptEvidence::attemptIndex) ==
                (1..attemptEvidence.size).toList(),
        )
        attemptEvidence.forEachIndexed { index, evidence ->
            require(evidence.round == (index / casesPerRound) + 1)
            require(evidence.slot == (index % casesPerRound) + 1)
            require(evidence.round <= provenance.repetitions)
            require(
                evidence.modelAuthoredCodePoints == null ||
                    evidence.modelAuthoredCodePoints <=
                    provenance.modelAuthoredCodePointLimit,
            )
            require(
                evidence.finalGroundedCodePoints == null ||
                    evidence.finalGroundedCodePoints <=
                    provenance.finalGroundedCodePointLimit,
            )
        }

        val attemptsByRound = attemptEvidence.groupBy(BioEvalAttemptEvidence::round)
        attemptsByRound.values.forEach { roundAttempts ->
            require(roundAttempts.map(BioEvalAttemptEvidence::caseId).distinct().size ==
                roundAttempts.size)
        }
        val firstRoundCaseIds =
            attemptsByRound.getValue(1).map(BioEvalAttemptEvidence::caseId)
        if (firstRoundCaseIds.size == casesPerRound) {
            attemptEvidence.forEach { evidence ->
                val expectedCaseIndex =
                    (evidence.slot - 1 + evidence.round - 1) % casesPerRound
                require(evidence.caseId == firstRoundCaseIds[expectedCaseIndex])
            }
        } else {
            require(attemptEvidence.all { evidence -> evidence.round == 1 })
        }

        val firstSeenEquivalenceIds = linkedSetOf<String>()
        attemptEvidence.mapNotNull(BioEvalAttemptEvidence::outputEquivalenceId)
            .forEach { equivalenceId ->
                if (firstSeenEquivalenceIds.add(equivalenceId)) {
                    require(
                        equivalenceId ==
                            "output-${firstSeenEquivalenceIds.size.toString().padStart(3, '0')}",
                    )
                }
            }
        attemptEvidence
            .filter { evidence -> evidence.outputEquivalenceId != null }
            .groupBy(BioEvalAttemptEvidence::outputEquivalenceId)
            .values
            .forEach { equivalentOutputs ->
                require(
                    equivalentOutputs
                        .map { evidence ->
                            listOf(
                                evidence.modelAuthoredCodePoints,
                                evidence.sentenceCount,
                                evidence.deterministicCatalogMatch,
                                evidence.finalGroundedCodePoints,
                            )
                        }
                        .distinct()
                        .size == 1,
                )
            }

        validateMetricsAgainstEvidence(overall, attemptEvidence)
        val evidenceByCase =
            attemptEvidence.groupBy(BioEvalAttemptEvidence::caseId).toSortedMap()
        require(byCase.keys == evidenceByCase.keys)
        byCase.forEach { (caseId, metrics) ->
            val caseEvidence = evidenceByCase.getValue(caseId)
            require(caseEvidence.map(BioEvalAttemptEvidence::sliceIds).distinct().size == 1)
            validateMetricsAgainstEvidence(metrics, caseEvidence)
        }
        val evidenceBySlice =
            attemptEvidence
                .flatMap { evidence ->
                    evidence.sliceIds.map { sliceId -> sliceId to evidence }
                }
                .groupBy(
                    keySelector = { entry -> entry.first },
                    valueTransform = { entry -> entry.second },
                )
                .toSortedMap()
        require(bySlice.keys == evidenceBySlice.keys)
        bySlice.forEach { (sliceId, metrics) ->
            validateMetricsAgainstEvidence(metrics, evidenceBySlice.getValue(sliceId))
        }
        require(byRound.keys == attemptsByRound.keys)
        require(byRound.keys.toList() == byRound.keys.sorted())
        byRound.forEach { (round, metrics) ->
            validateMetricsAgainstEvidence(metrics, attemptsByRound.getValue(round))
        }
    }

    /**
     * Deliberately content-free. Attempt evidence contains only positional
     * provenance, a validated corpus case ID, normalized outcome, a per-run
     * equality-class label, and structural measurements of validated output.
     * It contains no case request, provider request, provider response, prose,
     * prose hash, exception message, credential, or source profile value.
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
            "by_round" to
                byRound.mapKeys { entry -> entry.key.toString() }
                    .mapValues { entry -> entry.value.toSanitizedMap() },
        )

    private fun validateMetricsAgainstEvidence(
        metrics: BioEvalMetrics,
        evidence: List<BioEvalAttemptEvidence>,
    ) {
        val expectedResultCounts =
            BioEvalOutcome.entries.associateWith { outcome ->
                evidence.count { attempt -> attempt.outcome == outcome }
            }
        val validEvidence =
            evidence.filter { attempt -> attempt.outcome == BioEvalOutcome.VALID_PROSE }
        val groundedSizes =
            validEvidence.mapNotNull(BioEvalAttemptEvidence::finalGroundedCodePoints)
        require(metrics.attempts == evidence.size)
        require(metrics.validProseCount == validEvidence.size)
        require(
            metrics.distinctValidProseCount ==
                validEvidence.mapNotNull(BioEvalAttemptEvidence::outputEquivalenceId)
                    .distinct()
                    .size,
        )
        require(
            metrics.deterministicCatalogMatchCount ==
                validEvidence.count { attempt ->
                    attempt.deterministicCatalogMatch == true
                },
        )
        require(metrics.finalGroundedSizeReportedCount == groundedSizes.size)
        require(metrics.maximumFinalGroundedCodePoints == (groundedSizes.maxOrNull() ?: 0))
        require(
            metrics.validResultsWithoutGroundedMeasurement ==
                validEvidence.size - groundedSizes.size,
        )
        require(metrics.failureCount == evidence.size - validEvidence.size)
        require(metrics.resultCounts == expectedResultCounts)
    }
}
