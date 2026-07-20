package com.persons.finder.person.bio.remote.eval

import java.time.Duration
import java.util.concurrent.CancellationException

internal const val LIVE_AI_EVAL_MIN_CALL_INTERVAL_ENV =
    "LIVE_AI_EVAL_MIN_CALL_INTERVAL_MS"

internal val MAXIMUM_LIVE_AI_EVAL_MIN_CALL_INTERVAL: Duration = Duration.ofMinutes(1)

internal data class LiveBioEvalPacingSnapshot(
    val waitEventCount: Int,
    val actualWaitNanos: Long,
)

internal class MinimumAttemptStartPacer(
    private val minimumInterval: Duration,
    private val nanoTime: () -> Long = System::nanoTime,
    private val sleeper: (Duration) -> Unit = ::sleepInterruptibly,
) {
    private val minimumIntervalNanos = minimumInterval.toNanos()
    private var previousAttemptStartedNanos: Long? = null
    private var waitEventCount = 0
    private var actualWaitNanos = 0L

    init {
        require(!minimumInterval.isNegative) {
            "Minimum live AI call interval cannot be negative"
        }
        require(minimumInterval <= MAXIMUM_LIVE_AI_EVAL_MIN_CALL_INTERVAL) {
            "Minimum live AI call interval cannot exceed 60000 milliseconds"
        }
    }

    fun awaitAttemptStart(): Long {
        val previousStart = previousAttemptStartedNanos
        if (previousStart == null || minimumIntervalNanos == 0L) {
            return nanoTime().also { started -> previousAttemptStartedNanos = started }
        }

        var waitStartedNanos: Long? = null
        while (true) {
            val now = nanoTime()
            val elapsedSincePreviousStart = now - previousStart
            val remainingNanos = minimumIntervalNanos - elapsedSincePreviousStart
            if (remainingNanos <= 0L) {
                waitStartedNanos?.let { waitStarted ->
                    actualWaitNanos =
                        Math.addExact(
                            actualWaitNanos,
                            (now - waitStarted).coerceAtLeast(0L),
                        )
                }
                previousAttemptStartedNanos = now
                return now
            }

            if (waitStartedNanos == null) {
                waitStartedNanos = now
                waitEventCount = Math.addExact(waitEventCount, 1)
            }
            try {
                sleeper(Duration.ofNanos(remainingNanos))
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
                throw CancellationException("Live AI evaluation pacing was interrupted").apply {
                    initCause(exception)
                }
            }
        }
    }

    fun snapshot(): LiveBioEvalPacingSnapshot =
        LiveBioEvalPacingSnapshot(
            waitEventCount = waitEventCount,
            actualWaitNanos = actualWaitNanos,
        )

    private companion object {
        fun sleepInterruptibly(duration: Duration) {
            val millis = duration.toMillis()
            val remainingNanos = duration.minusMillis(millis).toNanos().toInt()
            Thread.sleep(millis, remainingNanos)
        }
    }
}

internal fun requireLiveBioEvalMinimumCallInterval(
    environment: (String) -> String?,
): Duration {
    val value = environment(LIVE_AI_EVAL_MIN_CALL_INTERVAL_ENV)
    require(!value.isNullOrBlank() && value == value.trim()) {
        "$LIVE_AI_EVAL_MIN_CALL_INTERVAL_ENV is required and must not have surrounding whitespace"
    }
    val millis =
        value.toLongOrNull()
            ?.takeIf { parsed -> parsed in 0L..60_000L }
            ?: throw IllegalArgumentException(
                "$LIVE_AI_EVAL_MIN_CALL_INTERVAL_ENV must be an integer from zero through 60000",
            )
    return Duration.ofMillis(millis)
}
