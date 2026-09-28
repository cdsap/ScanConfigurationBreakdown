package io.github.cdsap

import io.github.cdsap.builds.BuildFetcher
import io.github.cdsap.config.DevelocityConfig
import io.github.cdsap.scandata.BuildPerformanceConfiguration
import io.github.cdsap.scandata.PerformanceConfigurationFetcher
import io.github.cdsap.scandata.ScanDataClient

/**
 * The two-stage read: builds matching a set of tags, then the scan data for each of them.
 *
 * Holds the options shared by every command so a caller only supplies the tags, which is what
 * distinguishes one variant from another.
 */
class Pipeline(
    config: DevelocityConfig,
    private val maxBuilds: Int,
    private val concurrency: Int,
    private val project: String?,
    private val requestedTasks: List<String>,
    private val exclusiveTags: Boolean,
    private val serverSideQuery: Boolean,
) {
    private val buildFetcher = BuildFetcher(config)

    suspend fun run(
        tags: List<String>,
        client: ScanDataClient,
    ): List<BuildPerformanceConfiguration> {
        val builds =
            buildFetcher.buildsWithTags(
                tags = tags,
                maxBuilds = maxBuilds,
                project = project,
                requestedTasks = requestedTasks,
                concurrentCalls = concurrency,
                exclusiveTags = exclusiveTags,
                serverSideQuery = serverSideQuery,
            )
        if (builds.isEmpty()) return emptyList()
        return PerformanceConfigurationFetcher(client, concurrency).fetch(builds)
    }
}
