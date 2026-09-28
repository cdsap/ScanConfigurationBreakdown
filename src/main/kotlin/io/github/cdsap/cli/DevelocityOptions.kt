package io.github.cdsap.cli

import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import io.github.cdsap.Pipeline
import io.github.cdsap.config.DevelocityConfig
import io.github.cdsap.scandata.BuildPerformanceConfiguration
import io.github.cdsap.scandata.ScanDataAuthException

/**
 * Everything both commands need: which instance, which credentials, and which builds.
 *
 * Shared as an [OptionGroup] so `list` and `compare` present the same flags in the same order,
 * and so the two-stage read is configured in exactly one place ([pipeline]).
 */
class DevelocityOptions : OptionGroup("Develocity options") {
    val server by option("--server", help = "Develocity server URL, e.g. https://ge.solutions-team.gradle.com")
        .required()

    val accessKey by option(
        "--access-key",
        help = "Access key for listing builds. Falls back to ${DevelocityConfig.ACCESS_KEY_ENV}, " +
            "then ~/.gradle/develocity/keys.properties.",
    )

    val cookie by option(
        "--cookie",
        help = "Browser session cookie, for an instance that keeps /scan-data behind SSO. " +
            "Falls back to ${DevelocityConfig.SESSION_COOKIE_ENV}.",
    )

    val project by option("--project", help = "Restrict to a single project name.")

    val tasks by option(
        "--task",
        help = "Restrict to builds that requested this task, e.g. :app:assembleDebug. Repeat to " +
            "require several; a build must have requested all of them, most selective first.",
    ).multiple()

    val maxBuilds by option("--max-builds", help = "How many matching builds to fetch.").int().default(50)

    val concurrency by option("--concurrency", help = "Concurrent scan-data requests.").int().default(10)

    val allTags by option("--all-tags", help = "Require every tag rather than any of them.").flag()

    val clientSideFilter by option(
        "--client-side-filter",
        help = "Pull the newest --max-builds builds and filter tags locally, instead of querying the " +
            "API by tag. Only needed against Develocity older than 2023.3.",
    ).flag()

    fun config() = DevelocityConfig.resolve(server, accessKey, cookie)

    fun pipeline() =
        Pipeline(
            config = config(),
            maxBuilds = maxBuilds,
            concurrency = concurrency,
            project = project,
            requestedTasks = tasks,
            exclusiveTags = allTags,
            serverSideQuery = !clientSideFilter,
        )

    /** How the current filters read in a progress line: `tagged CI, main requesting assemble`. */
    fun describe(tags: List<String>) =
        buildString {
            append("tagged ${tags.joinToString(", ")}")
            if (tasks.isNotEmpty()) append(" requesting ${tasks.joinToString(", ")}")
            project?.let { append(" in $it") }
        }
}

/** True when any scan-data request was turned away for want of a logged-in session. */
internal fun List<BuildPerformanceConfiguration>.needsSession() = any { it.failure is ScanDataAuthException }
