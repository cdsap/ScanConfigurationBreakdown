package io.github.cdsap.builds

import io.github.cdsap.config.DevelocityConfig
import io.github.cdsap.geapi.client.domain.GetBuildsWithAttributes
import io.github.cdsap.geapi.client.domain.impl.GetBuildsFromQueryWithAttributesRequest
import io.github.cdsap.geapi.client.domain.impl.GetBuildsWithAttributesRequest
import io.github.cdsap.geapi.client.model.Filter
import io.github.cdsap.geapi.client.model.ScanWithAttributes
import io.github.cdsap.geapi.client.network.GEClient
import io.github.cdsap.geapi.client.repository.impl.GradleRepositoryImpl

/**
 * Step one of the pipeline: ask the Develocity API for the builds matching a set of tags.
 *
 * Thin wrapper over geapi-data, which handles the paging past the API's 1000-build ceiling and
 * the fan-out to `/gradle-attributes` for each scan.
 */
class BuildFetcher(config: DevelocityConfig) {
    private val repository =
        GradleRepositoryImpl(
            GEClient(
                token = requireNotNull(config.accessKey) {
                    "An access key is required to list builds. Pass --access-key, set " +
                        "${DevelocityConfig.ACCESS_KEY_ENV}, or add an entry for ${config.host} to " +
                        "~/.gradle/develocity/keys.properties."
                },
                geServer = config.serverUrl,
            ),
        )

    /**
     * Builds carrying [tags].
     *
     * With [serverSideQuery] on (the default), the tags go to the API as a `query=` term and
     * [maxBuilds] means *matching* builds. With it off, geapi-data pulls the [maxBuilds] most
     * recent builds on the instance and filters tags locally — so on a busy server a rare tag can
     * come back empty simply because no build carrying it fell inside that window. Requires
     * Develocity 2023.3+.
     *
     * Several [tags] are OR-ed unless [exclusiveTags] is set, which requires all of them.
     *
     * [requestedTasks] narrows to builds that were *asked* to run those tasks, all of them. The
     * API's query takes a single `requested:` term, so the first task filters server-side alongside
     * the tags and any further ones are matched here against each scan's requested tasks. That
     * makes [maxBuilds] a ceiling on the builds matching the tags and the *first* task, which a
     * second task can then take below it.
     */
    suspend fun buildsWithTags(
        tags: List<String>,
        maxBuilds: Int = 50,
        project: String? = null,
        requestedTasks: List<String> = emptyList(),
        includeFailedBuilds: Boolean = true,
        concurrentCalls: Int = 10,
        exclusiveTags: Boolean = false,
        serverSideQuery: Boolean = true,
    ): List<ScanWithAttributes> {
        val filter =
            Filter(
                maxBuilds = maxBuilds,
                tags = tags,
                project = project,
                requestedTask = requestedTasks.firstOrNull(),
                includeFailedBuilds = includeFailedBuilds,
                concurrentCalls = concurrentCalls,
                exclusiveTags = exclusiveTags,
            )
        val request: GetBuildsWithAttributes =
            if (serverSideQuery) {
                GetBuildsFromQueryWithAttributesRequest(repository)
            } else {
                GetBuildsWithAttributesRequest(repository)
            }
        val builds = request.get(filter)
        val extraTasks = requestedTasks.drop(1)
        if (extraTasks.isEmpty()) return builds
        return builds.filter { build -> extraTasks.all { it in build.requestedTasksGoals } }
    }
}
