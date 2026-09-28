package io.github.cdsap.scandata

import io.github.cdsap.geapi.client.model.ScanWithAttributes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** One build, plus whatever the scan-data endpoint had to say about it. */
data class BuildPerformanceConfiguration(
    val build: ScanWithAttributes,
    val configuration: PerformanceConfiguration?,
    val failure: Throwable? = null,
)

/**
 * Step two of the pipeline: one `/scan-data/.../performance-configuration` request per build,
 * capped at [concurrentCalls] in flight.
 *
 * A failing build does not sink the batch — its error is carried in
 * [BuildPerformanceConfiguration.failure] so the caller can report partial results.
 */
class PerformanceConfigurationFetcher(
    private val client: ScanDataClient,
    private val concurrentCalls: Int = 10,
) {
    suspend fun fetch(builds: List<ScanWithAttributes>): List<BuildPerformanceConfiguration> =
        coroutineScope {
            val permits = Semaphore(concurrentCalls)
            builds
                .map { build ->
                    async {
                        permits.withPermit {
                            try {
                                BuildPerformanceConfiguration(
                                    build = build,
                                    configuration = client.performanceConfiguration(build.id, build.buildTool),
                                )
                            } catch (e: Exception) {
                                BuildPerformanceConfiguration(build = build, configuration = null, failure = e)
                            }
                        }
                    }
                }.awaitAll()
        }
}
