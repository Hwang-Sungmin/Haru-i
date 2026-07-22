package com.sungmin.haru_i.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_meta")
data class PhotoMeta(
    @PrimaryKey val uri: String,
    val isFavorite: Boolean = false,
    val memo: String = "",
    val albumId: Long? = null, // 소속된 앨범 ID 추가
    val aiCaption: String? = null,
    val emotion: String? = null,
    val isBaby: Boolean = false
)
