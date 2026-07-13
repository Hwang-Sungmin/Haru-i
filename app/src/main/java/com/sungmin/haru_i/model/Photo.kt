package com.sungmin.haru_i.model

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
    val isFavorite: Boolean = false,
    val memo: String = "",
    val dateTaken: Long = 0L,
    val albumId: Long? = null
)
