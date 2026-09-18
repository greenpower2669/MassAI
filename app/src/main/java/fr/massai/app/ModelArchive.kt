package fr.massai.app

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object ModelArchive {

    private const val MAGIC = 0x4D415338
    private const val VERSION = 1
    private const val MAX_POINTS = 2_000_000
    private const val MAX_FLOATS = 20_000_000
    private const val MAX_INDICES = 20_000_000

    fun write(model: BodyReconstruction, output: OutputStream) {
        DataOutputStream(
            GZIPOutputStream(BufferedOutputStream(output))
        ).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)

            out.writeDouble(model.rawVolumeM3)
            out.writeDouble(model.repairedVolumeM3)
            out.writeInt(model.validViews)
            out.writeInt(model.totalViews)
            out.writeDouble(model.repairFraction)
            out.writeInt(model.quality)
            out.writeDouble(model.heightM)
            out.writeDouble(model.halfExtentM)
            out.writeDouble(model.angularCoverageDeg)
            out.writeBoolean(model.anglesMeasured)
            out.writeDouble(model.meanBlurScore)
            out.writeInt(model.removedIslandVoxels)
            out.writeInt(model.componentsBeforeCleanup)
            out.writeInt(model.componentsAfterCleanup)

            writePoints(out, model.rawSurface)
            writePoints(out, model.repairedSurface)
            writePoints(out, model.corrections)
            writeMesh(out, model.rawMesh)
            writeMesh(out, model.repairedMesh)
        }
    }

    fun read(input: InputStream): BodyReconstruction {
        DataInputStream(
            GZIPInputStream(BufferedInputStream(input))
        ).use { source ->
            require(source.readInt() == MAGIC) { "Ce fichier n'est pas un modèle MassAI." }
            val version = source.readInt()
            require(version == VERSION) {
                "Version de modèle MassAI non prise en charge : $version"
            }

            val rawVolumeM3 = source.readDouble()
            val repairedVolumeM3 = source.readDouble()
            val validViews = source.readInt()
            val totalViews = source.readInt()
            val repairFraction = source.readDouble()
            val quality = source.readInt()
            val heightM = source.readDouble()
            val halfExtentM = source.readDouble()
            val angularCoverageDeg = source.readDouble()
            val anglesMeasured = source.readBoolean()
            val meanBlurScore = source.readDouble()
            val removedIslandVoxels = source.readInt()
            val componentsBeforeCleanup = source.readInt()
            val componentsAfterCleanup = source.readInt()

            val rawSurface = readPoints(source)
            val repairedSurface = readPoints(source)
            val corrections = readPoints(source)
            val rawMesh = readMesh(source)
            val repairedMesh = readMesh(source)

            return BodyReconstruction(
                rawSurface = rawSurface,
                repairedSurface = repairedSurface,
                corrections = corrections,
                rawMesh = rawMesh,
                repairedMesh = repairedMesh,
                rawVolumeM3 = rawVolumeM3,
                repairedVolumeM3 = repairedVolumeM3,
                validViews = validViews,
                totalViews = totalViews,
                repairFraction = repairFraction,
                quality = quality,
                heightM = heightM,
                halfExtentM = halfExtentM,
                angularCoverageDeg = angularCoverageDeg,
                anglesMeasured = anglesMeasured,
                meanBlurScore = meanBlurScore,
                removedIslandVoxels = removedIslandVoxels,
                componentsBeforeCleanup = componentsBeforeCleanup,
                componentsAfterCleanup = componentsAfterCleanup
            )
        }
    }

    fun writeObj(mesh: Mesh3, output: OutputStream) {
        OutputStreamWriter(BufferedOutputStream(output), Charsets.UTF_8).use { writer ->
            writer.write("# MassAI triangle mesh\n")
            writer.write("# vertices ${mesh.vertexCount} triangles ${mesh.triangleCount}\n")

            var i = 0
            while (i + 2 < mesh.vertices.size) {
                writer.write(
                    String.format(
                        Locale.US,
                        "v %.6f %.6f %.6f\n",
                        mesh.vertices[i],
                        mesh.vertices[i + 1],
                        mesh.vertices[i + 2]
                    )
                )
                i += 3
            }

            i = 0
            while (i + 2 < mesh.indices.size) {
                writer.write(
                    "f ${mesh.indices[i] + 1} ${mesh.indices[i + 1] + 1} ${mesh.indices[i + 2] + 1}\n"
                )
                i += 3
            }
        }
    }

    private fun writePoints(out: DataOutputStream, points: List<Point3>) {
        out.writeInt(points.size)
        points.forEach { point ->
            out.writeFloat(point.x)
            out.writeFloat(point.y)
            out.writeFloat(point.z)
        }
    }

    private fun readPoints(input: DataInputStream): List<Point3> {
        val count = input.readInt()
        require(count in 0..MAX_POINTS) { "Nombre de points invalide : $count" }

        return ArrayList<Point3>(count).apply {
            repeat(count) {
                add(
                    Point3(
                        input.readFloat(),
                        input.readFloat(),
                        input.readFloat()
                    )
                )
            }
        }
    }

    private fun writeMesh(out: DataOutputStream, mesh: Mesh3) {
        out.writeInt(mesh.vertices.size)
        mesh.vertices.forEach(out::writeFloat)
        out.writeInt(mesh.indices.size)
        mesh.indices.forEach(out::writeInt)
    }

    private fun readMesh(input: DataInputStream): Mesh3 {
        val floatCount = input.readInt()
        require(floatCount in 0..MAX_FLOATS && floatCount % 3 == 0) {
            "Données de sommets invalides : $floatCount"
        }
        val vertices = FloatArray(floatCount) { input.readFloat() }

        val indexCount = input.readInt()
        require(indexCount in 0..MAX_INDICES && indexCount % 3 == 0) {
            "Données de triangles invalides : $indexCount"
        }
        val indices = IntArray(indexCount) { input.readInt() }

        require(
            indices.isEmpty() ||
                indices.all { it in 0 until (vertices.size / 3).coerceAtLeast(1) }
        ) {
            "Indices du mesh invalides."
        }

        return Mesh3(vertices, indices)
    }
}
