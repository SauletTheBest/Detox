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
 * The optional Reels & Shorts guard heals the same way (its own "wanted" flag, DB v6).
 *
 * Called from MainActivity.onResume and LauncherActivity.onResume (= every Home press
 * in strict mode), so it's cheap: quick checks before touching the database.
 */
object ProtectionGuard {

    /** @return true = something was off and Detox switched it back on. */
    suspend fun heal(context: Context): Boolean {
        if (!SystemStatus.canWriteSecureSettings(context)) return false
        val watcherOff = !AccessibilityStatus.isWatcherEnabled(context)
        val guardOff = !AccessibilityStatus.isReelsGuardEnabled(context)
        if (!watcherOff && !guardOff) return false                        // both on, nothing to heal

        // Off only matters if you WANT it on (false = you switched it off here, on purpose).
        val wanted = RulesRepository.readStrict()
        var healed = false
        if (watcherOff && wanted.protectionWanted) {
            healed = SystemStatus.setProtectionEnabled(context, true)
            Log.i(TAG, "Protection was off behind Detox's back. Switched back on: $healed")
        }
        if (guardOff && wanted.reelsGuardWanted) {
            val guardHealed = SystemStatus.setReelsGuardEnabled(context, true)
            Log.i(TAG, "Reels guard was off behind Detox's back. Switched back on: $guardHealed")
            healed = healed || guardHealed
        }
        return healed
    }

    private const val TAG = "Detox"
}
