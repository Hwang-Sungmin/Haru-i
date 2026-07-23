package com.sungmin.haru_i.worker

import android.content.Context
import androidx.work.*
import com.sungmin.haru_i.data.BabyManager
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
        val babyManager = BabyManager(applicationContext)
        val repository = PhotoRepository(applicationContext, database.photoDao(), babyManager)
        val faceDetectorHelper = FaceDetectorHelper(applicationContext)

        try {
            // 시작 전 서버 초기화 및 시작 알림
            repository.resetServer()
            repository.startBatch()

            NotificationHelper.showNotification(
                applicationContext,
                "아기 사진 분석 준비 중",
                "$month 분석을 준비하고 있습니다...",
                notificationId,
                isProgress = true,
                progress = 0
            )

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
                repository.finishBatch()
                return Result.success()
            }

            var matchCount = 0
            targetPhotos.forEachIndexed { index, photo ->
                if (isStopped) {
                    repository.stopServer()
                    return Result.retry()
                }

                // 알림 빈도 조절 (10개 단위 또는 처음/마지막)
                val isFirst = index == 0
                val isLast = index == targetPhotos.size - 1
                val isEveryTen = (index + 1) % 10 == 0

                if (isFirst || isLast || isEveryTen) {
                    NotificationHelper.showNotification(
                        applicationContext,
                        "아기 사진 분석 중 ($month)",
                        "${index + 1} / ${targetPhotos.size} 분석 중...",
                        notificationId,
                        isProgress = true,
                        progress = index + 1,
                        max = targetPhotos.size
                    )
                }

                if (faceDetectorHelper.isBabyPhoto(photo.uri)) {
                    val file = getFileFromUri(photo.uri)
                    if (file != null) {
                        try {
                            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                            val body = MultipartBody.Part.createFormData("file", "photo.jpg", requestFile)
                            val response = RetrofitClient.apiService.analyzePhoto(body, babyManager.getUserId())
                            
                            if (response.is_target_baby) {
                                repository.updateBabyStatus(photo, true)
                                matchCount++
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            file.delete()
                        }
                    }
                }
            }

            NotificationHelper.showNotification(
                applicationContext,
                "분석 완료 ($month)",
                "${targetPhotos.size}장 중 ${matchCount}장의 아기 사진을 찾았습니다!",
                notificationId
            )

            repository.finishBatch()
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            repository.finishBatch() // 에러 시에도 서버 상태 원복
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
