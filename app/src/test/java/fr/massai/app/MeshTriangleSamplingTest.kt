package fr.massai.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshTriangleSamplingTest {
    @Test fun decimationKeepsWholeFaces() {
        val result = MeshTriangleSampling.indices(120, 20)
        assertTrue(result.size <= 20)
        assertTrue(result.size % 2 == 0)
        result.asList().chunked(2).forEach {
            assertTrue(it[0] % 2 == 0)
            assertTrue(it[1] == it[0] + 1)
        }
    }
    @Test fun smallMeshPreserved() {
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4, 5), MeshTriangleSampling.indices(6, 18))
    }
    @Test fun emptyMeshIsEmpty() {
        assertArrayEquals(intArrayOf(), MeshTriangleSampling.indices(0, 18))
    }
}
