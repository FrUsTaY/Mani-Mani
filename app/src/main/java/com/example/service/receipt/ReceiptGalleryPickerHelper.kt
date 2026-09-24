package com.example.service.receipt

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReceiptGalleryPickerState internal constructor(
    private val fileManager: ReceiptFileManager,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val currentPhotoPathProvider: () -> String?,
    private val onSuccess: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val launchPickerAction: (PickVisualMediaRequest) -> Unit
) {
    var isProcessing by mutableStateOf(false)
        internal set

    /**
     * Opens the system Android Photo Picker (ImageOnly mode).
     * Does not require broad storage permissions on modern Android versions.
     */
    fun pickImage() {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        launchPickerAction(request)
    }

    internal fun onImagePicked(uri: Uri?) {
        if (uri == null) {
            // User cancelled or closed the picker without selecting a photo
            return
        }

        isProcessing = true
        scope.launch(ioDispatcher) {
            try {
                val processedFile = fileManager.processAndSaveImage(
                    sourceUri = uri,
                    oldFilePathToDelete = currentPhotoPathProvider()
                )
                withContext(mainDispatcher) {
                    isProcessing = false
                    onSuccess(processedFile.absolutePath)
                }
            } catch (e: Exception) {
                withContext(mainDispatcher) {
                    isProcessing = false
                    onError("Не удалось загрузить или обработать фото чека из галереи")
                }
            }
        }
    }
}

/**
 * Rememberable Android Photo Picker launcher for receipt photos.
 */
@Composable
fun rememberReceiptGalleryPicker(
    fileManager: ReceiptFileManager,
    currentPhotoPath: String? = null,
    onSuccess: (savedFilePath: String) -> Unit,
    onError: (message: String) -> Unit = {}
): ReceiptGalleryPickerState {
    val scope = rememberCoroutineScope()
    val currentPhotoPathState = rememberUpdatedState(currentPhotoPath)
    val onSuccessState = rememberUpdatedState(onSuccess)
    val onErrorState = rememberUpdatedState(onError)

    var stateRef by remember { mutableStateOf<ReceiptGalleryPickerState?>(null) }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        stateRef?.onImagePicked(uri)
    }

    val state = remember(fileManager) {
        ReceiptGalleryPickerState(
            fileManager = fileManager,
            scope = scope,
            currentPhotoPathProvider = { currentPhotoPathState.value },
            onSuccess = { onSuccessState.value(it) },
            onError = { onErrorState.value(it) },
            launchPickerAction = { request ->
                pickMediaLauncher.launch(request)
            }
        ).also { stateRef = it }
    }

    return state
}
