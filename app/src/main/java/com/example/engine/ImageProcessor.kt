package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import com.example.model.PrintSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ProcessedGrid(
    val rows: Int,
    val cols: Int,
    val rawLuminance: FloatArray,
    val detailTexture: FloatArray,
    val mask: ByteArray, // 1 for object, 0 for background
    val croppedWidthRatio: Float,
    val croppedHeightRatio: Float
)

object ImageProcessor {

    /**
     * Resizes bitmap smoothly to target resolution and extracts grid luminance & detail.
     */
    fun processBitmap(
        bitmap: Bitmap,
        polygonOutline: List<PointF>?,
        settings: PrintSettings
    ): ProcessedGrid {
        val origW = bitmap.width
        val origH = bitmap.height

        // Determine bounding box if polygon exists, else full frame
        var minX = 0f
        var maxX = 1f
        var minY = 0f
        var maxY = 1f

        if (!polygonOutline.isNullOrEmpty() && polygonOutline.size >= 3) {
            minX = polygonOutline.minOf { it.x }.coerceIn(0f, 1f)
            maxX = polygonOutline.maxOf { it.x }.coerceIn(0f, 1f)
            minY = polygonOutline.minOf { it.y }.coerceIn(0f, 1f)
            maxY = polygonOutline.maxOf { it.y }.coerceIn(0f, 1f)

            // Add 4% padding around selection
            val padX = 0.04f * (maxX - minX).coerceAtLeast(0.05f)
            val padY = 0.04f * (maxY - minY).coerceAtLeast(0.05f)
            minX = (minX - padX).coerceAtLeast(0f)
            maxX = (maxX + padX).coerceAtMost(1f)
            minY = (minY - padY).coerceAtLeast(0f)
            maxY = (maxY + padY).coerceAtMost(1f)
        }

        val cropW = ((maxX - minX) * origW).roundToInt().coerceAtLeast(10)
        val cropH = ((maxY - minY) * origH).roundToInt().coerceAtLeast(10)
        val cropX = (minX * origW).roundToInt().coerceIn(0, origW - cropW)
        val cropY = (minY * origH).roundToInt().coerceIn(0, origH - cropH)

        // Scale to settings.resolution
        val scale = min(1f, settings.resolution.toFloat() / max(cropW, cropH))
        val targetCols = (cropW * scale).roundToInt().coerceAtLeast(16)
        val targetRows = (cropH * scale).roundToInt().coerceAtLeast(16)

        val croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
        val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, targetCols, targetRows, true)

        val pixels = IntArray(targetCols * targetRows)
        scaledBitmap.getPixels(pixels, 0, targetCols, 0, 0, targetCols, targetRows)

        val rawLuminance = FloatArray(targetCols * targetRows)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            // Perceptual luminance Rec. 709
            val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
            rawLuminance[i] = lum
        }

        // Apply contrast & gamma
        val processedLum = FloatArray(rawLuminance.size)
        System.arraycopy(rawLuminance, 0, processedLum, 0, rawLuminance.size)

        if (settings.autoContrast) {
            applyAutoContrast(processedLum)
        }

        // Compute high-pass detail texture (raw minus box-blur)
        val blurred = boxBlur(processedLum, targetRows, targetCols, radius = 2)
        val detailTexture = FloatArray(processedLum.size)
        for (i in detailTexture.indices) {
            // High-frequency detail centered at 0.5
            detailTexture[i] = (processedLum[i] - blurred[i] + 0.5f).coerceIn(0f, 1f)
        }

        // Generate mask
        val mask = ByteArray(targetCols * targetRows)
        if (!polygonOutline.isNullOrEmpty() && polygonOutline.size >= 3) {
            rasterizePolygonToMask(
                polygonOutline = polygonOutline,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
                rows = targetRows,
                cols = targetCols,
                outMask = mask
            )
        } else {
            // No user outline: run automatic foreground isolation
            autoSegmentForeground(pixels, targetRows, targetCols, mask)
        }

        if (settings.mirrorHorizontal) {
            mirrorMaskAndData(targetRows, targetCols, mask, processedLum, detailTexture)
        }

        return ProcessedGrid(
            rows = targetRows,
            cols = targetCols,
            rawLuminance = processedLum,
            detailTexture = detailTexture,
            mask = mask,
            croppedWidthRatio = maxX - minX,
            croppedHeightRatio = maxY - minY
        )
    }

    private fun rasterizePolygonToMask(
        polygonOutline: List<PointF>,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        rows: Int,
        cols: Int,
        outMask: ByteArray
    ) {
        val maskBitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(maskBitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val path = Path()
        val spanX = (maxX - minX).coerceAtLeast(0.001f)
        val spanY = (maxY - minY).coerceAtLeast(0.001f)

        for (i in polygonOutline.indices) {
            val pt = polygonOutline[i]
            val px = ((pt.x - minX) / spanX) * cols
            val py = ((pt.y - minY) / spanY) * rows
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, paint)

        val alphaPixels = IntArray(cols * rows)
        // Convert to byte mask
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val pixelAlpha = maskBitmap.getPixel(c, r) ushr 24
                outMask[r * cols + c] = if (pixelAlpha > 120) 1.toByte() else 0.toByte()
            }
        }
    }

    /**
     * Automatic intelligent foreground cutout:
     * Samples perimeter colors as background, and marks high-contrast central features as foreground.
     */
    fun autoSegmentForeground(
        pixels: IntArray,
        rows: Int,
        cols: Int,
        outMask: ByteArray
    ) {
        // Collect edge/border background colors (first/last 2 rows/cols)
        var bgR = 0L; var bgG = 0L; var bgB = 0L; var count = 0L
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (r < 3 || r >= rows - 3 || c < 3 || c >= cols - 3) {
                    val p = pixels[r * cols + c]
                    bgR += (p shr 16) and 0xFF
                    bgG += (p shr 8) and 0xFF
                    bgB += p and 0xFF
                    count++
                }
            }
        }
        val avgBgR = (bgR / count.coerceAtLeast(1)).toFloat()
        val avgBgG = (bgG / count.coerceAtLeast(1)).toFloat()
        val avgBgB = (bgB / count.coerceAtLeast(1)).toFloat()

        // Distances from background color
        val diffs = FloatArray(rows * cols)
        var maxDiff = 1f
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val d = abs(r - avgBgR) + abs(g - avgBgG) + abs(b - avgBgB)
            diffs[i] = d
            if (d > maxDiff) maxDiff = d
        }

        // Thresholding: object is significantly different from frame edge
        val threshold = maxDiff * 0.28f
        for (i in diffs.indices) {
            outMask[i] = if (diffs[i] > threshold) 1.toByte() else 0.toByte()
        }

        // Clean up noise with 1 iteration of morphological opening
        cleanupMask(outMask, rows, cols)
    }

    private fun cleanupMask(mask: ByteArray, rows: Int, cols: Int) {
        val temp = ByteArray(mask.size)
        // Erosion
        for (r in 1 until rows - 1) {
            for (c in 1 until cols - 1) {
                val idx = r * cols + c
                val neighbors = mask[(r - 1) * cols + c].toInt() +
                        mask[(r + 1) * cols + c].toInt() +
                        mask[r * cols + (c - 1)].toInt() +
                        mask[r * cols + (c + 1)].toInt()
                temp[idx] = if (neighbors >= 3 && mask[idx].toInt() == 1) 1.toByte() else 0.toByte()
            }
        }
        // Dilation
        for (r in 1 until rows - 1) {
            for (c in 1 until cols - 1) {
                val idx = r * cols + c
                val neighbors = temp[(r - 1) * cols + c].toInt() +
                        temp[(r + 1) * cols + c].toInt() +
                        temp[r * cols + (c - 1)].toInt() +
                        temp[r * cols + (c + 1)].toInt()
                mask[idx] = if (neighbors >= 1 || temp[idx].toInt() == 1) 1.toByte() else 0.toByte()
            }
        }
    }

    fun boxBlur(data: FloatArray, rows: Int, cols: Int, radius: Int): FloatArray {
        if (radius <= 0) return data
        val temp = FloatArray(data.size)
        val result = FloatArray(data.size)
        val k = 2 * radius + 1

        for (r in 0 until rows) {
            val rOffset = r * cols
            for (c in 0 until cols) {
                var sum = 0f
                for (d in -radius..radius) {
                    val sc = (c + d).coerceIn(0, cols - 1)
                    sum += data[rOffset + sc]
                }
                temp[rOffset + c] = sum / k
            }
        }

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                var sum = 0f
                for (d in -radius..radius) {
                    val sr = (r + d).coerceIn(0, rows - 1)
                    sum += temp[sr * cols + c]
                }
                result[r * cols + c] = sum / k
            }
        }
        return result
    }

    private fun applyAutoContrast(data: FloatArray) {
        val bins = IntArray(256)
        for (v in data) {
            val bin = (v * 255f).toInt().coerceIn(0, 255)
            bins[bin]++
        }
        val cutoff = (data.size * 0.015f).toInt()
        var low = 0
        var acc = 0
        for (b in 0..255) {
            acc += bins[b]
            if (acc >= cutoff) {
                low = b
                break
            }
        }
        acc = 0
        var high = 255
        for (b in 255 downTo 0) {
            acc += bins[b]
            if (acc >= cutoff) {
                high = b
                break
            }
        }
        val range = (high - low).coerceAtLeast(10) / 255f
        val lowF = low / 255f
        for (i in data.indices) {
            data[i] = ((data[i] - lowF) / range).coerceIn(0f, 1f)
        }
    }

    private fun mirrorMaskAndData(
        rows: Int,
        cols: Int,
        mask: ByteArray,
        lum: FloatArray,
        detail: FloatArray
    ) {
        for (r in 0 until rows) {
            val rOffset = r * cols
            for (c in 0 until cols / 2) {
                val left = rOffset + c
                val right = rOffset + (cols - 1 - c)
                // swap mask
                val mTemp = mask[left]
                mask[left] = mask[right]
                mask[right] = mTemp
                // swap lum
                val lTemp = lum[left]
                lum[left] = lum[right]
                lum[right] = lTemp
                // swap detail
                val dTemp = detail[left]
                detail[left] = detail[right]
                detail[right] = dTemp
            }
        }
    }
}
