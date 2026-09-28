package io.github.cdsap.config

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The resolution order both credentials follow, and the per-host files they fall back to.
 *
 * Environment variables are not exercised here — a JVM cannot set its own — so the file and
 * system-property paths carry the test, and the env step sits between them in [DevelocityConfig].
 */
class DevelocityConfigTest {
    private val home = Files.createTempDirectory("reader-home").toFile()
    private val originalHome: String = System.getProperty("user.home")
    private val server = "https://dv.example.com"

    @AfterTest
    fun restore() {
        System.setProperty("user.home", originalHome)
        System.clearProperty(DevelocityConfig.ACCESS_KEY_PROPERTY)
        System.clearProperty(DevelocityConfig.SESSION_COOKIE_PROPERTY)
        home.deleteRecursively()
    }

    private fun write(
        path: String,
        contents: String,
    ) {
        System.setProperty("user.home", home.absolutePath)
        File(home, path).apply { parentFile.mkdirs() }.writeText(contents)
    }

    @Test
    fun `reads both credentials from the per-host properties files`() {
        write(".gradle/develocity/keys.properties", "dv.example.com=file-key\nother.example.com=nope\n")
        write(".gradle/develocity/cookies.properties", "dv.example.com=geapp1_0=file-cookie\n")

        val config = DevelocityConfig.resolve(server)

        assertEquals("file-key", config.accessKey)
        assertEquals("geapp1_0=file-cookie", config.sessionCookie)
    }

    @Test
    fun `ignores entries for a different host`() {
        write(".gradle/develocity/keys.properties", "other.example.com=nope\n")

        val config = DevelocityConfig.resolve(server)

        assertNull(config.accessKey)
        assertNull(config.sessionCookie)
    }

    @Test
    fun `a system property beats the file`() {
        write(".gradle/develocity/keys.properties", "dv.example.com=file-key\n")
        write(".gradle/develocity/cookies.properties", "dv.example.com=file-cookie\n")
        System.setProperty(DevelocityConfig.ACCESS_KEY_PROPERTY, "property-key")
        System.setProperty(DevelocityConfig.SESSION_COOKIE_PROPERTY, "property-cookie")

        val config = DevelocityConfig.resolve(server)

        assertEquals("property-key", config.accessKey)
        assertEquals("property-cookie", config.sessionCookie)
    }

    @Test
    fun `an explicit argument beats everything`() {
        write(".gradle/develocity/keys.properties", "dv.example.com=file-key\n")
        System.setProperty(DevelocityConfig.ACCESS_KEY_PROPERTY, "property-key")

        val config = DevelocityConfig.resolve(server, "flag-key", "flag-cookie")

        assertEquals("flag-key", config.accessKey)
        assertEquals("flag-cookie", config.sessionCookie)
    }

    @Test
    fun `a pasted Cookie header line is accepted wherever it comes from`() {
        System.setProperty(DevelocityConfig.SESSION_COOKIE_PROPERTY, "  Cookie: a=1; b=2  ")

        assertEquals("a=1; b=2", DevelocityConfig.resolve(server).sessionCookie)
    }

    @Test
    fun `the trailing slash on a server url is dropped`() {
        val config = DevelocityConfig.resolve("$server/")

        assertEquals(server, config.serverUrl)
        assertEquals("dv.example.com", config.host)
    }
}
