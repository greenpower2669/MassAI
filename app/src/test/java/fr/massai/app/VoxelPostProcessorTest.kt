package fr.massai.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoxelPostProcessorTest {

    @Test
    fun removesTinyIslandAndKeepsMainBody() {
        val nx = 10
        val ny = 10
        val nz = 10
        val voxels = BooleanArray(nx * ny * nz)

        fun index(x: Int, y: Int, z: Int) = (y * nx + x) * nz + z

        // Bloc principal 5 x 5 x 5 = 125 voxels.
        for (y in 2..6) {
            for (x in 2..6) {
                for (z in 2..6) {
                    voxels[index(x, y, z)] = true
                }
            }
        }

        // Petit îlot parasite isolé.
        voxels[index(9, 9, 9)] = true

        val result = VoxelPostProcessor.cleanup(voxels, nx, ny, nz)

        assertTrue(result.occupancy[index(4, 4, 4)])
        assertFalse(result.occupancy[index(9, 9, 9)])
        assertEquals(1, result.removedVoxels)
        assertEquals(2, result.componentsBefore)
        assertEquals(1, result.componentsAfter)
    }

    @Test
    fun preservesSecondLargeComponent() {
        val nx = 20
        val ny = 10
        val nz = 10
        val voxels = BooleanArray(nx * ny * nz)

        fun index(x: Int, y: Int, z: Int) = (y * nx + x) * nz + z

        // Deux volumes significatifs séparés.
        for (y in 1..5) {
            for (x in 1..5) {
                for (z in 1..5) {
                    voxels[index(x, y, z)] = true
                }
            }
        }
        for (y in 1..5) {
            for (x in 12..16) {
                for (z in 1..5) {
                    voxels[index(x, y, z)] = true
                }
            }
        }

        val result = VoxelPostProcessor.cleanup(voxels, nx, ny, nz)

        assertTrue(result.occupancy[index(3, 3, 3)])
        assertTrue(result.occupancy[index(14, 3, 3)])
        assertEquals(0, result.removedVoxels)
        assertEquals(2, result.componentsAfter)
    }
}
