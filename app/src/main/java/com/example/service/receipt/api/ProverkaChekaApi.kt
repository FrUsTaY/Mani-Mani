package com.example.service.receipt.api

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface ProverkaChekaApi {
    @FormUrlEncoded
    @POST("api/v1/check/get")
    suspend fun getCheck(
        @Field("token") token: String,
        @Field("qrraw") qrraw: String
    ): Response<ProverkaChekaResponse>
}
