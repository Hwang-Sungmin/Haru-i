package com.sungmin.haru_i.worker

import android.content.Context
import androidx.work.*
import com.sungmin.haru_i.data.FaceDetectorHelper
import com.sungmin.haru_i.data.PhotoRepository
import com.sungmin.haru_i.data.local.AppDatabase
import com.sungmin.haru_i.data.remote.RetrofitClient
import com.sungmin.haru_i.util.NotificationHelper
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PhotoAnalysisWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val month = inputData.getString("month") ?: return Result.failure()
        val notificationId = month.hashCode()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = PhotoRepository(applicationContext, database.photoDao())
        val faceDetectorHelper = FaceDetectorHelper(applicationContext)

        NotificationHelper.showNotification(
            applicationContext,
            "아기 사진 분석 중",
            "$month 사진을 분석하고 있습니다...",
            notificationId,
            isProgress = true,
            progress = 0
        )

        try {
            val allPhotos = repository.getPhotos().first()
            val dateFormat = SimpleDateFormat("yyyy년 MM월", Locale.KOREAN)
            val targetPhotos = allPhotos.filter { photo ->
                dateFormat.format(Date(photo.dateAdded * 1000L)) == month && !photo.isBaby
            }

            if (targetPhotos.isEmpty()) {
                NotificationHelper.showNotification(
                    applicationContext,
                    "분석 완료",
                    "$month 분석할 새로운 사진이 없습니다.",
                    notificationId
                )
                return Result.success()
            }

            var matchCount = 0
            targetPhotos.forEachIndexed { index, photo ->
                if (isStopped) return Result.retry()

                NotificationHelper.showNotification(
                    applicationContext,
                    "아기 사진 분석 중 ($month)",
                    "${index + 1} / ${targetPhotos.size} 분석 중...",
                    notificationId,
                    isProgress = true,
                    progress = index + 1,
                    max = targetPhotos.size
                )

                if (faceDetectorHelper.isBabyPhoto(photo.uri)) {
                    val file = getFileFromUri(photo.uri)
                    if (file != null) {
                        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        val body = MultipartBody.Part.createFormData("file", "photo.jpg", requestFile)
                        val response = RetrofitClient.apiService.analyzePhoto(body)
                        
                        if (response.is_target_baby) {
                            repository.updateBabyStatus(photo, true)
                            matchCount++
                        }
                        file.delete()
                    }
                }
            }

            NotificationHelper.showNotification(
                applicationContext,
                "분석 완료 ($month)",
                "${targetPhotos.size}장 중 ${matchCount}장의 아기 사진을 찾았습니다!",
                notificationId
            )

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            NotificationHelper.showNotification(
                applicationContext,
                "분석 실패 ($month)",
                "분석 중 오류가 발생했습니다: ${e.message}",
                notificationId
            )
            return Result.failure()
        }
    }

    private fun getFileFromUri(uri: android.net.Uri): File? {
        return try {
            val inputStream = applicationContext.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(applicationContext.cacheDir, "temp_worker_${System.currentTimeMillis()}.jpg")
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }
}
