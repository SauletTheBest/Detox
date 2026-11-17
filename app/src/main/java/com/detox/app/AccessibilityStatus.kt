package com.detox.app

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityStatus {

    /** True if the user switched Detox on in Settings -> Accessibility. */
    fun isWatcherEnabled(context: Context): Boolean = isEnabled(context, AppWatcherService::class.java)

    /** True if the optional "Detox Reels guard" is switched on there too. */
    fun isReelsGuardEnabled(context: Context): Boolean = isEnabled(context, ReelsGuardService::class.java)

    private fun isEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        val expected = ComponentName(context, service)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        // The setting is one string, e.g. "com.transsion.mol/.service.as.ASService:com.detox.app/.AppWatcherService".
        // unflattenFromString understands both the short ".Name" and the full form.
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
}
