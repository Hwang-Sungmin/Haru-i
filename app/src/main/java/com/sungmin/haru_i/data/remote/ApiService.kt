package com.sungmin.haru_i.data.remote

import okhttp3.MultipartBody
import retrofit2.http.*

data class RegisterResponse(
    val status: String,
    val message: String
)

data class AnalyzeResponse(
    val is_target_baby: Boolean = false,
    val distance: Float = 1.0f,
    val threshold: Float = 0.4f,
    val status: String? = null,
    val message: String? = null
)

data class DescribeResponse(
    val caption: String? = null,
    val emotion: String? = null,
    val status: String? = null,
    val message: String? = null
)

interface ApiService {
    @Multipart
    @POST("/register")
    suspend fun registerBaby(
        @Part file: MultipartBody.Part,
        @Header("X-User-ID") userId: String
    ): RegisterResponse

    @Multipart
    @POST("/analyze")
    suspend fun analyzePhoto(
        @Part file: MultipartBody.Part,
        @Header("X-User-ID") userId: String
    ): AnalyzeResponse

    @Multipart
    @POST("/describe")
    suspend fun describePhoto(
        @Part file: MultipartBody.Part,
        @Header("X-User-ID") userId: String
    ): DescribeResponse

    @POST("/stop")
    suspend fun stopServer(
        @Header("X-User-ID") userId: String
    ): RegisterResponse

    @POST("/reset")
    suspend fun resetServer(
        @Header("X-User-ID") userId: String
    ): RegisterResponse

    @POST("/start")
    suspend fun startBatch(
        @Header("X-User-ID") userId: String
    ): RegisterResponse

    @POST("/finish")
    suspend fun finishBatch(
        @Header("X-User-ID") userId: String
    ): RegisterResponse
}
