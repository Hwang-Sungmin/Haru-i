package com.sungmin.haru_i.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BabyInfo(
    val name: String = "",
    val birthday: Long = 0L, // Timestamp in milliseconds
    val referencePhotoUri: String? = null // 기준 사진 URI 추가
)

class BabyManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("baby_prefs", Context.MODE_PRIVATE)
    
    private val _babyInfo = MutableStateFlow(loadBabyInfo())
    val babyInfo: StateFlow<BabyInfo> = _babyInfo.asStateFlow()

    private fun loadBabyInfo(): BabyInfo {
        val name = prefs.getString("baby_name", "") ?: ""
        val birthday = prefs.getLong("baby_birthday", 0L)
        val photoUri = prefs.getString("baby_photo", null)
        return BabyInfo(name, birthday, photoUri)
    }

    fun updateBabyInfo(name: String, birthday: Long, photoUri: String? = null) {
        prefs.edit().apply {
            putString("baby_name", name)
            putLong("baby_birthday", birthday)
            putString("baby_photo", photoUri ?: _babyInfo.value.referencePhotoUri)
            apply()
        }
        _babyInfo.value = BabyInfo(name, birthday, photoUri ?: _babyInfo.value.referencePhotoUri)
    }

    fun getUserId(): String {
        var id = prefs.getString("user_id", null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("user_id", id).apply()
        }
        return id
    }
}
