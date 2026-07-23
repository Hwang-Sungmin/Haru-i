package com.sungmin.haru_i.worker

import android.content.Context
import androidx.work.*
import com.sungmin.haru_i.data.BabyManager
import com.sungmin.haru_i.data.PhotoRepository
import com.sungmin.haru_i.data.local.AppDatabase
import com.sungmin.haru_i.util.NotificationHelper
import kotlinx.coroutines.flow.first
import android.util.Log

class SmartJournalWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("SmartJournalWorker", "Work started")
        val photoUri = inputData.getString("photoUri") ?: return Result.failure()
        val photoId = inputData.getLong("photoId", -1L)
        val notificationId = photoId.toInt()

        Log.d("SmartJournalWorker", "Analyzing photoId: $photoId")

        val database = AppDatabase.getDatabase(applicationContext)
        val babyManager = BabyManager(applicationContext)
        val repository = PhotoRepository(applicationContext, database.photoDao(), babyManager)

        NotificationHelper.showNotification(
            applicationContext,
            "AI 기록 생성 중",
            "사진을 분석하여 기록을 작성하고 있습니다...",
            notificationId
        )

        try {
            val allPhotos = repository.getPhotos().first()
            val photo = allPhotos.find { it.id == photoId } ?: return Result.failure()

            val response = repository.describePhoto(photo)
            
            if (response != null) {
                Log.d("SmartJournalWorker", "Analysis success: ${response.caption}")
                NotificationHelper.showNotification(
                    applicationContext,
                    "AI 기록 완료",
                    "새로운 기록이 생성되었습니다: ${response.caption ?: ""}",
                    notificationId
                )
                return Result.success()
            } else {
                Log.e("SmartJournalWorker", "Analysis failed: null response")
                NotificationHelper.showNotification(
                    applicationContext,
                    "AI 기록 실패",
                    "서버와 통신 중 오류가 발생했습니다.",
                    notificationId
                )
                return Result.failure()
            }
        } catch (e: Exception) {
            Log.e("SmartJournalWorker", "Exception during analysis", e)
            e.printStackTrace()
            NotificationHelper.showNotification(
                applicationContext,
                "AI 기록 실패",
                "분석 중 오류가 발생했습니다: ${e.message}",
                notificationId
            )
            return Result.failure()
        }
    }
}
