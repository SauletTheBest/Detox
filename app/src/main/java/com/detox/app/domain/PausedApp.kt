package com.detox.app.domain

/**
 * An app that Detox closes during quiet times (and counts toward the daily limit).
 * [dailyLimitMin] = this app's own limit per day; null = no own limit.
 */
data class PausedApp(
    val name: String,
    val packageName: String,
    val dailyLimitMin: Int? = null,
) {
    companion object {
        /** Used by screen previews. Real starting rows are inserted by the database (SeedDefaults). */
        val defaults = listOf(
            PausedApp("Instagram", "com.instagram.android"),
            PausedApp("Threads", "com.instagram.barcelona"),
            PausedApp("YouTube", "com.google.android.youtube"),
            PausedApp("Pinterest", "com.pinterest"),
        )
    }
}
