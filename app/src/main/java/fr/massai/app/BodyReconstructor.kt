package fr.massai.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.Closeable
import java.io.File
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class Point3(val x: Float, val y: Float, val z: Float)

data class BodyReconstruction(
    val rawSurface: List<Point3>,
    val repairedSurface: List<Point3>,
    val corrections: List<Point3>,
    val rawVolumeM3: Double,
    val repairedVolumeM3: Double,
    val validViews: Int,
    val totalViews: Int,
    val repairFraction: Double,
    val quality: Int,
    val heightM: Double,
    val halfExtentM: Double
)

class BodyReconstructor : Closeable {

    private data class Silhouette(
        val width: Int,
        val height: Int,
        val probability: FloatArray,
        val top: Int,
        val bottom: Int,
        val rowCenter: FloatArray,
        val pxPerMeter: Double,
        val maxSpanMeters: Double
    )

    private val options = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
        .build()

    private val segmenter = Segmentation.getClient(options)

    fun reconstruct(
        frameFiles: List<File>,
        bodyHeightM: Double,
        progress: (stage: String, current: Int, total: Int) -> Unit = { _, _, _ -> }
    ): BodyReconstruction {
        require(bodyHeightM in 1.0..2.5) { "Taille physique invalide." }

        val silhouettes = ArrayList<Silhouette>()
        frameFiles.forEachIndexed { index, file ->
            progress("Segmentation silhouette", index + 1, frameFiles.size)
            val bitmap = decodeScaled(file, 480) ?: return@forEachIndexed
            try {
                segment(bitmap, bodyHeightM)?.let(silhouettes::add)
            } finally {
                bitmap.recycle()
            }
        }

        if (silhouettes.size < 8) {
            throw IllegalStateException(
                "Seulement ${silhouettes.size} vues exploitables. Il en faut au moins 8."
            )
        }

        progress("Construction du visual hull", 0, 1)
        val halfExtent = silhouettes.maxOf { it.maxSpanMeters }
            .times(0.62)
            .coerceIn(bodyHeightM * 0.18, bodyHeightM * 0.60)

        val nx = 48
        val ny = 96
        val nz = 48
        val totalVoxels = nx * ny * nz
        val raw = BooleanArray(totalVoxels)

        val dx = (2.0 * halfExtent) / nx.toDouble()
        val dy = bodyHeightM / ny.toDouble()
        val dz = (2.0 * halfExtent) / nz.toDouble()

        val requiredSupport = ceil(silhouettes.size * 0.86).toInt()
        val cosAngles = DoubleArray(silhouettes.size)
        val sinAngles = DoubleArray(silhouettes.size)

        silhouettes.indices.forEach { i ->
            val theta = 2.0 * PI * i.toDouble() / silhouettes.size.toDouble()
            cosAngles[i] = cos(theta)
            sinAngles[i] = sin(theta)
        }

        for (iy in 0 until ny) {
            val yMeters = (iy + 0.5) * dy

            for (ix in 0 until nx) {
                val xMeters = -halfExtent + (ix + 0.5) * dx

                for (iz in 0 until nz) {
                    val zMeters = -halfExtent + (iz + 0.5) * dz
                    var support = 0
                    var tested = 0

                    for (view in silhouettes.indices) {
                        tested++
                        val silhouette = silhouettes[view]
                        val row = (silhouette.bottom - yMeters * silhouette.pxPerMeter)
                            .roundToInt()

                        if (row in 0 until silhouette.height) {
                            val center = silhouette.rowCenter[row]
                            val projectedMeters =
                                xMeters * cosAngles[view] + zMeters * sinAngles[view]
                            val col = (center + projectedMeters * silhouette.pxPerMeter)
                                .roundToInt()

                            if (col in 0 until silhouette.width) {
                                val p = silhouette.probability[row * silhouette.width + col]
                                if (p >= FOREGROUND_THRESHOLD) support++
                            }
                        }

                        val remaining = silhouettes.size - tested
                        if (support + remaining < requiredSupport) break
                    }

                    if (support >= requiredSupport) {
                        raw[index(ix, iy, iz, nx, nz)] = true
                    }
                }
            }

            if (iy % 8 == 0 || iy == ny - 1) {
                progress("Construction du visual hull", iy + 1, ny)
            }
        }

        if (!raw.any { it }) {
            throw IllegalStateException(
                "Le visual hull est vide. Vérifiez que la vidéo couvre bien le sujet sur 360°."
            )
        }

        progress("Réparation voxel", 0, 1)
        val repaired = repair(raw, nx, ny, nz)
        val rawCount = raw.count { it }
        val repairedCount = repaired.count { it }
        val changedCount = raw.indices.count { raw[it] != repaired[it] }

        val voxelVolume = dx * dy * dz
        val rawVolume = rawCount * voxelVolume
        val repairedVolume = repairedCount * voxelVolume
        val repairFraction = if (repairedCount > 0) {
            changedCount.toDouble() / repairedCount.toDouble()
        } else {
            1.0
        }

        progress("Préparation du modèle 3D", 0, 1)
        val rawSurface = surfacePoints(raw, nx, ny, nz, halfExtent, bodyHeightM)
        val repairedSurface = surfacePoints(repaired, nx, ny, nz, halfExtent, bodyHeightM)
        val corrections = correctionPoints(raw, repaired, nx, ny, nz, halfExtent, bodyHeightM)

        return BodyReconstruction(
            rawSurface = rawSurface,
            repairedSurface = repairedSurface,
            corrections = corrections,
            rawVolumeM3 = rawVolume,
            repairedVolumeM3 = repairedVolume,
            validViews = silhouettes.size,
            totalViews = frameFiles.size,
            repairFraction = repairFraction,
            quality = BodyMath.technicalQuality(
                silhouettes.size,
                frameFiles.size,
                repairFraction
            ),
            heightM = bodyHeightM,
            halfExtentM = halfExtent
        )
    }

    private fun segment(bitmap: Bitmap, bodyHeightM: Double): Silhouette? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val mask = Tasks.await(segmenter.process(image))
        val width = mask.width
        val height = mask.height
        if (width <= 0 || height <= 0) return null

        val data = FloatArray(width * height)
        val byteBuffer = mask.buffer.duplicate().order(ByteOrder.nativeOrder())
        byteBuffer.rewind()
        val floatBuffer = byteBuffer.asFloatBuffer()
        if (floatBuffer.remaining() < data.size) return null
        floatBuffer.get(data)

        val minRowPixels = max(2, width / 120)
        val left = IntArray(height) { width }
        val right = IntArray(height) { -1 }
        val count = IntArray(height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                if (data[offset + x] >= FOREGROUND_THRESHOLD) {
                    if (x < left[y]) left[y] = x
                    right[y] = x
                    count[y]++
                }
            }
        }

        var top = -1
        var bottom = -1
        for (y in 0 until height) {
            if (count[y] >= minRowPixels) {
                if (top < 0) top = y
                bottom = y
            }
        }

        if (top < 0 || bottom <= top) return null
        val bodyHeightPx = bottom - top + 1
        if (bodyHeightPx < height * 0.28) return null

        val defaultCenter = run {
            var sum = 0.0
            var rows = 0
            for (y in top..bottom) {
                if (right[y] >= left[y]) {
                    sum += (left[y] + right[y]) * 0.5
                    rows++
                }
            }
            if (rows > 0) (sum / rows).toFloat() else (width * 0.5f)
        }

        val centers = FloatArray(height) { defaultCenter }
        var maxSpanPx = 0

        for (y in top..bottom) {
            if (right[y] >= left[y]) {
                centers[y] = (left[y] + right[y]) * 0.5f
                maxSpanPx = max(maxSpanPx, right[y] - left[y] + 1)
            }
        }

        // Lisse légèrement la ligne centrale pour limiter les sauts dus aux bras/jambes.
        for (pass in 0 until 2) {
            val copy = centers.copyOf()
            for (y in max(1, top) until min(height - 1, bottom + 1)) {
                centers[y] = (copy[y - 1] + copy[y] + copy[y + 1]) / 3.0f
            }
        }

        val pxPerMeter = bodyHeightPx.toDouble() / bodyHeightM
        val maxSpanMeters = maxSpanPx.toDouble() / pxPerMeter

        return Silhouette(
            width = width,
            height = height,
            probability = data,
            top = top,
            bottom = bottom,
            rowCenter = centers,
            pxPerMeter = pxPerMeter,
            maxSpanMeters = maxSpanMeters
        )
    }

    private fun repair(
        source: BooleanArray,
        nx: Int,
        ny: Int,
        nz: Int
    ): BooleanArray {
        var current = source.copyOf()

        repeat(2) {
            val next = current.copyOf()

            for (iy in 1 until ny - 1) {
                for (ix in 1 until nx - 1) {
                    for (iz in 1 until nz - 1) {
                        val i = index(ix, iy, iz, nx, nz)
                        val neighbors =
                            bool(current[index(ix - 1, iy, iz, nx, nz)]) +
                            bool(current[index(ix + 1, iy, iz, nx, nz)]) +
                            bool(current[index(ix, iy - 1, iz, nx, nz)]) +
                            bool(current[index(ix, iy + 1, iz, nx, nz)]) +
                            bool(current[index(ix, iy, iz - 1, nx, nz)]) +
                            bool(current[index(ix, iy, iz + 1, nx, nz)])

                        if (!current[i] && neighbors >= 5) next[i] = true
                        if (current[i] && neighbors <= 1) next[i] = false
                    }
                }
            }

            current = next
        }

        return current
    }

    private fun surfacePoints(
        occupancy: BooleanArray,
        nx: Int,
        ny: Int,
        nz: Int,
        halfExtent: Double,
        bodyHeightM: Double
    ): List<Point3> {
        val points = ArrayList<Point3>()
        val dx = (2.0 * halfExtent) / nx
        val dy = bodyHeightM / ny
        val dz = (2.0 * halfExtent) / nz

        for (iy in 0 until ny) {
            for (ix in 0 until nx) {
                for (iz in 0 until nz) {
                    val i = index(ix, iy, iz, nx, nz)
                    if (!occupancy[i]) continue
                    if (!isSurface(occupancy, ix, iy, iz, nx, ny, nz)) continue

                    // Sous-échantillonnage déterministe pour garder le rendu fluide.
                    if (((ix + iy + iz) and 1) != 0) continue

                    points.add(
                        Point3(
                            (-halfExtent + (ix + 0.5) * dx).toFloat(),
                            ((iy + 0.5) * dy).toFloat(),
                            (-halfExtent + (iz + 0.5) * dz).toFloat()
                        )
                    )
                }
            }
        }
        return points
    }

    private fun correctionPoints(
        raw: BooleanArray,
        repaired: BooleanArray,
        nx: Int,
        ny: Int,
        nz: Int,
        halfExtent: Double,
        bodyHeightM: Double
    ): List<Point3> {
        val points = ArrayList<Point3>()
        val dx = (2.0 * halfExtent) / nx
        val dy = bodyHeightM / ny
        val dz = (2.0 * halfExtent) / nz

        raw.indices.forEach { i ->
            if (raw[i] == repaired[i]) return@forEach
            val yz = nx * nz
            val iy = i / yz
            val rem = i % yz
            val ix = rem / nz
            val iz = rem % nz

            points.add(
                Point3(
                    (-halfExtent + (ix + 0.5) * dx).toFloat(),
                    ((iy + 0.5) * dy).toFloat(),
                    (-halfExtent + (iz + 0.5) * dz).toFloat()
                )
            )
        }
        return points
    }

    private fun isSurface(
        occupancy: BooleanArray,
        x: Int,
        y: Int,
        z: Int,
        nx: Int,
        ny: Int,
        nz: Int
    ): Boolean {
        if (x == 0 || y == 0 || z == 0 || x == nx - 1 || y == ny - 1 || z == nz - 1) {
            return true
        }
        return !occupancy[index(x - 1, y, z, nx, nz)] ||
            !occupancy[index(x + 1, y, z, nx, nz)] ||
            !occupancy[index(x, y - 1, z, nx, nz)] ||
            !occupancy[index(x, y + 1, z, nx, nz)] ||
            !occupancy[index(x, y, z - 1, nx, nz)] ||
            !occupancy[index(x, y, z + 1, nx, nz)]
    }

    private fun decodeScaled(file: File, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension * 2) {
            sample *= 2
        }

        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    }

    private fun index(x: Int, y: Int, z: Int, nx: Int, nz: Int): Int {
        return (y * nx + x) * nz + z
    }

    private fun bool(value: Boolean): Int = if (value) 1 else 0

    override fun close() {
        segmenter.close()
    }

    companion object {
        private const val FOREGROUND_THRESHOLD = 0.60f
    }
}
