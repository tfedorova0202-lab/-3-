package com.example.model

/**
 * Represents a generated 3D triangular mesh ready for preview and STL export.
 */
data class Mesh3D(
    val positions: FloatArray, // x, y, z triplets for each vertex in each triangle (size = numTriangles * 9)
    val normals: FloatArray,   // nx, ny, nz triplets for each vertex (size = numTriangles * 9)
    val triangleCount: Int,
    val widthMm: Float,
    val heightMm: Float,
    val thicknessMm: Float,
    val stepMm: Float,
    val volumeCm3: Float,
    val estimatedWeightGrams: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Mesh3D
        return triangleCount == other.triangleCount &&
                widthMm == other.widthMm &&
                heightMm == other.heightMm &&
                thicknessMm == other.thicknessMm
    }

    override fun hashCode(): Int {
        var result = triangleCount
        result = 31 * result + widthMm.hashCode()
        result = 31 * result + heightMm.hashCode()
        result = 31 * result + thicknessMm.hashCode()
        return result
    }
}

/**
 * 3D Bounding box for viewport scaling and centering
 */
data class BoundingBox(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val minZ: Float,
    val maxZ: Float
) {
    val sizeX: Float get() = maxX - minX
    val sizeY: Float get() = maxY - minY
    val sizeZ: Float get() = maxZ - minZ
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f
    val centerZ: Float get() = (minZ + maxZ) / 2f
    val maxDimension: Float get() = maxOf(sizeX, sizeY, sizeZ)
}
