package com.sungmin.haru_i.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photo_meta")
data class PhotoMeta(
    @PrimaryKey val uri: String,
    val isFavorite: Boolean = false,
    val memo: String = "" // 감성 메모 필드 추가
)
