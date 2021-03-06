package org.jetbrains.gradle.benchmarks

import kotlin.math.*

class ReportBenchmarksStatistics(values: DoubleArray) {
    val values = values.sortedArray()
    val size: Int get() = values.size

    fun valueAt(quantile: Double): Double {
        if (quantile < 0.0 || quantile > 1.0 || quantile.isNaN())
            throw IllegalArgumentException("$quantile is not in [0..1]")

        if (size == 0) return 0.0

        val pos = quantile * (values.size + 1)
        val index = pos.toInt()
        return when {
            index < 1 -> values[0]
            index >= values.size -> values[values.size - 1]
            else -> {
                val lower = values[index - 1]
                val upper = values[index]
                lower + (pos - floor(pos)) * (upper - lower)
            }
        }
    }

    fun median() = valueAt(0.5)

    fun min(): Double = when (size) {
        0 -> 0.0
        else -> values[0]
    }

    fun max(): Double = when (size) {
        0 -> 0.0
        else -> values[values.lastIndex]
    }

    fun mean(): Double = values.sumByDouble { it } / size

    fun standardDeviation(): Double {
        // two-pass algorithm for variance, avoids numeric overflow
        if (size <= 1)
            return 0.0

        val mean = mean()
        val sum = values.sumByDouble { (it - mean).let { it * it } }
        val variance = sum / values.lastIndex
        return sqrt(variance)
    }

    companion object {
        fun createResult(benchmarkName: String, samples: DoubleArray): ReportBenchmarkResult {
            val statistics = ReportBenchmarksStatistics(samples)
            val score = statistics.mean()
            val errorMargin = 1.96 * (statistics.standardDeviation() / sqrt(samples.size.toDouble()))

            val d = (4 - log10(score).toInt()).coerceAtLeast(0) // display 4 significant digits

            println("  ~ ${score.format(d)} ops/sec ±${(errorMargin / score * 100).format(2)}%")
            val minText = statistics.min().format(d)
            val meanText = statistics.mean().format(d)
            val maxText = statistics.max().format(d)
            val devText = statistics.standardDeviation().format(d)
            println("    min: $minText, avg: $meanText, max: $maxText, stddev: $devText")

            // These quantiles are inverted, because we are interested in ops/sec and the higher the better
            // so we need minimum speed at which 90% of samples run
            val percentiles = listOf(0.0, 0.25, 0.5, 0.75, 0.90, 0.99, 0.999, 0.9999, 1.0).associate {
                (it * 100) to statistics.valueAt(it)
            }
            return ReportBenchmarkResult(
                benchmarkName,
                score,
                errorMargin,
                (score - errorMargin) to (score + errorMargin),
                percentiles,
                samples
            )
        }
    }
}

expect fun Double.format(precision: Int): String