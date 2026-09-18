package fr.massai.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class OrientationMathTest {

    @Test
    fun unwrapCrossingPiKeepsSmallDelta() {
        val previous = Math.toRadians(179.0)
        val current = Math.toRadians(-179.0)
        val delta = OrientationMath.unwrapDelta(current, previous)
        assertEquals(Math.toRadians(2.0), delta, 1e-6)
    }

    @Test
    fun circularDistanceWrapsAtFullTurn() {
        val a = Math.toRadians(355.0)
        val b = Math.toRadians(5.0)
        assertEquals(Math.toRadians(10.0), OrientationMath.circularDistance(a, b), 1e-6)
    }

    @Test
    fun interpolationUsesUnwrappedYaw() {
        val metadata = ScanMetadata(
            listOf(
                OrientationSample(0, 0.0),
                OrientationSample(1000, PI)
            ),
            sensorAvailable = true
        )
        assertEquals(PI / 2.0, metadata.angleAt(500)!!, 1e-6)
        assertTrue(metadata.coverageDegrees > 179.0)
    }
}
