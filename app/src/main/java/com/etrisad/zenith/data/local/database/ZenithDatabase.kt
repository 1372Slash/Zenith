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
import com.etrisad.zenith.data.local.dao.DailyUsageDao
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.DailyUsageEntity
import com.etrisad.zenith.data.local.Converters

@Database(
    entities = [ShieldEntity::class, ScheduleEntity::class, DailyUsageEntity::class],
    version = 16,
    exportSchema = true,
    autoMigrations = [
        androidx.room.AutoMigration(from = 12, to = 13),
        androidx.room.AutoMigration(from = 13, to = 14),
        androidx.room.AutoMigration(from = 14, to = 15)
    ]
)
@TypeConverters(Converters::class)
abstract class ZenithDatabase : RoomDatabase() {
    abstract fun shieldDao(): ShieldDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun dailyUsageDao(): DailyUsageDao

    companion object {
        @Volatile
        private var INSTANCE: ZenithDatabase? = null

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Handle possible missing columns if previous migrations were skipped or partial
                try {
                    db.execSQL("ALTER TABLE schedules ADD COLUMN emergencyUseCount INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE schedules ADD COLUMN maxEmergencyUses INTEGER NOT NULL DEFAULT 3")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Placeholder if version 9 was an empty migration or handled elsewhere
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE schedules ADD COLUMN maxEmergencyUses INTEGER NOT NULL DEFAULT 3")
                } catch (_: Exception) {}
            }
        }

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

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure all ShieldEntity fields are present
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN maxUsesPerPeriod INTEGER NOT NULL DEFAULT 5")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN refreshPeriodMinutes INTEGER NOT NULL DEFAULT 60")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN currentPeriodUses INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN lastPeriodResetTimestamp INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN isRemindersEnabled INTEGER NOT NULL DEFAULT 1")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN isStrictModeEnabled INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN isAutoQuitEnabled INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN remainingTimeMillis INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN lastUsedTimestamp INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shields ADD COLUMN isPaused INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shields ADD COLUMN pauseEndTimestamp INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): ZenithDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZenithDatabase::class.java,
                    "zenith_database"
                )
                    .addMigrations(
                        MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, 
                        MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, 
                        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                        MIGRATION_15_16
                    )
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE) // Mengurangi file jurnal untuk hemat RAM/Storage
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
