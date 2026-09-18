package fr.massai.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.PI
import kotlin.math.abs

data class OrientationSample(
    val timeMs: Long,
    val yawRad: Double
)

data class ScanMetadata(
    val samples: List<OrientationSample>,
    val sensorAvailable: Boolean
) {
    val coverageDegrees: Double
        get() {
            if (samples.size < 2) return 0.0
            val min = samples.minOf { it.yawRad }
            val max = samples.maxOf { it.yawRad }
            return Math.toDegrees(max - min)
        }

    fun angleAt(timeMs: Long): Double? {
        if (samples.isEmpty()) return null
        if (timeMs <= samples.first().timeMs) return samples.first().yawRad
        if (timeMs >= samples.last().timeMs) return samples.last().yawRad

        var low = 0
        var high = samples.lastIndex
        while (low + 1 < high) {
            val mid = (low + high) ushr 1
            if (samples[mid].timeMs <= timeMs) low = mid else high = mid
        }

        val a = samples[low]
        val b = samples[high]
        val span = (b.timeMs - a.timeMs).coerceAtLeast(1L)
        val t = (timeMs - a.timeMs).toDouble() / span.toDouble()
        return a.yawRad + (b.yawRad - a.yawRad) * t
    }

    fun writeTo(file: File) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("sensorAvailable", sensorAvailable)
        root.put("coverageDegrees", coverageDegrees)

        val array = JSONArray()
        samples.forEach { sample ->
            array.put(
                JSONObject()
                    .put("timeMs", sample.timeMs)
                    .put("yawRad", sample.yawRad)
            )
        }
        root.put("samples", array)
        file.writeText(root.toString())
    }

    companion object {
        fun readFrom(file: File): ScanMetadata? {
            if (!file.exists()) return null
            return try {
                val root = JSONObject(file.readText())
                val array = root.optJSONArray("samples") ?: JSONArray()
                val samples = ArrayList<OrientationSample>(array.length())
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    samples += OrientationSample(
                        timeMs = item.getLong("timeMs"),
                        yawRad = item.getDouble("yawRad")
                    )
                }
                ScanMetadata(
                    samples = samples,
                    sensorAvailable = root.optBoolean("sensorAvailable", samples.isNotEmpty())
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

object OrientationMath {
    fun unwrapDelta(current: Double, previous: Double): Double {
        var delta = current - previous
        while (delta > PI) delta -= 2.0 * PI
        while (delta < -PI) delta += 2.0 * PI
        return delta
    }

    fun circularDistance(a: Double, b: Double): Double {
        var d = abs(a - b) % (2.0 * PI)
        if (d > PI) d = 2.0 * PI - d
        return d
    }
}
