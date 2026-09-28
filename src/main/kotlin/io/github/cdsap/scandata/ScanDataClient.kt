package io.github.cdsap.scandata

import io.github.cdsap.config.DevelocityConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Reads the `/scan-data` endpoints that back the Build Scan UI, e.g.
 * `https://ge.solutions-team.gradle.com/scan-data/gradle/37k6k3zc6scly/performance-configuration`.
 *
 * These are not part of the documented Develocity API: the shapes are tied to the UI and can change
 * between Develocity versions, which is why [PerformanceConfiguration] maps them leniently.
 */
class ScanDataClient(
    private val config: DevelocityConfig,
    private val httpClient: HttpClient = defaultHttpClient(),
) : AutoCloseable {
    /**
     * The configuration-phase breakdown for [buildScanId], or `null` when the server has no
     * configuration data for that scan (a 404, or a non-`COMPLETED` status).
     */
    suspend fun performanceConfiguration(
        buildScanId: String,
        buildTool: String = "gradle",
    ): PerformanceConfiguration? {
        val resource = "performance-configuration"
        val response = get(buildTool, buildScanId, resource)
        if (response.isAuthChallenge()) {
            throw ScanDataAuthException(buildScanId, response.status.value, url(buildTool, buildScanId, resource), config)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        if (!response.status.isSuccess()) {
            throw ScanDataException(buildScanId, response.status.value, url(buildTool, buildScanId, resource))
        }
        return response.body<ScanDataResponse<PerformanceConfiguration>>().takeIf { it.isCompleted }?.data
    }

    private suspend fun get(
        buildTool: String,
        buildScanId: String,
        resource: String,
    ): HttpResponse =
        httpClient.get(url(buildTool, buildScanId, resource)) {
            config.accessKey?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            config.sessionCookie?.let { header(HttpHeaders.Cookie, it) }
        }

    /**
     * An instance that gates `/scan-data` behind SSO answers with a 302 to its identity provider
     * rather than a 401. Redirects are not followed ([defaultHttpClient]), so that lands here
     * instead of being parsed as JSON — a login page is not a parse error, it is a missing cookie.
     */
    private fun HttpResponse.isAuthChallenge(): Boolean =
        status.value in 300..399 ||
            status == HttpStatusCode.Unauthorized ||
            status == HttpStatusCode.Forbidden

    private fun url(
        buildTool: String,
        buildScanId: String,
        resource: String,
    ) = "${config.serverUrl}/scan-data/$buildTool/$buildScanId/$resource"

    override fun close() = httpClient.close()

    companion object {
        /**
         * `ignoreUnknownKeys` is doing real work here: a single performance-configuration payload
         * runs to several MB, most of it fields [PerformanceConfiguration] deliberately skips.
         */
        val json =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                isLenient = true
                coerceInputValues = true
            }

        fun defaultHttpClient(): HttpClient =
            HttpClient(CIO) {
                expectSuccess = false
                followRedirects = false
                engine { requestTimeout = 0 }
                install(ContentNegotiation) { json(json) }
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 3)
                    exponentialDelay()
                }
            }
    }
}

class ScanDataException(
    val buildScanId: String,
    val statusCode: Int,
    url: String,
) : RuntimeException("scan-data request for build $buildScanId failed with HTTP $statusCode: $url")

/**
 * The instance wants a logged-in session for `/scan-data`, and the request did not have one.
 *
 * The message stays short because it is printed once per build; [hint] carries the part worth
 * saying once, and the commands print it at the end of a run.
 */
class ScanDataAuthException(
    val buildScanId: String,
    val statusCode: Int,
    val url: String,
    private val config: DevelocityConfig,
) : RuntimeException(
        "scan-data for build $buildScanId needs a logged-in session (HTTP $statusCode from ${config.host})",
    ) {
    companion object {
        fun hint(config: DevelocityConfig): String =
            """
            The /scan-data endpoints on ${config.host} are behind the browser session and ignore the
            access key, so listing builds works but reading their scan data does not.

            Log in to ${config.serverUrl} in a browser, open devtools, and from any request to
            /scan-data/... copy the request's Cookie header. Then any of:

              --cookie '<the cookie header>'
              export ${DevelocityConfig.SESSION_COOKIE_ENV}='<the cookie header>'
              JAVA_OPTS="-D${DevelocityConfig.SESSION_COOKIE_PROPERTY}='<the cookie header>'"
              echo '${config.host}=<the cookie header>' >> ~/.gradle/develocity/cookies.properties

            The cookie expires with the session, so it has to be renewed however you supply it. An
            instance with anonymous access enabled needs none of this.
            """.trimIndent()
    }
}
