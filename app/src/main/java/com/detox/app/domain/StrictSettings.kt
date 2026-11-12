package com.detox.app.domain

/**
 * Strict mode = the phone gets boring: the home screen becomes a plain list of app names,
 * and paused apps stay closed all day (not only in quiet times).
 *
 * countdownSec     = how long you must wait (on screen) before any rule can be loosened.
 * protectionWanted = do you WANT the bouncer (Accessibility service) on? It only becomes false
 *                    when you switch protection off inside Detox (after the countdown).
 *                    If protection is off but this is still true, someone switched it off
 *                    behind Detox's back, so Detox may switch it back on (see ProtectionGuard).
 */
data class StrictSettings(
    val strictModeOn: Boolean = false,
    val countdownSec: Int = 60,
    val protectionWanted: Boolean = true,
) {
    companion object {
        /** Choices offered in Settings. No "0": a countdown always exists. */
        val COUNTDOWN_CHOICES = listOf(30, 60, 300, 600)
    }
}
