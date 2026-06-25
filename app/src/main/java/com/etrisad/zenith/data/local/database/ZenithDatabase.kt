package com.etrisad.zenith.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors
import com.etrisad.zenith.data.local.dao.ScheduleDao
import com.etrisad.zenith.data.local.dao.ShieldDao
import com.etrisad.zenith.data.local.dao.DailyUsageDao
import com.etrisad.zenith.data.local.dao.HourlyUsageDao
import com.etrisad.zenith.data.local.dao.InterceptedNotificationDao
import com.etrisad.zenith.data.local.entity.ShieldEntity
import com.etrisad.zenith.data.local.entity.ScheduleEntity
import com.etrisad.zenith.data.local.entity.DailyUsageEntity
import com.etrisad.zenith.data.local.entity.HourlyUsageEntity
import com.etrisad.zenith.data.local.entity.InterceptedNotificationEntity
import com.etrisad.zenith.data.local.Converters

@Database(
    entities = [
        ShieldEntity::class,
        ScheduleEntity::class,
        DailyUsageEntity::class,
        HourlyUsageEntity::class,
        InterceptedNotificationEntity::class
    ],
    version = 26,
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
    abstract fun hourlyUsageDao(): HourlyUsageDao
    abstract fun interceptedNotificationDao(): InterceptedNotificationDao

    @Transaction
    open suspend fun deleteUsageForPackageTransaction(date: String, packageName: String) {
        dailyUsageDao().deleteUsageForPackage(date, packageName)
        hourlyUsageDao().deleteHourlyUsageForPackage(date, packageName)
    }

    companion object {
        @Volatile
        private var INSTANCE: ZenithDatabase? = null

        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN limitPeriod TEXT NOT NULL DEFAULT 'DAILY'")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_intercepted_notifications_scheduleId` ON `intercepted_notifications` (`scheduleId`)")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shields_packageName` ON `shields` (`packageName`)")
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_usage_date` ON `daily_usage` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hourly_usage_packageName_date_hour` ON `hourly_usage` (`packageName`, `date`, `hour`)")
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN isGoalCallerEnabled INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN isGoalCallerSoundEnabled INTEGER NOT NULL DEFAULT 1")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN goalCallerSoundUri TEXT")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN isPaused INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN pauseEndTimestamp INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN lastGoalReminderTimestamp INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN lastSessionEndTimestamp INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE shields ADD COLUMN bestStreak INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `daily_usage` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `packageName` TEXT NOT NULL, `usageTimeMillis` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_usage_date_packageName` ON `daily_usage` (`date`, `packageName`)")

                db.execSQL("CREATE TABLE IF NOT EXISTS `hourly_usage` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `hour` INTEGER NOT NULL, `packageName` TEXT NOT NULL, `usageTimeMillis` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_hourly_usage_date_hour_packageName` ON `hourly_usage` (`date`, `hour`, `packageName`)")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `daily_usage` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `packageName` TEXT NOT NULL, `usageTimeMillis` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `hourly_usage` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `hour` INTEGER NOT NULL, `packageName` TEXT NOT NULL, `usageTimeMillis` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE schedules ADD COLUMN interceptNotifications INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
                db.execSQL("CREATE TABLE IF NOT EXISTS `intercepted_notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packageName` TEXT NOT NULL, `title` TEXT, `text` TEXT, `timestamp` INTEGER NOT NULL, `scheduleId` INTEGER NOT NULL)")
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
                        MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
                        MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
                        MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26
                    )
                    .setQueryExecutor(Executors.newFixedThreadPool(4))
                    .setTransactionExecutor(Executors.newSingleThreadExecutor())
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration()
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