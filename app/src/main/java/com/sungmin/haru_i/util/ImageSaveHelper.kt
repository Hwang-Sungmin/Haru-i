package com.sungmin.haru_i.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.OutputStream

object ImageSaveHelper {
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, name: String): Uri? {
        val filename = "${name}_edited_${System.currentTimeMillis()}.jpg"
        var outputStream: OutputStream? = null
        var imageUri: Uri? = null
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Harui")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        try {
            imageUri = resolver.insert(collection, contentValues)
            if (imageUri == null) return null
            
            outputStream = resolver.openOutputStream(imageUri)
            if (outputStream == null) return null
            
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            
            return imageUri
        } catch (e: Exception) {
            e.printStackTrace()
            if (imageUri != null) {
                resolver.delete(imageUri, null, null)
            }
            return null
        } finally {
            outputStream?.close()
        }
    }
}
