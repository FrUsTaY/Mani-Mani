package com.example.ui.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.service.BankNotificationListener

object NotificationPermissionHelper {

    /**
     * Checks whether the NotificationListenerService is enabled by the user in Android system settings.
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if (!flat.isNullOrEmpty()) {
            val names = flat.split(":").toTypedArray()
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && cn.packageName == packageName) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Creates an Intent to open the Android System settings page for granting Notification Listener permission.
     */
    fun getNotificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}
