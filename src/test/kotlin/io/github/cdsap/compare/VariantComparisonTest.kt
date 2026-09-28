package io.github.cdsap.compare

import io.github.cdsap.scandata.BuildPerformanceConfiguration
import io.github.cdsap.scandata.ConfigurationCaching
import io.github.cdsap.scandata.PerformanceConfiguration
import io.github.cdsap.scandata.SerialConfigurationSummary
import io.github.cdsap.geapi.client.model.Environment
import io.github.cdsap.geapi.client.model.ScanWithAttributes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VariantComparisonTest {
    @Test
    fun `median of an odd sample is the middle value`() {
        assertEquals(20.0, MetricStats.median(listOf(10L, 20L, 90L)))
    }

    @Test
    fun `median of an even sample averages the middle pair`() {
        assertEquals(25.0, MetricStats.median(listOf(10L, 20L, 30L, 900L)))
    }

    /** The median is the point of using it: one outlier must not drag the comparison. */
    @Test
    fun `median resists an outlier that would move the mean`() {
        val stats = checkNotNull(MetricStats.of(listOf(100L, 100L, 100L, 100L, 10_000L)))

        assertEquals(100.0, stats.median)
        assertEquals(2080.0, stats.mean)
        assertEquals(10_000, stats.max)
    }

    @Test
    fun `delta and percent are measured from A to B`() {
        val comparison = comparisonOf(aTotals = listOf(1000L), bTotals = listOf(800L))
        val total = comparison.metrics.single { it.metric == ConfigurationMetric.TOTAL_CONFIGURATION }

        assertEquals(-200.0, total.delta)
        assertEquals(-20.0, total.deltaPercent)
        assertTrue(total.improved)
    }

    @Test
    fun `a metric must cross both thresholds to be highlighted`() {
        // 20% but only 20 ms: below the absolute floor.
        val small = comparisonOf(aTotals = listOf(100L), bTotals = listOf(80L))
        assertTrue(small.highlights().isEmpty())
        assertEquals(ConfigurationMetric.TOTAL_CONFIGURATION, small.movements.first().metric)

        // 20% and 200 ms: crosses both.
        val large = comparisonOf(aTotals = listOf(1000L), bTotals = listOf(800L))
        assertEquals(ConfigurationMetric.TOTAL_CONFIGURATION, large.highlights().single().metric)
    }

    @Test
    fun `a differing configuration cache outcome is called out as a confounder`() {
        val comparison =
            comparisonOf(
                aTotals = listOf(1000L),
                bTotals = listOf(100L),
                aOutcome = "miss",
                bOutcome = "hit",
            )

        assertTrue(comparison.caveats().any { it.contains("Configuration cache outcome differs") })
    }

    @Test
    fun `variants covering different projects are called out`() {
        val comparison =
            comparisonOf(
                aTotals = listOf(1000L),
                bTotals = listOf(900L),
                aProject = "app",
                bProject = "library",
            )

        assertTrue(comparison.caveats().any { it.contains("not measuring the same build") })
    }

    @Test
    fun `a thin sample is called out`() {
        val comparison = comparisonOf(aTotals = listOf(1000L), bTotals = listOf(900L))

        assertTrue(comparison.caveats().any { it.contains("a median over this few builds is noise") })
    }

    private fun comparisonOf(
        aTotals: List<Long>,
        bTotals: List<Long>,
        aOutcome: String = "miss",
        bOutcome: String = "miss",
        aProject: String = "app",
        bProject: String = "app",
    ) = VariantComparison(
        a = run("A", aTotals, aOutcome, aProject),
        b = run("B", bTotals, bOutcome, bProject),
    )

    private fun run(
        label: String,
        totals: List<Long>,
        outcome: String,
        project: String,
    ) = VariantRun(
        variant = Variant(listOf(label), label),
        results =
            totals.mapIndexed { index, total ->
                BuildPerformanceConfiguration(
                    build = scan("$label$index", project),
                    configuration =
                        PerformanceConfiguration(
                            serialConfigurationSummary = SerialConfigurationSummary(totalConfigurationTime = total),
                            configurationCaching = ConfigurationCaching(outcome = outcome),
                        ),
                )
            },
    )

    private fun scan(
        id: String,
        project: String,
    ) = ScanWithAttributes(
        id = id,
        projectName = project,
        requestedTasksGoals = emptyArray(),
        tags = emptyArray(),
        hasFailed = false,
        environment = Environment(username = "tester", numberOfCpuCores = "8"),
        buildDuration = 0,
        buildTool = "gradle",
        buildStartTime = 0,
        values = emptyArray(),
    )
}
