package com.detox.app

import android.content.Context
import android.util.Log
import com.detox.app.data.RulesRepository

/**
 * Self-healing protection.
 *
 * If you switch protection off INSIDE Detox (after the countdown), Detox remembers
 * "not wanted" and leaves it off. But if it was switched off some other way (Android's
 * Accessibility page, the floating shortcut, a HiOS cleaner...), "wanted" is still true,
 * so Detox quietly switches it back on.
 *
 * Only works after the one-time PC grant (WRITE_SECURE_SETTINGS). Without it, this does
 * nothing and the red "Protection is off" card on Home is the only reminder.
 *
 * Called from MainActivity.onResume and LauncherActivity.onResume (= every Home press
 * in strict mode), so it's cheap: two quick checks before touching the database.
 */
object ProtectionGuard {

    /** @return true = protection was off and Detox switched it back on. */
    suspend fun heal(context: Context): Boolean {
        if (!SystemStatus.canWriteSecureSettings(context)) return false
        if (AccessibilityStatus.isWatcherEnabled(context)) return false   // already on, nothing to heal

        val wanted = RulesRepository.readStrict().protectionWanted
        if (!wanted) return false                                         // you switched it off on purpose

        val healed = SystemStatus.setProtectionEnabled(context, true)
        Log.i(TAG, "Protection was off behind Detox's back. Switched back on: $healed")
        return healed
    }

    private const val TAG = "Detox"
}
