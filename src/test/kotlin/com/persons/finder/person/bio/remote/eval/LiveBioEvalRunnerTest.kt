package com.persons.finder.person.bio.remote.eval

import com.persons.finder.person.bio.BioGenerationFailure
import com.persons.finder.person.bio.BioGenerationResult
import com.persons.finder.person.bio.BioGenerator
import com.persons.finder.person.bio.GeneratedBioTemplate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CancellationException

class LiveBioEvalRunnerTest {
    private val corpus = BioEvalCorpusLoader.load()

    @Test
    fun `runner repeats one call per case and reports aggregate success`() {
        var calls = 0
        var ticker = 0L
        val generator =
            BioGenerator { request ->
                val prose = validatedProse("variant-$calls", request.hobbyCount)
                calls++
                BioGenerationResult.Template(prose)
            }
        val runner =
            LiveBioEvalRunner(
                clock = fixedClock(),
                nanoTime = {
                    val current = ticker
                    ticker += 1_000_000
                    current
                },
            )

        val report =
            runner.run(
                corpus = corpus,
                configuration = configuration(repetitions = 2, maxCalls = 24),
                generator = generator,
            )

        assertEquals(24, calls)
        assertEquals(6, report.reportSchemaVersion)
        assertEquals(24, report.provenance.plannedCalls)
        assertEquals(1_024, report.provenance.maxOutputTokens)
        assertEquals(512, report.provenance.modelAuthoredCodePointLimit)
        assertEquals(760, report.provenance.maximumGroundingSourceCodePoints)
        assertEquals(1_272, report.provenance.finalGroundedCodePointLimit)
        assertEquals(15_000L, report.provenance.generationDeadlineMillis)
        assertEquals(
            "indexed_hobbies_case_matched_source_bound_v4",
            report.provenance.groundingStrategy,
        )
        assertEquals("minimum_attempt_start_interval_v1", report.provenance.pacingStrategy)
        assertEquals(0L, report.provenance.minimumCallIntervalMillis)
        assertEquals(0L, report.provenance.configuredMinimumCallStartSpanMillis)
        assertEquals(LiveBioEvalPacingSnapshot(0, 0L), report.pacing)
        assertEquals(24, report.overall.attempts)
        assertEquals(24, report.overall.validProseCount)
        assertEquals(24, report.overall.distinctValidProseCount)
        assertEquals(0, report.overall.deterministicCatalogMatchCount)
        assertEquals(24, report.overall.finalGroundedSizeReportedCount)
        assertEquals(
            report.byCase.getValue("case-012").maximumFinalGroundedCodePoints,
            report.overall.maximumFinalGroundedCodePoints,
        )
        assertTrue(report.overall.maximumFinalGroundedCodePoints > 760)
        assertTrue(report.overall.maximumFinalGroundedCodePoints <= 1_272)
        assertEquals(0, report.overall.validResultsWithoutGroundedMeasurement)
        assertEquals(0, report.overall.failureCount)
        assertEquals(0.0, report.overall.observedFailureRate)
        assertTrue(report.overall.oneSided95WilsonUpperFailureBound > 0.0)
        assertEquals(
            BioEvalLatencySummary(
                p50Nanos = 1_000_000,
                p95Nanos = 1_000_000,
                maxNanos = 1_000_000,
            ),
            report.overall.latency,
        )
        assertEquals(
            setOf(
                "both-other",
                "job-coverage",
                "maximum-hobbies",
                "multi-hobby",
                "multi-interest",
                "single-hobby",
                "single-interest",
            ),
            report.bySlice.keys,
        )
        assertEquals(
            corpus.cases.map(BioEvalCase::id).toSet(),
            report.byCase.keys,
        )
        assertTrue(report.byCase.values.all { metrics -> metrics.attempts == 2 })
        assertEquals(setOf(1, 2), report.byRound.keys)
        assertTrue(report.byRound.values.all { metrics -> metrics.attempts == 12 })
        assertEquals(
            (1..24).toList(),
            report.attemptEvidence.map(BioEvalAttemptEvidence::attemptIndex),
        )
        assertEquals(
            List(12) { 1 } + List(12) { 2 },
            report.attemptEvidence.map(BioEvalAttemptEvidence::round),
        )
        assertEquals(
            (1..12).toList() + (1..12).toList(),
            report.attemptEvidence.map(BioEvalAttemptEvidence::slot),
        )
        assertEquals(
            corpus.cases.map(BioEvalCase::id) +
                (corpus.cases.drop(1) + corpus.cases.take(1)).map(BioEvalCase::id),
            report.attemptEvidence.map(BioEvalAttemptEvidence::caseId),
        )
        assertTrue(
            report.attemptEvidence.all { evidence ->
                evidence.outcome == BioEvalOutcome.VALID_PROSE &&
                    evidence.outputEquivalenceId != null &&
                    evidence.modelAuthoredCodePoints != null &&
                    evidence.sentenceCount == 1 &&
                    evidence.deterministicCatalogMatch == false &&
                    evidence.finalGroundedCodePoints != null
            },
        )
    }

    @Test
    fun `runner starts immediately then spaces attempts without adding pacing to latency`() {
        val time = FakeMonotonicTime()
        val attemptStarts = mutableListOf<Long>()
        val generator =
            BioGenerator { request ->
                attemptStarts += time.nowNanos
                time.advance(Duration.ofSeconds(1))
                BioGenerationResult.Template(validatedProse("paced", request.hobbyCount))
            }
        val runner =
            LiveBioEvalRunner(
                clock = fixedClock(),
                nanoTime = time::nanoTime,
                sleeper = time::sleep,
            )

        val report =
            runner.run(
                corpus = corpus,
                configuration =
                    configuration(
                        repetitions = 1,
                        maxCalls = corpus.cases.size,
                        minimumCallInterval = Duration.ofSeconds(6),
                    ),
                generator = generator,
            )

        assertEquals(0L, attemptStarts.first())
        assertTrue(
            attemptStarts.zipWithNext().all { (previous, next) ->
                next - previous == Duration.ofSeconds(6).toNanos()
            },
        )
        assertEquals(
            List(corpus.cases.size - 1) { Duration.ofSeconds(5) },
            time.sleeps,
        )
        assertEquals(
            BioEvalLatencySummary(
                p50Nanos = Duration.ofSeconds(1).toNanos(),
                p95Nanos = Duration.ofSeconds(1).toNanos(),
                maxNanos = Duration.ofSeconds(1).toNanos(),
            ),
            report.overall.latency,
        )
        assertEquals(
            LiveBioEvalPacingSnapshot(
                waitEventCount = corpus.cases.size - 1,
                actualWaitNanos =
                    Duration.ofSeconds(5).toNanos() * (corpus.cases.size - 1),
            ),
            report.pacing,
        )
        assertEquals("minimum_attempt_start_interval_v1", report.provenance.pacingStrategy)
        assertEquals(6_000L, report.provenance.minimumCallIntervalMillis)
        assertEquals(
            6_000L * (corpus.cases.size - 1),
            report.provenance.configuredMinimumCallStartSpanMillis,
        )

        val sanitized = report.toSanitizedMap()
        assertEquals(6, sanitized["report_schema_version"])
        assertEquals(
            "sanitized_metrics_no_request_or_response_content",
            sanitized["data_policy"],
        )
        val sanitizedProvenance = sanitized.getValue("provenance") as Map<*, *>
        assertEquals(
            "minimum_attempt_start_interval_v1",
            sanitizedProvenance["pacing_strategy"],
        )
        assertEquals(6_000L, sanitizedProvenance["minimum_call_interval_millis"])
        assertEquals(1_024, sanitizedProvenance["max_output_tokens"])
        assertEquals(512, sanitizedProvenance["model_authored_code_point_limit"])
        assertEquals(760, sanitizedProvenance["maximum_grounding_source_code_points"])
        assertEquals(1_272, sanitizedProvenance["final_grounded_code_point_limit"])
        assertEquals(15_000L, sanitizedProvenance["generation_deadline_millis"])
        assertEquals(
            "indexed_hobbies_case_matched_source_bound_v4",
            sanitizedProvenance["grounding_strategy"],
        )
        assertEquals(
            6_000L * (corpus.cases.size - 1),
            sanitizedProvenance["configured_minimum_call_start_span_millis"],
        )
        val sanitizedPacing = sanitized.getValue("pacing") as Map<*, *>
        assertEquals(corpus.cases.size - 1, sanitizedPacing["wait_event_count"])
        assertEquals(
            Duration.ofSeconds(5).toNanos() * (corpus.cases.size - 1),
            sanitizedPacing["actual_wait_nanos"],
        )
        val sanitizedAttempts = sanitized.getValue("attempt_evidence") as List<*>
        assertEquals(corpus.cases.size, sanitizedAttempts.size)
        val firstAttempt = sanitizedAttempts.first() as Map<*, *>
        assertEquals(
            setOf(
                "attempt_index",
                "round",
                "slot",
                "case_id",
                "slice_ids",
                "normalized_result",
                "output_equivalence_id",
                "model_authored_code_points",
                "sentence_count",
                "deterministic_catalog_match",
                "final_grounded_code_points",
            ),
            firstAttempt.keys,
        )
        assertEquals(1, firstAttempt["attempt_index"])
        assertEquals(1, firstAttempt["round"])
        assertEquals(1, firstAttempt["slot"])
        assertEquals(corpus.cases.first().id, firstAttempt["case_id"])
        assertEquals(corpus.cases.first().slices.sorted(), firstAttempt["slice_ids"])
        assertEquals("valid_prose", firstAttempt["normalized_result"])
        assertEquals("output-001", firstAttempt["output_equivalence_id"])
        assertTrue((firstAttempt["model_authored_code_points"] as Int) > 0)
        assertEquals(1, firstAttempt["sentence_count"])
        assertEquals(false, firstAttempt["deterministic_catalog_match"])
        assertTrue((firstAttempt["final_grounded_code_points"] as Int) > 0)
        val sanitizedByRound = sanitized.getValue("by_round") as Map<*, *>
        assertEquals(setOf("1"), sanitizedByRound.keys)
    }

    @Test
    fun `runner assigns stable first-seen output equivalence ids without prose hashes`() {
        val first = validatedProse("equivalence-alpha")
        val second = validatedProse("equivalence-beta")
        val labels =
            listOf(
                "equivalence-alpha",
                "equivalence-alpha",
                "equivalence-beta",
                "equivalence-alpha",
                "equivalence-beta",
                "equivalence-beta",
            )
        var calls = 0
        val report =
            LiveBioEvalRunner(
                clock = fixedClock(),
                nanoTime = constantLatencyTicker(),
            ).run(
                corpus = corpus,
                configuration = configuration(repetitions = 1, maxCalls = 12),
                generator =
                    BioGenerator { request ->
                        val label = labels[calls++ % labels.size]
                        BioGenerationResult.Template(
                            validatedProse(label, request.hobbyCount),
                        )
                    },
            )

        assertEquals(4, report.overall.distinctValidProseCount)
        assertEquals(
            listOf(
                "output-001",
                "output-001",
                "output-002",
                "output-003",
                "output-002",
                "output-002",
                "output-001",
                "output-001",
                "output-002",
                "output-001",
                "output-002",
                "output-004",
            ),
            report.attemptEvidence.map(BioEvalAttemptEvidence::outputEquivalenceId),
        )
        val sanitized = report.toSanitizedMap().toString()
        assertFalse(sanitized.contains("equivalence-alpha"))
        assertFalse(sanitized.contains("equivalence-beta"))
        assertFalse(sanitized.contains(BioEvalHash.sha256(first.value)))
        assertFalse(sanitized.contains(BioEvalHash.sha256(second.value)))
    }

    @Test
    fun `runner aggregates each round independently`() {
        var calls = 0
        val report =
            LiveBioEvalRunner(
                clock = fixedClock(),
                nanoTime = constantLatencyTicker(),
            ).run(
                corpus = corpus,
                configuration = configuration(repetitions = 2, maxCalls = 24),
                generator =
                    BioGenerator { request ->
                        calls++
                        if (calls <= corpus.cases.size) {
                            BioGenerationResult.Template(
                                validatedProse("round-one", request.hobbyCount),
                            )
                        } else {
                            BioGenerationResult.Failure(BioGenerationFailure.TIMEOUT)
                        }
                    },
            )

        assertEquals(12, report.byRound.getValue(1).validProseCount)
        assertEquals(0, report.byRound.getValue(1).failureCount)
        assertEquals(0, report.byRound.getValue(2).validProseCount)
        assertEquals(12, report.byRound.getValue(2).failureCount)
        assertEquals(
            12,
            report.byRound.getValue(2).resultCounts.getValue(BioEvalOutcome.TIMEOUT),
        )
        assertEquals(12, report.overall.validProseCount)
        assertEquals(12, report.overall.failureCount)
    }

    @Test
    fun `runner preserves all twenty five round aggregates and cyclic positions`() {
        var calls = 0
        val report =
            LiveBioEvalRunner(
                clock = fixedClock(),
                nanoTime = constantLatencyTicker(),
            ).run(
                corpus = corpus,
                configuration = configuration(repetitions = 25, maxCalls = 300),
                generator =
                    BioGenerator { request ->
                        calls++
                        BioGenerationResult.Template(
                            validatedProse("repeated", request.hobbyCount),
                        )
                    },
            )

        assertEquals(300, calls)
        assertEquals((1..25).toSet(), report.byRound.keys)
        assertTrue(report.byRound.values.all { metrics -> metrics.attempts == 12 })
        assertEquals(25, report.attemptEvidence.last().round)
        assertEquals(12, report.attemptEvidence.last().slot)
        assertEquals(
            corpus.cases.map(BioEvalCase::id),
            report.attemptEvidence.takeLast(12).map(BioEvalAttemptEvidence::caseId),
        )
        assertEquals(
            setOf("output-001", "output-002", "output-003"),
            report.attemptEvidence.mapNotNull(
                BioEvalAttemptEvidence::outputEquivalenceId,
            ).toSet(),
        )
    }

    @Test
    fun `runner keeps every normalized failure and never retries`() {
        val sequence =
            listOf(
                BioGenerationResult.Failure(BioGenerationFailure.TIMEOUT),
                BioGenerationResult.Failure(BioGenerationFailure.RATE_LIMITED),
                BioGenerationResult.Failure(BioGenerationFailure.UNAVAILABLE),
                BioGenerationResult.Failure(BioGenerationFailure.INVALID_OUTPUT),
                BioGenerationResult.Failure(BioGenerationFailure.POLICY_REJECTED),
            )
        var calls = 0
        val generator =
            BioGenerator { request ->
                val result =
                    sequence.getOrElse(calls) {
                        BioGenerationResult.Template(
                            validatedProse("fallback-$calls", request.hobbyCount),
                        )
                    }
                calls++
                result
            }

        val report =
            LiveBioEvalRunner(
                clock = fixedClock(),
                nanoTime = constantLatencyTicker(),
            ).run(
                corpus = corpus,
                configuration = configuration(repetitions = 1, maxCalls = 12),
                generator = generator,
            )

        assertEquals(corpus.cases.size, calls)
        assertEquals(1, report.overall.resultCounts[BioEvalOutcome.TIMEOUT])
        assertEquals(1, report.overall.resultCounts[BioEvalOutcome.RATE_LIMITED])
        assertEquals(1, report.overall.resultCounts[BioEvalOutcome.UNAVAILABLE])
        assertEquals(1, report.overall.resultCounts[BioEvalOutcome.INVALID_OUTPUT])
        assertEquals(1, report.overall.resultCounts[BioEvalOutcome.POLICY_REJECTED])
        assertEquals(corpus.cases.size - sequence.size, report.overall.validProseCount)
        assertEquals(
            corpus.cases.size - sequence.size,
            report.overall.finalGroundedSizeReportedCount,
        )
        assertEquals(0, report.overall.validResultsWithoutGroundedMeasurement)
        assertEquals(sequence.size, report.overall.failureCount)
        assertTrue(
            report.attemptEvidence.take(sequence.size).all { evidence ->
                evidence.outputEquivalenceId == null &&
                    evidence.modelAuthoredCodePoints == null &&
                    evidence.sentenceCount == null &&
                    evidence.deterministicCatalogMatch == null &&
                    evidence.finalGroundedCodePoints == null
            },
        )
        assertTrue(
            report.attemptEvidence.drop(sequence.size).all { evidence ->
                evidence.outputEquivalenceId != null &&
                    evidence.modelAuthoredCodePoints != null &&
                    evidence.sentenceCount != null &&
                    evidence.deterministicCatalogMatch != null &&
                    evidence.finalGroundedCodePoints != null
            },
        )
        assertEquals(
            1,
            report.byCase.getValue("case-001")
                .resultCounts
                .getValue(BioEvalOutcome.TIMEOUT),
        )
        assertEquals(
            1,
            report.byCase.getValue("case-002")
                .resultCounts
                .getValue(BioEvalOutcome.RATE_LIMITED),
        )
    }

    @Test
    fun `runner returns partial sanitized evidence after a classified terminal attempt`() {
        var calls = 0
        var terminal = false
        val generator =
            BioGenerator {
                calls++
                terminal = true
                BioGenerationResult.Failure(BioGenerationFailure.UNAVAILABLE)
            }

        val report =
            LiveBioEvalRunner(
                clock = fixedClock(),
                nanoTime = constantLatencyTicker(),
            ).run(
                corpus = corpus,
                configuration = configuration(repetitions = 2, maxCalls = 24),
                generator = generator,
                stopAfterAttempt = { terminal },
            )

        assertEquals(1, calls)
        assertEquals(1, report.overall.attempts)
        assertEquals(1, report.overall.resultCounts[BioEvalOutcome.UNAVAILABLE])
        assertFalse(report.toSanitizedMap().toString().contains("terminal"))
    }

    @Test
    fun `runner checkpoints every completed paid attempt before propagating cancellation`() {
        var calls = 0
        val checkpointAttempts = mutableListOf<Int>()
        var finalCheckpoint: LiveBioEvalReport? = null
        val providerCancellation =
            CancellationException("RAW-SECRET-CANCELLATION-CONTENT")
        val generator =
            BioGenerator { request ->
                calls++
                if (calls == 4) {
                    throw providerCancellation
                }
                BioGenerationResult.Template(
                    validatedProse("checkpoint-$calls", request.hobbyCount),
                )
            }

        val cancellation =
            assertThrows<CancellationException> {
                LiveBioEvalRunner(
                    clock = fixedClock(),
                    nanoTime = constantLatencyTicker(),
                ).run(
                    corpus = corpus,
                    configuration = configuration(repetitions = 1, maxCalls = 12),
                    generator = generator,
                    afterAttempt = { report ->
                        checkpointAttempts += report.overall.attempts
                        finalCheckpoint = report
                        assertEquals(
                            report.overall.validProseCount,
                            report.overall.finalGroundedSizeReportedCount,
                        )
                        assertEquals(
                            report.overall.attempts,
                            report.attemptEvidence.size,
                        )
                        assertFalse(
                            report.toSanitizedMap().toString()
                                .contains("RAW-SECRET-CANCELLATION-CONTENT"),
                        )
                    },
                )
            }

        assertEquals(4, calls)
        assertTrue(cancellation === providerCancellation)
        assertEquals("RAW-SECRET-CANCELLATION-CONTENT", cancellation.message)
        assertEquals(listOf(1, 2, 3, 4), checkpointAttempts)
        val checkpoint = requireNotNull(finalCheckpoint)
        assertEquals(1, checkpoint.overall.resultCounts[BioEvalOutcome.CANCELLED])
        val cancelledAttempt = checkpoint.attemptEvidence.last()
        assertEquals(BioEvalOutcome.CANCELLED, cancelledAttempt.outcome)
        assertEquals(null, cancelledAttempt.outputEquivalenceId)
        assertEquals(null, cancelledAttempt.modelAuthoredCodePoints)
        assertEquals(null, cancelledAttempt.sentenceCount)
        assertEquals(null, cancelledAttempt.deterministicCatalogMatch)
        assertEquals(null, cancelledAttempt.finalGroundedCodePoints)
        val sanitizedCheckpoint = checkpoint.toSanitizedMap().toString()
        assertTrue(sanitizedCheckpoint.contains("cancelled"))
        assertFalse(sanitizedCheckpoint.contains("RAW-SECRET-CANCELLATION-CONTENT"))
    }

    @Test
    fun `call budget is rejected before a provider invocation and plan-only stays offline`() {
        var calls = 0
        var sleeps = 0
        val generator =
            BioGenerator { request ->
                calls++
                BioGenerationResult.Template(validatedProse("budget", request.hobbyCount))
            }
        val runner =
            LiveBioEvalRunner(
                clock = fixedClock(),
                sleeper = { sleeps++ },
            )

        val plan =
            runner.planOnly(
                corpus = corpus,
                configuration =
                    configuration(
                        repetitions = 2,
                        maxCalls = 24,
                        minimumCallInterval = Duration.ofSeconds(6),
                    ),
            )
        assertEquals(24, plan.plannedCalls)
        assertEquals(512, plan.modelAuthoredCodePointLimit)
        assertEquals(760, plan.maximumGroundingSourceCodePoints)
        assertEquals(1_272, plan.finalGroundedCodePointLimit)
        assertEquals(15_000L, plan.generationDeadlineMillis)
        assertEquals(
            "indexed_hobbies_case_matched_source_bound_v4",
            plan.groundingStrategy,
        )
        assertEquals("minimum_attempt_start_interval_v1", plan.pacingStrategy)
        assertEquals(6_000L, plan.minimumCallIntervalMillis)
        assertEquals(138_000L, plan.configuredMinimumCallStartSpanMillis)
        assertEquals(0, calls)
        assertEquals(0, sleeps)

        assertThrows<IllegalArgumentException> {
            runner.run(
                corpus = corpus,
                configuration =
                    configuration(
                        repetitions = 2,
                        maxCalls = 23,
                        minimumCallInterval = Duration.ofSeconds(6),
                    ),
                generator = generator,
            )
        }
        assertEquals(0, calls)
        assertEquals(0, sleeps)
    }

    @Test
    fun `novel valid prose and unexpected exceptions are aggregated without raw content`() {
        var calls = 0
        val generator =
            BioGenerator { request ->
                calls++
                when (calls) {
                    1 ->
                        BioGenerationResult.Template(
                            validatedProse("novel", request.hobbyCount),
                        )
                    2 -> error("RAW-SECRET-PROVIDER-CONTENT")
                    else ->
                        BioGenerationResult.Template(
                            validatedProse("unused", request.hobbyCount),
                        )
                }
            }

        val report =
            LiveBioEvalRunner(
                clock = fixedClock(),
                nanoTime = constantLatencyTicker(),
            ).run(
                corpus = corpus,
                configuration = configuration(repetitions = 1, maxCalls = 12),
                generator = generator,
            )
        val sanitizedReport = report.toSanitizedMap().toString()

        assertEquals(1, report.overall.resultCounts[BioEvalOutcome.VALID_PROSE])
        assertEquals(1, report.overall.resultCounts[BioEvalOutcome.HARNESS_ERROR])
        assertEquals(1, report.overall.validProseCount)
        assertEquals(1, report.overall.finalGroundedSizeReportedCount)
        assertEquals(0, report.overall.validResultsWithoutGroundedMeasurement)
        assertEquals(1, report.overall.distinctValidProseCount)
        assertEquals(0, report.overall.deterministicCatalogMatchCount)
        assertEquals(2, calls)
        assertEquals(2, report.overall.attempts)
        assertTrue(report.attemptEvidence[0].finalGroundedCodePoints != null)
        assertEquals(null, report.attemptEvidence[1].finalGroundedCodePoints)
        assertFalse(sanitizedReport.contains("RAW-SECRET-PROVIDER-CONTENT"))
        assertFalse(sanitizedReport.contains("technology_engineering"))
        assertFalse(sanitizedReport.contains("{{NAME}}"))
        assertTrue(
            sanitizedReport.contains("sanitized_metrics_no_request_or_response_content"),
        )
    }

    @Test
    fun `schema rejects incomplete output evidence and noncanonical run labels`() {
        assertThrows<IllegalArgumentException> {
            BioEvalAttemptEvidence(
                attemptIndex = 1,
                round = 1,
                slot = 1,
                caseId = "case-001",
                sliceIds = setOf("job-coverage"),
                outcome = BioEvalOutcome.VALID_PROSE,
                outputEquivalenceId = null,
                modelAuthoredCodePoints = null,
                sentenceCount = null,
                deterministicCatalogMatch = null,
                finalGroundedCodePoints = null,
            )
        }
        assertThrows<IllegalArgumentException> {
            BioEvalAttemptEvidence(
                attemptIndex = 1,
                round = 1,
                slot = 1,
                caseId = "case-001",
                sliceIds = setOf("job-coverage"),
                outcome = BioEvalOutcome.TIMEOUT,
                outputEquivalenceId = "output-001",
                modelAuthoredCodePoints = 10,
                sentenceCount = 1,
                deterministicCatalogMatch = false,
                finalGroundedCodePoints = 20,
            )
        }

        val report =
            LiveBioEvalRunner(
                clock = fixedClock(),
                nanoTime = constantLatencyTicker(),
            ).run(
                corpus = corpus,
                configuration = configuration(repetitions = 1, maxCalls = 12),
                generator =
                    BioGenerator { request ->
                        BioGenerationResult.Template(
                            validatedProse("canonical", request.hobbyCount),
                        )
                    },
            )
        assertThrows<IllegalArgumentException> {
            report.copy(
                attemptEvidence =
                    report.attemptEvidence.mapIndexed { index, evidence ->
                        if (index == 0) {
                            evidence.copy(outputEquivalenceId = "output-002")
                        } else {
                            evidence
                        }
                    },
            )
        }
        assertThrows<IllegalArgumentException> {
            report.copy(bySlice = emptyMap())
        }
        assertThrows<IllegalArgumentException> {
            report.copy(
                attemptEvidence =
                    report.attemptEvidence.mapIndexed { index, evidence ->
                        if (index == 1) {
                            evidence.copy(
                                modelAuthoredCodePoints =
                                    evidence.modelAuthoredCodePoints!! + 1,
                            )
                        } else {
                            evidence
                        }
                    },
            )
        }
    }

    private fun validatedProse(
        label: String,
        hobbyCount: Int = 1,
    ): GeneratedBioTemplate =
        when (
            val result =
                GeneratedBioTemplate.validate(
                    "{{NAME}} makes " +
                        (0 until hobbyCount).joinToString("; ") { index ->
                            "{{HOBBY[$index]}}"
                        } +
                        " quirky $label as a {{JOB}}.",
                    hobbyCount,
                )
        ) {
            is BioGenerationResult.Template -> result.value
            is BioGenerationResult.Failure ->
                error("Fixture must be valid generated prose: ${result.reason}")
        }

    private fun configuration(
        repetitions: Int,
        maxCalls: Int,
        minimumCallInterval: Duration = Duration.ZERO,
    ): LiveBioEvalConfiguration =
        LiveBioEvalConfiguration(
            provider = "fake",
            exactModelId = "fake-model-v1",
            codeRevision = "0".repeat(40),
            promptSha256 = BioEvalHash.sha256("prompt-v1"),
            outputSchemaSha256 = BioEvalHash.sha256("schema-v1"),
            maxOutputTokens = 1_024,
            repetitions = repetitions,
            maxCalls = maxCalls,
            minimumCallInterval = minimumCallInterval,
        )

    private fun fixedClock(): Clock =
        Clock.fixed(
            Instant.parse("2026-07-20T00:00:00Z"),
            ZoneOffset.UTC,
        )

    private fun constantLatencyTicker(): () -> Long {
        var current = 0L
        return {
            val value = current
            current += 1_000
            value
        }
    }

    private class FakeMonotonicTime {
        var nowNanos: Long = 0L
            private set
        val sleeps = mutableListOf<Duration>()

        fun nanoTime(): Long = nowNanos

        fun sleep(duration: Duration) {
            sleeps += duration
            advance(duration)
        }

        fun advance(duration: Duration) {
            nowNanos = Math.addExact(nowNanos, duration.toNanos())
        }
    }
}
