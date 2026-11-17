package com.detox.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Reels guard's decision: only ID names + "selected", and only for the apps it watches. */
class ShortsRulesTest {

    private val yt = ShortsRules.YOUTUBE
    private val ig = ShortsRules.INSTAGRAM

    @Test fun `youtube shorts player is short videos`() {
        val views = listOf(SeenView("$yt:id/reel_progress_bar", selected = false))
        assertTrue(ShortsRules.isShortVideos(yt, views))
    }

    @Test fun `youtube with nothing found is fine`() {
        assertFalse(ShortsRules.isShortVideos(yt, emptyList()))
    }

    @Test fun `instagram reels tab counts only while selected`() {
        assertTrue(ShortsRules.isShortVideos(ig, listOf(SeenView("$ig:id/clips_tab", selected = true))))
        // On the home feed the Reels tab is visible too, but not selected: DMs and feed stay usable.
        assertFalse(ShortsRules.isShortVideos(ig, listOf(SeenView("$ig:id/clips_tab", selected = false))))
    }

    @Test fun `an id from another app does not count`() {
        val views = listOf(SeenView("com.example.video:id/reel_progress_bar", selected = false))
        assertFalse(ShortsRules.isShortVideos(yt, views))
    }

    @Test fun `apps the guard does not watch are never short videos`() {
        val views = listOf(SeenView("$yt:id/reel_progress_bar", selected = true))
        assertFalse(ShortsRules.isShortVideos("org.telegram.messenger", views))
        assertFalse(ShortsRules.watches("org.telegram.messenger"))
        assertTrue(ShortsRules.watches(yt))
        assertTrue(ShortsRules.watches(ig))
    }

    @Test fun `full id is what android expects`() {
        assertTrue(ShortsRules.fullId(ig, Marker("clips_tab")) == "com.instagram.android:id/clips_tab")
    }

    @Test fun `the notice uses each app's own word`() {
        assertTrue(ShortsRules.feedName(yt) == "Shorts")
        assertTrue(ShortsRules.feedName(ig) == "Reels")
    }

    @Test fun `back is pressed at most once per 2 seconds`() {
        val cooldown = BackCooldown()
        assertTrue(cooldown.tryPress(10_000))
        assertFalse(cooldown.tryPress(10_500))
        assertFalse(cooldown.tryPress(11_999))
        assertTrue(cooldown.tryPress(12_000))
    }
}
