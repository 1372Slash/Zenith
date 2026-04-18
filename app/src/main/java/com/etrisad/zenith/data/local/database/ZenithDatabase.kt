package com.etrisad.zenith.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.etrisad.zenith.data.local.dao.ShieldDao
import com.etrisad.zenith.data.local.entity.ShieldEntity

@Database(entities = [ShieldEntity::class], version = 3, exportSchema = false)
abstract class ZenithDatabase : RoomDatabase() {
    abstract fun shieldDao(): ShieldDao

    companion object {
        @Volatile
        private var INSTANCE: ZenithDatabase? = null

        fun getDatabase(context: Context): ZenithDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZenithDatabase::class.java,
                    "zenith_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
