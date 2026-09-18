package fr.massai.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.Closeable
import java.nio.ByteOrder
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
    val halfExtentM: Double,
    val angularCoverageDeg: Double,
    val anglesMeasured: Boolean,
    val meanBlurScore: Double,
    val removedIslandVoxels: Int,
    val componentsBeforeCleanup: Int,
    val componentsAfterCleanup: Int
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
        val maxSpanMeters: Double,
        val threshold: Float,
        val angleRad: Double,
        val measuredAngle: Boolean
    )

    private val humanOptions = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
        .build()

    private val humanSegmenter = Segmentation.getClient(humanOptions)

    private val objectOptions = SubjectSegmenterOptions.Builder()
        .enableForegroundConfidenceMask()
        .build()

    private val objectSegmenter = SubjectSegmentation.getClient(objectOptions)

    fun reconstruct(
        frames: List<ReconstructionFrame>,
        bodyHeightM: Double,
        mode: SegmentationMode = SegmentationMode.HUMAN,
        progress: (stage: String, current: Int, total: Int) -> Unit = { _, _, _ -> }
    ): BodyReconstruction {
        when (mode) {
            SegmentationMode.HUMAN ->
                require(bodyHeightM in 1.0..2.5) { "Taille humaine invalide." }
            SegmentationMode.OBJECT ->
                require(bodyHeightM in 0.05..2.5) { "Hauteur objet invalide." }
        }

        if (mode == SegmentationMode.OBJECT) {
            progress("Préparation segmentation objet", 0, 1)
            try {
                Tasks.await(objectSegmenter.initTask)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Le modèle de segmentation objet n'est pas encore disponible. " +
                        "Gardez Internet actif puis relancez l'analyse dans quelques instants.",
                    e
                )
            }
        }

        val silhouettes = ArrayList<Silhouette>()
        frames.forEachIndexed { index, frame ->
            progress("Segmentation silhouette", index + 1, frames.size)
            val maxDimension = if (mode == SegmentationMode.OBJECT) 640 else 480
            val bitmap = decodeScaled(frame.file.absolutePath, maxDimension)
                ?: return@forEachIndexed

            try {
                val silhouette = when (mode) {
                    SegmentationMode.HUMAN -> segmentHuman(
                        bitmap = bitmap,
                        bodyHeightM = bodyHeightM,
                        angleRad = frame.angleRad,
                        measuredAngle = frame.measuredAngle
                    )

                    SegmentationMode.OBJECT -> segmentObject(
                        bitmap = bitmap,
                        bodyHeightM = bodyHeightM,
                        angleRad = frame.angleRad,
                        measuredAngle = frame.measuredAngle
                    )
                }
                silhouette?.let(silhouettes::add)
            } finally {
                bitmap.recycle()
            }
        }

        if (silhouettes.size < 8) {
            throw IllegalStateException(
                "Seulement ${silhouettes.size} vues exploitables. Il en faut au moins 8."
            )
        }

        val anglesMeasured = silhouettes.all { it.measuredAngle }
        val angularCoverageDeg = if (anglesMeasured) {
            OrientationMath.circularCoverageDegrees(
                silhouettes.map { it.angleRad }
            )
        } else {
            360.0
        }

        if (anglesMeasured && angularCoverageDeg < 300.0) {
            throw IllegalStateException(
                "Couverture angulaire insuffisante : ${angularCoverageDeg.roundToInt()}°. " +
                    "Effectuez un tour plus complet autour du sujet."
            )
        }

        progress("Construction du visual hull", 0, 1)
        val halfExtent = silhouettes.maxOf { it.maxSpanMeters }
            .times(0.62)
            .coerceIn(bodyHeightM * 0.18, bodyHeightM * 0.60)

        val nx = 48
        val ny = 96
        val nz = 48
        val raw = BooleanArray(nx * ny * nz)

        val dx = (2.0 * halfExtent) / nx.toDouble()
        val dy = bodyHeightM / ny.toDouble()
        val dz = (2.0 * halfExtent) / nz.toDouble()

        val requiredSupport = ceil(silhouettes.size * 0.86).toInt()
        val cosAngles = DoubleArray(silhouettes.size)
        val sinAngles = DoubleArray(silhouettes.size)

        silhouettes.indices.forEach { i ->
            cosAngles[i] = cos(silhouettes[i].angleRad)
            sinAngles[i] = sin(silhouettes[i].angleRad)
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
                                if (p >= silhouette.threshold) support++
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
                "Le visual hull est vide. Vérifiez le cadrage et la couverture autour du sujet."
            )
        }

        progress("Nettoyage des artefacts", 0, 1)

        // Nettoyage conservateur avant réparation : on retire uniquement les
        // petites composantes déconnectées. Les gros morceaux séparés restent
        // présents afin de ne pas supprimer un membre mal raccordé.
        val rawCleanup = VoxelPostProcessor.cleanup(raw, nx, ny, nz)
        val cleanedRaw = rawCleanup.occupancy

        progress("Réparation voxel", 0, 1)
        val repaired = repair(cleanedRaw, nx, ny, nz)

        // La réparation peut elle-même créer quelques petits îlots ; deuxième
        // passe légère avant le calcul final.
        val repairedCleanup = VoxelPostProcessor.cleanup(repaired, nx, ny, nz)
        val finalRepaired = repairedCleanup.occupancy

        val rawCount = raw.count { it }
        val repairedCount = finalRepaired.count { it }
        val changedCount = raw.indices.count { raw[it] != finalRepaired[it] }
        val removedIslandVoxels =
            rawCleanup.removedVoxels + repairedCleanup.removedVoxels

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
        val repairedSurface =
            surfacePoints(finalRepaired, nx, ny, nz, halfExtent, bodyHeightM)
        val corrections =
            correctionPoints(raw, finalRepaired, nx, ny, nz, halfExtent, bodyHeightM)

        return BodyReconstruction(
            rawSurface = rawSurface,
            repairedSurface = repairedSurface,
            corrections = corrections,
            rawVolumeM3 = rawVolume,
            repairedVolumeM3 = repairedVolume,
            validViews = silhouettes.size,
            totalViews = frames.size,
            repairFraction = repairFraction,
            quality = BodyMath.technicalQuality(
                validViews = silhouettes.size,
                totalViews = frames.size,
                repairFraction = repairFraction,
                coverageDegrees = angularCoverageDeg
            ),
            heightM = bodyHeightM,
            halfExtentM = halfExtent,
            angularCoverageDeg = angularCoverageDeg,
            anglesMeasured = anglesMeasured,
            meanBlurScore = frames.map { it.blurScore }.average(),
            removedIslandVoxels = removedIslandVoxels,
            componentsBeforeCleanup = rawCleanup.componentsBefore,
            componentsAfterCleanup = repairedCleanup.componentsAfter
        )
    }

    private fun segmentHuman(
        bitmap: Bitmap,
        bodyHeightM: Double,
        angleRad: Double,
        measuredAngle: Boolean
    ): Silhouette? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val mask = Tasks.await(humanSegmenter.process(image))
        val width = mask.width
        val height = mask.height
        if (width <= 0 || height <= 0) return null

        val data = FloatArray(width * height)
        val byteBuffer = mask.buffer.duplicate().order(ByteOrder.nativeOrder())
        byteBuffer.rewind()
        val floatBuffer = byteBuffer.asFloatBuffer()
        if (floatBuffer.remaining() < data.size) return null
        floatBuffer.get(data)

        return buildSilhouette(
            data = data,
            width = width,
            height = height,
            bodyHeightM = bodyHeightM,
            angleRad = angleRad,
            measuredAngle = measuredAngle,
            threshold = 0.60f
        )
    }

    private fun segmentObject(
        bitmap: Bitmap,
        bodyHeightM: Double,
        angleRad: Double,
        measuredAngle: Boolean
    ): Silhouette? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = Tasks.await(objectSegmenter.process(image))
        val mask = result.foregroundConfidenceMask ?: return null
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null

        val data = FloatArray(width * height)
        val buffer = mask.duplicate()
        buffer.rewind()
        if (buffer.remaining() < data.size) return null
        buffer.get(data)

        return buildSilhouette(
            data = data,
            width = width,
            height = height,
            bodyHeightM = bodyHeightM,
            angleRad = angleRad,
            measuredAngle = measuredAngle,
            threshold = 0.50f
        )
    }

    private fun buildSilhouette(
        data: FloatArray,
        width: Int,
        height: Int,
        bodyHeightM: Double,
        angleRad: Double,
        measuredAngle: Boolean,
        threshold: Float
    ): Silhouette? {
        val minRowPixels = max(2, width / 120)
        val left = IntArray(height) { width }
        val right = IntArray(height) { -1 }
        val count = IntArray(height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                if (data[offset + x] >= threshold) {
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
        val subjectHeightPx = bottom - top + 1
        if (subjectHeightPx < height * 0.20) return null

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

        repeat(2) {
            val copy = centers.copyOf()
            for (y in max(1, top) until min(height - 1, bottom + 1)) {
                centers[y] = (copy[y - 1] + copy[y] + copy[y + 1]) / 3.0f
            }
        }

        val pxPerMeter = subjectHeightPx.toDouble() / bodyHeightM
        val maxSpanMeters = maxSpanPx.toDouble() / pxPerMeter

        return Silhouette(
            width = width,
            height = height,
            probability = data,
            top = top,
            bottom = bottom,
            rowCenter = centers,
            pxPerMeter = pxPerMeter,
            maxSpanMeters = maxSpanMeters,
            threshold = threshold,
            angleRad = angleRad,
            measuredAngle = measuredAngle
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

    private fun decodeScaled(path: String, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension * 2) {
            sample *= 2
        }

        return BitmapFactory.decodeFile(
            path,
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
        humanSegmenter.close()
        objectSegmenter.close()
    }

}
