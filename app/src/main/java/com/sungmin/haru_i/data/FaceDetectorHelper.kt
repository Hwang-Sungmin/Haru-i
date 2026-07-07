package com.sungmin.haru_i.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await

class FaceDetectorHelper(private val context: Context) {

    private val faceOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()

    private val faceDetector = FaceDetection.getClient(faceOptions)
    
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    suspend fun isBabyPhoto(uri: Uri): Boolean {
        return try {
            val image = InputImage.fromFilePath(context, uri)
            
            // 1. 얼굴이 있는지 먼저 확인
            val faces = faceDetector.process(image).await()
            if (faces.isEmpty()) return false
            
            // 2. 이미지 라벨링을 통해 아기 관련 키워드가 있는지 확인
            val labels = labeler.process(image).await()
            labels.any { label ->
                val text = label.text.lowercase()
                // 아기, 유아, 어린이 관련 키워드 필터링
                text.contains("baby") || 
                text.contains("infant") || 
                text.contains("toddler") || 
                text.contains("child") ||
                text.contains("face") && label.confidence > 0.8 // 얼굴 확신도가 높을 때
            }
        } catch (e: Exception) {
            false
        }
    }
}
