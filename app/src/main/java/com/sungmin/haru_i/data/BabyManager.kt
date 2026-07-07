package com.sungmin.haru_i.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BabyInfo(
    val name: String = "",
    val birthday: Long = 0L // Timestamp in milliseconds
)

class BabyManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("baby_prefs", Context.MODE_PRIVATE)
    
    private val _babyInfo = MutableStateFlow(loadBabyInfo())
    val babyInfo: StateFlow<BabyInfo> = _babyInfo.asStateFlow()

    private fun loadBabyInfo(): BabyInfo {
        val name = prefs.getString("baby_name", "") ?: ""
        val birthday = prefs.getLong("baby_birthday", 0L)
        return BabyInfo(name, birthday)
    }

    fun updateBabyInfo(name: String, birthday: Long) {
        prefs.edit().apply {
            putString("baby_name", name)
            putLong("baby_birthday", birthday)
            apply()
        }
        _babyInfo.value = BabyInfo(name, birthday)
    }
}
