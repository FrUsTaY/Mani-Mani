package com.example.service.receipt

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class FiscalQrData(
    val timestamp: Long?,
    val sum: Double?,
    val fn: String,
    val fd: String,
    val fp: String,
    val n: Int?,
    val raw: String
)

object FiscalQrParser {

    private val dateFormats = listOf(
        SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US),
        SimpleDateFormat("yyyyMMdd'T'HHmm", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
    )

    fun parse(rawString: String?): FiscalQrData? {
        if (rawString.isNullOrBlank()) return null
        val trimmed = rawString.trim()

        val queryPart = if (trimmed.contains("?")) {
            trimmed.substringAfter("?")
        } else {
            trimmed
        }

        val params = mutableMapOf<String, String>()
        val pairs = queryPart.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = pair.substring(0, idx).trim().lowercase(Locale.US)
                val value = pair.substring(idx + 1).trim()
                params[key] = value
            }
        }

        // Required fiscal parameters: fn, fp, and either i or fd
        val fn = params["fn"] ?: return null
        val fp = params["fp"] ?: return null
        val fd = params["i"] ?: params["fd"] ?: return null

        if (fn.isBlank() || fp.isBlank() || fd.isBlank()) return null

        val sum = params["s"]?.toDoubleOrNull()
        val n = params["n"]?.toIntOrNull()

        val timestamp = params["t"]?.let { parseTimestamp(it) }

        return FiscalQrData(
            timestamp = timestamp,
            sum = sum,
            fn = fn,
            fd = fd,
            fp = fp,
            n = n,
            raw = trimmed
        )
    }

    private fun parseTimestamp(rawDate: String): Long? {
        for (format in dateFormats) {
            try {
                format.timeZone = TimeZone.getDefault()
                val parsed = format.parse(rawDate)
                if (parsed != null) {
                    return parsed.time
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun operationTypeToString(n: Int?): String {
        return when (n) {
            1 -> "Приход"
            2 -> "Возврат прихода"
            3 -> "Расход"
            4 -> "Возврат расхода"
            else -> "Приход"
        }
    }
}
