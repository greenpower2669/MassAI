package fr.massai.app

import kotlin.math.ceil

/** Display-only sampling: the voxel builder emits each quad as two consecutive triangles. */
object MeshTriangleSampling {
    fun indices(triangleCount: Int, budget: Int): IntArray {
        if (triangleCount <= 0 || budget < 2) return IntArray(0)
        val pairs = triangleCount / 2
        if (pairs == 0) return intArrayOf(0)
        val maxPairs = (budget / 2).coerceAtLeast(1)
        val stride = ceil(pairs.toDouble() / maxPairs).toInt().coerceAtLeast(1)
        val pairCount = (pairs + stride - 1) / stride
        val appendOdd = triangleCount % 2 != 0 && stride == 1 && pairCount * 2 < budget
        val result = IntArray(pairCount * 2 + if (appendOdd) 1 else 0)
        var out = 0
        for (pair in 0 until pairs step stride) {
            result[out++] = pair * 2
            result[out++] = pair * 2 + 1
        }
        if (appendOdd) result[out] = triangleCount - 1
        return result
    }
}
