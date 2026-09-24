package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.example.service.receipt.ReceiptFileManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReceiptFileManagerTest {

    private lateinit var context: Context
    private lateinit var fileManager: ReceiptFileManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fileManager = ReceiptFileManager(context)
    }

    @After
    fun tearDown() {
        fileManager.receiptsDir.deleteRecursively()
        fileManager.tempDir.deleteRecursively()
    }

    @Test
    fun testDirectoriesAndFileNaming() {
        assertTrue(fileManager.receiptsDir.exists())
        assertTrue(fileManager.tempDir.exists())

        val tempCameraFile = fileManager.createTempCameraFile()
        assertTrue(tempCameraFile.parentFile?.canonicalPath == fileManager.tempDir.canonicalPath)

        val receiptFile = fileManager.generateReceiptFile()
        assertTrue(receiptFile.parentFile?.canonicalPath == fileManager.receiptsDir.canonicalPath)
        assertTrue(receiptFile.name.startsWith("receipt_"))
        assertTrue(receiptFile.name.endsWith(".jpg"))
    }

    @Test
    fun testProcessAndSaveFileDownscalingAndCompression() = runBlocking {
        // Create a large fake image (3000 x 4000)
        val largeBitmap = Bitmap.createBitmap(3000, 4000, Bitmap.Config.ARGB_8888)
        val rawFile = fileManager.createTempCameraFile()
        FileOutputStream(rawFile).use { out ->
            largeBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        largeBitmap.recycle()

        assertTrue(rawFile.exists())
        assertTrue(rawFile.length() > 0)

        // Process and save
        val processedFile = fileManager.processAndSaveFile(
            sourceFile = rawFile,
            oldFilePathToDelete = null,
            deleteSourceAfterProcessing = true
        )

        // Verify source temp file was cleaned up
        assertFalse(rawFile.exists())

        // Verify processed file exists in receipts directory
        assertTrue(processedFile.exists())
        assertTrue(processedFile.parentFile?.canonicalPath == fileManager.receiptsDir.canonicalPath)

        // Check image dimensions are downscaled to <= 2048
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(processedFile.absolutePath, options)
        val maxDim = max(options.outWidth, options.outHeight)
        assertTrue("Max dimension should be <= 2048, was $maxDim", maxDim <= ReceiptFileManager.MAX_IMAGE_DIMENSION)
    }

    @Test
    fun testReplaceDeletesOldFile() = runBlocking {
        // 1. Create first file
        val bitmap1 = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val raw1 = fileManager.createTempCameraFile()
        FileOutputStream(raw1).use { bitmap1.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap1.recycle()

        val saved1 = fileManager.processAndSaveFile(raw1)
        assertTrue(saved1.exists())

        // 2. Create second file and replace first
        val bitmap2 = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val raw2 = fileManager.createTempCameraFile()
        FileOutputStream(raw2).use { bitmap2.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap2.recycle()

        val saved2 = fileManager.processAndSaveFile(
            sourceFile = raw2,
            oldFilePathToDelete = saved1.absolutePath
        )

        assertTrue(saved2.exists())
        // Old file must be deleted
        assertFalse(saved1.exists())
    }

    @Test
    fun testCleanOrphanFiles() = runBlocking {
        // Create 3 files in receiptsDir
        val file1 = fileManager.generateReceiptFile().apply { writeText("receipt1") }
        val file2 = fileManager.generateReceiptFile().apply { writeText("receipt2") }
        val file3 = fileManager.generateReceiptFile().apply { writeText("receipt3") }

        assertTrue(file1.exists())
        assertTrue(file2.exists())
        assertTrue(file3.exists())

        // Only file1 is valid in database
        val validPaths = setOf(file1.absolutePath)
        val removed = fileManager.cleanOrphanFiles(validPaths)

        assertEquals(2, removed)
        assertTrue(file1.exists())
        assertFalse(file2.exists())
        assertFalse(file3.exists())
    }
}
