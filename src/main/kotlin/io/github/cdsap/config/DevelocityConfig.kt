package io.github.cdsap.config

import java.io.File
import java.net.URI
import java.util.Properties

/**
 * Where to talk to, and with which credentials.
 *
 * Two endpoints, two different credentials. `/api/builds`, used to list builds, takes the
 * [accessKey] as a bearer token. The `/scan-data` endpoints backing the Build Scan UI do not:
 * depending on the instance's access control they are either readable anonymously (as on
 * `ge.solutions-team.gradle.com`) or behind the browser's SSO session, which is what
 * [sessionCookie] carries. The access key is sent to both in case an instance wants it; a
 * `/scan-data` request that comes back as a redirect to a login page is reported as
 * [io.github.cdsap.scandata.ScanDataAuthException] rather than as a parse failure.
 *
 * Both credentials resolve the same way — flag, then system property, then environment variable,
 * then a per-host entry in a properties file under the Gradle home. The file is the one to reach
 * for when a credential is long-lived (the access key); the flag and the environment variable suit
 * the one that expires every few hours (the cookie).
 */
data class DevelocityConfig(
    val serverUrl: String,
    val accessKey: String?,
    val sessionCookie: String? = null,
) {
    val host: String = URI(serverUrl).host

    companion object {
        const val ACCESS_KEY_ENV = "DEVELOCITY_ACCESS_KEY"
        const val SESSION_COOKIE_ENV = "DEVELOCITY_SESSION_COOKIE"

        /** `-Ddevelocity.accessKey=…`, reachable through `JAVA_OPTS` when running the fat binary. */
        const val ACCESS_KEY_PROPERTY = "develocity.accessKey"
        const val SESSION_COOKIE_PROPERTY = "develocity.sessionCookie"

        /** `~/.gradle/develocity/keys.properties`, as written by `./gradlew provisionDevelocityAccessKey`. */
        private val KEY_FILES =
            listOf(
                ".gradle/develocity/keys.properties",
                ".gradle/enterprise/keys.properties",
            )

        /** Same shape, same `host=value` lines — ours to write, since Gradle has no cookie file. */
        private val COOKIE_FILES = listOf(".gradle/develocity/cookies.properties")

        /**
         * Resolves both credentials for [serverUrl], each from the first source that has one:
         * the explicit argument, the system property, the environment variable, then the per-host
         * entry in the properties files.
         */
        fun resolve(
            serverUrl: String,
            explicitKey: String? = null,
            explicitCookie: String? = null,
        ): DevelocityConfig {
            val normalized = serverUrl.trimEnd('/')
            val host = URI(normalized).host ?: error("Could not read a host out of '$serverUrl'")
            val key =
                explicitKey
                    ?: property(ACCESS_KEY_PROPERTY)
                    ?: environment(ACCESS_KEY_ENV)
                    ?: hostEntry(KEY_FILES, host)
            val cookie =
                explicitCookie
                    ?: property(SESSION_COOKIE_PROPERTY)
                    ?: environment(SESSION_COOKIE_ENV)
                    ?: hostEntry(COOKIE_FILES, host)
            return DevelocityConfig(normalized, key, cookie?.let(::normalizeCookie))
        }

        private fun property(name: String) = System.getProperty(name)?.takeIf { it.isNotBlank() }

        private fun environment(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }

        /**
         * The cookie is copied out of a browser's devtools, so it arrives either bare or as the
         * whole `Cookie: a=1; b=2` header line. Both are accepted.
         */
        private fun normalizeCookie(raw: String): String? =
            raw
                .trim()
                .let { if (it.startsWith("cookie:", ignoreCase = true)) it.substring("cookie:".length) else it }
                .trim()
                .takeIf { it.isNotBlank() }

        /** The value keyed by [host] in the first of [files], relative to the user's home, that has one. */
        private fun hostEntry(
            files: List<String>,
            host: String,
        ): String? {
            val home = File(System.getProperty("user.home"))
            return files
                .asSequence()
                .map { File(home, it) }
                .filter { it.isFile }
                .mapNotNull { file ->
                    val properties = Properties().apply { file.inputStream().use { load(it) } }
                    properties.getProperty(host)?.takeIf { it.isNotBlank() }
                }.firstOrNull()
        }
    }
}
