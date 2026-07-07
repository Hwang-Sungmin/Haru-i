package com.sungmin.haru_i.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtils {
    fun calculateDDay(birthdayMillis: Long, targetMillis: Long): Long {
        if (birthdayMillis <= 0L) return 0L
        
        val birthCalendar = Calendar.getInstance().apply {
            timeInMillis = birthdayMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val targetCalendar = Calendar.getInstance().apply {
            timeInMillis = targetMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val diff = targetCalendar.timeInMillis - birthCalendar.timeInMillis
        return TimeUnit.MILLISECONDS.toDays(diff) + 1
    }

    fun formatDDay(days: Long): String {
        return if (days >= 0) "D+$days" else "D$days"
    }

    fun formatDate(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN)
        return dateFormat.format(Date(timestamp))
    }
}
