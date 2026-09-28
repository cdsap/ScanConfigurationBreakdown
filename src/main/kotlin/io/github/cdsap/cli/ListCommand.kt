package io.github.cdsap.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import io.github.cdsap.config.DevelocityConfig
import io.github.cdsap.scandata.BuildPerformanceConfiguration
import io.github.cdsap.scandata.ScanDataAuthException
import io.github.cdsap.scandata.ScanDataClient
import kotlinx.coroutines.runBlocking

/**
 * One CSV row per build, on stdout.
 *
 * Everything else — progress, the builds that produced no row, the session hint — goes to stderr,
 * so `reader list … > builds.csv` writes a file a spreadsheet or `csv.DictReader` reads as-is.
 */
class ListCommand : CliktCommand(name = "list") {
    override fun help(context: Context) = "List the configuration timings of the builds carrying a tag."

    private val dv by DevelocityOptions()

    private val tags by option("--tag", help = "Tag to filter builds by. Repeat for several tags.")
        .multiple(required = true)

    override fun run() =
        runBlocking {
            val config = dv.config()
            echo("Fetching up to ${dv.maxBuilds} builds ${dv.describe(tags)} from ${config.host}", err = true)

            val results = ScanDataClient(config).use { dv.pipeline().run(tags, it) }
            echo("Found ${results.size} builds", err = true)

            echo(CSV_HEADER)
            results.forEach { result -> csvRow(result)?.let { echo(it) } }

            report(results, config)
        }

    /** `null` for a build the instance had no configuration data for — those are reported, not rowed. */
    private fun csvRow(result: BuildPerformanceConfiguration): String? {
        val summary = result.configuration?.serialConfigurationSummary ?: return null
        return listOf(
            result.build.id,
            "\"${result.build.projectName}\"",
            summary.totalConfigurationTime,
            summary.scriptCompilationTime,
            summary.modelConfigurationTime,
            summary.configurationResolutionTime,
            summary.pluginBuildingTime,
            summary.taskGraphCalculationTime,
        ).joinToString(",")
    }

    private fun report(
        results: List<BuildPerformanceConfiguration>,
        config: DevelocityConfig,
    ) {
        val missing = results.filter { it.configuration?.serialConfigurationSummary == null }
        echo("", err = true)
        echo("${results.size - missing.size}/${results.size} builds returned configuration data", err = true)
        missing.forEach { echo("  ${it.build.id}: ${it.failure?.message ?: "no configuration data"}", err = true) }
        if (results.needsSession()) echo("\n${ScanDataAuthException.hint(config)}", err = true)
    }

    companion object {
        private const val CSV_HEADER =
            "buildId,project,totalConfiguration,scriptCompilation,modelConfiguration," +
                "configurationResolution,pluginBuilding,taskGraphCalculation"
    }
}
