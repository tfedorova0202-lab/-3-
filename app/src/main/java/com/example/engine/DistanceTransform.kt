package com.example.engine

import com.example.model.ShapeProfile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 2D Distance Transform and Shape from Silhouette inflation engine.
 * Solves the critical issue where black objects (like a blowtorch) collapse into holes in naive heightmaps.
 * Generates true volumetric 3D cross-sections from isolated object boundaries.
 */
object DistanceTransform {

    /**
     * Calculates distance from every foreground pixel to the nearest background pixel.
     * Uses 2-pass 8-connected Chamfer Distance metric.
     */
    fun computeDistanceField(
        mask: ByteArray,
        rows: Int,
        cols: Int
    ): FloatArray {
        val d = FloatArray(rows * cols)
        val infinity = 1e7f
        for (i in d.indices) {
            d[i] = if (mask[i].toInt() != 0) infinity else 0f
        }

        val getVal = { r: Int, c: Int ->
            if (r in 0 until rows && c in 0 until cols) d[r * cols + c] else 0f
        }

        val sqrt2 = 1.41421356f

        // Forward pass: top-left to bottom-right
        for (r in 0 until rows) {
            val rOffset = r * cols
            for (c in 0 until cols) {
                val idx = rOffset + c
                if (mask[idx].toInt() != 0) {
                    var minD = d[idx]
                    val up = getVal(r - 1, c) + 1f
                    val left = getVal(r, c - 1) + 1f
                    val upLeft = getVal(r - 1, c - 1) + sqrt2
                    val upRight = getVal(r - 1, c + 1) + sqrt2

                    if (up < minD) minD = up
                    if (left < minD) minD = left
                    if (upLeft < minD) minD = upLeft
                    if (upRight < minD) minD = upRight

                    d[idx] = minD
                }
            }
        }

        // Backward pass: bottom-right to top-left
        for (r in rows - 1 downTo 0) {
            val rOffset = r * cols
            for (c in cols - 1 downTo 0) {
                val idx = rOffset + c
                if (mask[idx].toInt() != 0) {
                    var minD = d[idx]
                    val down = getVal(r + 1, c) + 1f
                    val right = getVal(r, c + 1) + 1f
                    val downRight = getVal(r + 1, c + 1) + sqrt2
                    val downLeft = getVal(r + 1, c - 1) + sqrt2

                    if (down < minD) minD = down
                    if (right < minD) minD = right
                    if (downRight < minD) minD = downRight
                    if (downLeft < minD) minD = downLeft

                    d[idx] = minD
                }
            }
        }

        return d
    }

    /**
     * Generates a 0.0 .. 1.0 height map from the object silhouette and distance field,
     * infused with high-frequency surface detail (e.g. seams, screws, switches) without
     * letting black plastic color collapse the volumetric shape.
     */
    fun shapeFromSilhouette(
        distField: FloatArray,
        mask: ByteArray,
        detailTexture: FloatArray, // 0..1 high-pass / normalized texture details
        rows: Int,
        cols: Int,
        profile: ShapeProfile,
        detailAmount: Float
    ): FloatArray {
        var maxD = 1f
        for (v in distField) {
            if (v < 1e6f && v > maxD) {
                maxD = v
            }
        }

        val outHeight = FloatArray(rows * cols)

        for (k in outHeight.indices) {
            if (mask[k].toInt() != 0) {
                // Normalized distance: 0 at edge, 1 at center axis
                val t = (distField[k] / maxD).coerceIn(0f, 1f)

                // Base volume shape based on profile
                val baseVolume = when (profile) {
                    ShapeProfile.CYLINDRICAL -> {
                        // Semicircle profile cross-section: sqrt(1 - (1-t)^2)
                        val inv = 1f - t
                        sqrt(max(0f, 1f - inv * inv))
                    }
                    ShapeProfile.DOME -> {
                        // Smooth parabolic / cosine dome
                        1f - (1f - t) * (1f - t)
                    }
                    ShapeProfile.CHAMFER -> {
                        // 45-degree bevel up to 25% inwards, then flat plateau
                        min(1f, t / 0.28f)
                    }
                    ShapeProfile.FLAT -> {
                        // Constant flat height with slight edge rounding
                        if (t > 0.05f) 1f else (t / 0.05f)
                    }
                }

                // Add embossed surface texture: detailTexture is centered around 0.5f
                val detailOffset = (detailTexture[k] - 0.5f) * 2f // -1 .. +1
                val modulated = baseVolume + (detailOffset * detailAmount * baseVolume)

                outHeight[k] = modulated.coerceIn(0f, 1.2f)
            } else {
                outHeight[k] = 0f
            }
        }

        return outHeight
    }
}
