package com.etrisad.zenith.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.etrisad.zenith.data.local.dao.ScheduleDao
import com.etrisad.zenith.data.local.dao.ShieldDao
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.Converters

@Database(entities = [ShieldEntity::class, ScheduleEntity::class], version = 9, exportSchema = false)
@TypeConverters(Converters::class)
abstract class ZenithDatabase : RoomDatabase() {
    abstract fun shieldDao(): ShieldDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: ZenithDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shields ADD COLUMN type TEXT NOT NULL DEFAULT 'SHIELD'")
                db.execSQL("ALTER TABLE shields ADD COLUMN goalReminderPeriodMinutes INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shields ADD COLUMN maxEmergencyUses INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE shields ADD COLUMN lastEmergencyRechargeTimestamp INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shields ADD COLUMN isDelayAppEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shields ADD COLUMN lastDelayStartTimestamp INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shields ADD COLUMN currentStreak INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shields ADD COLUMN lastStreakUpdateTimestamp INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): ZenithDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZenithDatabase::class.java,
                    "zenith_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
