package fr.massai.app

import android.graphics.Bitmap
import java.io.File
import kotlin.math.abs
import kotlin.math.max

data class CandidateFrame(
    val file: File,
    val timeMs: Long,
    val blurScore: Double,
    val signature: DoubleArray
)

data class ReconstructionFrame(
    val file: File,
    val timeMs: Long,
    val angleRad: Double,
    val blurScore: Double,
    val measuredAngle: Boolean
)

object FrameQuality {

    fun blurScore(bitmap: Bitmap): Double {
        val target = if (max(bitmap.width, bitmap.height) > 320) {
            val scale = 320.0 / max(bitmap.width, bitmap.height).toDouble()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(2),
                (bitmap.height * scale).toInt().coerceAtLeast(2),
                true
            )
        } else {
            bitmap
        }

        try {
            if (target.width < 3 || target.height < 3) return 0.0
            var sum = 0.0
            var sumSq = 0.0
            var count = 0

            for (y in 1 until target.height - 1 step 2) {
                for (x in 1 until target.width - 1 step 2) {
                    val c = luminance(target.getPixel(x, y))
                    val lap =
                        luminance(target.getPixel(x - 1, y)) +
                        luminance(target.getPixel(x + 1, y)) +
                        luminance(target.getPixel(x, y - 1)) +
                        luminance(target.getPixel(x, y + 1)) -
                        4.0 * c

                    sum += lap
                    sumSq += lap * lap
                    count++
                }
            }

            if (count == 0) return 0.0
            val mean = sum / count
            return (sumSq / count) - mean * mean
        } finally {
            if (target !== bitmap) target.recycle()
        }
    }

    fun signature(bitmap: Bitmap): DoubleArray {
        val w = 8
        val h = 8
        val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
        try {
            val values = DoubleArray(w * h)
            var mean = 0.0
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val v = luminance(small.getPixel(x, y))
                    values[y * w + x] = v
                    mean += v
                }
            }
            mean /= values.size
            for (i in values.indices) values[i] = if (values[i] >= mean) 1.0 else 0.0
            return values
        } finally {
            small.recycle()
        }
    }

    fun signatureDistance(a: DoubleArray, b: DoubleArray): Double {
        if (a.size != b.size || a.isEmpty()) return 1.0
        var different = 0
        for (i in a.indices) {
            if (a[i] != b[i]) different++
        }
        return different.toDouble() / a.size.toDouble()
    }

    private fun luminance(pixel: Int): Double {
        val r = (pixel shr 16) and 0xff
        val g = (pixel shr 8) and 0xff
        val b = pixel and 0xff
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}

object FrameSelector {

    fun select(
        candidates: List<CandidateFrame>,
        wanted: Int,
        metadata: ScanMetadata?
    ): List<ReconstructionFrame> {
        if (candidates.isEmpty() || wanted <= 0) return emptyList()

        val hasMeasuredAngles =
            metadata != null &&
            metadata.sensorAvailable &&
            metadata.samples.size >= 8

        val selected = if (hasMeasuredAngles) {
            selectByMeasuredAngle(candidates, wanted, metadata!!)
        } else {
            selectByTime(candidates, wanted)
        }

        if (selected.isEmpty()) return emptyList()

        val medianBlur = selected.map { it.blurScore }.sorted()
            .let { it[it.size / 2].coerceAtLeast(1.0) }

        val filtered = ArrayList<ReconstructionFrame>()
        var previousSignature: DoubleArray? = null

        selected.forEachIndexed { index, candidate ->
            val tooBlurry = candidate.blurScore < medianBlur * 0.18
            val tooSimilar = previousSignature?.let {
                FrameQuality.signatureDistance(it, candidate.signature) < 0.015
            } ?: false

            if (!tooBlurry && (!tooSimilar || index == selected.lastIndex)) {
                val angle = if (hasMeasuredAngles) {
                    metadata!!.angleAt(candidate.timeMs) ?: 0.0
                } else {
                    2.0 * Math.PI * filtered.size.toDouble() / wanted.toDouble()
                }

                filtered += ReconstructionFrame(
                    file = candidate.file,
                    timeMs = candidate.timeMs,
                    angleRad = angle,
                    blurScore = candidate.blurScore,
                    measuredAngle = hasMeasuredAngles
                )
                previousSignature = candidate.signature
            }
        }

        return filtered
    }

    private fun selectByMeasuredAngle(
        candidates: List<CandidateFrame>,
        wanted: Int,
        metadata: ScanMetadata
    ): List<CandidateFrame> {
        val withAngles = candidates.mapNotNull { candidate ->
            metadata.angleAt(candidate.timeMs)?.let { angle -> candidate to angle }
        }
        if (withAngles.isEmpty()) return emptyList()

        val minAngle = withAngles.minOf { it.second }
        val maxAngle = withAngles.maxOf { it.second }
        val span = (maxAngle - minAngle).coerceAtLeast(0.001)

        return (0 until wanted).mapNotNull { bin ->
            val lo = minAngle + span * bin / wanted.toDouble()
            val hi = minAngle + span * (bin + 1) / wanted.toDouble()
            withAngles
                .asSequence()
                .filter { (_, angle) ->
                    if (bin == wanted - 1) angle in lo..hi else angle >= lo && angle < hi
                }
                .maxByOrNull { it.first.blurScore }
                ?.first
        }.sortedBy { it.timeMs }
    }

    private fun selectByTime(
        candidates: List<CandidateFrame>,
        wanted: Int
    ): List<CandidateFrame> {
        val sorted = candidates.sortedBy { it.timeMs }
        val minTime = sorted.first().timeMs
        val maxTime = sorted.last().timeMs
        val span = (maxTime - minTime).coerceAtLeast(1L)

        return (0 until wanted).mapNotNull { bin ->
            val lo = minTime + span * bin / wanted
            val hi = minTime + span * (bin + 1) / wanted
            sorted.asSequence()
                .filter { candidate ->
                    if (bin == wanted - 1) candidate.timeMs in lo..hi
                    else candidate.timeMs >= lo && candidate.timeMs < hi
                }
                .maxByOrNull { it.blurScore }
        }
    }
}
