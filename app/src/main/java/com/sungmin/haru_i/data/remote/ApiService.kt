package com.sungmin.haru_i.data.remote

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

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

interface ApiService {
    @Multipart
    @POST("/register")
    suspend fun registerBaby(
        @Part file: MultipartBody.Part
    ): RegisterResponse

    @Multipart
    @POST("/analyze")
    suspend fun analyzePhoto(
        @Part file: MultipartBody.Part
    ): AnalyzeResponse
}
