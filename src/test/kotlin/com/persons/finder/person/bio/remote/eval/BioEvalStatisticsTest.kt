package com.persons.finder.person.bio.remote.eval

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BioEvalStatisticsTest {
    @Test
    fun `Wilson upper bound uses a one-sided 95 percent interval`() {
        assertEquals(
            0.008937,
            BioEvalStatistics.oneSided95WilsonUpperBound(
                failures = 0,
                attempts = 300,
            ),
            0.000001,
        )
        assertEquals(
            1.0,
            BioEvalStatistics.oneSided95WilsonUpperBound(
                failures = 1,
                attempts = 1,
            ),
            0.000001,
        )
    }

    @Test
    fun `latency summary uses nearest-rank percentiles`() {
        assertEquals(
            BioEvalLatencySummary(
                p50Nanos = 2,
                p95Nanos = 4,
                maxNanos = 4,
            ),
            BioEvalStatistics.latencySummary(listOf(4, 1, 3, 2)),
        )
    }

    @Test
    fun `statistics reject invalid samples`() {
        assertThrows<IllegalArgumentException> {
            BioEvalStatistics.oneSided95WilsonUpperBound(
                failures = 0,
                attempts = 0,
            )
        }
        assertThrows<IllegalArgumentException> {
            BioEvalStatistics.oneSided95WilsonUpperBound(
                failures = 2,
                attempts = 1,
            )
        }
        assertThrows<IllegalArgumentException> {
            BioEvalStatistics.latencySummary(emptyList())
        }
    }
}
