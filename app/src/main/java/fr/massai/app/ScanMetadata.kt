package fr.massai.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.PI
import kotlin.math.abs

data class OrientationSample(
    val timeMs: Long,
    val yawRad: Double,
    val level: Int = 0
)

data class ScanMetadata(
    val samples: List<OrientationSample>,
    val sensorAvailable: Boolean,
    val targetLevels: Int = 1,
    val completedLevels: Int = 0,
    val cameraPreference: String? = null,
    val cameraId: String? = null,
    val focalLengthMm: Float? = null
) {
    val levelsPresent: List<Int>
        get() = samples
            .asSequence()
            .map { it.level }
            .filter { it >= 0 }
            .distinct()
            .sorted()
            .toList()

    val cameraLabel: String?
        get() {
            val focal = focalLengthMm
            return when {
                focal != null && cameraPreference == CameraPreference.ULTRA_WIDE.name ->
                    "ultra grand-angle %.1f mm".format(focal)
                focal != null ->
                    "caméra %.1f mm".format(focal)
                !cameraPreference.isNullOrBlank() ->
                    CameraPreference.fromName(cameraPreference).label
                else -> null
            }
        }

    val coverageDegrees: Double
        get() {
            if (samples.size < 2) return 0.0
            val levels = levelsPresent
            if (levels.isEmpty()) return 0.0
            return levels.maxOf { coverageDegreesForLevel(it) }
        }

    fun coverageDegreesForLevel(level: Int): Double {
        val angles = samples
            .asSequence()
            .filter { it.level == level }
            .map { it.yawRad }
            .toList()
        return OrientationMath.circularCoverageDegrees(angles)
    }

    fun angleAt(timeMs: Long): Double? {
        if (samples.isEmpty()) return null
        if (timeMs <= samples.first().timeMs) return samples.first().yawRad
        if (timeMs >= samples.last().timeMs) return samples.last().yawRad

        val (low, high) = surroundingIndices(timeMs)
        val a = samples[low]
        val b = samples[high]
        val span = (b.timeMs - a.timeMs).coerceAtLeast(1L)
        val t = (timeMs - a.timeMs).toDouble() / span.toDouble()
        return a.yawRad + (b.yawRad - a.yawRad) * t
    }

    fun wrappedAngleAt(timeMs: Long): Double? {
        return angleAt(timeMs)?.let(OrientationMath::wrap0To2Pi)
    }

    fun levelAt(timeMs: Long): Int? {
        if (samples.isEmpty()) return null
        if (timeMs <= samples.first().timeMs) return samples.first().level
        if (timeMs >= samples.last().timeMs) return samples.last().level

        val (low, high) = surroundingIndices(timeMs)
        val a = samples[low]
        val b = samples[high]
        return if (timeMs - a.timeMs <= b.timeMs - timeMs) a.level else b.level
    }

    private fun surroundingIndices(timeMs: Long): Pair<Int, Int> {
        var low = 0
        var high = samples.lastIndex
        while (low + 1 < high) {
            val mid = (low + high) ushr 1
            if (samples[mid].timeMs <= timeMs) low = mid else high = mid
        }
        return low to high
    }

    fun writeTo(file: File) {
        val root = JSONObject()
        root.put("version", 3)
        root.put("sensorAvailable", sensorAvailable)
        root.put("coverageDegrees", coverageDegrees)
        root.put("targetLevels", targetLevels)
        root.put("completedLevels", completedLevels)
        root.put("cameraPreference", cameraPreference)
        root.put("cameraId", cameraId)
        focalLengthMm?.let { root.put("focalLengthMm", it.toDouble()) }

        val array = JSONArray()
        samples.forEach { sample ->
            array.put(
                JSONObject()
                    .put("timeMs", sample.timeMs)
                    .put("yawRad", sample.yawRad)
                    .put("level", sample.level)
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
                        yawRad = item.getDouble("yawRad"),
                        level = item.optInt("level", 0)
                    )
                }

                val targetLevels = root.optInt("targetLevels", 1).coerceAtLeast(1)
                val completedLevels = root.optInt(
                    "completedLevels",
                    if (samples.isNotEmpty()) 1 else 0
                ).coerceIn(0, targetLevels)

                ScanMetadata(
                    samples = samples,
                    sensorAvailable = root.optBoolean(
                        "sensorAvailable",
                        samples.isNotEmpty()
                    ),
                    targetLevels = targetLevels,
                    completedLevels = completedLevels,
                    cameraPreference = root.optString("cameraPreference", "")
                        .takeIf { it.isNotBlank() },
                    cameraId = root.optString("cameraId", "")
                        .takeIf { it.isNotBlank() },
                    focalLengthMm = if (root.has("focalLengthMm")) {
                        root.optDouble("focalLengthMm").toFloat()
                    } else {
                        null
                    }
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

    fun wrap0To2Pi(angle: Double): Double {
        var wrapped = angle % (2.0 * PI)
        if (wrapped < 0.0) wrapped += 2.0 * PI
        return wrapped
    }

    fun circularCoverageDegrees(angles: List<Double>): Double {
        if (angles.size < 2) return 0.0

        val sorted = angles
            .map(::wrap0To2Pi)
            .sorted()

        var largestGap = 0.0
        for (i in 0 until sorted.lastIndex) {
            largestGap = maxOf(largestGap, sorted[i + 1] - sorted[i])
        }
        largestGap = maxOf(
            largestGap,
            (sorted.first() + 2.0 * PI) - sorted.last()
        )

        val covered = (2.0 * PI - largestGap).coerceIn(0.0, 2.0 * PI)
        return Math.toDegrees(covered)
    }
}
