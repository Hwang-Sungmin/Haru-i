package com.sungmin.haru_i.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

class FaceDetectorHelper(private val context: Context) {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .build()

    private val detector = FaceDetection.getClient(options)

    suspend fun hasFace(uri: Uri): Boolean {
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val faces = detector.process(image).await()
            faces.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
