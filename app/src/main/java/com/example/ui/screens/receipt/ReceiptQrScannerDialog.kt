package com.example.ui.screens.receipt

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun ReceiptQrScannerDialog(
    onQrScanned: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val isScanned = remember { AtomicBoolean(false) }

    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("receipt_qr_scanner_dialog")
        ) {
            if (cameraError != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = cameraError ?: "Ошибка камеры",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onDismiss) {
                        Text("Закрыть")
                    }
                }
            } else {
                // CameraX Preview
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }

                                val barcodeOptions = BarcodeScannerOptions.Builder()
                                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                    .build()
                                val barcodeScanner = BarcodeScanning.getClient(barcodeOptions)

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null && !isScanned.get()) {
                                        val inputImage = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees
                                        )
                                        barcodeScanner.process(inputImage)
                                            .addOnSuccessListener { barcodes ->
                                                for (barcode in barcodes) {
                                                    val rawValue = barcode.rawValue
                                                    if (!rawValue.isNullOrBlank() && isScanned.compareAndSet(false, true)) {
                                                        ContextCompat.getMainExecutor(ctx).execute {
                                                            onQrScanned(rawValue)
                                                        }
                                                        break
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                }

                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                                cameraControl = camera.cameraControl
                            } catch (e: Exception) {
                                Log.e("ReceiptQrScanner", "Camera initialization failed", e)
                                cameraError = "Не удалось инициализировать камеру для сканирования QR-кода."
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    }
                )

                // Reticle / Viewfinder Frame Overlay
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val boxSize = 260.dp

                    // Framing reticle box
                    Box(
                        modifier = Modifier
                            .size(boxSize)
                            .testTag("qr_scanner_reticle")
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 4.dp.toPx()
                            val cornerLength = 32.dp.toPx()
                            val color = androidx.compose.ui.graphics.Color(0xFF4CAF50)

                            // Top-Left corner
                            val tlPath = Path().apply {
                                moveTo(0f, cornerLength)
                                lineTo(0f, 0f)
                                lineTo(cornerLength, 0f)
                            }
                            drawPath(tlPath, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

                            // Top-Right corner
                            val trPath = Path().apply {
                                moveTo(size.width - cornerLength, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width, cornerLength)
                            }
                            drawPath(trPath, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

                            // Bottom-Left corner
                            val blPath = Path().apply {
                                moveTo(0f, size.height - cornerLength)
                                lineTo(0f, size.height)
                                lineTo(cornerLength, size.height)
                            }
                            drawPath(blPath, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))

                            // Bottom-Right corner
                            val brPath = Path().apply {
                                moveTo(size.width - cornerLength, size.height)
                                lineTo(size.width, size.height)
                                lineTo(size.width, size.height - cornerLength)
                            }
                            drawPath(brPath, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
                        }
                    }

                    // Prompt text below frame
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = boxSize + 48.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Наведите камеру на QR-код чека",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Top Bar Controls (Close button, Torch button)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("close_qr_scanner_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть сканер",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            val newTorch = !isTorchOn
                            cameraControl?.enableTorch(newTorch)
                            isTorchOn = newTorch
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .testTag("toggle_torch_button")
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Default.FlashOff else Icons.Default.FlashOn,
                            contentDescription = if (isTorchOn) "Выключить вспышку" else "Включить вспышку",
                            tint = if (isTorchOn) Color(0xFFFFD54F) else Color.White
                        )
                    }
                }
            }
        }
    }
}
