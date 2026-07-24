package com.sungmin.haru_i.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object BitmapUtils {
    private const val TAG = "BitmapUtils"

    fun getResizedImageFile(context: Context, uri: Uri, maxDimension: Int = 1024): File? {
        return try {
            val resolver = context.contentResolver
            
            // 1. 이미지 크기 측정
            val options = BitmapFactory.Options()
            val field1 = options.javaClass.getField("inJustDecodeSize")
            field1.set(options, true)
            
            resolver.openInputStream(uri)?.use { 
                BitmapFactory.decodeStream(it, null, options)
            }

            // 2. 샘플 사이즈 계산
            var sampleSize = 1
            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            
            if (max(srcWidth, srcHeight) > maxDimension) {
                val halfWidth = srcWidth / 2
                val halfHeight = srcHeight / 2
                while ((halfWidth / sampleSize) >= maxDimension || (halfHeight / sampleSize) >= maxDimension) {
                    sampleSize *= 2
                }
            }

            // 3. 비트맵 로드
            val decodeOptions = BitmapFactory.Options()
            val field2 = decodeOptions.javaClass.getField("inSampleSize")
            field2.set(decodeOptions, sampleSize)
            
            var bitmap = resolver.openInputStream(uri)?.use { 
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            // 4. 회전 보정
            bitmap = rotateBitmapIfRequired(context, bitmap, uri)

            // 5. 정밀 리사이징
            if (max(bitmap.width, bitmap.height) > maxDimension) {
                val scale = maxDimension.toFloat() / max(bitmap.width, bitmap.height)
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                if (scaledBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = scaledBitmap
                }
            }

            // 6. 저장
            val tempFile = File(context.cacheDir, "resized_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            
            Log.d(TAG, "Resized to: ${tempFile.length() / 1024} KB")
            bitmap.recycle()
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Resize error", e)
            null
        }
    }

    private fun rotateBitmapIfRequired(context: Context, bitmap: Bitmap, uri: Uri): Bitmap {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exifInterface = ExifInterface(input)
                val orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
                    else -> bitmap
                }
            } ?: bitmap
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }
}
