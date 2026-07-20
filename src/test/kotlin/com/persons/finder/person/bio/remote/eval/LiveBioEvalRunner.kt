package com.persons.finder.person.bio.remote.eval

import com.persons.finder.person.bio.BioGenerationFailure
import com.persons.finder.person.bio.BioGenerationResult
import com.persons.finder.person.bio.BioGenerator
import com.persons.finder.person.bio.BioGrounding
import com.persons.finder.person.bio.BioTemplateId
import com.persons.finder.person.bio.GeneratedBio
import com.persons.finder.person.bio.GeneratedBioTemplate
import com.persons.finder.person.model.PersonProfile
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CancellationException

internal data class LiveBioEvalConfiguration(
    val provider: String,
    val exactModelId: String,
    val codeRevision: String,
    val promptSha256: String,
    val outputSchemaSha256: String,
    val maxOutputTokens: Int,
    val repetitions: Int,
    val maxCalls: Int,
    val minimumCallInterval: Duration,
) {
    init {
        require(SAFE_METADATA_PATTERN.matches(provider)) {
            "Provider metadata is invalid"
        }
        require(SAFE_METADATA_PATTERN.matches(exactModelId)) {
            "Exact model id metadata is invalid"
        }
        require(GIT_COMMIT_PATTERN.matches(codeRevision)) {
            "Code revision must be a full Git commit identifier"
        }
        require(SHA_256_PATTERN.matches(promptSha256)) {
            "Prompt hash must be lowercase SHA-256"
        }
        require(SHA_256_PATTERN.matches(outputSchemaSha256)) {
            "Output schema hash must be lowercase SHA-256"
        }
        require(maxOutputTokens > 0) { "Maximum output tokens must be positive" }
        require(repetitions > 0) { "Repetition count must be positive" }
        require(maxCalls > 0) { "Maximum call count must be positive" }
        require(!minimumCallInterval.isNegative) {
            "Minimum live AI call interval cannot be negative"
        }
        require(minimumCallInterval <= MAXIMUM_LIVE_AI_EVAL_MIN_CALL_INTERVAL) {
            "Minimum live AI call interval cannot exceed 60000 milliseconds"
        }
    }

    companion object {
        private val SAFE_METADATA_PATTERN =
            Regex("[A-Za-z0-9][A-Za-z0-9._:/@+-]{0,127}")
        private val GIT_COMMIT_PATTERN = Regex("[a-f0-9]{40,64}")
        private val SHA_256_PATTERN = Regex("[a-f0-9]{64}")
    }
}

internal data class BioEvalCallPlan(
    val corpusId: String,
    val corpusSha256: String,
    val caseCount: Int,
    val repetitions: Int,
    val plannedCalls: Int,
    val maxCalls: Int,
    val maxOutputTokens: Int,
    val pacingStrategy: String,
    val minimumCallIntervalMillis: Long,
    val configuredMinimumCallStartSpanMillis: Long,
)

internal class LiveBioEvalRunner(
    private val clock: Clock = Clock.systemUTC(),
    private val nanoTime: () -> Long = System::nanoTime,
    private val sleeper: (Duration) -> Unit = { duration ->
        val millis = duration.toMillis()
        val remainingNanos = duration.minusMillis(millis).toNanos().toInt()
        Thread.sleep(millis, remainingNanos)
    },
) {
    fun planOnly(
        corpus: BioEvalCorpus,
        configuration: LiveBioEvalConfiguration,
    ): BioEvalCallPlan {
        val plannedCalls =
            try {
                Math.multiplyExact(corpus.cases.size, configuration.repetitions)
            } catch (exception: ArithmeticException) {
                throw IllegalArgumentException("Planned provider call count is too large", exception)
            }
        require(plannedCalls <= configuration.maxCalls) {
            "Planned provider calls ($plannedCalls) exceed the explicit maximum " +
                "(${configuration.maxCalls})"
        }
        val minimumCallIntervalMillis = configuration.minimumCallInterval.toMillis()
        val configuredMinimumCallStartSpanMillis =
            try {
                Math.multiplyExact(
                    (plannedCalls - 1).toLong(),
                    minimumCallIntervalMillis,
                )
            } catch (exception: ArithmeticException) {
                throw IllegalArgumentException(
                    "Configured minimum provider call-start span is too large",
                    exception,
                )
            }
        return BioEvalCallPlan(
            corpusId = corpus.id,
            corpusSha256 = corpus.sha256,
            caseCount = corpus.cases.size,
            repetitions = configuration.repetitions,
            plannedCalls = plannedCalls,
            maxCalls = configuration.maxCalls,
            maxOutputTokens = configuration.maxOutputTokens,
            pacingStrategy = PACING_STRATEGY,
            minimumCallIntervalMillis = minimumCallIntervalMillis,
            configuredMinimumCallStartSpanMillis = configuredMinimumCallStartSpanMillis,
        )
    }

    fun run(
        corpus: BioEvalCorpus,
        configuration: LiveBioEvalConfiguration,
        generator: BioGenerator,
        stopAfterAttempt: () -> Boolean = { false },
        afterAttempt: (LiveBioEvalReport) -> Unit = {},
    ): LiveBioEvalReport {
        // The budget guard intentionally runs before the first provider invocation.
        val plan = planOnly(corpus, configuration)
        val startedAt = clock.instant()
        val attempts = ArrayList<AttemptAggregate>(plan.plannedCalls)
        val pacer =
            MinimumAttemptStartPacer(
                minimumInterval = configuration.minimumCallInterval,
                nanoTime = nanoTime,
                sleeper = sleeper,
            )

        evaluation@
        for (repetition in 0 until configuration.repetitions) {
            val offset = repetition % corpus.cases.size
            val orderedCases =
                corpus.cases.drop(offset) + corpus.cases.take(offset)
            for (testCase in orderedCases) {
                val startedNanos = pacer.awaitAttemptStart()
                val classified =
                    try {
                        classify(generator.generate(testCase.toRequest()))
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: RuntimeException) {
                        ClassifiedResult(BioEvalOutcome.HARNESS_ERROR)
                    }
                val elapsedNanos = (nanoTime() - startedNanos).coerceAtLeast(0)
                attempts +=
                    AttemptAggregate(
                        caseId = testCase.id,
                        slices = testCase.slices,
                        outcome = classified.outcome,
                        proseFingerprint = classified.proseFingerprint,
                        deterministicCatalogMatch = classified.deterministicCatalogMatch,
                        latencyNanos = elapsedNanos,
                    )
                afterAttempt(
                    buildReport(
                        corpus = corpus,
                        configuration = configuration,
                        plan = plan,
                        startedAt = startedAt,
                        attempts = attempts,
                        pacer = pacer,
                    ),
                )
                if (
                    classified.outcome == BioEvalOutcome.HARNESS_ERROR ||
                    stopAfterAttempt()
                ) {
                    break@evaluation
                }
            }
        }

        return buildReport(
            corpus = corpus,
            configuration = configuration,
            plan = plan,
            startedAt = startedAt,
            attempts = attempts,
            pacer = pacer,
        )
    }

    private fun buildReport(
        corpus: BioEvalCorpus,
        configuration: LiveBioEvalConfiguration,
        plan: BioEvalCallPlan,
        startedAt: Instant,
        attempts: List<AttemptAggregate>,
        pacer: MinimumAttemptStartPacer,
    ): LiveBioEvalReport =
        LiveBioEvalReport(
            startedAt = startedAt,
            completedAt = clock.instant(),
            provenance =
                BioEvalProvenance(
                    provider = configuration.provider,
                    exactModelId = configuration.exactModelId,
                    codeRevision = configuration.codeRevision,
                    corpusId = corpus.id,
                    corpusSchemaVersion = corpus.schemaVersion,
                    corpusSha256 = corpus.sha256,
                    promptSha256 = configuration.promptSha256,
                    outputSchemaSha256 = configuration.outputSchemaSha256,
                    maxOutputTokens = configuration.maxOutputTokens,
                    caseOrderStrategy = "cyclic_rotation_v1",
                    repetitions = configuration.repetitions,
                    plannedCalls = plan.plannedCalls,
                    pacingStrategy = plan.pacingStrategy,
                    minimumCallIntervalMillis = plan.minimumCallIntervalMillis,
                    configuredMinimumCallStartSpanMillis =
                        plan.configuredMinimumCallStartSpanMillis,
                ),
            pacing = pacer.snapshot(),
            overall = metricsFor(attempts),
            byCase =
                attempts
                    .groupBy(AttemptAggregate::caseId)
                    .toSortedMap()
                    .mapValues { entry -> metricsFor(entry.value) },
            bySlice =
                attempts
                    .flatMap { attempt ->
                        attempt.slices.map { slice -> slice to attempt }
                    }
                    .groupBy(
                        keySelector = { entry -> entry.first },
                        valueTransform = { entry -> entry.second },
                    )
                    .toSortedMap()
                    .mapValues { entry -> metricsFor(entry.value) },
        )

    private fun classify(result: BioGenerationResult): ClassifiedResult =
        when (result) {
            is BioGenerationResult.Failure ->
                ClassifiedResult(result.reason.toEvalOutcome())

            is BioGenerationResult.Template -> {
                val validated = GeneratedBioTemplate.validate(result.value.value)
                if (validated != BioGenerationResult.Template(result.value)) {
                    ClassifiedResult(BioEvalOutcome.INVALID_OUTPUT)
                } else {
                    try {
                        GeneratedBio.compose(result.value, MAXIMUM_SYNTHETIC_GROUNDING)
                        ClassifiedResult(
                            outcome = BioEvalOutcome.VALID_PROSE,
                            proseFingerprint = BioEvalHash.sha256(result.value.value),
                            deterministicCatalogMatch =
                                result.value in DETERMINISTIC_CATALOG_TEMPLATES,
                        )
                    } catch (_: IllegalArgumentException) {
                        ClassifiedResult(BioEvalOutcome.INVALID_OUTPUT)
                    }
                }
            }
        }

    private fun metricsFor(attempts: List<AttemptAggregate>): BioEvalMetrics {
        val resultCounts =
            BioEvalOutcome.entries.associateWith { outcome ->
                attempts.count { attempt -> attempt.outcome == outcome }
            }
        val validCount = resultCounts.getValue(BioEvalOutcome.VALID_PROSE)
        val failureCount = attempts.size - validCount
        return BioEvalMetrics(
            attempts = attempts.size,
            validProseCount = validCount,
            distinctValidProseCount =
                attempts.mapNotNull(AttemptAggregate::proseFingerprint).distinct().size,
            deterministicCatalogMatchCount =
                attempts.count(AttemptAggregate::deterministicCatalogMatch),
            failureCount = failureCount,
            observedFailureRate = failureCount.toDouble() / attempts.size,
            oneSided95WilsonUpperFailureBound =
                BioEvalStatistics.oneSided95WilsonUpperBound(
                    failures = failureCount,
                    attempts = attempts.size,
                ),
            resultCounts = resultCounts,
            latency =
                BioEvalStatistics.latencySummary(
                    attempts.map(AttemptAggregate::latencyNanos),
                ),
        )
    }

    private fun BioGenerationFailure.toEvalOutcome(): BioEvalOutcome =
        when (this) {
            BioGenerationFailure.TIMEOUT -> BioEvalOutcome.TIMEOUT
            BioGenerationFailure.RATE_LIMITED -> BioEvalOutcome.RATE_LIMITED
            BioGenerationFailure.UNAVAILABLE -> BioEvalOutcome.UNAVAILABLE
            BioGenerationFailure.INVALID_OUTPUT -> BioEvalOutcome.INVALID_OUTPUT
            BioGenerationFailure.POLICY_REJECTED -> BioEvalOutcome.POLICY_REJECTED
        }

    private data class AttemptAggregate(
        val caseId: String,
        val slices: Set<String>,
        val outcome: BioEvalOutcome,
        val proseFingerprint: String?,
        val deterministicCatalogMatch: Boolean,
        val latencyNanos: Long,
    )

    private data class ClassifiedResult(
        val outcome: BioEvalOutcome,
        val proseFingerprint: String? = null,
        val deterministicCatalogMatch: Boolean = false,
    )

    private companion object {
        const val PACING_STRATEGY = "minimum_attempt_start_interval_v1"

        val DETERMINISTIC_CATALOG_TEMPLATES: Set<GeneratedBioTemplate> =
            BioTemplateId.entries.mapTo(mutableSetOf(), GeneratedBioTemplate::fromCatalog)
        val MAXIMUM_SYNTHETIC_GROUNDING =
            BioGrounding(
                name = "N".repeat(PersonProfile.MAX_NAME_CODE_POINTS),
                jobTitle = "J".repeat(PersonProfile.MAX_JOB_TITLE_CODE_POINTS),
                hobby = "H".repeat(PersonProfile.MAX_HOBBY_CODE_POINTS),
            )
    }
}
