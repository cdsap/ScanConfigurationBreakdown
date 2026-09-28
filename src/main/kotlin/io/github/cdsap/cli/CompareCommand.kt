package io.github.cdsap.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.double
import io.github.cdsap.compare.ComparisonReport
import io.github.cdsap.compare.Style
import io.github.cdsap.compare.Variant
import io.github.cdsap.compare.VariantComparison
import io.github.cdsap.compare.VariantRun
import io.github.cdsap.scandata.ScanDataAuthException
import io.github.cdsap.scandata.ScanDataClient
import kotlinx.coroutines.runBlocking

/**
 * Reads variant A, then variant B, then reports the difference.
 *
 * Sequential on purpose: the two variants are read one after the other so a slow instance or a
 * rate limit shows up against the variant it belongs to rather than being interleaved.
 */
class CompareCommand : CliktCommand(name = "compare") {
    override fun help(context: Context) =
        "Read two tagged variants in turn and report how their configuration timings differ."

    private val dv by DevelocityOptions()

    private val variantA by option("--variant-a", help = "Tag identifying variant A. Repeat for several tags.")
        .multiple(required = true)

    private val variantB by option("--variant-b", help = "Tag identifying variant B. Repeat for several tags.")
        .multiple(required = true)

    private val labelA by option("--label-a", help = "Name for variant A in the report. Defaults to its tags.")

    private val labelB by option("--label-b", help = "Name for variant B in the report. Defaults to its tags.")

    private val color by option("--color", help = "Force ANSI colour even when stdout is not a terminal.").flag()

    private val minPercent by option(
        "--min-percent",
        help = "Minimum relative change for a metric to be highlighted.",
    ).double().default(5.0)

    private val minDelta by option(
        "--min-delta",
        help = "Minimum absolute change in ms for a metric to be highlighted.",
    ).double().default(50.0)

    override fun run() =
        runBlocking {
            val config = dv.config()
            val pipeline = dv.pipeline()
            val a = Variant(variantA, labelA ?: variantA.joinToString("+"))
            val b = Variant(variantB, labelB ?: variantB.joinToString("+"))

            ScanDataClient(config).use { client ->
                echo("Variant A: reading builds ${dv.describe(a.tags)} from ${config.host}")
                val runA = VariantRun(a, pipeline.run(a.tags, client))
                echo("  ${runA.results.size} builds, ${runA.withConfiguration.size} with configuration data")

                echo("Variant B: reading builds ${dv.describe(b.tags)} from ${config.host}")
                val runB = VariantRun(b, pipeline.run(b.tags, client))
                echo("  ${runB.results.size} builds, ${runB.withConfiguration.size} with configuration data")
                echo("")

                if ((runA.results + runB.results).needsSession()) {
                    echo(ScanDataAuthException.hint(config), err = true)
                    echo("")
                }

                if (runA.withConfiguration.isEmpty() || runB.withConfiguration.isEmpty()) {
                    echo("Nothing to compare: a variant returned no configuration data.", err = true)
                    listOf(runA, runB).filter { it.withConfiguration.isEmpty() }.forEach {
                        echo("  ${it.variant.label} matched ${it.results.size} builds.", err = true)
                    }
                    return@use
                }

                echo(ComparisonReport(VariantComparison(runA, runB, minPercent, minDelta), Style.detect(color)).render())
            }
        }
}
