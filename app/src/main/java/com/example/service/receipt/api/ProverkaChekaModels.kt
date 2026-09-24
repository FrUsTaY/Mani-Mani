package com.example.service.receipt.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProverkaChekaResponse(
    @Json(name = "code") val code: Int? = 0,
    @Json(name = "first") val first: Int? = null,
    @Json(name = "data") val data: ProverkaChekaData? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ProverkaChekaData(
    @Json(name = "json") val json: FnsReceiptJson? = null,
    @Json(name = "html") val html: String? = null
)

@JsonClass(generateAdapter = true)
data class FnsReceiptJson(
    @Json(name = "user") val user: String? = null,
    @Json(name = "retailPlace") val retailPlace: String? = null,
    @Json(name = "userInn") val userInn: String? = null,
    @Json(name = "dateTime") val dateTime: String? = null,
    @Json(name = "totalSum") val totalSum: Double? = null,
    @Json(name = "fiscalDriveNumber") val fiscalDriveNumber: String? = null,
    @Json(name = "fiscalDocumentNumber") val fiscalDocumentNumber: Long? = null,
    @Json(name = "fiscalSign") val fiscalSign: Long? = null,
    @Json(name = "operationType") val operationType: Int? = null,
    @Json(name = "items") val items: List<FnsReceiptItemJson>? = null
)

@JsonClass(generateAdapter = true)
data class FnsReceiptItemJson(
    @Json(name = "name") val name: String? = null,
    @Json(name = "price") val price: Double? = null,
    @Json(name = "quantity") val quantity: Double? = null,
    @Json(name = "sum") val sum: Double? = null
)
