package com.example.engine

import com.example.model.Mesh3D
import com.example.model.PrintSettings
import com.example.model.SolidType
import kotlin.math.hypot
import kotlin.math.max

object MeshBuilder {

    /**
     * Builds a manifold, watertight 3D solid mesh with flat base and side walls.
     */
    fun buildMesh(
        heightMap: FloatArray,
        mask: ByteArray,
        rows: Int,
        cols: Int,
        settings: PrintSettings
    ): Mesh3D {
        val step = settings.widthMm / (cols - 1).toFloat()
        val heightMm = step * (rows - 1)

        val inV = { r: Int, c: Int ->
            r in 0 until rows && c in 0 until cols && mask[r * cols + c].toInt() != 0
        }

        // Cell is active if all 4 corner vertices are within mask
        val inC = { r: Int, c: Int ->
            r in 0 until rows - 1 && c in 0 until cols - 1 &&
                    inV(r, c) && inV(r, c + 1) && inV(r + 1, c) && inV(r + 1, c + 1)
        }

        var numActiveCells = 0
        var numBoundaryWalls = 0

        for (r in 0 until rows - 1) {
            for (c in 0 until cols - 1) {
                if (inC(r, c)) {
                    numActiveCells++
                    if (!inC(r - 1, c)) numBoundaryWalls++
                    if (!inC(r + 1, c)) numBoundaryWalls++
                    if (!inC(r, c - 1)) numBoundaryWalls++
                    if (!inC(r, c + 1)) numBoundaryWalls++
                }
            }
        }

        // 2 triangles top + 2 triangles bottom = 4 triangles per cell
        // 2 triangles per boundary wall quad
        val totalTriangles = 4 * numActiveCells + 2 * numBoundaryWalls
        if (totalTriangles == 0) {
            return Mesh3D(
                positions = FloatArray(0),
                normals = FloatArray(0),
                triangleCount = 0,
                widthMm = settings.widthMm,
                heightMm = heightMm,
                thicknessMm = settings.baseMm + settings.reliefMm,
                stepMm = step,
                volumeCm3 = 0f,
                estimatedWeightGrams = 0f
            )
        }

        val positions = FloatArray(totalTriangles * 9)
        val normals = FloatArray(totalTriangles * 9)
        var pIdx = 0

        val putVertex = { x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float ->
            positions[pIdx] = x
            positions[pIdx + 1] = y
            positions[pIdx + 2] = z
            normals[pIdx] = nx
            normals[pIdx + 1] = ny
            normals[pIdx + 2] = nz
            pIdx += 3
        }

        val zGrid = FloatArray(rows * cols)
        var maxZ = 0f
        for (i in zGrid.indices) {
            val z = settings.baseMm + heightMap[i] * settings.reliefMm
            zGrid[i] = z
            if (mask[i].toInt() != 0 && z > maxZ) {
                maxZ = z
            }
        }

        // Coordinate transforms: center around (0, 0)
        val halfW = settings.widthMm / 2f
        val halfH = heightMm / 2f
        val xCoord = { c: Int -> c * step - halfW }
        val yCoord = { r: Int -> (rows - 1 - r) * step - halfH }

        // Compute smooth top normals
        val vNormals = FloatArray(rows * cols * 3)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val rPrev = max(r - 1, 0)
                val rNext = kotlin.math.min(r + 1, rows - 1)
                val cPrev = max(c - 1, 0)
                val cNext = kotlin.math.min(c + 1, cols - 1)

                val dzdx = (zGrid[r * cols + cNext] - zGrid[r * cols + cPrev]) / ((cNext - cPrev).coerceAtLeast(1) * step)
                val dzdy = -(zGrid[rNext * cols + c] - zGrid[rPrev * cols + c]) / ((rNext - rPrev).coerceAtLeast(1) * step)

                val len = hypot(hypot(dzdx, dzdy), 1f)
                val o = (r * cols + c) * 3
                vNormals[o] = -dzdx / len
                vNormals[o + 1] = -dzdy / len
                vNormals[o + 2] = 1f / len
            }
        }

        val putTop = { r: Int, c: Int ->
            val idx = r * cols + c
            val o = idx * 3
            putVertex(xCoord(c), yCoord(r), zGrid[idx], vNormals[o], vNormals[o + 1], vNormals[o + 2])
        }

        val putBottom = { r: Int, c: Int ->
            val bottomZ = if (settings.solidType == SolidType.DUAL_SIDED) {
                // Symmetrical lower half
                -(zGrid[r * cols + c] - settings.baseMm)
            } else {
                0f // Flat printing base
            }
            putVertex(xCoord(c), yCoord(r), bottomZ, 0f, 0f, -1f)
        }

        val putWall = { r0: Int, c0: Int, r1: Int, c1: Int ->
            val x0 = xCoord(c0); val y0 = yCoord(r0); val z0 = zGrid[r0 * cols + c0]
            val x1 = xCoord(c1); val y1 = yCoord(r1); val z1 = zGrid[r1 * cols + c1]

            val baseZ0 = if (settings.solidType == SolidType.DUAL_SIDED) -(z0 - settings.baseMm) else 0f
            val baseZ1 = if (settings.solidType == SolidType.DUAL_SIDED) -(z1 - settings.baseMm) else 0f

            val dx = x1 - x0
            val dy = y1 - y0
            val len = hypot(dx, dy).coerceAtLeast(0.001f)
            val nx = -dy / len
            val ny = dx / len

            // Quad as 2 triangles (outward normal)
            putVertex(x0, y0, z0, nx, ny, 0f)
            putVertex(x1, y1, z1, nx, ny, 0f)
            putVertex(x1, y1, baseZ1, nx, ny, 0f)

            putVertex(x0, y0, z0, nx, ny, 0f)
            putVertex(x1, y1, baseZ1, nx, ny, 0f)
            putVertex(x0, y0, baseZ0, nx, ny, 0f)
        }

        // Generate geometry for each active cell
        for (r in 0 until rows - 1) {
            for (c in 0 until cols - 1) {
                if (inC(r, c)) {
                    // Top triangles (CCW winding)
                    putTop(r, c)
                    putTop(r + 1, c + 1)
                    putTop(r, c + 1)

                    putTop(r, c)
                    putTop(r + 1, c)
                    putTop(r + 1, c + 1)

                    // Bottom triangles (CW winding for normal down)
                    putBottom(r, c)
                    putBottom(r, c + 1)
                    putBottom(r + 1, c + 1)

                    putBottom(r, c)
                    putBottom(r + 1, c + 1)
                    putBottom(r + 1, c)

                    // Perimeter walls where neighbor is absent
                    if (!inC(r - 1, c)) putWall(r, c, r, c + 1)         // North
                    if (!inC(r, c + 1)) putWall(r, c + 1, r + 1, c + 1) // East
                    if (!inC(r + 1, c)) putWall(r + 1, c + 1, r + 1, c) // South
                    if (!inC(r, c - 1)) putWall(r + 1, c, r, c)         // West
                }
            }
        }

        // Calculate exact mesh volume using signed tetrahedra
        val volumeMm3 = calculateSignedVolume(positions)
        val volumeCm3 = max(0f, volumeMm3 / 1000f)
        val effectiveDensity = settings.materialDensityGcm3 * (settings.infillPercent / 100f).coerceIn(0.1f, 1f)
        val massGrams = volumeCm3 * effectiveDensity

        return Mesh3D(
            positions = positions,
            normals = normals,
            triangleCount = totalTriangles,
            widthMm = settings.widthMm,
            heightMm = heightMm,
            thicknessMm = maxZ,
            stepMm = step,
            volumeCm3 = volumeCm3,
            estimatedWeightGrams = massGrams
        )
    }

    private fun calculateSignedVolume(pos: FloatArray): Float {
        var v = 0.0
        var i = 0
        while (i < pos.size) {
            val ax = pos[i].toDouble(); val ay = pos[i + 1].toDouble(); val az = pos[i + 2].toDouble()
            val bx = pos[i + 3].toDouble(); val by = pos[i + 4].toDouble(); val bz = pos[i + 5].toDouble()
            val cx = pos[i + 6].toDouble(); val cy = pos[i + 7].toDouble(); val cz = pos[i + 8].toDouble()

            v += ax * (by * cz - bz * cy) -
                    ay * (bx * cz - bz * cx) +
                    az * (bx * cy - by * cx)
            i += 9
        }
        return (v / 6.0).toFloat()
    }
}
