package com.sungmin.haru_i.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PhotoMeta::class, AlbumEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao

    companion object {
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) return tempInstance
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "haru_i_database"
                )
                .fallbackToDestructiveMigration() // 스키마 변경 시 기존 데이터 초기화 및 업데이트 허용
                .build()
                INSTANCE = instance
                return instance
            }
        }
    }
}
