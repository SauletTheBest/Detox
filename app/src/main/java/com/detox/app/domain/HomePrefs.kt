package com.detox.app.domain

/**
 * Colour themes for the strict home screen. Colours are plain ARGB numbers (Long), so this
 * file stays pure Kotlin (no Android / Compose): the screen turns them into Color(...).
 *
 *   background = the "paper", ink = main text, soft = secondary text (date, labels)
 *   dark = true -> light text on a dark background (status-bar icons must be white)
 */
enum class HomeTheme(val background: Long, val ink: Long, val soft: Long, val dark: Boolean) {
    PAPER(0xFFF4F4F1, 0xFF2B2B2B, 0xFF8A8A85, false),
    SAND(0xFFEFE6D8, 0xFF3A3328, 0xFF8C8172, false),
    MIST(0xFFE6EAEC, 0xFF263038, 0xFF7D8890, false),
    NIGHT(0xFF121412, 0xFFE4E6E1, 0xFF8E948C, true),
}

/**
 * How the strict home screen looks and behaves. One row in the `home_prefs` table.
 * These are looks, not rules, so changing them never needs the countdown.
 */
data class HomePrefs(
    val theme: HomeTheme = HomeTheme.PAPER,
    val wallpaperOn: Boolean = false,          // the photo itself is a file (WallpaperStore), not in the DB
    val quoteOn: Boolean = true,
    val darkDuringQuiet: Boolean = true,       // NIGHT colours while a quiet time is active
    val screenTimeOn: Boolean = true,
    val swipeDownNotifications: Boolean = true,
    val doubleTapLock: Boolean = true,
)
