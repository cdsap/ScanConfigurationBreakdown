package io.github.cdsap.compare

import java.util.Locale
import kotlin.math.abs

/** ANSI styling, collapsing to plain text when the output is not a terminal. */
class Style(private val enabled: Boolean) {
    fun green(text: String) = wrap(text, "32")

    fun red(text: String) = wrap(text, "31")

    fun bold(text: String) = wrap(text, "1")

    fun dim(text: String) = wrap(text, "2")

    private fun wrap(
        text: String,
        code: String,
    ) = if (enabled) "$ESC[${code}m$text$ESC[0m" else text

    companion object {
        private const val ESC = "\u001B"

        /** Gradle's `run` task gives the process no console, so this is off unless forced. */
        fun detect(force: Boolean = false) = Style(force || System.console() != null)
    }
}

/** Renders a [VariantComparison] as the terminal report. */
class ComparisonReport(
    private val comparison: VariantComparison,
    private val style: Style = Style.detect(),
) {
    fun render(): String =
        buildString {
            appendLine(style.bold("Comparison"))
            appendLine("  A  ${comparison.a.variant.label}  ${describe(comparison.a)}")
            appendLine("  B  ${comparison.b.variant.label}  ${describe(comparison.b)}")
            appendLine()

            appendTable()

            val highlights = comparison.highlights()
            appendLine()
            appendLine(style.bold("Highlights"))
            if (highlights.isEmpty()) {
                appendLine(
                    "  Nothing crossed the ${format(comparison.minPercent, 1)}% / " +
                        "${format(comparison.minDelta, 1)} ms threshold.",
                )
                comparison.movements.firstOrNull()?.let {
                    appendLine("  Largest movement: ${highlight(it)}")
                }
            } else {
                highlights.forEach { appendLine("  ${highlight(it)}") }
            }

            val caveats = comparison.caveats()
            if (caveats.isNotEmpty()) {
                appendLine()
                appendLine(style.bold("Read with care"))
                caveats.forEach { appendLine("  ! $it") }
            }
        }

    private fun describe(run: VariantRun): String {
        val outcomes =
            run.configurationCacheOutcomes.entries
                .sortedByDescending { it.value }
                .joinToString(", ") { "${it.value} ${it.key}" }
        return style.dim(
            "${run.withConfiguration.size}/${run.results.size} builds with data" +
                if (outcomes.isEmpty()) "" else "; config cache: $outcomes",
        )
    }

    private fun StringBuilder.appendTable() {
        val headers = listOf("Metric", "A median", "B median", "Delta")
        val rows =
            comparison.metrics.map { metric ->
                listOf(
                    metric.metric.label,
                    metric.a?.let { ms(it.median) } ?: "-",
                    metric.b?.let { ms(it.median) } ?: "-",
                    deltaText(metric),
                )
            }
        val widths =
            headers.indices.map { column ->
                (rows.map { it[column].length } + headers[column].length).max()
            }

        fun layout(cells: List<String>) =
            cells
                .mapIndexed { i, cell -> if (i == 0) cell.padEnd(widths[i]) else cell.padStart(widths[i]) }
                .joinToString("  ")

        appendLine(style.bold(layout(headers)))
        appendLine("-".repeat(widths.sum() + 2 * (widths.size - 1)))
        rows.forEachIndexed { index, row ->
            // The delta column is the point of the table, so colour the whole row by its direction.
            val metric = comparison.metrics[index]
            val line = layout(row)
            appendLine(
                when {
                    metric.deltaPercent == null -> line
                    metric.improved -> style.green(line)
                    else -> style.red(line)
                },
            )
        }
    }

    private fun deltaText(metric: MetricComparison): String {
        val delta = metric.delta ?: return "-"
        val sign = if (delta > 0) "+" else ""
        val percent = metric.deltaPercent?.let { " ($sign${format(it, PERCENT_DECIMALS)}%)" } ?: ""
        return "$sign${ms(delta)}$percent"
    }

    private fun highlight(metric: MetricComparison): String {
        val percent = metric.deltaPercent ?: return metric.metric.label
        val direction = if (metric.improved) "faster" else "slower"
        val marker = if (metric.improved) style.green("v") else style.red("^")
        val detail = style.dim("${ms(metric.a!!.median)} -> ${ms(metric.b!!.median)}")
        return "$marker ${metric.metric.label}: ${format(abs(percent), PERCENT_DECIMALS)}% $direction in B  $detail"
    }

    private fun ms(value: Double) = "${String.format(Locale.US, "%,.0f", value)} ms"

    private fun format(
        value: Double,
        decimals: Int,
    ) = String.format(Locale.US, "%.${decimals}f", value)

    private companion object {
        /**
         * Two decimals so a percentage never rounds to the threshold it failed to cross --
         * 4.96% displayed as "5.0%" next to "nothing crossed 5.0%" reads as a bug.
         */
        const val PERCENT_DECIMALS = 2
    }
}
