package com.example.service.receipt

import com.example.data.entity.ReceiptEntity
import com.example.data.entity.ReceiptItemEntity
import com.example.data.entity.ReceiptWithItems
import com.example.data.repository.FinanceRepository
import com.example.service.receipt.api.ProverkaChekaApi
import com.example.service.receipt.api.ProverkaChekaClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ReceiptQrService(
    private val repository: FinanceRepository,
    private val api: ProverkaChekaApi = ProverkaChekaClient.api,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val isoDateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    )

    suspend fun processAndSaveReceiptQr(
        transactionId: Long,
        qrRaw: String,
        apiKey: String
    ): Result<ReceiptWithItems> = withContext(ioDispatcher) {
        val qrData = FiscalQrParser.parse(qrRaw)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Отсканированный QR-код не является чеком (отсутствуют фискальные реквизиты ФН, ФД, ФП).")
            )

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Для получения данных чека необходимо указать API-ключ в настройках.")
            )
        }

        val response = try {
            api.getCheck(token = apiKey.trim(), qrraw = qrData.raw)
        } catch (e: Exception) {
            return@withContext Result.failure(
                Exception("Не удалось получить данные чека. Проверьте подключение к интернету и повторите попытку.", e)
            )
        }

        if (!response.isSuccessful) {
            val code = response.code()
            val errorMsg = when (code) {
                401, 403 -> "Неверный или неактивный API-ключ сервиса «Проверка чека»."
                404 -> "Чек не найден на сервере сервиса."
                429 -> "Превышен лимит запросов к сервису. Попробуйте позже."
                else -> "Ошибка сервера «Проверка чека» (код $code)."
            }
            return@withContext Result.failure(Exception(errorMsg))
        }

        val body = response.body()
            ?: return@withContext Result.failure(Exception("Пустой ответ от сервиса «Проверка чека»."))

        if (body.code != 1) {
            val msg = when (body.code) {
                0 -> "Чек ещё не поступил в базу ФНС от кассы или ожидает обработки. Пожалуйста, повторите попытку через несколько минут."
                2 -> "Ошибка запроса: ${body.message ?: "некорректные параметры чека"}"
                3 -> "Ошибка API-ключа: ${body.message ?: "неверный токен или исчерпан лимит запросов"}"
                else -> body.message ?: "Ошибка получения чека (код ${body.code})"
            }
            return@withContext Result.failure(Exception(msg))
        }

        val json = body.data?.json
            ?: return@withContext Result.failure(Exception("Сервис не вернул фискальные данные чека."))

        val merchantName = json.retailPlace?.takeIf { it.isNotBlank() }
            ?: json.user?.takeIf { it.isNotBlank() }
            ?: "Чек"

        val parsedDateTime = parseDateTime(json.dateTime) ?: qrData.timestamp ?: System.currentTimeMillis()
        val totalAmount = normalizeAmount(json.totalSum, qrData.sum)

        val existing = repository.getReceiptByTransactionIdSync(transactionId)
        val existingPhotoPath = existing?.receipt?.imagePath
        val receiptId = existing?.receipt?.id ?: 0L

        val receiptEntity = ReceiptEntity(
            id = receiptId,
            transactionId = transactionId,
            imagePath = existingPhotoPath, // Photo is strictly preserved
            merchant = merchantName,
            dateTime = parsedDateTime,
            total = totalAmount,
            fiscalNumber = json.fiscalDriveNumber?.takeIf { it.isNotBlank() } ?: qrData.fn,
            fiscalDocument = json.fiscalDocumentNumber?.toString() ?: qrData.fd,
            fiscalSign = json.fiscalSign?.toString() ?: qrData.fp,
            operationType = json.operationType?.let { FiscalQrParser.operationTypeToString(it) }
                ?: FiscalQrParser.operationTypeToString(qrData.n),
            rawQrData = qrData.raw
        )

        val receiptItems = (json.items ?: emptyList()).map { item ->
            val quantity = item.quantity ?: 1.0
            val rawSum = item.sum ?: 0.0
            val rawPrice = item.price ?: (if (quantity > 0) rawSum / quantity else 0.0)

            val itemSum = normalizeItemAmount(rawSum)
            val itemPrice = normalizeItemAmount(rawPrice)

            ReceiptItemEntity(
                receiptId = receiptId,
                name = item.name?.takeIf { it.isNotBlank() } ?: "Позиция",
                quantity = quantity,
                price = itemPrice,
                total = itemSum
            )
        }

        repository.saveReceiptWithItems(receiptEntity, receiptItems)

        val updated = repository.getReceiptByTransactionIdSync(transactionId)
            ?: return@withContext Result.failure(Exception("Ошибка сохранения данных чека в локальную базу данных."))

        Result.success(updated)
    }

    private fun parseDateTime(rawDate: String?): Long? {
        if (rawDate.isNullOrBlank()) return null
        // Try parsing numeric timestamp
        rawDate.toLongOrNull()?.let { num ->
            return if (num < 10_000_000_000L) num * 1000L else num
        }
        for (format in isoDateFormats) {
            try {
                format.timeZone = TimeZone.getDefault()
                val parsed = format.parse(rawDate)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun normalizeAmount(apiAmount: Double?, qrAmount: Double?): Double {
        if (apiAmount == null) return qrAmount ?: 0.0
        if (qrAmount != null && qrAmount > 0.0) {
            if (Math.abs(apiAmount / 100.0 - qrAmount) < 0.05) return qrAmount
            if (Math.abs(apiAmount - qrAmount) < 0.05) return qrAmount
        }
        // FNS standard is kopecks
        return if (apiAmount >= 100.0) apiAmount / 100.0 else apiAmount
    }

    private fun normalizeItemAmount(rawVal: Double): Double {
        return if (rawVal >= 100.0) rawVal / 100.0 else rawVal
    }
}
