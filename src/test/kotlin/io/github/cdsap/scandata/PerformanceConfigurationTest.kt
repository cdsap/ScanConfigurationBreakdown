package io.github.cdsap.scandata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Parses a real (trimmed) `/scan-data/gradle/{id}/performance-configuration` payload, so a
 * Develocity-side shape change shows up here rather than as silently empty numbers.
 */
class PerformanceConfigurationTest {
    private fun fixture(): ScanDataResponse<PerformanceConfiguration> =
        checkNotNull(javaClass.getResourceAsStream("/performance-configuration.json"))
            .use { it.reader().readText() }
            .let { ScanDataClient.json.decodeFromString(it) }

    @Test
    fun `reads the configuration timing summary`() {
        val response = fixture()

        assertTrue(response.isCompleted)
        val summary = assertNotNull(response.data?.serialConfigurationSummary)
        assertEquals(437768, summary.totalConfigurationTime)
        assertEquals(201321, summary.scriptCompilationTime)
        assertEquals(17766, summary.configurationResolutionTime)
    }

    @Test
    fun `reads the configuration cache outcome`() {
        val caching = assertNotNull(fixture().data?.configurationCaching)

        assertEquals("miss", caching.outcome)
        assertEquals("no cached configuration was available", caching.invalidationReason)
        assertEquals(176, caching.store?.duration)
        assertEquals(false, caching.load?.hasFailed)
    }

    @Test
    fun `reads task totals and script compilation counts`() {
        val data = assertNotNull(fixture().data)

        assertEquals(80590, data.taskTotals?.total)
        assertEquals(355, data.taskTotals?.createdDuringConfiguration)
        assertEquals(355, data.scriptCompilationDetails?.totalCompiledScripts)
    }

    /** `sumOwnTime` build-wide and `ownTime` per project both surface as [CodeUnitSummary.ownTime]. */
    @Test
    fun `normalises the two spellings of own time`() {
        val data = assertNotNull(fixture().data)

        val buildWide = data.byCodeUnits.first()
        assertEquals("com.android.internal.library", buildWide.displayName)
        assertEquals(35827, buildWide.ownTime)
        assertEquals(38298, buildWide.totalTime)

        val perProject = data.byProjects.first().codeUnitGroups.first()
        assertEquals(31983, perProject.ownTime)
        assertEquals(34724, perProject.totalTime)
    }

    /** The payload's heaviest field is intentionally unmapped; parsing must not choke on it. */
    @Test
    fun `ignores unmapped fields such as codeUnitApplications`() {
        assertTrue(assertNotNull(fixture().data).hasDetailedInfo)
    }
}
