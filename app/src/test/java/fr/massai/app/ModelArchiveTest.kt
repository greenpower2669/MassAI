package fr.massai.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelArchiveTest {

    @Test
    fun roundTripPreservesMeshAndMetrics() {
        val model = BodyReconstruction(
            rawSurface = listOf(Point3(0f, 0f, 0f)),
            repairedSurface = listOf(Point3(1f, 2f, 3f)),
            corrections = emptyList(),
            rawMesh = Mesh3(
                floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
                intArrayOf(0, 1, 2)
            ),
            repairedMesh = Mesh3(
                floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
                intArrayOf(0, 1, 2)
            ),
            rawVolumeM3 = 0.08,
            repairedVolumeM3 = 0.075,
            validViews = 20,
            totalViews = 24,
            repairFraction = 0.02,
            quality = 88,
            heightM = 1.78,
            halfExtentM = 0.4,
            angularCoverageDeg = 350.0,
            anglesMeasured = true,
            meanBlurScore = 25.0,
            removedIslandVoxels = 12,
            componentsBeforeCleanup = 4,
            componentsAfterCleanup = 1
        )

        val bytes = ByteArrayOutputStream()
        ModelArchive.write(model, bytes)
        val loaded = ModelArchive.read(ByteArrayInputStream(bytes.toByteArray()))

        assertEquals(model.repairedVolumeM3, loaded.repairedVolumeM3, 1e-12)
        assertEquals(1, loaded.repairedMesh.triangleCount)
        assertEquals(model.quality, loaded.quality)
        assertTrue(loaded.anglesMeasured)
    }
}
