package com.detox.app

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityStatus {

    /** True if the user switched Detox on in Settings -> Accessibility. */
    fun isWatcherEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AppWatcherService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        // The setting is one string, e.g. "com.transsion.mol/.service.as.ASService:com.detox.app/.AppWatcherService".
        // unflattenFromString understands both the short ".Name" and the full form.
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
}
