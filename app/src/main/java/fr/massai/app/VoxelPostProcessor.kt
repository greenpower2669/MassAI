package fr.massai.app

import kotlin.math.max

data class VoxelCleanupResult(
    val occupancy: BooleanArray,
    val removedVoxels: Int,
    val componentsBefore: Int,
    val componentsAfter: Int
)

object VoxelPostProcessor {

    fun cleanup(
        source: BooleanArray,
        nx: Int,
        ny: Int,
        nz: Int
    ): VoxelCleanupResult {
        if (source.isEmpty()) {
            return VoxelCleanupResult(
                occupancy = source.copyOf(),
                removedVoxels = 0,
                componentsBefore = 0,
                componentsAfter = 0
            )
        }

        val components = connectedComponents(source, nx, ny, nz)
        if (components.isEmpty()) {
            return VoxelCleanupResult(
                occupancy = BooleanArray(source.size),
                removedVoxels = source.count { it },
                componentsBefore = 0,
                componentsAfter = 0
            )
        }

        val largestSize = components.maxOf { it.size }
        val occupiedCount = source.count { it }

        // Seuil conservateur :
        // - au moins 18 voxels ;
        // - au moins 0,20 % du volume occupé ;
        // - ou au moins 2 % de la plus grosse composante.
        //
        // Cela supprime les confettis sans imposer que tout le corps soit déjà
        // parfaitement connecté. Une jambe/bras séparé mais volumineux survit.
        val absoluteThreshold = max(18, occupiedCount / 500)
        val relativeThreshold = max(1, largestSize / 50)
        val keepThreshold = max(absoluteThreshold, relativeThreshold)

        val result = BooleanArray(source.size)
        var keptComponents = 0
        var keptVoxels = 0

        components.forEach { component ->
            if (component.size >= keepThreshold || component.size == largestSize) {
                keptComponents++
                component.indices.forEach { index ->
                    result[index] = true
                    keptVoxels++
                }
            }
        }

        return VoxelCleanupResult(
            occupancy = result,
            removedVoxels = (occupiedCount - keptVoxels).coerceAtLeast(0),
            componentsBefore = components.size,
            componentsAfter = keptComponents
        )
    }

    private data class Component(
        val indices: IntArray,
        val size: Int
    )

    private fun connectedComponents(
        source: BooleanArray,
        nx: Int,
        ny: Int,
        nz: Int
    ): List<Component> {
        val visited = BooleanArray(source.size)
        val queue = IntArray(source.size)
        val componentBuffer = IntArray(source.size)
        val components = ArrayList<Component>()

        for (start in source.indices) {
            if (!source[start] || visited[start]) continue

            var head = 0
            var tail = 0
            var count = 0

            queue[tail++] = start
            visited[start] = true

            while (head < tail) {
                val current = queue[head++]
                componentBuffer[count++] = current

                val yz = nx * nz
                val y = current / yz
                val rem = current % yz
                val x = rem / nz
                val z = rem % nz

                fun enqueue(index: Int) {
                    if (source[index] && !visited[index]) {
                        visited[index] = true
                        queue[tail++] = index
                    }
                }

                if (x > 0) enqueue(indexOf(x - 1, y, z, nx, nz))
                if (x < nx - 1) enqueue(indexOf(x + 1, y, z, nx, nz))
                if (y > 0) enqueue(indexOf(x, y - 1, z, nx, nz))
                if (y < ny - 1) enqueue(indexOf(x, y + 1, z, nx, nz))
                if (z > 0) enqueue(indexOf(x, y, z - 1, nx, nz))
                if (z < nz - 1) enqueue(indexOf(x, y, z + 1, nx, nz))
            }

            components += Component(
                indices = componentBuffer.copyOf(count),
                size = count
            )
        }

        return components
    }

    private fun indexOf(x: Int, y: Int, z: Int, nx: Int, nz: Int): Int {
        return (y * nx + x) * nz + z
    }
}
