package com.etrisad.zenith.data.local.database

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

data class UsageGraphLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: Level,
    val tag: String,
    val message: String
) {
    enum class Level { D, W, E }

    fun format(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return "${sdf.format(Date(timestamp))} ${level.name} $tag: $message"
    }
}

object UsageGraphLogBuffer {
    private const val MAX_ENTRIES = 500
    private val entries = CopyOnWriteArrayList<UsageGraphLogEntry>()

    fun d(tag: String, message: String) {
        add(UsageGraphLogEntry(level = UsageGraphLogEntry.Level.D, tag = tag, message = message))
    }

    fun w(tag: String, message: String) {
        add(UsageGraphLogEntry(level = UsageGraphLogEntry.Level.W, tag = tag, message = message))
    }

    fun e(tag: String, message: String) {
        add(UsageGraphLogEntry(level = UsageGraphLogEntry.Level.E, tag = tag, message = message))
    }

    private fun add(entry: UsageGraphLogEntry) {
        entries.add(entry)
        if (entries.size > MAX_ENTRIES) {
            val toRemove = entries.size - MAX_ENTRIES
            repeat(toRemove) { entries.removeAt(0) }
        }
    }

    fun getAll(): List<UsageGraphLogEntry> = entries.toList()

    fun getFiltered(tag: String? = null, level: UsageGraphLogEntry.Level? = null): List<UsageGraphLogEntry> {
        return entries.filter { e ->
            (tag == null || e.tag == tag) && (level == null || e.level == level)
        }
    }

    fun clear() = entries.clear()

    fun copyAll(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Zenith Usage Graph Log Dump ===")
        sb.appendLine("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine("Entries: ${entries.size}")
        sb.appendLine("Legend: <date>:t=<shown bar total>,db=<database TOTAL or ->,sys=<system TOTAL or ->")
        sb.appendLine("        ,ZERO_BUT_SYS marks a bar showing 0 while system usage has data for that day")
        sb.appendLine("===================================")
        entries.forEach { sb.appendLine(it.format()) }
        return sb.toString()
    }
}