package com.persons.finder.person.bio.remote.eval

import kotlin.math.ceil
import kotlin.math.sqrt

internal data class BioEvalLatencySummary(
    val p50Nanos: Long,
    val p95Nanos: Long,
    val maxNanos: Long,
)

internal object BioEvalStatistics {
    private const val ONE_SIDED_95_PERCENT_Z = 1.6448536269514722

    fun oneSided95WilsonUpperBound(
        failures: Int,
        attempts: Int,
    ): Double {
        require(attempts > 0) { "Wilson interval requires at least one attempt" }
        require(failures in 0..attempts) {
            "Failure count must be between zero and the attempt count"
        }

        val sampleSize = attempts.toDouble()
        val observedRate = failures / sampleSize
        val zSquared = ONE_SIDED_95_PERCENT_Z * ONE_SIDED_95_PERCENT_Z
        val denominator = 1.0 + zSquared / sampleSize
        val centre = observedRate + zSquared / (2.0 * sampleSize)
        val spread =
            ONE_SIDED_95_PERCENT_Z *
                sqrt(
                    observedRate * (1.0 - observedRate) / sampleSize +
                        zSquared / (4.0 * sampleSize * sampleSize),
                )
        return ((centre + spread) / denominator).coerceIn(0.0, 1.0)
    }

    fun latencySummary(latenciesNanos: List<Long>): BioEvalLatencySummary {
        require(latenciesNanos.isNotEmpty()) {
            "Latency summary requires at least one attempt"
        }
        require(latenciesNanos.all { latency -> latency >= 0 }) {
            "Latency cannot be negative"
        }
        val sorted = latenciesNanos.sorted()
        return BioEvalLatencySummary(
            p50Nanos = nearestRank(sorted, 0.50),
            p95Nanos = nearestRank(sorted, 0.95),
            maxNanos = sorted.last(),
        )
    }

    private fun nearestRank(
        sortedValues: List<Long>,
        percentile: Double,
    ): Long {
        val rank = ceil(percentile * sortedValues.size).toInt().coerceAtLeast(1)
        return sortedValues[rank - 1]
    }
}
