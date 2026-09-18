package fr.massai.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoxelMeshBuilderTest {

    @Test
    fun singleVoxelBuildsClosedTriangleMesh() {
        val mesh = VoxelMeshBuilder.build(
            occupancy = booleanArrayOf(true),
            nx = 1,
            ny = 1,
            nz = 1,
            halfExtentM = 0.5,
            heightM = 1.0
        )

        assertEquals(24, mesh.vertexCount)
        assertEquals(12, mesh.triangleCount)
        assertTrue(mesh.vertices.all { it in -0.5f..1.0f })
    }

    @Test
    fun adjacentVoxelsDoNotKeepInternalFace() {
        val mesh = VoxelMeshBuilder.build(
            occupancy = booleanArrayOf(true, true),
            nx = 1,
            ny = 1,
            nz = 2,
            halfExtentM = 0.5,
            heightM = 1.0
        )

        // Deux cubes collés = 10 faces externes, soit 20 triangles.
        assertEquals(20, mesh.triangleCount)
    }
}
