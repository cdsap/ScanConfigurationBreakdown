package io.github.cdsap.scandata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Envelope every `/scan-data/...` endpoint wraps its payload in.
 *
 * [status] is `COMPLETED` once the server has finished deriving the data; anything else means
 * [data] may be absent.
 */
@Serializable
data class ScanDataResponse<T>(
    val status: String,
    val data: T? = null,
) {
    val isCompleted: Boolean get() = status == "COMPLETED"
}

/**
 * `/scan-data/gradle/{id}/performance-configuration` — the configuration-phase breakdown behind
 * the Build Scan "Performance > Configuration" tab.
 *
 * Deliberately partial. The endpoint also returns a `codeUnitApplications` array with one entry
 * per plugin/script application (thousands of entries, most of the several-MB payload); leaving
 * it unmapped lets the parser skip it instead of allocating it. Add it here if you need it.
 */
@Serializable
data class PerformanceConfiguration(
    val hasDetailedInfo: Boolean = false,
    val serialConfigurationSummary: SerialConfigurationSummary? = null,
    val scriptCompilationDetails: ScriptCompilationDetails? = null,
    val configurationCaching: ConfigurationCaching? = null,
    val taskTotals: TaskTotals? = null,
    val byProjects: List<ProjectConfiguration> = emptyList(),
    val byCodeUnits: List<CodeUnitSummary> = emptyList(),
)

/** Configuration-phase timings, in milliseconds. */
@Serializable
data class SerialConfigurationSummary(
    val scriptCompilationTime: Long = 0,
    val pluginBuildingTime: Long = 0,
    val modelConfigurationTime: Long = 0,
    val taskGraphCalculationTime: Long = 0,
    val configurationCachingTime: Long = 0,
    val configurationResolutionTime: Long = 0,
    val totalConfigurationTime: Long = 0,
)

@Serializable
data class ScriptCompilationDetails(
    val totalScripts: Int = 0,
    val totalCompiledScripts: Int = 0,
    val scriptCompilations: List<ScriptCompilation> = emptyList(),
)

@Serializable
data class ScriptCompilation(
    val type: String? = null,
    val displayName: String? = null,
    val duration: Long = 0,
)

@Serializable
data class ConfigurationCaching(
    /** `hit`, `miss`, or absent when the configuration cache was not in play. */
    val outcome: String? = null,
    val invalidationReason: String? = null,
    val formattedSize: String? = null,
    val store: ConfigurationCachePhase? = null,
    val load: ConfigurationCachePhase? = null,
)

@Serializable
data class ConfigurationCachePhase(
    val duration: Long = 0,
    val hasFailed: Boolean = false,
    val originBuildInvocationId: String? = null,
)

@Serializable
data class TaskTotals(
    val total: Int = 0,
    val createdImmediately: Int = 0,
    val createdDuringConfiguration: Int = 0,
    val createdDuringTaskGraphCalculation: Int = 0,
    val createdDuringTaskExecution: Int = 0,
    val notCreated: Int = 0,
)

@Serializable
data class ProjectConfiguration(
    val projectPath: String,
    val ownTime: Long = 0,
    val totalTime: Long = 0,
    val earlyTasksCount: Int = 0,
    val codeUnitGroups: List<CodeUnitSummary> = emptyList(),
)

/**
 * A plugin, script, or other code unit, aggregated either across the build ([PerformanceConfiguration.byCodeUnits])
 * or within one project ([ProjectConfiguration.codeUnitGroups]).
 */
@Serializable
data class CodeUnitSummary(
    /** e.g. `plugin_id`, `project_script`, `other_script`. */
    val type: String? = null,
    val displayName: String? = null,
    val label: String? = null,
    @SerialName("ownTime") private val groupOwnTime: Long? = null,
    @SerialName("totalTime") private val groupTotalTime: Long? = null,
    @SerialName("sumOwnTime") private val sumOwnTime: Long? = null,
    @SerialName("sumTotalTime") private val sumTotalTime: Long? = null,
    val applicationCount: Int = 0,
    val projectCount: Int = 0,
    val earlyTasksCount: Int = 0,
) {
    /**
     * Time spent in this code unit itself. The endpoint names the field `sumOwnTime` in the
     * build-wide list and `ownTime` in the per-project list; both land here.
     */
    val ownTime: Long get() = sumOwnTime ?: groupOwnTime ?: 0

    /** [ownTime] plus time in the code units this one applied. */
    val totalTime: Long get() = sumTotalTime ?: groupTotalTime ?: 0
}
