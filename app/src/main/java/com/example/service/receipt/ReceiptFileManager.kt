package com.example.service.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class ReceiptFileManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        const val RECEIPTS_DIR_NAME = "receipts"
        const val TEMP_DIR_NAME = "temp_receipts"
        const val MAX_IMAGE_DIMENSION = 2048
        const val COMPRESSION_QUALITY = 82
    }

    val receiptsDir: File by lazy {
        File(context.filesDir, RECEIPTS_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    val tempDir: File by lazy {
        File(context.cacheDir, TEMP_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Creates a temporary file in cache directory for camera capture.
     */
    fun createTempCameraFile(): File {
        return File.createTempFile("camera_raw_", ".jpg", tempDir)
    }

    /**
     * Converts a File to a content Uri via FileProvider.
     */
    fun getUriForFile(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Generates a new unique destination file in the internal receipts directory.
     */
    fun generateReceiptFile(): File {
        val uniqueName = "receipt_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        return File(receiptsDir, uniqueName)
    }

    /**
     * Reads image from content Uri (e.g. from PhotoPicker or Camera),
     * downsamples, fixes EXIF rotation, strips metadata, compresses to target file in receipts/,
     * and deletes old file if specified.
     */
    suspend fun processAndSaveImage(
        sourceUri: Uri,
        oldFilePathToDelete: String? = null
    ): File = withContext(ioDispatcher) {
        val tempSource = File.createTempFile("import_temp_", ".tmp", tempDir)
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(tempSource).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalArgumentException("Не удалось открыть изображение по Uri: $sourceUri")

            processFileInternal(tempSource, oldFilePathToDelete)
        } finally {
            if (tempSource.exists()) {
                tempSource.delete()
            }
        }
    }

    /**
     * Processes a local image file (e.g. raw camera capture),
     * downsamples, fixes EXIF rotation, compresses, and saves to receipts/.
     */
    suspend fun processAndSaveFile(
        sourceFile: File,
        oldFilePathToDelete: String? = null,
        deleteSourceAfterProcessing: Boolean = true
    ): File = withContext(ioDispatcher) {
        try {
            processFileInternal(sourceFile, oldFilePathToDelete)
        } finally {
            if (deleteSourceAfterProcessing && sourceFile.exists()) {
                sourceFile.delete()
            }
        }
    }

    private fun processFileInternal(
        sourceFile: File,
        oldFilePathToDelete: String?
    ): File {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            throw IllegalArgumentException("Исходный файл изображения пуст или не существует")
        }

        // 1. Determine image bounds and rotation
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(sourceFile.absolutePath, boundsOptions)
        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight

        if (origWidth <= 0 || origHeight <= 0) {
            throw IllegalArgumentException("Не удалось определить размеры изображения")
        }

        val rotationDegrees = getExifRotation(sourceFile)

        // 2. Compute sample size to avoid loading huge bitmap into RAM
        val decodeOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = calculateInSampleSize(origWidth, origHeight, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
        }

        val decodedBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
            ?: throw IllegalStateException("Не удалось декодировать изображение")

        // 3. Scale down if dimension still exceeds MAX_IMAGE_DIMENSION
        val currentMaxDim = max(decodedBitmap.width, decodedBitmap.height)
        val scaledBitmap = if (currentMaxDim > MAX_IMAGE_DIMENSION) {
            val scaleFactor = MAX_IMAGE_DIMENSION.toFloat() / currentMaxDim.toFloat()
            val newWidth = (decodedBitmap.width * scaleFactor).roundToInt().coerceAtLeast(1)
            val newHeight = (decodedBitmap.height * scaleFactor).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(decodedBitmap, newWidth, newHeight, true)
            if (scaled != decodedBitmap) {
                decodedBitmap.recycle()
            }
            scaled
        } else {
            decodedBitmap
        }

        // 4. Rotate according to EXIF if needed
        val finalBitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(
                scaledBitmap,
                0,
                0,
                scaledBitmap.width,
                scaledBitmap.height,
                matrix,
                true
            )
            if (rotated != scaledBitmap) {
                scaledBitmap.recycle()
            }
            rotated
        } else {
            scaledBitmap
        }

        // 5. Compress and write to internal receipts directory
        val targetFile = generateReceiptFile()
        try {
            FileOutputStream(targetFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, out)
            }
        } finally {
            finalBitmap.recycle()
        }

        // 6. Delete old file if requested and distinct from new file
        if (!oldFilePathToDelete.isNullOrBlank() && oldFilePathToDelete != targetFile.absolutePath) {
            deleteReceiptFile(oldFilePathToDelete)
        }

        return targetFile
    }

    /**
     * Reads EXIF rotation from a file.
     */
    fun getExifRotation(file: File): Int {
        return try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Safely deletes a receipt file by path.
     */
    fun deleteReceiptFile(filePath: String?): Boolean {
        if (filePath.isNullOrBlank()) return false
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks all files in receiptsDir and deletes any file not present in validFilePaths.
     * Also removes any stale files in tempDir.
     * Returns the count of orphan files removed.
     */
    suspend fun cleanOrphanFiles(validFilePaths: Set<String>): Int = withContext(ioDispatcher) {
        var removedCount = 0

        // Normalize paths for reliable comparison
        val normalizedValidPaths = validFilePaths.mapNotNull {
            try { File(it).canonicalPath } catch (e: Exception) { null }
        }.toSet()

        val files = receiptsDir.listFiles()
        if (files != null) {
            for (file in files) {
                val canonical = try { file.canonicalPath } catch (e: Exception) { file.absolutePath }
                if (!normalizedValidPaths.contains(canonical)) {
                    if (file.delete()) {
                        removedCount++
                    }
                }
            }
        }

        // Clean temp dir
        val tempFiles = tempDir.listFiles()
        if (tempFiles != null) {
            val now = System.currentTimeMillis()
            val oneHourMs = 3600_000L
            for (tempFile in tempFiles) {
                if (now - tempFile.lastModified() > oneHourMs) {
                    tempFile.delete()
                }
            }
        }

        removedCount
    }
}
