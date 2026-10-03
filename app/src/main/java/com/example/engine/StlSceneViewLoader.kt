package com.example.engine

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.example.model.Mesh3D
import dev.romainguy.kotlin.math.Float2
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Float4
import io.github.sceneview.geometries.Geometry
import io.github.sceneview.node.GeometryNode
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object StlSceneViewLoader {

    /**
     * Creates a SceneView GeometryNode from an in-memory Mesh3D.
     */
    fun createNodeFromMesh(
        engine: Engine,
        materialInstance: MaterialInstance,
        mesh: Mesh3D
    ): GeometryNode {
        val pos = mesh.positions
        val norms = mesh.normals
        val numTris = mesh.triangleCount

        val defaultUv = Float2(0f, 0f)
        val defaultColor = Float4(1f, 1f, 1f, 1f)

        // Scale down from mm to meter/decimeter units so it fits comfortably in SceneView viewport
        val maxDim = maxOf(mesh.widthMm, mesh.heightMm, mesh.thicknessMm).coerceAtLeast(1f)
        val scaleFactor = 1.0f / maxDim // Normalize to ~1.0 unit box

        val vertices = ArrayList<Geometry.Vertex>(numTris * 3)
        val indices = ArrayList<Int>(numTris * 3)

        var pIdx = 0
        var vertCount = 0

        while (pIdx < pos.size) {
            val v1x = pos[pIdx] * scaleFactor; val v1y = pos[pIdx + 1] * scaleFactor; val v1z = pos[pIdx + 2] * scaleFactor
            val n1x = norms[pIdx]; val n1y = norms[pIdx + 1]; val n1z = norms[pIdx + 2]

            val v2x = pos[pIdx + 3] * scaleFactor; val v2y = pos[pIdx + 4] * scaleFactor; val v2z = pos[pIdx + 5] * scaleFactor
            val n2x = norms[pIdx + 3]; val n2y = norms[pIdx + 4]; val n2z = norms[pIdx + 5]

            val v3x = pos[pIdx + 6] * scaleFactor; val v3y = pos[pIdx + 7] * scaleFactor; val v3z = pos[pIdx + 8] * scaleFactor
            val n3x = norms[pIdx + 6]; val n3y = norms[pIdx + 7]; val n3z = norms[pIdx + 8]

            vertices.add(Geometry.Vertex(Float3(v1x, v1z, -v1y), Float3(n1x, n1z, -n1y), defaultUv, defaultColor))
            vertices.add(Geometry.Vertex(Float3(v2x, v2z, -v2y), Float3(n2x, n2z, -n2y), defaultUv, defaultColor))
            vertices.add(Geometry.Vertex(Float3(v3x, v3z, -v3y), Float3(n3x, n3z, -n3y), defaultUv, defaultColor))

            indices.add(vertCount++)
            indices.add(vertCount++)
            indices.add(vertCount++)

            pIdx += 9
        }

        val geometry = Geometry.Builder(RenderableManager.PrimitiveType.TRIANGLES)
            .vertices(vertices)
            .indices(indices)
            .build(engine)

        return GeometryNode(
            engine = engine,
            geometry = geometry,
            materialInstance = materialInstance
        )
    }

    /**
     * Parses a binary STL file into a SceneView GeometryNode for direct inspection.
     */
    fun createNodeFromStlFile(
        engine: Engine,
        materialInstance: MaterialInstance,
        file: File
    ): GeometryNode? {
        if (!file.exists() || file.length() < 84) return null
        return try {
            val bytes = file.readBytes()
            createNodeFromStlBytes(engine, materialInstance, bytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parses a binary STL byte array into a SceneView GeometryNode.
     */
    fun createNodeFromStlBytes(
        engine: Engine,
        materialInstance: MaterialInstance,
        bytes: ByteArray
    ): GeometryNode? {
        if (bytes.size < 84) return null
        return try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(80)
            val numTris = buffer.int
            if (numTris <= 0 || bytes.size < 84 + numTris * 50) return null

            val defaultUv = Float2(0f, 0f)
            val defaultColor = Float4(1f, 1f, 1f, 1f)

            val vertices = ArrayList<Geometry.Vertex>(numTris * 3)
            val indices = ArrayList<Int>(numTris * 3)

            // Find bounds for normalization
            val tempPositions = FloatArray(numTris * 9)
            val tempNormals = FloatArray(numTris * 9)
            var maxDim = 1f

            var readIdx = 0
            for (t in 0 until numTris) {
                val nx = buffer.float; val ny = buffer.float; val nz = buffer.float
                val v1x = buffer.float; val v1y = buffer.float; val v1z = buffer.float
                val v2x = buffer.float; val v2y = buffer.float; val v2z = buffer.float
                val v3x = buffer.float; val v3y = buffer.float; val v3z = buffer.float
                buffer.short // attribute byte count

                tempPositions[readIdx] = v1x; tempPositions[readIdx + 1] = v1y; tempPositions[readIdx + 2] = v1z
                tempPositions[readIdx + 3] = v2x; tempPositions[readIdx + 4] = v2y; tempPositions[readIdx + 5] = v2z
                tempPositions[readIdx + 6] = v3x; tempPositions[readIdx + 7] = v3y; tempPositions[readIdx + 8] = v3z

                tempNormals[readIdx] = nx; tempNormals[readIdx + 1] = ny; tempNormals[readIdx + 2] = nz
                tempNormals[readIdx + 3] = nx; tempNormals[readIdx + 4] = ny; tempNormals[readIdx + 5] = nz
                tempNormals[readIdx + 6] = nx; tempNormals[readIdx + 7] = ny; tempNormals[readIdx + 8] = nz

                val maxCoord = maxOf(
                    kotlin.math.abs(v1x), kotlin.math.abs(v1y), kotlin.math.abs(v1z),
                    kotlin.math.abs(v2x), kotlin.math.abs(v2y), kotlin.math.abs(v2z),
                    kotlin.math.abs(v3x), kotlin.math.abs(v3y), kotlin.math.abs(v3z)
                )
                if (maxCoord > maxDim) maxDim = maxCoord

                readIdx += 9
            }

            val scale = 0.8f / maxDim
            var vIdx = 0
            for (i in 0 until numTris * 3) {
                val px = tempPositions[i * 3] * scale
                val py = tempPositions[i * 3 + 1] * scale
                val pz = tempPositions[i * 3 + 2] * scale

                val nx = tempNormals[i * 3]
                val ny = tempNormals[i * 3 + 1]
                val nz = tempNormals[i * 3 + 2]

                // Flip Y/Z to align with Filament world axes
                vertices.add(Geometry.Vertex(Float3(px, pz, -py), Float3(nx, nz, -ny), defaultUv, defaultColor))
                indices.add(vIdx++)
            }

            val geometry = Geometry.Builder(RenderableManager.PrimitiveType.TRIANGLES)
                .vertices(vertices)
                .indices(indices)
                .build(engine)

            GeometryNode(
                engine = engine,
                geometry = geometry,
                materialInstance = materialInstance
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
