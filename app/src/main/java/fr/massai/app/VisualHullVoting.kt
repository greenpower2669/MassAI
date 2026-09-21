package fr.massai.app

import kotlin.math.ceil
import kotlin.math.max

/**
 * Visual-hull evidence gate. Out-of-frame samples are UNKNOWN, not rejection.
 * The minimum observation count prevents a tiny accidental visible region
 * from inventing geometry when the support threshold is tolerant.
 */
object VisualHullVoting {
    fun minimumObserved(totalViews: Int, turbo: Boolean): Int =
        max(4, ceil(totalViews * if (turbo) 0.16 else 0.20).toInt())

    fun accepts(
        support: Int,
        observed: Int,
        minimumObserved: Int,
        supportRatio: Double
    ): Boolean {
        require(supportRatio in 0.5..1.0)
        return observed >= minimumObserved &&
            support >= ceil(observed * supportRatio).toInt()
    }
}
