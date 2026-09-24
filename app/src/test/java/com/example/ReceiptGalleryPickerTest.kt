package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.service.receipt.ReceiptFileManager
import com.example.service.receipt.ReceiptGalleryPickerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReceiptGalleryPickerTest {

    private lateinit var context: Context
    private lateinit var fileManager: ReceiptFileManager
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fileManager = ReceiptFileManager(context, testDispatcher)
    }

    @After
    fun tearDown() {
        fileManager.receiptsDir.deleteRecursively()
        fileManager.tempDir.deleteRecursively()
    }

    @Test
    fun testPickerCancelledNoOp() {
        var successPath: String? = null
        var errorMessage: String? = null

        val state = ReceiptGalleryPickerState(
            fileManager = fileManager,
            scope = testScope,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher,
            currentPhotoPathProvider = { null },
            onSuccess = { successPath = it },
            onError = { errorMessage = it },
            launchPickerAction = {}
        )

        state.onImagePicked(null)
        testScope.advanceUntilIdle()

        assertFalse(state.isProcessing)
        assertNull(successPath)
        assertNull(errorMessage)
    }

    @Test
    fun testPickerSuccessCopiesAndCompressesImage() {
        var successPath: String? = null
        var errorMessage: String? = null

        // 1. Create a fake "gallery" image file
        val galleryFile = File(context.cacheDir, "gallery_photo.jpg")
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        FileOutputStream(galleryFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        val galleryUri = Uri.fromFile(galleryFile)

        // 2. Create existing old receipt file to test replacement
        val oldFile = fileManager.generateReceiptFile().apply { writeText("old receipt photo") }
        assertTrue(oldFile.exists())

        val state = ReceiptGalleryPickerState(
            fileManager = fileManager,
            scope = testScope,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher,
            currentPhotoPathProvider = { oldFile.absolutePath },
            onSuccess = { successPath = it },
            onError = { errorMessage = it },
            launchPickerAction = {}
        )

        state.onImagePicked(galleryUri)
        testScope.advanceUntilIdle()

        assertNull(errorMessage)
        assertNotNull(successPath)
        assertFalse(state.isProcessing)

        val newReceiptFile = File(successPath!!)
        assertTrue(newReceiptFile.exists())
        assertTrue(newReceiptFile.parentFile?.canonicalPath == fileManager.receiptsDir.canonicalPath)

        // Old file must be deleted upon successful replacement
        assertFalse(oldFile.exists())

        // Original gallery file must still exist (independence from gallery original)
        assertTrue(galleryFile.exists())
        galleryFile.delete()
    }

    @Test
    fun testPickerInvalidUriReportsError() {
        var successPath: String? = null
        var errorMessage: String? = null

        val invalidUri = Uri.parse("file:///non_existent_folder/missing.jpg")

        val state = ReceiptGalleryPickerState(
            fileManager = fileManager,
            scope = testScope,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher,
            currentPhotoPathProvider = { null },
            onSuccess = { successPath = it },
            onError = { errorMessage = it },
            launchPickerAction = {}
        )

        state.onImagePicked(invalidUri)
        testScope.advanceUntilIdle()

        assertFalse(state.isProcessing)
        assertNull(successPath)
        assertNotNull(errorMessage)
    }
}
