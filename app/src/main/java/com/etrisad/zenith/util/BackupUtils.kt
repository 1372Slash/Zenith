package com.etrisad.zenith.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.etrisad.zenith.data.local.database.ZenithDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupUtils {
    private const val DATABASE_NAME = "zenith_database"
    private const val PREFS_FILE_NAME = "settings.preferences_pb"

    data class BackupMetadata(
        val hasDatabase: Boolean,
        val hasPreferences: Boolean,
        val fileSize: Long,
        val latestUsageDate: String? = null,
        val latestUsageMillis: Long? = null
    )

    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
    }

    suspend fun backupDatabase(context: Context, targetUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Siapkan file-file yang akan di-backup
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            val prefsFile = File(context.filesDir, "datastore/$PREFS_FILE_NAME")

            // Tutup DB untuk memastikan data di-flush ke disk
            ZenithDatabase.closeDatabase()

            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zipOut ->
                    // Backup Database
                    if (dbFile.exists()) {
                        addToZip(zipOut, dbFile, DATABASE_NAME)
                    }
                    // Backup Preferences (DataStore)
                    if (prefsFile.exists()) {
                        addToZip(zipOut, prefsFile, PREFS_FILE_NAME)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreDatabase(context: Context, sourceUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Tutup koneksi DB aktif
            ZenithDatabase.closeDatabase()

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            DATABASE_NAME -> {
                                val dbFile = context.getDatabasePath(DATABASE_NAME)
                                FileOutputStream(dbFile).use { zipIn.copyTo(it) }
                            }
                            PREFS_FILE_NAME -> {
                                val prefsFile = File(context.filesDir, "datastore/$PREFS_FILE_NAME")
                                prefsFile.parentFile?.mkdirs()
                                FileOutputStream(prefsFile).use { zipIn.copyTo(it) }
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }

            // Hapus file sementara Room agar database yang di-restore bersih
            val dbPath = context.getDatabasePath(DATABASE_NAME).path
            File("$dbPath-wal").delete()
            File("$dbPath-shm").delete()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBackupMetadata(context: Context, uri: Uri): BackupMetadata? = withContext(Dispatchers.IO) {
        try {
            var hasDb = false
            var hasPrefs = false
            val fileSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L

            // Coba baca sebagai ZIP terlebih dahulu
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    try {
                        var entry = zipIn.nextEntry
                        while (entry != null) {
                            val entryName = entry.name.lowercase()
                            if (entryName.contains(DATABASE_NAME.lowercase())) hasDb = true
                            if (entryName.contains(PREFS_FILE_NAME.lowercase())) hasPrefs = true
                            
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    } catch (e: Exception) {
                        // Jika bukan ZIP, biarkan hasDb & hasPrefs tetap false
                    }
                }
            }

            // Jika bukan ZIP, cek apakah ini file DB mentah (Fallback untuk backup lama)
            if (!hasDb && !hasPrefs) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val header = ByteArray(16)
                    if (input.read(header) >= 16) {
                        val headerString = String(header)
                        if (headerString.contains("SQLite format 3")) {
                            hasDb = true
                        }
                    }
                }
            }

            if (hasDb || hasPrefs) {
                var latestDate: String? = null
                var latestMillis: Long? = null

                // Jika ada DB, coba intip data screen time terakhir
                if (hasDb) {
                    try {
                        val tempDbFile = File(context.cacheDir, "temp_restore.db")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            ZipInputStream(input).use { zipIn ->
                                var entry = zipIn.nextEntry
                                while (entry != null) {
                                    if (entry.name.lowercase().contains(DATABASE_NAME.lowercase())) {
                                        FileOutputStream(tempDbFile).use { zipIn.copyTo(it) }
                                        break
                                    }
                                    zipIn.closeEntry()
                                    entry = zipIn.nextEntry
                                }
                            }
                            
                            // Jika ZIP gagal menemukan (mungkin file mentah), coba copy langsung
                            if (!tempDbFile.exists() || tempDbFile.length() == 0L) {
                                context.contentResolver.openInputStream(uri)?.use { rawInput ->
                                    FileOutputStream(tempDbFile).use { rawInput.copyTo(it) }
                                }
                            }
                        }

                        if (tempDbFile.exists() && tempDbFile.length() > 0) {
                            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                                tempDbFile.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                            )
                            db.rawQuery(
                                "SELECT date, usageTimeMillis FROM daily_usage WHERE packageName = 'TOTAL' ORDER BY date DESC LIMIT 1",
                                null
                            ).use { cursor ->
                                if (cursor.moveToFirst()) {
                                    latestDate = cursor.getString(0)
                                    latestMillis = cursor.getLong(1)
                                }
                            }
                            db.close()
                        }
                        tempDbFile.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                BackupMetadata(hasDb, hasPrefs, fileSize, latestDate, latestMillis)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun addToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { input ->
            zipOut.putNextEntry(ZipEntry(entryName))
            input.copyTo(zipOut)
            zipOut.closeEntry()
        }
    }
}
