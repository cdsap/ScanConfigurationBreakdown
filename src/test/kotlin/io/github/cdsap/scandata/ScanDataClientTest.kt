package io.github.cdsap.scandata

import io.github.cdsap.config.DevelocityConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How the client behaves against an instance that keeps `/scan-data` behind SSO: the session
 * cookie goes out with the request, and a login redirect comes back as a named failure rather
 * than as a JSON parse error on a Keycloak HTML page.
 */
class ScanDataClientTest {
    private val config =
        DevelocityConfig(
            serverUrl = "https://dv.example.com",
            accessKey = "the-key",
            sessionCookie = "geapp1_sess_0=abc; geapp1_lat_0=def",
        )

    private val completed =
        """{"status":"COMPLETED","data":{"serialConfigurationSummary":{"totalConfigurationTime":1234}}}"""

    private fun client(
        status: HttpStatusCode,
        body: String = "",
        location: String? = null,
        captured: MutableList<HttpRequestData> = mutableListOf(),
        config: DevelocityConfig = this.config,
    ): ScanDataClient {
        val engine =
            MockEngine { request ->
                captured += request
                respond(
                    content = body,
                    status = status,
                    headers =
                        headersOf(
                            HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                            *(location?.let { arrayOf(HttpHeaders.Location to listOf(it)) } ?: emptyArray()),
                        ),
                )
            }
        val http =
            HttpClient(engine) {
                expectSuccess = false
                followRedirects = false
                install(ContentNegotiation) { json(ScanDataClient.json) }
            }
        return ScanDataClient(config, http)
    }

    @Test
    fun `sends the session cookie alongside the bearer token`() {
        val captured = mutableListOf<HttpRequestData>()
        val summary =
            runBlocking {
                client(HttpStatusCode.OK, completed, captured = captured).use {
                    it.performanceConfiguration("37k6k3zc6scly")
                }
            }?.serialConfigurationSummary

        assertEquals(1234, summary?.totalConfigurationTime)
        val request = captured.single()
        assertEquals("Bearer the-key", request.headers[HttpHeaders.Authorization])
        assertEquals("geapp1_sess_0=abc; geapp1_lat_0=def", request.headers[HttpHeaders.Cookie])
        assertEquals(
            "https://dv.example.com/scan-data/gradle/37k6k3zc6scly/performance-configuration",
            request.url.toString(),
        )
    }

    @Test
    fun `omits the cookie header when there is no session`() {
        val captured = mutableListOf<HttpRequestData>()
        runBlocking {
            client(
                HttpStatusCode.OK,
                completed,
                captured = captured,
                config = config.copy(sessionCookie = null),
            ).use { it.performanceConfiguration("37k6k3zc6scly") }
        }

        assertNull(captured.single().headers[HttpHeaders.Cookie])
    }

    @Test
    fun `reports a login redirect as an auth failure`() {
        val failure =
            assertFailsWith<ScanDataAuthException> {
                runBlocking {
                    client(
                        HttpStatusCode.Found,
                        location = "https://dv.example.com/keycloak/realms/gradle-enterprise/protocol/openid-connect/auth",
                    ).use { it.performanceConfiguration("37k6k3zc6scly") }
                }
            }

        assertEquals(302, failure.statusCode)
        assertEquals("37k6k3zc6scly", failure.buildScanId)
        assertTrue(ScanDataAuthException.hint(config).contains("--cookie"))
    }

    @Test
    fun `reports an expired session as an auth failure`() {
        val failure =
            assertFailsWith<ScanDataAuthException> {
                runBlocking {
                    client(HttpStatusCode.Unauthorized).use { it.performanceConfiguration("37k6k3zc6scly") }
                }
            }

        assertEquals(401, failure.statusCode)
    }

    @Test
    fun `a missing scan is still just missing`() {
        val result =
            runBlocking {
                client(HttpStatusCode.NotFound).use { it.performanceConfiguration("37k6k3zc6scly") }
            }

        assertNull(result)
    }
}
