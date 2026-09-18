package fr.massai.app

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object BodyMath {
    fun liters(volumeM3: Double): Double = volumeM3 * 1000.0

    fun densityKgPerLiter(weightKg: Double, volumeM3: Double): Double {
        val liters = liters(volumeM3)
        return if (liters > 0.0) weightKg / liters else Double.NaN
    }

    fun densityKgPerM3(weightKg: Double, volumeM3: Double): Double {
        return if (volumeM3 > 0.0) weightKg / volumeM3 else Double.NaN
    }

    fun technicalQuality(
        validViews: Int,
        totalViews: Int,
        repairFraction: Double
    ): Int {
        return technicalQuality(
            validViews = validViews,
            totalViews = totalViews,
            repairFraction = repairFraction,
            coverageDegrees = 360.0
        )
    }

    fun technicalQuality(
        validViews: Int,
        totalViews: Int,
        repairFraction: Double,
        coverageDegrees: Double
    ): Int {
        if (totalViews <= 0) return 0

        val viewScore = (validViews.toDouble() / totalViews.toDouble())
            .coerceIn(0.0, 1.0)
        val coverageScore = (coverageDegrees / 330.0)
            .coerceIn(0.0, 1.0)
        val repairPenalty = min(1.0, max(0.0, repairFraction) * 8.0)
        val repairScore = 1.0 - repairPenalty

        return (
            (
                0.42 * viewScore +
                    0.34 * coverageScore +
                    0.24 * repairScore
                ) * 100.0
            )
            .roundToInt()
            .coerceIn(0, 100)
    }
}
