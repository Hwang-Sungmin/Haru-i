package com.sungmin.haru_i.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.sungmin.haru_i.data.local.AlbumEntity
import com.sungmin.haru_i.data.local.PhotoDao
import com.sungmin.haru_i.data.local.PhotoMeta
import com.sungmin.haru_i.data.remote.RetrofitClient
import com.sungmin.haru_i.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class PhotoRepository(
    private val context: Context,
    private val photoDao: PhotoDao
) {

    fun getPhotos(): Flow<List<Photo>> = combine(
        getMediaStorePhotos(),
        photoDao.getAllMeta()
    ) { mediaStorePhotos, favoriteMetas ->
        val metaMap = favoriteMetas.associateBy { it.uri }
        mediaStorePhotos.map { photo ->
            val meta = metaMap[photo.uri.toString()]
            photo.copy(
                isFavorite = meta?.isFavorite ?: false,
                memo = meta?.memo ?: "",
                albumId = meta?.albumId,
                aiCaption = meta?.aiCaption,
                emotion = meta?.emotion
            )
        }
    }.flowOn(Dispatchers.IO)

    fun getAllAlbums(): Flow<List<AlbumEntity>> = photoDao.getAllAlbums()

    suspend fun createAlbum(name: String, photos: List<Photo>) {
        val albumId = photoDao.insertAlbum(AlbumEntity(name = name))
        photoDao.addPhotosToAlbum(photos.map { it.uri.toString() }, albumId)
    }

    suspend fun addPhotosToAlbum(albumId: Long, photos: List<Photo>) {
        photoDao.addPhotosToAlbum(photos.map { it.uri.toString() }, albumId)
    }

    suspend fun removePhotoFromAlbum(photo: Photo) {
        val existingMeta = photoDao.getMetaByUri(photo.uri.toString())
        photoDao.insertMeta(
            existingMeta?.copy(albumId = null)
                ?: PhotoMeta(uri = photo.uri.toString(), albumId = null)
        )
    }

    suspend fun deleteAlbum(album: AlbumEntity) {
        // First, clear albumId for all photos in this album
        val photosInAlbum = photoDao.getPhotosInAlbum(album.id).first()
        photoDao.addPhotosToAlbum(photosInAlbum.map { it.uri }, null)
        photoDao.deleteAlbum(album)
    }

    private fun getMediaStorePhotos(): Flow<List<Photo>> = flow {
        val photos = mutableListOf<Photo>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val dateTaken = cursor.getLong(dateTakenColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                photos.add(Photo(id, contentUri, name, dateAdded, dateTaken = dateTaken))
            }
        }
        emit(photos)
    }

    suspend fun toggleFavorite(photo: Photo) {
        val newFavoriteStatus = !photo.isFavorite
        val existingMeta = photoDao.getMetaByUri(photo.uri.toString())
        photoDao.insertMeta(
            existingMeta?.copy(isFavorite = newFavoriteStatus)
                ?: PhotoMeta(uri = photo.uri.toString(), isFavorite = newFavoriteStatus)
        )
    }

    suspend fun updateMemo(photo: Photo, memo: String) {
        val existingMeta = photoDao.getMetaByUri(photo.uri.toString())
        photoDao.insertMeta(
            existingMeta?.copy(memo = memo)
                ?: PhotoMeta(uri = photo.uri.toString(), memo = memo)
        )
    }

    suspend fun updateAiAnalysis(photo: Photo, caption: String?, emotion: String?) {
        val existingMeta = photoDao.getMetaByUri(photo.uri.toString())
        photoDao.insertMeta(
            existingMeta?.copy(aiCaption = caption, emotion = emotion)
                ?: PhotoMeta(uri = photo.uri.toString(), aiCaption = caption, emotion = emotion)
        )
    }

    suspend fun describePhoto(photo: Photo): com.sungmin.haru_i.data.remote.DescribeResponse? {
        return try {
            val file = getFileFromUri(photo.uri) ?: return null
            val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            
            val response = RetrofitClient.apiService.describePhoto(body)
            
            // 분석 결과 저장
            if (response.caption != null || response.emotion != null) {
                updateAiAnalysis(photo, response.caption, response.emotion)
            }
            
            response
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileFromUri(uri: android.net.Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "temp_describe_${System.currentTimeMillis()}.jpg")
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }
}
