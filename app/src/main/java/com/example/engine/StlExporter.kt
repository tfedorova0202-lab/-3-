package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.Mesh3D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot

object StlExporter {

    /**
     * Converts a Mesh3D into binary STL format byte buffer.
     */
    fun createBinaryStlBytes(mesh: Mesh3D): ByteArray {
        val numTris = mesh.triangleCount
        val totalBytes = 84 + (50 * numTris)
        val buffer = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN)

        // 80-byte header
        val headerText = "Photo2STL 3D Studio - Watertight Solid for 3D Printing"
        val headerBytes = headerText.toByteArray(Charsets.US_ASCII)
        buffer.put(headerBytes, 0, headerBytes.size.coerceAtMost(80))
        // Pad remaining to 80 bytes
        for (i in headerBytes.size until 80) {
            buffer.put(0.toByte())
        }

        // 4-byte uint32 triangle count
        buffer.putInt(numTris)

        val pos = mesh.positions
        var idx = 0
        while (idx < pos.size) {
            val ax = pos[idx]; val ay = pos[idx + 1]; val az = pos[idx + 2]
            val bx = pos[idx + 3]; val by = pos[idx + 4]; val bz = pos[idx + 5]
            val cx = pos[idx + 6]; val cy = pos[idx + 7]; val cz = pos[idx + 8]

            // Calculate face normal
            val uX = bx - ax; val uY = by - ay; val uZ = bz - az
            val vX = cx - ax; val vY = cy - ay; val vZ = cz - az

            var nx = uY * vZ - uZ * vY
            var ny = uZ * vX - uX * vZ
            var nz = uX * vY - uY * vX
            val len = hypot(hypot(nx, ny), nz).coerceAtLeast(1e-6f)
            nx /= len; ny /= len; nz /= len

            // Normal vector (12 bytes)
            buffer.putFloat(nx)
            buffer.putFloat(ny)
            buffer.putFloat(nz)

            // 3 Vertices (36 bytes)
            buffer.putFloat(ax); buffer.putFloat(ay); buffer.putFloat(az)
            buffer.putFloat(bx); buffer.putFloat(by); buffer.putFloat(bz)
            buffer.putFloat(cx); buffer.putFloat(cy); buffer.putFloat(cz)

            // Attribute byte count (2 bytes)
            buffer.putShort(0.toShort())

            idx += 9
        }

        return buffer.array()
    }

    /**
     * Saves binary STL to device Downloads / Documents folder.
     */
    suspend fun saveStlToDownloads(
        context: Context,
        mesh: Mesh3D,
        fileName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanName = fileName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val fullName = "${cleanName}.stl"
            val stlBytes = createBinaryStlBytes(mesh)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fullName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "model/stl")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Photo2STL")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(Exception("Cannot create MediaStore entry"))

                context.contentResolver.openOutputStream(uri)?.use { os: OutputStream ->
                    os.write(stlBytes)
                    os.flush()
                }
                Result.success("Файл сохранён в Загрузки/Photo2STL/$fullName")
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = File(downloadsDir, "Photo2STL").apply { mkdirs() }
                val file = File(appDir, fullName)
                FileOutputStream(file).use { it.write(stlBytes) }
                Result.success("Файл сохранён в ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Saves to cache and creates a Share Intent for 3D Slicers (Cura, Bambu Handy, Creality Cloud, etc.)
     */
    suspend fun createShareIntent(
        context: Context,
        mesh: Mesh3D,
        fileName: String
    ): Intent? = withContext(Dispatchers.IO) {
        try {
            val cleanName = fileName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val cacheStlDir = File(context.cacheDir, "stl").apply { mkdirs() }
            val file = File(cacheStlDir, "${cleanName}.stl")

            val bytes = createBinaryStlBytes(mesh)
            FileOutputStream(file).use { it.write(bytes) }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Intent(Intent.ACTION_SEND).apply {
                type = "model/stl"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "3D модель: $cleanName")
                putExtra(Intent.EXTRA_TEXT, "STL модель готова для 3D-печати. Размер: ${mesh.widthMm.toInt()}×${mesh.heightMm.toInt()}×${mesh.thicknessMm.toInt()} мм.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
