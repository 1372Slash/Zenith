package com.etrisad.zenith.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
class ZenithDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Zenith tamper protection is active. Removing it allows Zenith to be uninstalled " +
            "and its shields changed without your PIN."

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, ZenithDeviceAdminReceiver::class.java)
    }
}
