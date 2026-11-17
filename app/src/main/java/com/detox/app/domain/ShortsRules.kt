package com.detox.app.domain

/**
 * "Is this screen Reels / Shorts?", decided from the NAMES of screen parts only.
 *
 * Every Android screen is a tree of views, and many views have a fixed ID name, like
 *   com.google.android.youtube:id/reel_progress_bar   (the thin bar under each Short)
 *   com.instagram.android:id/clips_tab                (the Reels tab; Instagram calls Reels "clips")
 * The Reels guard asks Android "is a view with this ID on screen?" and gets back only the
 * ID and whether it is selected. It never asks for texts, so it can't see messages or names.
 *
 * Pure Kotlin (no Android classes), so the decision is unit-tested on the PC (ShortsRulesTest).
 * When Instagram or YouTube rename a view in an update, this file is the one place to fix.
 */
object ShortsRules {

    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"

    /**
     * What to look for, per app. Keep the app list in sync with android:packageNames in
     * res/xml/reels_guard_config.xml (Android only sends the guard events from those apps).
     */
    val markers: Map<String, List<Marker>> = mapOf(
        YOUTUBE to listOf(Marker("reel_progress_bar")),
        // A tab is always on screen; it only means "you are in Reels" while it is SELECTED.
        INSTAGRAM to listOf(Marker("clips_tab", selectedOnly = true)),
    )

    /** Does the guard care about this app at all? */
    fun watches(packageName: String): Boolean = packageName in markers

    /** "com.instagram.android" + clips_tab -> "com.instagram.android:id/clips_tab" (what Android expects). */
    fun fullId(packageName: String, marker: Marker): String = "$packageName:id/${marker.idName}"

    /**
     * @param views what Android found on screen for this app's markers (ID + selected state).
     * @return true = this is the short-video feed: go back.
     */
    fun isShortVideos(packageName: String, views: List<SeenView>): Boolean {
        val wanted = markers[packageName] ?: return false
        return views.any { view ->
            wanted.any { marker -> view.id == fullId(packageName, marker) && (!marker.selectedOnly || view.selected) }
        }
    }
}

/**
 * One screen part to look for.
 * @param idName the part after ":id/", e.g. "clips_tab".
 * @param selectedOnly true = only counts while selected (tabs), false = being on screen is enough.
 */
data class Marker(val idName: String, val selectedOnly: Boolean = false)

/** One view Android found: its full ID name and whether it is selected. Nothing else (no text). */
data class SeenView(val id: String, val selected: Boolean)

/**
 * "Press Back at most once every 2 seconds."
 * Android can send several events for one screen; without this, the guard would press Back
 * again and again and throw you out of the whole app instead of just out of Reels.
 * Times come in as a parameter, so tests need no clock (same idea as MindfulGate).
 */
class BackCooldown(private val gapMs: Long = GAP_MS) {

    private var lastBackAt: Long? = null   // null = never pressed yet

    /** @return true = go ahead and press Back now (and remember the time); false = too soon. */
    @Synchronized
    fun tryPress(nowMs: Long): Boolean {
        val last = lastBackAt
        if (last != null && nowMs - last < gapMs) return false
        lastBackAt = nowMs
        return true
    }

    companion object {
        const val GAP_MS = 2_000L
    }
}
