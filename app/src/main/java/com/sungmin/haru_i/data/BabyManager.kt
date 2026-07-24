package com.sungmin.haru_i.data

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BabyInfo(
    val name: String = "",
    val birthday: Long = 0L, // Timestamp in milliseconds
    val referencePhotoUri: String? = null // 기준 사진 URI 추가
)

class BabyManager(private val context: Context) {
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

    /**
     * 기기 고유의 Android ID를 반환합니다.
     * 앱을 삭제 후 재설치해도 하드웨어가 동일하면 같은 ID를 유지합니다.
     */
    fun getUserId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }
}
