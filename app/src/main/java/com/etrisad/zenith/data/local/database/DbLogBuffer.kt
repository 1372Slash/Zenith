package com.etrisad.zenith.data.local.database

import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

data class DbLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: Level,
    val tag: String,
    val message: String
) {
    enum class Level { D, I, W, E }

    fun format(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return "${sdf.format(Date(timestamp))} ${level.name} $tag: $message"
    }
}

object DbLogBuffer {
    private const val MAX_ENTRIES = 500
    private val entries = CopyOnWriteArrayList<DbLogEntry>()

    fun d(tag: String, message: String) {
        add(DbLogEntry(level = DbLogEntry.Level.D, tag = tag, message = message))
    }

    fun w(tag: String, message: String) {
        add(DbLogEntry(level = DbLogEntry.Level.W, tag = tag, message = message))
    }

    fun e(tag: String, message: String) {
        add(DbLogEntry(level = DbLogEntry.Level.E, tag = tag, message = message))
    }

    private fun add(entry: DbLogEntry) {
        entries.add(entry)
        if (entries.size > MAX_ENTRIES) {
            val toRemove = entries.size - MAX_ENTRIES
            repeat(toRemove) { entries.removeAt(0) }
        }
    }

    fun getAll(): List<DbLogEntry> = entries.toList()

    fun getFiltered(tag: String? = null, level: DbLogEntry.Level? = null): List<DbLogEntry> {
        return entries.filter { e ->
            (tag == null || e.tag == tag) && (level == null || e.level == level)
        }
    }

    fun clear() = entries.clear()

    fun copyAll(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Zenith DB Log Dump ===")
        sb.appendLine("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine("Entries: ${entries.size}")
        sb.appendLine("==========================")
        entries.forEach { sb.appendLine(it.format()) }
        return sb.toString()
    }

    fun copyFiltered(tag: String): String {
        val filtered = getFiltered(tag = tag)
        val sb = StringBuilder()
        sb.appendLine("=== Zenith Log: $tag ===")
        sb.appendLine("Entries: ${filtered.size}")
        sb.appendLine("==========================")
        filtered.forEach { sb.appendLine(it.format()) }
        return sb.toString()
    }
}
