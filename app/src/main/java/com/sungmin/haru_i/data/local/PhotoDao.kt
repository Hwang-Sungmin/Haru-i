package com.sungmin.haru_i.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photo_meta")
    fun getAllMeta(): Flow<List<PhotoMeta>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeta(meta: PhotoMeta)

    @Delete
    suspend fun deleteMeta(meta: PhotoMeta)
    
    @Query("SELECT * FROM photo_meta WHERE uri = :uri LIMIT 1")
    suspend fun getMetaByUri(uri: String): PhotoMeta?

    // 앨범 관련 쿼리
    @Query("SELECT * FROM albums ORDER BY createdAt DESC")
    fun getAllAlbums(): Flow<List<AlbumEntity>>

    @Insert
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Delete
    suspend fun deleteAlbum(album: AlbumEntity)

    @Query("UPDATE photo_meta SET albumId = :albumId WHERE uri IN (:uris)")
    suspend fun addPhotosToAlbum(uris: List<String>, albumId: Long?)

    @Query("SELECT * FROM photo_meta WHERE albumId = :albumId")
    fun getPhotosInAlbum(albumId: Long): Flow<List<PhotoMeta>>

    @Query("UPDATE photo_meta SET aiCaption = :caption, emotion = :emotion WHERE uri = :uri")
    suspend fun updateAiAnalysis(uri: String, caption: String?, emotion: String?)

    @Query("UPDATE photo_meta SET isBaby = :isBaby WHERE uri = :uri")
    suspend fun updateBabyStatus(uri: String, isBaby: Boolean)
}
