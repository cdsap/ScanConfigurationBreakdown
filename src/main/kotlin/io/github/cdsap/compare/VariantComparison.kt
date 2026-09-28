package io.github.cdsap.compare

import kotlin.math.abs

/** Distribution of one metric within one variant. */
data class MetricStats(
    val n: Int,
    val median: Double,
    val mean: Double,
    val min: Long,
    val max: Long,
) {
    companion object {
        fun of(values: List<Long>): MetricStats? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            return MetricStats(
                n = sorted.size,
                median = median(sorted),
                mean = sorted.sum().toDouble() / sorted.size,
                min = sorted.first(),
                max = sorted.last(),
            )
        }

        /** [sorted] must already be ascending. */
        fun median(sorted: List<Long>): Double {
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) {
                sorted[middle].toDouble()
            } else {
                (sorted[middle - 1] + sorted[middle]) / 2.0
            }
        }
    }
}

/**
 * One metric, both variants.
 *
 * Compared on the median rather than the mean: a single cold or contended build would drag a
 * mean around, and build timings are routinely that skewed.
 */
data class MetricComparison(
    val metric: ConfigurationMetric,
    val a: MetricStats?,
    val b: MetricStats?,
) {
    /** Positive means variant B took longer. */
    val delta: Double? = if (a != null && b != null) b.median - a.median else null

    val deltaPercent: Double? =
        if (a != null && b != null && a.median > 0.0) (b.median - a.median) / a.median * 100 else null

    val improved: Boolean get() = (delta ?: 0.0) < 0
}

/**
 * Variant A against variant B, plus the reasons the comparison might not mean what it appears to.
 */
class VariantComparison(
    val a: VariantRun,
    val b: VariantRun,
    /** A metric must cross both thresholds to be highlighted. */
    val minPercent: Double = 5.0,
    val minDelta: Double = 50.0,
) {
    val metrics: List<MetricComparison> =
        ConfigurationMetric.ordered.map { metric ->
            MetricComparison(metric, MetricStats.of(a.values(metric)), MetricStats.of(b.values(metric)))
        }

    /** Every metric that moved at all, largest relative movement first. */
    val movements: List<MetricComparison> =
        metrics
            .filter { it.deltaPercent != null }
            .sortedByDescending { abs(it.deltaPercent ?: 0.0) }

    /**
     * Metrics that moved enough to be worth reading. The absolute floor keeps small metrics
     * (task graph calculation is often a couple of hundred ms) from showing up as large
     * percentages of nothing.
     */
    fun highlights(): List<MetricComparison> =
        movements.filter { abs(it.deltaPercent!!) >= minPercent && abs(it.delta!!) >= minDelta }

    /**
     * Confounders that make the medians above misleading. These are not warnings about the tool —
     * they are reasons a real difference in the numbers may not be caused by the thing you changed.
     */
    fun caveats(): List<String> =
        buildList {
            listOf(a, b).forEach { run ->
                if (run.withConfiguration.isEmpty()) {
                    add("${run.variant.label}: no build returned configuration data — nothing to compare.")
                } else if (run.withConfiguration.size < 5) {
                    add(
                        "${run.variant.label}: only ${run.withConfiguration.size} build(s) with data — " +
                            "a median over this few builds is noise.",
                    )
                }
                if (run.projects.size > 1) {
                    add(
                        "${run.variant.label}: spans ${run.projects.size} projects " +
                            "(${run.projects.take(3).joinToString(", ")}${if (run.projects.size > 3) ", …" else ""}) — " +
                            "medians mix unlike builds.",
                    )
                }
                if (run.failures > 0) {
                    add("${run.variant.label}: ${run.failures} scan-data request(s) failed and are excluded.")
                }
            }

            if (a.projects.isNotEmpty() && b.projects.isNotEmpty() && a.projects != b.projects) {
                add(
                    "Variants cover different projects (${a.projects.joinToString(", ")} vs " +
                        "${b.projects.joinToString(", ")}) — they are not measuring the same build.",
                )
            }

            // A configuration-cache hit skips most of the configuration phase, so an outcome mix
            // that differs between variants explains a large delta on its own.
            val aDominant = a.configurationCacheOutcomes.maxByOrNull { it.value }?.key
            val bDominant = b.configurationCacheOutcomes.maxByOrNull { it.value }?.key
            if (aDominant != null && bDominant != null && aDominant != bDominant) {
                add(
                    "Configuration cache outcome differs (${a.variant.label} mostly $aDominant, " +
                        "${b.variant.label} mostly $bDominant) — that alone moves configuration time.",
                )
            }
        }
}
