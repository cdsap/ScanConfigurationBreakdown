package io.github.cdsap.compare

import io.github.cdsap.scandata.BuildPerformanceConfiguration

/**
 * One side of a comparison: the builds carrying [tags].
 *
 * A variant is only as meaningful as its tag — the tag has to identify builds that differ from
 * the other variant in the one way you are measuring, and agree in every other way.
 */
data class Variant(
    val tags: List<String>,
    val label: String = tags.joinToString("+"),
)

/** A [Variant] after its builds have been fetched and their scan data read. */
data class VariantRun(
    val variant: Variant,
    val results: List<BuildPerformanceConfiguration>,
) {
    val withConfiguration = results.mapNotNull { it.configuration }

    val failures = results.count { it.failure != null }

    val projects: List<String> = results.map { it.build.projectName }.distinct().sorted()

    /** Configuration cache outcome mix, e.g. `{miss=48, hit=2}`. */
    val configurationCacheOutcomes: Map<String, Int> =
        withConfiguration
            .map { it.configurationCaching?.outcome ?: "none" }
            .groupingBy { it }
            .eachCount()

    fun values(metric: ConfigurationMetric): List<Long> = withConfiguration.mapNotNull(metric.extract)
}
