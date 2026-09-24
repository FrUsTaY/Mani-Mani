package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.service.receipt.CameraPermissionHelper
import com.example.service.receipt.ReceiptCameraCaptureState
import com.example.service.receipt.ReceiptFileManager
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
class ReceiptCameraCaptureTest {

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
    fun testAppSettingsIntent() {
        val intent = CameraPermissionHelper.getAppSettingsIntent(context)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }

    @Test
    fun testPermissionDeniedCallback() {
        var permissionDeniedCalled = false
        var cameraLaunched = false

        val state = ReceiptCameraCaptureState(
            context = context,
            fileManager = fileManager,
            scope = testScope,
            currentPhotoPathProvider = { null },
            onSuccess = {},
            onError = {},
            onPermissionDenied = { permissionDeniedCalled = true },
            requestPermissionAction = {},
            launchCameraAction = { cameraLaunched = true }
        )

        state.onPermissionResult(false)
        assertTrue(permissionDeniedCalled)
        assertFalse(cameraLaunched)
    }

    @Test
    fun testPictureResultCancelledCleansTempFile() {
        var successPath: String? = null
        val tempFile = fileManager.createTempCameraFile().apply {
            writeText("dummy content")
        }
        assertTrue(tempFile.exists())

        val state = ReceiptCameraCaptureState(
            context = context,
            fileManager = fileManager,
            scope = testScope,
            currentPhotoPathProvider = { null },
            onSuccess = { successPath = it },
            onError = {},
            onPermissionDenied = {},
            requestPermissionAction = {},
            launchCameraAction = {}
        ).apply {
            currentTempFile = tempFile
        }

        // Picture capture was cancelled
        state.onPictureResult(false)

        assertNull(successPath)
        assertFalse(tempFile.exists())
    }

    @Test
    fun testPictureResultSuccessProcessesAndSaves() {
        var successPath: String? = null
        var errorMessage: String? = null

        // Create a fake captured photo
        val tempFile = fileManager.createTempCameraFile()
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        FileOutputStream(tempFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()

        val state = ReceiptCameraCaptureState(
            context = context,
            fileManager = fileManager,
            scope = testScope,
            ioDispatcher = testDispatcher,
            mainDispatcher = testDispatcher,
            currentPhotoPathProvider = { null },
            onSuccess = { successPath = it },
            onError = { errorMessage = it },
            onPermissionDenied = {},
            requestPermissionAction = {},
            launchCameraAction = {}
        ).apply {
            currentTempFile = tempFile
        }

        state.onPictureResult(true)
        testScope.advanceUntilIdle()

        assertNull(errorMessage)
        assertNotNull(successPath)
        assertTrue(File(successPath!!).exists())
        assertFalse(tempFile.exists())
    }
}
