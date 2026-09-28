package io.github.cdsap.compare

import io.github.cdsap.scandata.PerformanceConfiguration

/**
 * The configuration-phase timings compared across variants, all in milliseconds.
 *
 * [TOTAL_CONFIGURATION] is the headline; the rest are its constituents, so a regression in the
 * total can be attributed to whichever part moved with it.
 */
enum class ConfigurationMetric(
    val label: String,
    val extract: (PerformanceConfiguration) -> Long?,
) {
    TOTAL_CONFIGURATION("Total configuration", { it.serialConfigurationSummary?.totalConfigurationTime }),
    SCRIPT_COMPILATION("Script compilation", { it.serialConfigurationSummary?.scriptCompilationTime }),
    PLUGIN_BUILDING("Plugin building", { it.serialConfigurationSummary?.pluginBuildingTime }),
    MODEL_CONFIGURATION("Model configuration", { it.serialConfigurationSummary?.modelConfigurationTime }),
    CONFIGURATION_RESOLUTION("Configuration resolution", { it.serialConfigurationSummary?.configurationResolutionTime }),
    TASK_GRAPH_CALCULATION("Task graph calculation", { it.serialConfigurationSummary?.taskGraphCalculationTime }),
    CONFIGURATION_CACHING("Configuration caching", { it.serialConfigurationSummary?.configurationCachingTime }),
    ;

    companion object {
        /** [TOTAL_CONFIGURATION] first, then its parts — the order the report renders in. */
        val ordered: List<ConfigurationMetric> = entries
    }
}
