package com.sungmin.haru_i.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.sungmin.haru_i.data.local.PhotoDao
import com.sungmin.haru_i.data.local.PhotoMeta
import com.sungmin.haru_i.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

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
                memo = meta?.memo ?: ""
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun getMediaStorePhotos(): Flow<List<Photo>> = flow {
        val photos = mutableListOf<Photo>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
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

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                photos.add(Photo(id, contentUri, name, dateAdded))
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
}
