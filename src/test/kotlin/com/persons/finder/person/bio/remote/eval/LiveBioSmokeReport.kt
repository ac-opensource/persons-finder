package com.persons.finder.person.bio.remote.eval

import com.persons.finder.person.bio.BioGenerationFailure
import com.persons.finder.person.bio.BioGenerationResult
import com.persons.finder.person.bio.GeneratedBio
import com.persons.finder.person.bio.modelAuthoredCodePointCount
import com.persons.finder.person.bio.sentenceCount
import com.persons.finder.person.bio.remote.RemoteBioGenerationDiagnosticEvent
import com.persons.finder.person.bio.remote.RemoteBioGenerationDiagnosticSink
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import tools.jackson.databind.json.JsonMapper

internal data class LiveEvidencePaths(
    val scratchPath: Path,
    val durablePath: Path,
)

private data class ValidBioStructuralEvidence(
    val modelAuthoredCodePoints: Int,
    val sentenceCount: Int,
    var finalGroundedCodePoints: Int? = null,
)

internal class LiveBioSmokeCallController {
    private val mutableNormalizedFailures = mutableListOf<BioGenerationFailure>()

    var attemptCount: Int = 0
        private set
    var stopReason: String = "completed"
        private set
    var terminalProviderFailureCategory: String? = null
        private set
    val normalizedFailures: List<BioGenerationFailure>
        get() = mutableNormalizedFailures.toList()

    fun observeAttempt(
        result: BioGenerationResult,
        providerSendDelegated: Boolean,
        boundaryViolationCount: Int,
        terminalFailureCategory: String?,
    ): Boolean {
        check(stopReason == "completed") {
            "A stopped live smoke execution cannot observe another attempt"
        }
        attemptCount++
        if (boundaryViolationCount > 0) {
            stopReason = "security_boundary"
            return false
        }
        if (!providerSendDelegated) {
            (result as? BioGenerationResult.Failure)?.let {
                mutableNormalizedFailures += it.reason
            }
            stopReason = "pre_transport_failure"
            return false
        }
        (result as? BioGenerationResult.Failure)?.let {
            mutableNormalizedFailures += it.reason
        }
        if (terminalFailureCategory != null) {
            terminalProviderFailureCategory = terminalFailureCategory
            stopReason = "terminal_provider_failure"
            return false
        }
        return true
    }

    fun finish() {
        if (stopReason == "completed" && mutableNormalizedFailures.isNotEmpty()) {
            stopReason = "ordinary_results_failed"
        }
    }

    fun markHarnessError() {
        stopReason = "harness_error"
    }
}

internal class LiveRemoteBioDiagnosticAccumulator : RemoteBioGenerationDiagnosticSink {
    private val events = mutableListOf<RemoteBioGenerationDiagnosticEvent>()

    override fun record(event: RemoteBioGenerationDiagnosticEvent) {
        events += event
    }

    fun summary(): Map<String, Any> {
        val outputJsonBytes = events.mapNotNull { it.outputJsonUtf8Bytes }
        val outputJsonCodePoints = events.mapNotNull { it.outputJsonCodePoints }
        val bioTemplateCodePoints = events.mapNotNull { it.bioTemplateCodePoints }
        val modelAuthoredCodePoints = events.mapNotNull { it.modelAuthoredCodePoints }
        val sentenceCounts = events.mapNotNull { it.sentenceCount }
        return linkedMapOf(
            "diagnostic_count" to events.size,
            "diagnostic_sequence" to
                events.map { event -> event.diagnostic.wireValue },
            "diagnostic_counts" to
                events
                    .groupingBy { event -> event.diagnostic.wireValue }
                    .eachCount()
                    .toSortedMap(),
            "events" to
                events.mapIndexed { index, event ->
                    linkedMapOf(
                        "invocation_index" to index + 1,
                        "diagnostic" to event.diagnostic.wireValue,
                        "output_json_utf8_bytes" to event.outputJsonUtf8Bytes,
                        "output_json_code_points" to event.outputJsonCodePoints,
                        "bio_template_well_formed_unicode" to
                            event.bioTemplateWellFormedUnicode,
                        "bio_template_code_points" to event.bioTemplateCodePoints,
                        "model_authored_code_points" to event.modelAuthoredCodePoints,
                        "name_placeholder_count" to event.namePlaceholderCount,
                        "job_placeholder_count" to event.jobPlaceholderCount,
                        "hobby_placeholder_count" to event.hobbyPlaceholderCount,
                        "sentence_count" to event.sentenceCount,
                        "printable_ascii" to event.printableAscii,
                    )
                },
            "output_json_size_reported_count" to outputJsonBytes.size,
            "maximum_output_json_utf8_bytes" to (outputJsonBytes.maxOrNull() ?: 0),
            "maximum_output_json_code_points" to
                (outputJsonCodePoints.maxOrNull() ?: 0),
            "bio_template_size_reported_count" to bioTemplateCodePoints.size,
            "maximum_bio_template_code_points" to
                (bioTemplateCodePoints.maxOrNull() ?: 0),
            "model_authored_size_reported_count" to modelAuthoredCodePoints.size,
            "maximum_model_authored_code_points" to
                (modelAuthoredCodePoints.maxOrNull() ?: 0),
            "sentence_count_reported_count" to sentenceCounts.size,
            "maximum_sentence_count" to (sentenceCounts.maxOrNull() ?: 0),
        )
    }
}

internal class LiveBioSmokeRecorder(
    private val provider: String,
    private val exactModelId: String,
    private val codeRevision: String,
    private val fixtureId: String,
    private val fixtureSha256: String,
    private val promptSha256: String,
    private val outputSchemaSha256: String,
    private val maxOutputTokens: Int,
    private val startedAt: Instant = Instant.now(),
) {
    private val resultCounts = linkedMapOf<BioEvalOutcome, Int>()
    private val resultSequence = mutableListOf<BioEvalOutcome>()
    private val invocationLatencyMillis = mutableListOf<Long>()
    private val invocationCaseIds = mutableListOf<String>()
    private val validBioStructuralEvidence = mutableListOf<ValidBioStructuralEvidence>()
    private val validProseFingerprints = mutableSetOf<String>()
    private var invocationCount = 0
    private var harnessErrorCount = 0

    fun preflightEvidenceDestinations(
        reportDirectory: Path =
            Path.of(
                System.getProperty(
                    "liveAiSmoke.reportDir",
                    "build/reports/live-ai-smoke",
                ),
            ),
        durableReportDirectory: Path =
            Path.of(
                System.getProperty(
                    "liveAiSmoke.durableReportDir",
                    ".agents/evidence/live-ai-smoke",
                ),
            ),
    ) {
        requireEvidenceDirectoriesWritable(
            reportDirectory,
            durableReportDirectory.resolve(codeRevision),
        )
    }

    fun recordInvocationStart(caseId: String = "unspecified") {
        require(caseId.matches(Regex("[a-z0-9_-]{1,64}"))) {
            "Live smoke case ID must be a safe stable identifier"
        }
        invocationCount++
        invocationCaseIds += caseId
    }

    fun record(
        result: BioGenerationResult,
        elapsedNanos: Long = 0L,
    ) {
        check(resultCounts.values.sum() < invocationCount) {
            "A provider result cannot be recorded without a matching invocation"
        }
        recordInvocationLatency(elapsedNanos)
        val outcome =
            when (result) {
                is BioGenerationResult.Template -> {
                    validProseFingerprints += BioEvalHash.sha256(result.value.value)
                    validBioStructuralEvidence +=
                        ValidBioStructuralEvidence(
                            modelAuthoredCodePoints =
                                result.value.modelAuthoredCodePointCount(),
                            sentenceCount = result.value.sentenceCount(),
                        )
                    BioEvalOutcome.VALID_PROSE
                }

                is BioGenerationResult.Failure -> result.reason.toEvalOutcome()
            }
        resultCounts[outcome] = resultCounts.getOrDefault(outcome, 0) + 1
        resultSequence += outcome
    }

    fun recordGroundedBio(bio: GeneratedBio) {
        val evidence =
            validBioStructuralEvidence.lastOrNull { it.finalGroundedCodePoints == null }
                ?: error("A grounded bio cannot be recorded without a valid template result")
        evidence.finalGroundedCodePoints =
            bio.value.codePointCount(0, bio.value.length)
    }

    fun recordInvocationFailure(elapsedNanos: Long) {
        recordInvocationLatency(elapsedNanos)
        recordHarnessError()
    }

    fun recordHarnessError() {
        harnessErrorCount++
    }

    fun toSanitizedMap(
        providerRequestEvidence: Map<String, Any>,
        providerResponseEvidence: Map<String, Any>,
        applicationDiagnostics: Map<String, Any>,
        delegatedHttpSendAttempts: Int,
        boundaryViolationCounts: Map<String, Int>,
        boundaryAssertionsCompleted: Boolean,
        stopReason: String = "completed",
        terminalProviderFailureCategory: String? = null,
        passed: Boolean,
        completedAt: Instant = Instant.now(),
    ): Map<String, Any?> {
        val recordedResults = resultCounts.values.sum()
        val applicationDiagnosticCount =
            applicationDiagnostics["diagnostic_count"] as? Int ?: 0
        val usageReportedResponseCount =
            (providerResponseEvidence["usage_reported_response_count"] as? Number)
                ?.toInt() ?: 0
        val providerResponseCount =
            (providerResponseEvidence["response_count"] as? Number)?.toInt() ?: 0
        val providerAttemptCount =
            (providerResponseEvidence["attempt_count"] as? Number)?.toInt() ?: 0
        val providerRequestCount =
            (providerRequestEvidence["request_count"] as? Number)?.toInt() ?: 0
        val requestConfigurationMatched =
            providerRequestEvidence["all_requests_match_expected_configuration"] == true
        val evidenceCompleteForAttemptedCalls =
            providerRequestCount == delegatedHttpSendAttempts &&
                providerAttemptCount == delegatedHttpSendAttempts &&
                (providerRequestCount == 0 || requestConfigurationMatched)
        val allPlannedCallsCompleted =
            invocationCount == PLANNED_CALLS &&
                recordedResults == invocationCount &&
                invocationLatencyMillis.size == invocationCount &&
                applicationDiagnosticCount == invocationCount &&
                delegatedHttpSendAttempts == invocationCount &&
                evidenceCompleteForAttemptedCalls
        val applicationDiagnosticSequence =
            applicationDiagnostics["diagnostic_sequence"] as? List<*>
                ?: emptyList<Any>()
        val effectivePassed =
            passed &&
                allPlannedCallsCompleted &&
                evidenceCompleteForAttemptedCalls &&
                boundaryAssertionsCompleted &&
                boundaryViolationCounts.values.sum() == 0 &&
                harnessErrorCount == 0
        return linkedMapOf(
            "report_schema_version" to 2,
            "evidence_kind" to "metered_live_provider_smoke",
            "data_policy" to
                "metadata_and_usage_only_no_prompt_request_response_credential_or_profile_content",
            "started_at" to startedAt.toString(),
            "completed_at" to completedAt.toString(),
            "provenance" to
                linkedMapOf(
                    "provider" to provider,
                    "exact_model_id" to exactModelId,
                    "code_revision" to codeRevision,
                    "fixture_id" to fixtureId,
                    "fixture_sha256" to fixtureSha256,
                    "planned_calls" to PLANNED_CALLS,
                    "prompt_sha256" to promptSha256,
                    "output_schema_sha256" to outputSchemaSha256,
                    "max_output_tokens" to maxOutputTokens,
                ),
            "execution" to
                linkedMapOf(
                    "model_provider_client_invocations" to invocationCount,
                    "stop_reason" to stopReason,
                    "terminal_provider_failure_category" to
                        terminalProviderFailureCategory,
                    "provider_result_count" to recordedResults,
                    "application_generation_diagnostics" to applicationDiagnostics,
                    "invocations_without_application_diagnostic" to
                        (invocationCount - applicationDiagnosticCount).coerceAtLeast(0),
                    "delegated_http_send_attempts" to delegatedHttpSendAttempts,
                    "not_attempted_calls" to
                        (PLANNED_CALLS - invocationCount).coerceAtLeast(0),
                    "invocations_without_result" to
                        (invocationCount - recordedResults).coerceAtLeast(0),
                    "retries" to 0,
                    "top_up_calls" to 0,
                    "normalized_result_counts" to
                        resultCounts.mapKeys { entry -> entry.key.wireValue }.toSortedMap(),
                    "normalized_result_sequence" to resultSequence.map(BioEvalOutcome::wireValue),
                    "invocations" to
                        (0 until invocationCount).map { index ->
                            linkedMapOf(
                                "invocation_index" to index + 1,
                                "case_id" to invocationCaseIds.getOrNull(index),
                                "normalized_result" to
                                    resultSequence.getOrNull(index)?.wireValue,
                                "model_provider_client_latency_millis" to
                                    invocationLatencyMillis.getOrNull(index),
                                "application_generation_diagnostic" to
                                    applicationDiagnosticSequence.getOrNull(index),
                                "provider_request_index" to
                                    (index + 1).takeIf { index < providerRequestCount },
                                "provider_attempt_index" to
                                    (index + 1).takeIf { index < providerAttemptCount },
                            )
                        },
                    "model_provider_client_latency_millis" to invocationLatencyMillis.toList(),
                    "total_model_provider_client_latency_millis" to
                        invocationLatencyMillis.sum(),
                    "minimum_model_provider_client_latency_millis" to
                        (invocationLatencyMillis.minOrNull() ?: 0L),
                    "maximum_model_provider_client_latency_millis" to
                        (invocationLatencyMillis.maxOrNull() ?: 0L),
                    "invocations_without_latency" to
                        (invocationCount - invocationLatencyMillis.size).coerceAtLeast(0),
                    "distinct_valid_prose_count" to validProseFingerprints.size,
                    "valid_bio_structural_evidence" to
                        validBioStructuralEvidence.mapIndexed { index, evidence ->
                            linkedMapOf(
                                "valid_result_index" to index + 1,
                                "model_authored_code_points" to
                                    evidence.modelAuthoredCodePoints,
                                "sentence_count" to evidence.sentenceCount,
                                "final_grounded_code_points" to
                                    evidence.finalGroundedCodePoints,
                            )
                        },
                    "maximum_model_authored_code_points" to
                        (
                            validBioStructuralEvidence.maxOfOrNull {
                                it.modelAuthoredCodePoints
                            } ?: 0
                        ),
                    "maximum_sentence_count" to
                        (validBioStructuralEvidence.maxOfOrNull { it.sentenceCount } ?: 0),
                    "maximum_final_grounded_code_points" to
                        (
                            validBioStructuralEvidence
                                .mapNotNull { it.finalGroundedCodePoints }
                                .maxOrNull() ?: 0
                        ),
                    "valid_results_without_grounded_measurement" to
                        validBioStructuralEvidence.count {
                            it.finalGroundedCodePoints == null
                        },
                    "harness_error_count" to harnessErrorCount,
                    "boundary_assertions_completed" to boundaryAssertionsCompleted,
                    "boundary_violation_counts" to boundaryViolationCounts.toSortedMap(),
                    "provider_request_evidence" to providerRequestEvidence,
                    "provider_response_evidence" to providerResponseEvidence,
                ),
            "billing" to
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
            "gate" to
                linkedMapOf(
                    "passed" to effectivePassed,
                    "all_planned_calls_completed" to allPlannedCallsCompleted,
                    "evidence_complete_for_attempted_calls" to
                        evidenceCompleteForAttemptedCalls,
                    "zero_boundary_violations" to
                        (boundaryAssertionsCompleted && boundaryViolationCounts.values.sum() == 0),
                    "zero_harness_errors" to (harnessErrorCount == 0),
                ),
        )
    }

    fun write(
        objectMapper: JsonMapper,
        providerRequestEvidence: Map<String, Any>,
        providerResponseEvidence: Map<String, Any>,
        applicationDiagnostics: Map<String, Any>,
        delegatedHttpSendAttempts: Int,
        boundaryViolationCounts: Map<String, Int>,
        boundaryAssertionsCompleted: Boolean,
        stopReason: String = "completed",
        terminalProviderFailureCategory: String? = null,
        passed: Boolean,
        reportDirectory: Path =
            Path.of(
                System.getProperty(
                    "liveAiSmoke.reportDir",
                    "build/reports/live-ai-smoke",
                ),
            ),
        durableReportDirectory: Path =
            Path.of(
                System.getProperty(
                    "liveAiSmoke.durableReportDir",
                    ".agents/evidence/live-ai-smoke",
                ),
            ),
    ): Path {
        val report =
            toSanitizedMap(
                providerRequestEvidence = providerRequestEvidence,
                providerResponseEvidence = providerResponseEvidence,
                applicationDiagnostics = applicationDiagnostics,
                delegatedHttpSendAttempts = delegatedHttpSendAttempts,
                boundaryViolationCounts = boundaryViolationCounts,
                boundaryAssertionsCompleted = boundaryAssertionsCompleted,
                stopReason = stopReason,
                terminalProviderFailureCategory = terminalProviderFailureCategory,
                passed = passed,
            )
        return writeSanitizedEvidenceCopies(
            objectMapper = objectMapper,
            report = report,
            scratchPath = reportDirectory.resolve("report.json"),
            durableReportDirectory = durableReportDirectory,
            codeRevision = codeRevision,
            provider = provider,
            exactModelId = exactModelId,
            startedAt = startedAt,
        ).scratchPath
    }

    private fun recordInvocationLatency(elapsedNanos: Long) {
        check(invocationLatencyMillis.size < invocationCount) {
            "Invocation latency cannot be recorded without a matching invocation"
        }
        invocationLatencyMillis += elapsedNanos.coerceAtLeast(0L) / NANOS_PER_MILLISECOND
    }

    private fun BioGenerationFailure.toEvalOutcome(): BioEvalOutcome =
        when (this) {
            BioGenerationFailure.TIMEOUT -> BioEvalOutcome.TIMEOUT
            BioGenerationFailure.RATE_LIMITED -> BioEvalOutcome.RATE_LIMITED
            BioGenerationFailure.UNAVAILABLE -> BioEvalOutcome.UNAVAILABLE
            BioGenerationFailure.INVALID_OUTPUT -> BioEvalOutcome.INVALID_OUTPUT
            BioGenerationFailure.POLICY_REJECTED -> BioEvalOutcome.POLICY_REJECTED
        }

    private companion object {
        const val PLANNED_CALLS = 3
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

internal fun writeSanitizedEvidenceCopies(
    objectMapper: JsonMapper,
    report: Map<String, *>,
    scratchPath: Path,
    durableReportDirectory: Path,
    codeRevision: String,
    provider: String,
    exactModelId: String,
    startedAt: Instant,
): LiveEvidencePaths {
    require(codeRevision.matches(Regex("[a-f0-9]{40,64}"))) {
        "Evidence code revision must be a lowercase Git object ID"
    }
    require(provider.matches(Regex("[a-z0-9_-]+"))) {
        "Evidence provider must be filename-safe"
    }
    val runFileName =
        listOf(
            startedAt.toString().replace(':', '-'),
            provider,
            BioEvalHash.sha256(exactModelId).take(12),
            "report.json",
        ).joinToString("-")
    val durablePath =
        durableReportDirectory
            .resolve(codeRevision)
            .resolve(runFileName)
    val json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n"
    writeAtomically(durablePath, json)
    writeAtomically(scratchPath, json)
    return LiveEvidencePaths(
        scratchPath = scratchPath,
        durablePath = durablePath,
    )
}

private fun writeAtomically(
    destination: Path,
    content: String,
) {
    val directory = requireNotNull(destination.parent)
    Files.createDirectories(directory)
    val temporary = Files.createTempFile(directory, ".${destination.fileName}.", ".tmp")
    try {
        Files.writeString(temporary, content, StandardCharsets.UTF_8)
        try {
            Files.move(
                temporary,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary,
                destination,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}

internal fun requireEvidenceDirectoriesWritable(vararg directories: Path) {
    directories.forEach { directory ->
        Files.createDirectories(directory)
        val probe = Files.createTempFile(directory, ".write-check-", ".tmp")
        Files.delete(probe)
    }
}
