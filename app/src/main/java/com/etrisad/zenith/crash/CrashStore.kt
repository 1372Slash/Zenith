package com.etrisad.zenith.crash

import android.content.Context
import android.os.Build
import com.etrisad.zenith.BuildConfig
import com.etrisad.zenith.data.local.database.DbLogBuffer
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
private const val MAX_LOG_TAIL_LINES = 400
data class CrashRecord(
    val timestamp: Long = System.currentTimeMillis(),
    val versionName: String = "",
    val versionCode: Int = 0,
    val flavor: String = "",
    val threadName: String = "",
    val exceptionClass: String = "",
    val message: String = "",
    val stackTrace: String = "",
    val logTail: String = "",
    val device: String = ""
) {
    fun toReportText(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return buildString {
            appendLine("==== Zenith Crash Report ====")
            appendLine("Time    : ${sdf.format(Date(timestamp))}")
            appendLine("Version : $versionName ($versionCode) [$flavor]")
            appendLine("Device  : $device")
            appendLine("Thread  : $threadName")
            appendLine("Error   : $exceptionClass")
            if (message.isNotBlank()) appendLine("Message : $message")
            appendLine()
            appendLine("--- Stack trace ---")
            appendLine(stackTrace)
            appendLine()
            appendLine("--- App log (last $MAX_LOG_TAIL_LINES lines) ---")
            appendLine(logTail)
            appendLine("==============================")
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("timestamp", timestamp)
        put("versionName", versionName)
        put("versionCode", versionCode)
        put("flavor", flavor)
        put("threadName", threadName)
        put("exceptionClass", exceptionClass)
        put("message", message)
        put("stackTrace", stackTrace)
        put("logTail", logTail)
        put("device", device)
    }

    companion object {
        fun fromJson(json: JSONObject): CrashRecord = CrashRecord(
            timestamp = json.optLong("timestamp"),
            versionName = json.optString("versionName"),
            versionCode = json.optInt("versionCode"),
            flavor = json.optString("flavor"),
            threadName = json.optString("threadName"),
            exceptionClass = json.optString("exceptionClass"),
            message = json.optString("message"),
            stackTrace = json.optString("stackTrace"),
            logTail = json.optString("logTail"),
            device = json.optString("device")
        )
    }
}

/**
 * File + preference storage for caught crashes. Every public method is
 * defensive: it must never throw while running inside the uncaught-exception
 * handler.
 */
object CrashStore {
    private const val PREFS_NAME = "zenith_crash"
    private const val KEY_UNSEEN = "unseen_crash"
    private const val KEY_TIMES = "crash_times"

    private const val MAX_HISTORY_FILES = 10
    private const val MAX_STACK_CHARS = 32_000

    const val RAPID_WINDOW_MS = 60_000L
    const val RAPID_THRESHOLD = 3

    /**
     * True when [crashTimes] contains [RAPID_THRESHOLD] or more crashes inside
     * [RAPID_WINDOW_MS]. Used to stop relaunching the crash screen forever
     * when the app crashes on startup or on the crash screen itself.
     * Pure function so it can be unit tested.
     */
    fun isRapidCrashLoop(crashTimes: List<Long>, now: Long = System.currentTimeMillis()): Boolean =
        crashTimes.count { it <= now && now - it <= RAPID_WINDOW_MS } >= RAPID_THRESHOLD

    fun record(context: Context, thread: Thread, throwable: Throwable): CrashRecord? {
        return try {
            val record = CrashRecord(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                flavor = BuildConfig.FLAVOR,
                threadName = thread.name,
                exceptionClass = throwable.javaClass.name,
                message = throwable.message ?: "",
                stackTrace = stackTraceOf(throwable),
                logTail = logTail(),
                device = "${Build.MANUFACTURER} ${Build.MODEL} " +
                    "(Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"
            )
            val dir = crashDir(context)
            if (!dir.exists()) dir.mkdirs()
            val report = record.toReportText()
            File(dir, "last_crash.txt").writeText(report)
            File(dir, "last_crash.json").writeText(record.toJson().toString())

            val historyDir = File(dir, "history")
            if (!historyDir.exists()) historyDir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(record.timestamp))
            try {
                File(historyDir, "crash_$stamp.txt").writeText(report)
            } catch (_: Throwable) {
            }
            pruneHistory(historyDir)

            markUnseen(context, record.timestamp)
            record
        } catch (_: Throwable) {
            null
        }
    }

    fun latest(context: Context): CrashRecord? {
        return try {
            val file = File(crashDir(context), "last_crash.json")
            if (!file.exists()) return null
            CrashRecord.fromJson(JSONObject(file.readText()))
        } catch (_: Throwable) {
            null
        }
    }

    fun lastCrashReportText(context: Context): String? = try {
        val file = File(crashDir(context), "last_crash.txt")
        if (file.exists()) file.readText() else null
    } catch (_: Throwable) {
        null
    }

    fun hasAnyCrash(context: Context): Boolean = try {
        File(crashDir(context), "last_crash.json").exists()
    } catch (_: Throwable) {
        false
    }

    /**
     * Relative zip path -> text content for the diagnostic export, so a bug
     * report always carries the last crash plus a bit of history.
     */
    fun reportEntries(context: Context): List<Pair<String, String>> {
        val entries = mutableListOf<Pair<String, String>>()
        try {
            val dir = crashDir(context)
            val last = File(dir, "last_crash.txt")
            if (last.exists()) entries += "crash/last_crash.txt" to last.readText()
            val historyDir = File(dir, "history")
            historyDir.listFiles()
                ?.filter { it.isFile }
                ?.sortedByDescending { it.name }
                ?.take(5)
                ?.forEach { file ->
                    try {
                        entries += "crash/history/${file.name}" to file.readText()
                    } catch (_: Throwable) {
                    }
                }
        } catch (_: Throwable) {
        }
        return entries
    }

    fun hasUnseen(context: Context): Boolean = try {
        prefs(context).getBoolean(KEY_UNSEEN, false)
    } catch (_: Throwable) {
        false
    }

    fun markSeen(context: Context) {
        try {
            prefs(context).edit().putBoolean(KEY_UNSEEN, false).apply()
        } catch (_: Throwable) {
        }
    }

    fun crashTimes(context: Context): List<Long> = try {
        val raw = prefs(context).getString(KEY_TIMES, "") ?: ""
        raw.split(",").mapNotNull { it.trim().toLongOrNull() }
    } catch (_: Throwable) {
        emptyList()
    }

    fun isRapidCrashLoop(context: Context): Boolean = isRapidCrashLoop(crashTimes(context))

    private fun markUnseen(context: Context, timestamp: Long) {
        try {
            val existing = prefs(context).getString(KEY_TIMES, "") ?: ""
            val times = (existing.split(",").mapNotNull { it.trim().toLongOrNull() } + timestamp)
                .takeLast(20)
            prefs(context).edit()
                .putBoolean(KEY_UNSEEN, true)
                .putString(KEY_TIMES, times.joinToString(","))
                .commit()
        } catch (_: Throwable) {
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun crashDir(context: Context): File = File(context.filesDir, "crash")

    private fun pruneHistory(historyDir: File) {
        try {
            val files = historyDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.name }
                ?: return
            files.drop(MAX_HISTORY_FILES).forEach { it.delete() }
        } catch (_: Throwable) {
        }
    }

    private fun stackTraceOf(throwable: Throwable): String {
        return try {
            val writer = StringWriter()
            throwable.printStackTrace(PrintWriter(writer))
            val full = writer.toString()
            if (full.length > MAX_STACK_CHARS) {
                full.take(MAX_STACK_CHARS) + "\n... (truncated)"
            } else {
                full
            }
        } catch (_: Throwable) {
            "(stack trace unavailable)"
        }
    }

    private fun logTail(): String {
        return try {
            DbLogBuffer.getAll().takeLast(MAX_LOG_TAIL_LINES).joinToString("\n") { it.format() }
        } catch (_: Throwable) {
            "(log buffer unavailable)"
        }
    }
}
