package com.example.ui.screens.receipt

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.entity.ReceiptWithItems
import com.example.service.receipt.CameraPermissionHelper
import com.example.service.receipt.FiscalQrParser
import com.example.service.receipt.ReceiptFileManager
import com.example.service.receipt.ReceiptPreferenceManager
import com.example.service.receipt.rememberReceiptCameraCapture
import com.example.service.receipt.rememberReceiptGalleryPicker
import com.example.ui.util.CurrencyHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class PendingPhotoAction {
    CAMERA, GALLERY
}

@Composable
fun ReceiptTabContent(
    receiptWithItems: ReceiptWithItems?,
    fileManager: ReceiptFileManager,
    preferenceManager: ReceiptPreferenceManager,
    transactionAmount: Double? = null,
    onAttachPhoto: (photoPath: String) -> Unit,
    onDeletePhoto: () -> Unit,
    onDeleteQrData: () -> Unit = {},
    onProcessQrScanned: (qrRaw: String) -> Unit = {},
    isQrLoading: Boolean = false,
    qrErrorMessage: String? = null,
    onClearQrError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentPhotoPath = receiptWithItems?.receipt?.imagePath
    val hasQrData = receiptWithItems?.receipt?.total != null || !receiptWithItems?.items.isNullOrEmpty()

    var showPhotoViewer by remember { mutableStateOf(false) }
    var showReplacePhotoConfirmDialog by remember { mutableStateOf(false) }
    var showDeletePhotoConfirmDialog by remember { mutableStateOf(false) }
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    var pendingPhotoAction by remember { mutableStateOf<PendingPhotoAction?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showReplaceQrConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteQrConfirmDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var isItemsExpanded by remember { mutableStateOf(true) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showQrScanner = true
        } else {
            showPermissionDeniedDialog = true
        }
    }

    fun proceedToScanQr() {
        if (!preferenceManager.isApiKeyConfigured()) {
            showApiKeyDialog = true
        } else if (!CameraPermissionHelper.hasCameraPermission(context)) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            showQrScanner = true
        }
    }

    fun handleScanQrClick() {
        if (hasQrData) {
            showReplaceQrConfirmDialog = true
        } else {
            proceedToScanQr()
        }
    }

    val cameraCapture = rememberReceiptCameraCapture(
        fileManager = fileManager,
        currentPhotoPath = currentPhotoPath,
        onSuccess = { savedPath ->
            errorMessage = null
            onAttachPhoto(savedPath)
        },
        onError = { err -> errorMessage = err },
        onPermissionDenied = {
            showPermissionDeniedDialog = true
        }
    )

    val galleryPicker = rememberReceiptGalleryPicker(
        fileManager = fileManager,
        currentPhotoPath = currentPhotoPath,
        onSuccess = { savedPath ->
            errorMessage = null
            onAttachPhoto(savedPath)
        },
        onError = { err -> errorMessage = err }
    )

    val isProcessingPhoto = cameraCapture.isProcessing || galleryPicker.isProcessing

    fun handlePhotoAction(action: PendingPhotoAction) {
        if (!currentPhotoPath.isNullOrBlank()) {
            pendingPhotoAction = action
            showReplacePhotoConfirmDialog = true
        } else {
            when (action) {
                PendingPhotoAction.CAMERA -> cameraCapture.capture()
                PendingPhotoAction.GALLERY -> galleryPicker.pickImage()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("receipt_tab_content"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Error banner for photo / local error
        errorMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { errorMessage = null }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Скрыть",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Error banner for QR error
        qrErrorMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("qr_error_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearQrError) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Скрыть",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Photo loading state indicator
        if (isProcessingPhoto) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Оптимизация и сжатие снимка чека...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // QR loading state indicator
        if (isQrLoading) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("qr_loading_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Получение данных чека из ФНС...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 1. Photo Section
        if (!currentPhotoPath.isNullOrBlank()) {
            // Existing Photo View
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Фото чека",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Сохранено локально",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Thumbnail image preview with zoom hint
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.05f))
                            .clickable { showPhotoViewer = true },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = File(currentPhotoPath),
                            contentDescription = "Миниатюра чека",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Zoom hint badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ZoomIn,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Нажмите для зума",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Photo action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showPhotoViewer = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_photo_button")
                        ) {
                            Icon(Icons.Outlined.ZoomIn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Открыть")
                        }

                        OutlinedButton(
                            onClick = { showDeletePhotoConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("delete_photo_button")
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Удалить")
                        }
                    }

                    // Replace options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { handlePhotoAction(PendingPhotoAction.CAMERA) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Переснять", fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = { handlePhotoAction(PendingPhotoAction.GALLERY) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Из галереи", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            // Empty Photo State: Offer Camera or Gallery
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Добавить фото чека",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Сохраните фотографию чека. Она будет сжата и сохранена локально в приложении.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { handlePhotoAction(PendingPhotoAction.CAMERA) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("camera_capture_button")
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Камера")
                        }

                        OutlinedButton(
                            onClick = { handlePhotoAction(PendingPhotoAction.GALLERY) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gallery_picker_button")
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Галерея")
                        }
                    }
                }
            }
        }

        // 2. QR Code Section (Section 3.1, Section 11, 17, 21 ТЗ)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("receipt_qr_section")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Данные фискального чека",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (hasQrData) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2E7D32)
                        ) {
                            Text(
                                text = "✓ Чек из ФНС",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("fns_badge")
                            )
                        }
                    }
                }

                if (hasQrData) {
                    val receipt = receiptWithItems?.receipt
                    val items = receiptWithItems?.items ?: emptyList()
                    val receiptTotal = receipt?.total ?: 0.0

                    // 1. Merchant / Магазин (Section 17 & 36 ТЗ)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receipt_merchant_section"),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Магазин:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = receipt?.merchant?.takeIf { it.isNotBlank() } ?: "Не указан",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("receipt_merchant_name")
                        )
                    }

                    // 2. Date and Time / Дата (Section 17 & 36 ТЗ)
                    receipt?.dateTime?.let { dt ->
                        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(dt))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("receipt_date_section"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Дата:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("receipt_date_text")
                            )
                        }
                    }

                    HorizontalDivider()

                    // 3. Total / Сумма (Section 17 & 36 ТЗ)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("receipt_total_section"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Сумма:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = CurrencyHelper.formatAmount(receiptTotal, "RUB"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("receipt_total_amount")
                        )
                    }

                    // 4. Comparison with Transaction (Section 21 & 36 ТЗ)
                    if (transactionAmount != null) {
                        val absTxAmount = Math.abs(transactionAmount)
                        val isMatching = Math.abs(absTxAmount - receiptTotal) < 0.05

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isMatching) Color(0xFF2E7D32).copy(alpha = 0.12f) else Color(0xFFE65100).copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(if (isMatching) "receipt_sum_match" else "receipt_sum_diff")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isMatching) {
                                    Text(
                                        text = "✓ Сумма операции совпадает с чеком",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF2E7D32)
                                    )
                                } else {
                                    Text(
                                        text = "⚠ Сумма операции отличается от суммы чека",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Операция: ${CurrencyHelper.formatAmount(absTxAmount, "RUB")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFE65100),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Чек: ${CurrencyHelper.formatAmount(receiptTotal, "RUB")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFE65100),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Fiscal Details / Фискальные данные (Section 15 & 36 ТЗ)
                    val fn = receipt?.fiscalNumber?.takeIf { it.isNotBlank() }
                    val fd = receipt?.fiscalDocument?.takeIf { it.isNotBlank() }
                    val fp = receipt?.fiscalSign?.takeIf { it.isNotBlank() }
                    val opType = receipt?.operationType?.takeIf { it.isNotBlank() }

                    if (fn != null || fd != null || fp != null || opType != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("receipt_fiscal_details")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Фискальные данные",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                fn?.let {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("ФН:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("receipt_fn_value"))
                                    }
                                }
                                fd?.let {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("ФД:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("receipt_fd_value"))
                                    }
                                }
                                fp?.let {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("ФП:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("receipt_fp_value"))
                                    }
                                }
                                opType?.let {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Тип операции:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("receipt_operation_type_value"))
                                    }
                                }
                            }
                        }
                    }

                    // 6. Items List / Список товаров (Section 16, 17 & 36 ТЗ)
                    if (items.isNotEmpty()) {
                        HorizontalDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isItemsExpanded = !isItemsExpanded }
                                .padding(vertical = 4.dp)
                                .testTag("receipt_items_toggle"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Товары (${items.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = if (isItemsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isItemsExpanded) "Свернуть" else "Развернуть"
                            )
                        }

                        if (isItemsExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("receipt_items_list"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items.forEachIndexed { index, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("receipt_item_$index"),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp)
                                        ) {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.testTag("receipt_item_name_$index")
                                            )
                                            val qtyPriceText = if (item.quantity == 1.0) {
                                                CurrencyHelper.formatAmount(item.price, "RUB")
                                            } else {
                                                "${item.quantity} × ${CurrencyHelper.formatAmount(item.price, "RUB")}"
                                            }
                                            Text(
                                                text = qtyPriceText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = CurrencyHelper.formatAmount(item.total, "RUB"),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.testTag("receipt_item_total_$index")
                                        )
                                    }
                                    if (index < items.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // QR action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { handleScanQrClick() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rescan_qr_button")
                        ) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Обновить QR", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { showDeleteQrConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("delete_qr_button")
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Удалить QR", fontSize = 12.sp)
                        }
                    }
                } else {
                    // Empty QR State
                    Text(
                        text = "QR-код чека позволяет получить состав покупок и фискальные реквизиты из базы ФНС через сервис «Проверка чека».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { handleScanQrClick() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scan_qr_button")
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сканировать QR-код")
                    }
                }
            }
        }
    }

    // Fullscreen Photo Viewer Dialog
    if (showPhotoViewer && !currentPhotoPath.isNullOrBlank()) {
        ReceiptPhotoViewerDialog(
            photoPath = currentPhotoPath,
            onDismiss = { showPhotoViewer = false }
        )
    }

    // QR Camera Scanner Dialog
    if (showQrScanner) {
        ReceiptQrScannerDialog(
            onQrScanned = { rawQr ->
                showQrScanner = false
                val parsed = FiscalQrParser.parse(rawQr)
                if (parsed == null) {
                    errorMessage = "Отсканированный QR-код не является чеком (нет реквизитов ФН, ФД, ФП)."
                } else {
                    onProcessQrScanned(rawQr)
                }
            },
            onDismiss = { showQrScanner = false }
        )
    }

    // API Key Dialog (if not configured)
    if (showApiKeyDialog) {
        ReceiptApiKeyDialog(
            initialKey = preferenceManager.getApiKey(),
            onSaveKey = { newKey ->
                preferenceManager.saveApiKey(newKey)
                showApiKeyDialog = false
                proceedToScanQr()
            },
            onDismiss = { showApiKeyDialog = false }
        )
    }

    // Replace photo confirmation dialog (Section 20 ТЗ)
    if (showReplacePhotoConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReplacePhotoConfirmDialog = false },
            title = { Text("Заменить фотографию чека?") },
            text = { Text("Фотография чека уже добавлена. При подтверждении старая фотография будет заменена новой.") },
            confirmButton = {
                Button(
                    onClick = {
                        showReplacePhotoConfirmDialog = false
                        when (pendingPhotoAction) {
                            PendingPhotoAction.CAMERA -> cameraCapture.capture()
                            PendingPhotoAction.GALLERY -> galleryPicker.pickImage()
                            null -> {}
                        }
                        pendingPhotoAction = null
                    },
                    modifier = Modifier.testTag("confirm_replace_photo_button")
                ) {
                    Text("Заменить")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showReplacePhotoConfirmDialog = false
                        pendingPhotoAction = null
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    // Delete photo confirmation dialog (Section 10 & 27 ТЗ)
    if (showDeletePhotoConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeletePhotoConfirmDialog = false },
            title = { Text("Удалить фото чека?") },
            text = { Text("Файл фотографии будет удалён с устройства. Данные QR-чека (если есть) останутся неизменными.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeletePhotoConfirmDialog = false
                        onDeletePhoto()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_photo_button")
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeletePhotoConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Replace QR confirmation dialog (Section 19 ТЗ)
    if (showReplaceQrConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceQrConfirmDialog = false },
            title = { Text("Заменить данные чека?") },
            text = { Text("Данные чека уже существуют. Заменить их новыми? Фото чека останется без изменений.") },
            confirmButton = {
                Button(
                    onClick = {
                        showReplaceQrConfirmDialog = false
                        proceedToScanQr()
                    },
                    modifier = Modifier.testTag("confirm_replace_qr_button")
                ) {
                    Text("Заменить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showReplaceQrConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Delete QR confirmation dialog (Section 17 & 27 ТЗ)
    if (showDeleteQrConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteQrConfirmDialog = false },
            title = { Text("Удалить данные чека?") },
            text = { Text("Структурированные данные и позиции чека будут удалены. Фотография чека (если есть) останется сохранённой.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteQrConfirmDialog = false
                        onDeleteQrData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_qr_button")
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteQrConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Camera permission denied dialog
    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = { Text("Требуется доступ к камере") },
            text = { Text("Для съёмки чека или сканирования QR-кода необходимо предоставить разрешение на использование камеры в настройках приложения.") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDeniedDialog = false
                        try {
                            context.startActivity(CameraPermissionHelper.getAppSettingsIntent(context))
                        } catch (e: Exception) {}
                    }
                ) {
                    Text("Открыть настройки")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPermissionDeniedDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
