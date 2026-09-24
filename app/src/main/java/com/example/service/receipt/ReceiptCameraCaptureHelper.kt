package com.example.service.receipt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object CameraPermissionHelper {
    /**
     * Checks if CAMERA permission is granted.
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Intent to open application details settings page so user can grant camera permission.
     */
    fun getAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }
}

class ReceiptCameraCaptureState internal constructor(
    private val context: Context,
    private val fileManager: ReceiptFileManager,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val currentPhotoPathProvider: () -> String?,
    private val onSuccess: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onPermissionDenied: () -> Unit,
    private val requestPermissionAction: () -> Unit,
    private val launchCameraAction: (Uri) -> Unit
) {
    var isProcessing by mutableStateOf(false)
        internal set

    var currentTempFile: File? = null
        internal set

    /**
     * Initiates the camera capture flow. Requests permission if not granted.
     */
    fun capture() {
        if (!CameraPermissionHelper.hasCameraPermission(context)) {
            requestPermissionAction()
            return
        }
        startCameraIntent()
    }

    internal fun onPermissionResult(isGranted: Boolean) {
        if (isGranted) {
            startCameraIntent()
        } else {
            onPermissionDenied()
        }
    }

    private fun startCameraIntent() {
        try {
            val tempFile = fileManager.createTempCameraFile()
            currentTempFile = tempFile
            val uri = fileManager.getUriForFile(tempFile)
            launchCameraAction(uri)
        } catch (e: Exception) {
            currentTempFile?.delete()
            currentTempFile = null
            onError("Не удалось запустить камеру: ${e.localizedMessage ?: "ошибка создания файла"}")
        }
    }

    internal fun onPictureResult(success: Boolean) {
        val tempFile = currentTempFile
        currentTempFile = null

        if (!success) {
            tempFile?.delete()
            return // User cancelled or camera closed without taking photo
        }

        if (tempFile == null || !tempFile.exists() || tempFile.length() == 0L) {
            tempFile?.delete()
            onError("Снимок не был получен или файл пуст")
            return
        }

        isProcessing = true
        scope.launch(ioDispatcher) {
            try {
                val processedFile = fileManager.processAndSaveFile(
                    sourceFile = tempFile,
                    oldFilePathToDelete = currentPhotoPathProvider(),
                    deleteSourceAfterProcessing = true
                )
                withContext(mainDispatcher) {
                    isProcessing = false
                    onSuccess(processedFile.absolutePath)
                }
            } catch (e: Exception) {
                tempFile.delete()
                withContext(mainDispatcher) {
                    isProcessing = false
                    onError("Не удалось обработать и сохранить фото чека")
                }
            }
        }
    }
}

/**
 * Rememberable camera capture handler for receipt photos.
 */
@Composable
fun rememberReceiptCameraCapture(
    fileManager: ReceiptFileManager,
    currentPhotoPath: String? = null,
    onSuccess: (savedFilePath: String) -> Unit,
    onError: (message: String) -> Unit = {},
    onPermissionDenied: () -> Unit = {}
): ReceiptCameraCaptureState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentPhotoPathState = rememberUpdatedState(currentPhotoPath)
    val onSuccessState = rememberUpdatedState(onSuccess)
    val onErrorState = rememberUpdatedState(onError)
    val onPermissionDeniedState = rememberUpdatedState(onPermissionDenied)

    var stateRef by remember { mutableStateOf<ReceiptCameraCaptureState?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        stateRef?.onPictureResult(success)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        stateRef?.onPermissionResult(isGranted)
    }

    val state = remember(fileManager, context) {
        ReceiptCameraCaptureState(
            context = context,
            fileManager = fileManager,
            scope = scope,
            currentPhotoPathProvider = { currentPhotoPathState.value },
            onSuccess = { onSuccessState.value(it) },
            onError = { onErrorState.value(it) },
            onPermissionDenied = { onPermissionDeniedState.value() },
            requestPermissionAction = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            launchCameraAction = { uri ->
                takePictureLauncher.launch(uri)
            }
        ).also { stateRef = it }
    }

    return state
}
