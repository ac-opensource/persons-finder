package com.persons.finder.person.bio.remote.eval

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.CancellationException

class LiveBioEvalPacingTest {
    @Test
    fun `pacer starts immediately then preserves spacing without adding provider latency`() {
        var now = 0L
        val sleeps = mutableListOf<Duration>()
        val pacer =
            MinimumAttemptStartPacer(
                minimumInterval = Duration.ofSeconds(6),
                nanoTime = { now },
                sleeper = { duration ->
                    sleeps += duration
                    now += duration.toNanos()
                },
            )

        val firstStart = pacer.awaitAttemptStart()
        now += Duration.ofSeconds(1).toNanos()
        val secondStart = pacer.awaitAttemptStart()
        now += Duration.ofSeconds(1).toNanos()
        val thirdStart = pacer.awaitAttemptStart()

        assertEquals(0L, firstStart)
        assertEquals(Duration.ofSeconds(6).toNanos(), secondStart)
        assertEquals(Duration.ofSeconds(12).toNanos(), thirdStart)
        assertEquals(
            listOf(Duration.ofSeconds(5), Duration.ofSeconds(5)),
            sleeps,
        )
        assertEquals(
            LiveBioEvalPacingSnapshot(
                waitEventCount = 2,
                actualWaitNanos = Duration.ofSeconds(10).toNanos(),
            ),
            pacer.snapshot(),
        )
    }

    @Test
    fun `provider latency beyond the configured interval requires no pacing wait`() {
        var now = 0L
        var sleeps = 0
        val pacer =
            MinimumAttemptStartPacer(
                minimumInterval = Duration.ofSeconds(6),
                nanoTime = { now },
                sleeper = { sleeps++ },
            )

        pacer.awaitAttemptStart()
        now += Duration.ofSeconds(7).toNanos()
        assertEquals(now, pacer.awaitAttemptStart())

        assertEquals(0, sleeps)
        assertEquals(LiveBioEvalPacingSnapshot(0, 0L), pacer.snapshot())
    }

    @Test
    fun `interrupted pacing restores interruption and cancels before another attempt`() {
        var now = 0L
        val pacer =
            MinimumAttemptStartPacer(
                minimumInterval = Duration.ofSeconds(1),
                nanoTime = { now },
                sleeper = { throw InterruptedException("synthetic") },
            )

        pacer.awaitAttemptStart()
        val exception =
            assertThrows<CancellationException> {
                pacer.awaitAttemptStart()
            }

        assertTrue(Thread.interrupted())
        assertTrue(exception.message.orEmpty().contains("pacing"))
    }

    @Test
    fun `minimum interval environment value is explicit and bounded`() {
        assertEquals(
            Duration.ZERO,
            requireLiveBioEvalMinimumCallInterval { name ->
                if (name == LIVE_AI_EVAL_MIN_CALL_INTERVAL_ENV) "0" else null
            },
        )
        assertEquals(
            Duration.ofSeconds(6),
            requireLiveBioEvalMinimumCallInterval { name ->
                if (name == LIVE_AI_EVAL_MIN_CALL_INTERVAL_ENV) "6000" else null
            },
        )

        listOf(null, "", " 0", "-1", "60001", "1.5").forEach { invalid ->
            assertThrows<IllegalArgumentException> {
                requireLiveBioEvalMinimumCallInterval { name ->
                    if (name == LIVE_AI_EVAL_MIN_CALL_INTERVAL_ENV) invalid else null
                }
            }
        }
    }
}
