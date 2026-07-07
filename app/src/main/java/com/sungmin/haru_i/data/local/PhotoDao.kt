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
}
