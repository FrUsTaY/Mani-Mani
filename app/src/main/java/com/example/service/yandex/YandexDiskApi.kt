package com.example.service.yandex

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Url
import okhttp3.RequestBody

@JsonClass(generateAdapter = true)
data class YandexUploadUrlResponse(
    @Json(name = "operation_id") val operationId: String,
    @Json(name = "href") val href: String,
    @Json(name = "method") val method: String,
    @Json(name = "templated") val templated: Boolean
)

@JsonClass(generateAdapter = true)
data class YandexDownloadUrlResponse(
    @Json(name = "href") val href: String,
    @Json(name = "method") val method: String,
    @Json(name = "templated") val templated: Boolean
)

interface YandexDiskApi {
    @GET("v1/disk/resources/upload")
    suspend fun getUploadUrl(
        @Header("Authorization") token: String,
        @Query("path") path: String,
        @Query("overwrite") overwrite: Boolean = true
    ): YandexUploadUrlResponse

    @PUT
    suspend fun uploadFile(
        @Url url: String,
        @retrofit2.http.Body requestBody: RequestBody
    )

    @GET("v1/disk/resources/download")
    suspend fun getDownloadUrl(
        @Header("Authorization") token: String,
        @Query("path") path: String
    ): YandexDownloadUrlResponse
}
