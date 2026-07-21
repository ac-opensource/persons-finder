package com.persons.finder.person.bio.remote.eval

import com.persons.finder.person.bio.BIO_GENERATION_DEADLINE
import com.persons.finder.person.bio.BioGenerationFailure
import com.persons.finder.person.bio.BioGenerationResult
import com.persons.finder.person.bio.BioGenerator
import com.persons.finder.person.bio.BioGrounding
import com.persons.finder.person.bio.BioPolicy
import com.persons.finder.person.bio.BioTemplateRequest
import com.persons.finder.person.bio.BioTemplateId
import com.persons.finder.person.bio.GeneratedBio
import com.persons.finder.person.bio.GeneratedBioTemplate
import com.persons.finder.person.bio.modelAuthoredCodePointCount
import com.persons.finder.person.bio.sentenceCount
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
    val generationDeadline: Duration = BIO_GENERATION_DEADLINE,
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
        require(!generationDeadline.isZero && !generationDeadline.isNegative) {
            "Generation deadline must be positive"
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
    val modelAuthoredCodePointLimit: Int,
    val maximumGroundingSourceCodePoints: Int,
    val finalGroundedCodePointLimit: Int,
    val groundingStrategy: String,
    val generationDeadlineMillis: Long,
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
            modelAuthoredCodePointLimit =
                BioPolicy.MAXIMUM_BIO_TEMPLATE_LITERAL_CODE_POINTS,
            maximumGroundingSourceCodePoints =
                BioPolicy.MAX_GROUNDED_SOURCE_CODE_POINTS,
            finalGroundedCodePointLimit = BioPolicy.FINAL_BIO_MAX_CODE_POINTS,
            groundingStrategy = MAXIMUM_SYNTHETIC_GROUNDING_STRATEGY,
            generationDeadlineMillis = configuration.generationDeadline.toMillis(),
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
            for ((slotIndex, testCase) in orderedCases.withIndex()) {
                val startedNanos = pacer.awaitAttemptStart()
                var cancellation: CancellationException? = null
                val classified =
                    try {
                        classify(
                            result = generator.generate(testCase.toRequest()),
                            grounding = maximumSyntheticGrounding(testCase.hobbyCount),
                        )
                    } catch (exception: CancellationException) {
                        cancellation = exception
                        ClassifiedResult(BioEvalOutcome.CANCELLED)
                    } catch (_: RuntimeException) {
                        ClassifiedResult(BioEvalOutcome.HARNESS_ERROR)
                    }
                val elapsedNanos = (nanoTime() - startedNanos).coerceAtLeast(0)
                attempts +=
                    AttemptAggregate(
                        round = repetition + 1,
                        slot = slotIndex + 1,
                        caseId = testCase.id,
                        slices = testCase.slices,
                        outcome = classified.outcome,
                        outputIdentity = classified.outputIdentity,
                        modelAuthoredCodePoints = classified.modelAuthoredCodePoints,
                        sentenceCount = classified.sentenceCount,
                        deterministicCatalogMatch = classified.deterministicCatalogMatch,
                        finalGroundedCodePoints = classified.finalGroundedCodePoints,
                        latencyNanos = elapsedNanos,
                    )
                val checkpoint =
                    buildReport(
                        corpus = corpus,
                        configuration = configuration,
                        plan = plan,
                        startedAt = startedAt,
                        attempts = attempts,
                        pacer = pacer,
                    )
                try {
                    afterAttempt(checkpoint)
                } catch (checkpointFailure: RuntimeException) {
                    cancellation?.addSuppressed(checkpointFailure)
                    throw cancellation ?: checkpointFailure
                }
                cancellation?.let { exception ->
                    throw exception
                }
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
    ): LiveBioEvalReport {
        val equivalenceIdsByOutput = linkedMapOf<GeneratedBioTemplate, String>()
        return LiveBioEvalReport(
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
                    modelAuthoredCodePointLimit = plan.modelAuthoredCodePointLimit,
                    maximumGroundingSourceCodePoints =
                        plan.maximumGroundingSourceCodePoints,
                    finalGroundedCodePointLimit = plan.finalGroundedCodePointLimit,
                    groundingStrategy = plan.groundingStrategy,
                    generationDeadlineMillis = plan.generationDeadlineMillis,
                    caseOrderStrategy = "cyclic_rotation_v1",
                    repetitions = configuration.repetitions,
                    plannedCalls = plan.plannedCalls,
                    pacingStrategy = plan.pacingStrategy,
                    minimumCallIntervalMillis = plan.minimumCallIntervalMillis,
                    configuredMinimumCallStartSpanMillis =
                        plan.configuredMinimumCallStartSpanMillis,
                ),
            pacing = pacer.snapshot(),
            attemptEvidence =
                attempts.mapIndexed { index, attempt ->
                    BioEvalAttemptEvidence(
                        attemptIndex = index + 1,
                        round = attempt.round,
                        slot = attempt.slot,
                        caseId = attempt.caseId,
                        sliceIds = attempt.slices,
                        outcome = attempt.outcome,
                        outputEquivalenceId =
                            attempt.outputIdentity?.let { output ->
                                equivalenceIdsByOutput.getOrPut(output) {
                                    "output-${
                                        (equivalenceIdsByOutput.size + 1)
                                            .toString()
                                            .padStart(3, '0')
                                    }"
                                }
                            },
                        modelAuthoredCodePoints = attempt.modelAuthoredCodePoints,
                        sentenceCount = attempt.sentenceCount,
                        deterministicCatalogMatch =
                            attempt.deterministicCatalogMatch,
                        finalGroundedCodePoints = attempt.finalGroundedCodePoints,
                    )
                },
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
            byRound =
                attempts
                    .groupBy(AttemptAggregate::round)
                    .toSortedMap()
                    .mapValues { entry -> metricsFor(entry.value) },
        )
    }

    private fun classify(
        result: BioGenerationResult,
        grounding: BioGrounding,
    ): ClassifiedResult =
        when (result) {
            is BioGenerationResult.Failure ->
                ClassifiedResult(result.reason.toEvalOutcome())

            is BioGenerationResult.Template -> {
                val validatedTemplate =
                    (
                        GeneratedBioTemplate.validate(
                            result.value.value,
                            grounding.hobbies.size,
                        )
                            as? BioGenerationResult.Template
                    )?.value
                if (validatedTemplate != result.value) {
                    ClassifiedResult(BioEvalOutcome.INVALID_OUTPUT)
                } else {
                    try {
                        val grounded =
                            GeneratedBio.compose(
                                validatedTemplate,
                                grounding,
                            )
                        ClassifiedResult(
                            outcome = BioEvalOutcome.VALID_PROSE,
                            outputIdentity = validatedTemplate,
                            modelAuthoredCodePoints =
                                validatedTemplate.modelAuthoredCodePointCount(),
                            sentenceCount = validatedTemplate.sentenceCount(),
                            deterministicCatalogMatch =
                                validatedTemplate in DETERMINISTIC_CATALOG_TEMPLATES,
                            finalGroundedCodePoints =
                                grounded.value.codePointCount(0, grounded.value.length),
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
        val groundedSizes =
            attempts.mapNotNull(AttemptAggregate::finalGroundedCodePoints)
        return BioEvalMetrics(
            attempts = attempts.size,
            validProseCount = validCount,
            distinctValidProseCount =
                attempts.mapNotNull(AttemptAggregate::outputIdentity).distinct().size,
            deterministicCatalogMatchCount =
                attempts.count { attempt -> attempt.deterministicCatalogMatch == true },
            finalGroundedSizeReportedCount = groundedSizes.size,
            maximumFinalGroundedCodePoints = groundedSizes.maxOrNull() ?: 0,
            validResultsWithoutGroundedMeasurement = validCount - groundedSizes.size,
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
        val round: Int,
        val slot: Int,
        val caseId: String,
        val slices: Set<String>,
        val outcome: BioEvalOutcome,
        val outputIdentity: GeneratedBioTemplate?,
        val modelAuthoredCodePoints: Int?,
        val sentenceCount: Int?,
        val deterministicCatalogMatch: Boolean?,
        val finalGroundedCodePoints: Int?,
        val latencyNanos: Long,
    )

    private data class ClassifiedResult(
        val outcome: BioEvalOutcome,
        val outputIdentity: GeneratedBioTemplate? = null,
        val modelAuthoredCodePoints: Int? = null,
        val sentenceCount: Int? = null,
        val deterministicCatalogMatch: Boolean? = null,
        val finalGroundedCodePoints: Int? = null,
    )

    private companion object {
        const val PACING_STRATEGY = "minimum_attempt_start_interval_v1"
        const val MAXIMUM_SYNTHETIC_GROUNDING_STRATEGY =
            "indexed_hobbies_case_matched_source_bound_v4"
        const val MAXIMUM_HOBBY_FILLERS = "ABCDEFGHIK"

        val DETERMINISTIC_CATALOG_TEMPLATES: Set<GeneratedBioTemplate> =
            (1..BioTemplateRequest.MAX_HOBBY_PLACEHOLDERS)
                .flatMap { hobbyCount ->
                    BioTemplateId.entries.map { templateId ->
                        GeneratedBioTemplate.fromCatalog(templateId, hobbyCount)
                    }
                }.toSet()

        fun maximumSyntheticGrounding(hobbyCount: Int): BioGrounding =
            BioGrounding(
                name = "N".repeat(PersonProfile.MAX_NAME_CODE_POINTS),
                jobTitle = "J".repeat(PersonProfile.MAX_JOB_TITLE_CODE_POINTS),
                hobbies =
                    List(hobbyCount) { index ->
                        MAXIMUM_HOBBY_FILLERS[index].toString()
                            .repeat(PersonProfile.MAX_HOBBY_CODE_POINTS)
                    },
            )
    }
}
