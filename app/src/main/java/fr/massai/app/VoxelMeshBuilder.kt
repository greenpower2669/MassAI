package fr.massai.app

data class Mesh3(
    val vertices: FloatArray,
    val indices: IntArray
) {
    val vertexCount: Int
        get() = vertices.size / 3

    val triangleCount: Int
        get() = indices.size / 3

    companion object {
        val EMPTY = Mesh3(FloatArray(0), IntArray(0))
    }
}

object VoxelMeshBuilder {

    fun build(
        occupancy: BooleanArray,
        nx: Int,
        ny: Int,
        nz: Int,
        halfExtentM: Double,
        heightM: Double
    ): Mesh3 {
        if (occupancy.isEmpty() || !occupancy.any { it }) return Mesh3.EMPTY

        val vertices = FloatBuilder()
        val indices = IntBuilder()

        val dx = (2.0 * halfExtentM / nx.toDouble()).toFloat()
        val dy = (heightM / ny.toDouble()).toFloat()
        val dz = (2.0 * halfExtentM / nz.toDouble()).toFloat()
        val minX = -halfExtentM.toFloat()
        val minZ = -halfExtentM.toFloat()

        fun occupied(x: Int, y: Int, z: Int): Boolean {
            if (x !in 0 until nx || y !in 0 until ny || z !in 0 until nz) return false
            return occupancy[(y * nx + x) * nz + z]
        }

        fun addVertex(x: Float, y: Float, z: Float): Int {
            val index = vertices.size / 3
            vertices.add(x)
            vertices.add(y)
            vertices.add(z)
            return index
        }

        fun addQuad(
            ax: Float, ay: Float, az: Float,
            bx: Float, by: Float, bz: Float,
            cx: Float, cy: Float, cz: Float,
            dxv: Float, dyv: Float, dzv: Float
        ) {
            val a = addVertex(ax, ay, az)
            val b = addVertex(bx, by, bz)
            val c = addVertex(cx, cy, cz)
            val d = addVertex(dxv, dyv, dzv)

            indices.add(a)
            indices.add(b)
            indices.add(c)
            indices.add(a)
            indices.add(c)
            indices.add(d)
        }

        for (y in 0 until ny) {
            val y0 = y * dy
            val y1 = y0 + dy

            for (x in 0 until nx) {
                val x0 = minX + x * dx
                val x1 = x0 + dx

                for (z in 0 until nz) {
                    if (!occupied(x, y, z)) continue

                    val z0 = minZ + z * dz
                    val z1 = z0 + dz

                    if (!occupied(x - 1, y, z)) {
                        addQuad(
                            x0, y0, z1,
                            x0, y0, z0,
                            x0, y1, z0,
                            x0, y1, z1
                        )
                    }

                    if (!occupied(x + 1, y, z)) {
                        addQuad(
                            x1, y0, z0,
                            x1, y0, z1,
                            x1, y1, z1,
                            x1, y1, z0
                        )
                    }

                    if (!occupied(x, y - 1, z)) {
                        addQuad(
                            x0, y0, z0,
                            x1, y0, z0,
                            x1, y0, z1,
                            x0, y0, z1
                        )
                    }

                    if (!occupied(x, y + 1, z)) {
                        addQuad(
                            x0, y1, z1,
                            x1, y1, z1,
                            x1, y1, z0,
                            x0, y1, z0
                        )
                    }

                    if (!occupied(x, y, z - 1)) {
                        addQuad(
                            x0, y0, z0,
                            x0, y1, z0,
                            x1, y1, z0,
                            x1, y0, z0
                        )
                    }

                    if (!occupied(x, y, z + 1)) {
                        addQuad(
                            x1, y0, z1,
                            x1, y1, z1,
                            x0, y1, z1,
                            x0, y0, z1
                        )
                    }
                }
            }
        }

        return Mesh3(vertices.toArray(), indices.toArray())
    }

    private class FloatBuilder(initialCapacity: Int = 8192) {
        private var data = FloatArray(initialCapacity)
        var size: Int = 0
            private set

        fun add(value: Float) {
            ensure(size + 1)
            data[size++] = value
        }

        fun toArray(): FloatArray = data.copyOf(size)

        private fun ensure(required: Int) {
            if (required <= data.size) return
            var next = data.size.coerceAtLeast(1)
            while (next < required) next *= 2
            data = data.copyOf(next)
        }
    }

    private class IntBuilder(initialCapacity: Int = 8192) {
        private var data = IntArray(initialCapacity)
        var size: Int = 0
            private set

        fun add(value: Int) {
            ensure(size + 1)
            data[size++] = value
        }

        fun toArray(): IntArray = data.copyOf(size)

        private fun ensure(required: Int) {
            if (required <= data.size) return
            var next = data.size.coerceAtLeast(1)
            while (next < required) next *= 2
            data = data.copyOf(next)
        }
    }
}
