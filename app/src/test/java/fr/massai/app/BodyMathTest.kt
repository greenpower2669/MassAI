package fr.massai.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BodyMathTest {

    @Test
    fun densityConversionsAreConsistent() {
        val weightKg = 70.0
        val volumeM3 = 0.070

        assertEquals(70.0, BodyMath.liters(volumeM3), 0.0001)
        assertEquals(1.0, BodyMath.densityKgPerLiter(weightKg, volumeM3), 0.0001)
        assertEquals(1000.0, BodyMath.densityKgPerM3(weightKg, volumeM3), 0.0001)
    }

    @Test
    fun technicalQualityDropsWhenRepairsGrow() {
        val clean = BodyMath.technicalQuality(20, 20, 0.0)
        val repaired = BodyMath.technicalQuality(20, 20, 0.08)
        assert(clean > repaired)
    }
}
