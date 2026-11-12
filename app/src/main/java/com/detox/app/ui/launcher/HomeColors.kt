package com.detox.app.ui.launcher

import androidx.compose.ui.graphics.Color
import com.detox.app.domain.HomePrefs
import com.detox.app.domain.HomeTheme

/**
 * The colours the strict home actually uses right now.
 *
 * HomeTheme (in domain) is what you CHOSE in Settings. HomeLook is what we PAINT, because two
 * things can override your choice for a while:
 *   - a quiet time is running and "Dark during quiet time" is on  -> NIGHT colours
 *   - a photo wallpaper is shown -> we darken it (scrim), so the text must be light
 */
data class HomeLook(
    val background: Color,   // the "paper" (hidden behind the photo when a wallpaper is on)
    val ink: Color,          // main text: clock, app names
    val soft: Color,         // secondary text: date, labels, links
    val dark: Boolean,       // light text on dark -> status-bar icons must be white
    val menu: Color,         // long-press menu background
    val scrimAlpha: Float,   // how much black goes over the wallpaper photo (0 = no photo)
)

fun homeLook(prefs: HomePrefs, quietNow: Boolean, hasWallpaper: Boolean): HomeLook {
    val night = prefs.darkDuringQuiet && quietNow
    val theme = if (night) HomeTheme.NIGHT else prefs.theme
    // Over a darkened photo, dark text would be unreadable: borrow NIGHT's light text colours.
    val text = if (hasWallpaper) HomeTheme.NIGHT else theme
    return HomeLook(
        background = Color(theme.background),
        ink = Color(text.ink),
        soft = Color(text.soft),
        dark = text.dark,
        menu = if (text.dark) Color(0xFF2A2B29) else Color.White,
        // Night + photo: a heavier scrim, so the screen is calm and dim at night.
        scrimAlpha = when {
            !hasWallpaper -> 0f
            night -> 0.70f
            else -> 0.45f
        },
    )
}
