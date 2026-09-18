package fr.massai.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class ScanGuideMathTest {

    @Test
    fun circularCoverageFullTurnIsCloseToFullCircle() {
        val angles = (0 until 24).map { 2.0 * PI * it / 24.0 }
        val coverage = OrientationMath.circularCoverageDegrees(angles)
        assertTrue(coverage >= 340.0)
        assertTrue(coverage <= 360.0)
    }

    @Test
    fun circularCoverageQuarterTurnIsSmall() {
        val angles = listOf(0.0, PI / 12.0, PI / 6.0, PI / 4.0, PI / 2.0)
        val coverage = OrientationMath.circularCoverageDegrees(angles)
        assertTrue(coverage in 85.0..95.0)
    }

    @Test
    fun wrapAngleKeepsEquivalentDirectionsTogether() {
        assertEquals(
            OrientationMath.wrap0To2Pi(-PI / 2.0),
            OrientationMath.wrap0To2Pi(3.0 * PI / 2.0),
            1e-9
        )
    }
}
