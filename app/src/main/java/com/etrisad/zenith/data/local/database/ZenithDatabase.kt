package com.etrisad.zenith.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.etrisad.zenith.data.local.dao.ShieldDao
import com.etrisad.zenith.data.local.entity.ShieldEntity

@Database(entities = [ShieldEntity::class], version = 4, exportSchema = false)
abstract class ZenithDatabase : RoomDatabase() {
    abstract fun shieldDao(): ShieldDao

    companion object {
        @Volatile
        private var INSTANCE: ZenithDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add 'type' column with default 'SHIELD'
                db.execSQL("ALTER TABLE shields ADD COLUMN type TEXT NOT NULL DEFAULT 'SHIELD'")
                // Add 'goalReminderPeriodMinutes' column with default 0
                db.execSQL("ALTER TABLE shields ADD COLUMN goalReminderPeriodMinutes INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): ZenithDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZenithDatabase::class.java,
                    "zenith_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
