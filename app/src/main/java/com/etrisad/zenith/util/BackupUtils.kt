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
                    var entry: ZipEntry? = zipIn.getNextEntry()
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
                        entry = zipIn.getNextEntry()
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

    private fun addToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { input ->
            zipOut.putNextEntry(ZipEntry(entryName))
            input.copyTo(zipOut)
            zipOut.closeEntry()
        }
    }
}
