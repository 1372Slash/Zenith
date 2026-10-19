package com.etrisad.zenith.crash

import android.content.Context
import android.content.Intent
import android.os.Process
object SafeMode {
    private const val PREFS_NAME = "zenith_safety"
    private const val KEY_ENABLED = "safe_mode_enabled"

    @Volatile
    private var cached: Boolean? = null

    fun isEnabled(context: Context): Boolean {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: run {
                val value = try {
                    context.applicationContext
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(KEY_ENABLED, false)
                } catch (_: Throwable) {
                    false
                }
                cached = value
                value
            }
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        cached = enabled
        try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .commit()
        } catch (_: Throwable) {
        }
        try {
            com.etrisad.zenith.data.local.database.DbLogBuffer.w(
                "ZenithSafety", "SAFE_MODE: enabled=$enabled (restart required)"
            )
        } catch (_: Throwable) {
        }
    }

    /**
     * Relaunches the app in a completely fresh process. Needed after toggling
     * safe mode because repositories are bound to their store/database at
     * first access. The queued activity start is handed to ActivityManager
     * (synchronous binder call) before the process is killed.
     */
    fun restartApp(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent().setClassName(context.packageName, "com.etrisad.zenith.MainActivity")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
        killProcess()
    }

    fun killProcess() {
        try {
            Process.killProcess(Process.myPid())
        } catch (_: Throwable) {
        }
        try {
            kotlin.system.exitProcess(10)
        } catch (_: Throwable) {
        }
    }
}
