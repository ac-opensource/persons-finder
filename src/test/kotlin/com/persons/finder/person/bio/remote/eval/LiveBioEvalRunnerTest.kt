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
            BioGenerator {
                val prose = validatedProse("variant-$calls")
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
        assertEquals(5, report.reportSchemaVersion)
        assertEquals(24, report.provenance.plannedCalls)
        assertEquals(1_024, report.provenance.maxOutputTokens)
        assertEquals(512, report.provenance.modelAuthoredCodePointLimit)
        assertEquals(220, report.provenance.maximumGroundingSourceCodePoints)
        assertEquals(732, report.provenance.finalGroundedCodePointLimit)
        assertEquals(
            "maximum_approved_source_lengths_v1",
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
        assertEquals(252, report.overall.maximumFinalGroundedCodePoints)
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
            setOf("both-other", "job-coverage", "multi-interest", "single-interest"),
            report.bySlice.keys,
        )
        assertEquals(
            corpus.cases.map(BioEvalCase::id).toSet(),
            report.byCase.keys,
        )
        assertTrue(report.byCase.values.all { metrics -> metrics.attempts == 2 })
        assertEquals(
            (1..24).toList(),
            report.attemptEvidence.map(BioEvalAttemptEvidence::attemptIndex),
        )
        assertEquals(
            corpus.cases.map(BioEvalCase::id) +
                (corpus.cases.drop(1) + corpus.cases.take(1)).map(BioEvalCase::id),
            report.attemptEvidence.map(BioEvalAttemptEvidence::caseId),
        )
        assertTrue(
            report.attemptEvidence.all { evidence ->
                evidence.outcome == BioEvalOutcome.VALID_PROSE &&
                    evidence.finalGroundedCodePoints != null
            },
        )
    }

    @Test
    fun `runner starts immediately then spaces attempts without adding pacing to latency`() {
        val time = FakeMonotonicTime()
        val attemptStarts = mutableListOf<Long>()
        val generator =
            BioGenerator {
                attemptStarts += time.nowNanos
                time.advance(Duration.ofSeconds(1))
                BioGenerationResult.Template(validatedProse("paced"))
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
        assertEquals(5, sanitized["report_schema_version"])
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
        assertEquals(220, sanitizedProvenance["maximum_grounding_source_code_points"])
        assertEquals(732, sanitizedProvenance["final_grounded_code_point_limit"])
        assertEquals(
            "maximum_approved_source_lengths_v1",
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
                "case_id",
                "normalized_result",
                "final_grounded_code_points",
            ),
            firstAttempt.keys,
        )
        assertEquals(1, firstAttempt["attempt_index"])
        assertEquals(corpus.cases.first().id, firstAttempt["case_id"])
        assertEquals("valid_prose", firstAttempt["normalized_result"])
        assertTrue((firstAttempt["final_grounded_code_points"] as Int) > 0)
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
            BioGenerator {
                val result =
                    sequence.getOrElse(calls) {
                        BioGenerationResult.Template(validatedProse("fallback-$calls"))
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
                evidence.finalGroundedCodePoints == null
            },
        )
        assertTrue(
            report.attemptEvidence.drop(sequence.size).all { evidence ->
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
        val generator =
            BioGenerator {
                calls++
                if (calls == 4) {
                    throw CancellationException("RAW-SECRET-CANCELLATION-CONTENT")
                }
                BioGenerationResult.Template(validatedProse("checkpoint-$calls"))
            }

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
        assertEquals(listOf(1, 2, 3), checkpointAttempts)
    }

    @Test
    fun `call budget is rejected before a provider invocation and plan-only stays offline`() {
        var calls = 0
        var sleeps = 0
        val generator =
            BioGenerator {
                calls++
                BioGenerationResult.Template(validatedProse("budget"))
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
        assertEquals(220, plan.maximumGroundingSourceCodePoints)
        assertEquals(732, plan.finalGroundedCodePointLimit)
        assertEquals("maximum_approved_source_lengths_v1", plan.groundingStrategy)
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
        val novelProse = validatedProse("novel")
        var calls = 0
        val generator =
            BioGenerator {
                calls++
                when (calls) {
                    1 -> BioGenerationResult.Template(novelProse)
                    2 -> error("RAW-SECRET-PROVIDER-CONTENT")
                    else -> BioGenerationResult.Template(validatedProse("unused"))
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

    private fun validatedProse(label: String): GeneratedBioTemplate =
        when (
            val result =
                GeneratedBioTemplate.validate(
                    "{{NAME}} makes {{HOBBY}} quirky $label as a {{JOB}}.",
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
