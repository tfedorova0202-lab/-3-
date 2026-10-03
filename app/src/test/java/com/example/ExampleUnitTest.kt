package com.example

import com.example.engine.DistanceTransform
import com.example.engine.MeshBuilder
import com.example.engine.StlExporter
import com.example.model.PrintSettings
import com.example.model.ShapeProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExampleUnitTest {

    @Test
    fun testDistanceTransformAndCylindricalInflation() {
        // Create 10x10 mask with 6x6 object in center
        val rows = 10
        val cols = 10
        val mask = ByteArray(rows * cols)
        for (r in 2..7) {
            for (c in 2..7) {
                mask[r * cols + c] = 1.toByte()
            }
        }

        val dist = DistanceTransform.computeDistanceField(mask, rows, cols)
        // Center pixels (4,4) should have higher distance than perimeter (2,2)
        val centerDist = dist[4 * cols + 4]
        val edgeDist = dist[2 * cols + 2]
        assertTrue("Center distance ($centerDist) must be greater than edge distance ($edgeDist)", centerDist > edgeDist)

        val detail = FloatArray(rows * cols) { 0.5f }
        val heightMap = DistanceTransform.shapeFromSilhouette(
            distField = dist,
            mask = mask,
            detailTexture = detail,
            rows = rows,
            cols = cols,
            profile = ShapeProfile.CYLINDRICAL,
            detailAmount = 0.2f
        )

        assertTrue("Center height should be positive and close to 1", heightMap[4 * cols + 4] > 0.8f)
        assertEquals("Background pixel must have zero height", 0f, heightMap[0], 0.001f)
    }

    @Test
    fun testMeshBuilderGeneratesWatertightSolid() {
        val rows = 8
        val cols = 8
        val mask = ByteArray(rows * cols) { 1.toByte() }
        val heightMap = FloatArray(rows * cols) { 0.8f }
        val settings = PrintSettings(
            widthMm = 50f,
            reliefMm = 10f,
            baseMm = 2f
        )

        val mesh = MeshBuilder.buildMesh(heightMap, mask, rows, cols, settings)

        assertTrue("Mesh must contain triangles", mesh.triangleCount > 0)
        assertTrue("Mesh volume must be strictly positive", mesh.volumeCm3 > 0f)
        assertTrue("Estimated weight must be positive", mesh.estimatedWeightGrams > 0f)
        assertEquals(50f, mesh.widthMm, 0.01f)
    }

    @Test
    fun testBinaryStlExportFormat() {
        val rows = 4
        val cols = 4
        val mask = ByteArray(rows * cols) { 1.toByte() }
        val heightMap = FloatArray(rows * cols) { 0.5f }
        val settings = PrintSettings(widthMm = 30f, reliefMm = 5f, baseMm = 1f)

        val mesh = MeshBuilder.buildMesh(heightMap, mask, rows, cols, settings)
        val stlBytes = StlExporter.createBinaryStlBytes(mesh)

        // Expected binary STL byte size: 84 header + 50 * triangleCount
        val expectedSize = 84 + 50 * mesh.triangleCount
        assertEquals(expectedSize, stlBytes.size)

        val buffer = ByteBuffer.wrap(stlBytes).order(ByteOrder.LITTLE_ENDIAN)
        // Check triangle count in header
        buffer.position(80)
        val countInHeader = buffer.int
        assertEquals(mesh.triangleCount, countInHeader)
    }
}
