package com.sungmin.haru_i.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object BitmapUtils {
    private const val TAG = "BitmapUtils"

    /**
     * URI로부터 이미지를 읽어와 리사이징하고 임시 파일로 저장합니다.
     * (Android 12 이상 최적화 방식)
     */
    fun getResizedImageFile(context: Context, uri: Uri, maxDimension: Int = 1024): File? {
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            
            // 1. 리사이징 설정
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val srcWidth = info.size.width
                val srcHeight = info.size.height
                
                if (max(srcWidth, srcHeight) > maxDimension) {
                    val ratio = maxDimension.toFloat() / max(srcWidth, srcHeight)
                    val targetWidth = (srcWidth * ratio).toInt()
                    val targetHeight = (srcHeight * ratio).toInt()
                    decoder.setTargetSize(targetWidth, targetHeight)
                    Log.d(TAG, "Resizing from ${srcWidth}x${srcHeight} to ${targetWidth}x${targetHeight}")
                }
                
                // 고화질 설정 및 회전 자동 보정
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }

            // 2. 임시 파일 생성
            val tempFile = File(context.cacheDir, "resized_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            
            Log.d(TAG, "Resized file created: ${tempFile.length() / 1024} KB")
            
            // 3. 비트맵 자원 해제
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error in getResizedImageFile", e)
            null
        }
    }
}
