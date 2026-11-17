package com.detox.app

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.detox.app.data.RulesRepository
import com.detox.app.domain.BackCooldown
import com.detox.app.domain.SeenView
import com.detox.app.domain.ShortsRules

/**
 * The optional "Reels & Shorts guard": a SECOND, separate accessibility service.
 *
 * Why separate? The main bouncer (AppWatcherService) stays blind on purpose: it only hears
 * "which app opened". This one must look at screen parts, so it gets its own switch in Android,
 * and its config (res/xml/reels_guard_config.xml) limits it to Instagram + YouTube:
 * Android doesn't even send it events from other apps.
 *
 *   Instagram/YouTube screen changed -> ask Android for the marker IDs (ShortsRules)
 *     -> Reels tab selected / Shorts player on screen -> press Back once + a short message
 *
 * It only asks "is a view with this ID here, and is it selected?". It never reads texts.
 */
class ReelsGuardService : AccessibilityService() {

    private val cooldown = BackCooldown()

    override fun onServiceConnected() {
        Log.i(TAG, "Reels guard connected")
        // Running = you want it (switched on here or in Android). Only the countdown in
        // Detox sets this to false, so self-healing knows "off on purpose" (see ProtectionGuard).
        RulesRepository.setReelsGuardWanted(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (!ShortsRules.watches(pkg)) return
        // The window in front. null = Android is busy switching screens; the next event will come.
        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != pkg) return   // something else is in front by now
            if (!ShortsRules.isShortVideos(pkg, findMarkers(root, pkg))) return
            if (!cooldown.tryPress(SystemClock.elapsedRealtime())) return

            Log.i(TAG, "Short videos in $pkg: going back")
            performGlobalAction(GLOBAL_ACTION_BACK)
            Toast.makeText(this, "Reels and Shorts are paused 🍃", Toast.LENGTH_SHORT).show()
        } finally {
            recycle(root)
        }
    }

    /**
     * For each marker of this app: "Android, find views with this ID". The search runs inside
     * the app's own process and returns only matches, so it's cheap even on big screens.
     * We keep just two facts per view: the ID and "selected". No text is touched.
     */
    private fun findMarkers(root: AccessibilityNodeInfo, pkg: String): List<SeenView> =
        ShortsRules.markers[pkg].orEmpty().flatMap { marker ->
            root.findAccessibilityNodeInfosByViewId(ShortsRules.fullId(pkg, marker)).map { node ->
                val seen = SeenView(node.viewIdResourceName.orEmpty(), node.isSelected)
                recycle(node)
                seen
            }
        }

    /** Before Android 13, node objects come from a pool and must be handed back. Newer: no-op. */
    private fun recycle(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    // Required by Android. Nothing to stop: we only react to events.
    override fun onInterrupt() {}

    companion object {
        private const val TAG = "Detox" // same Logcat tag as the bouncer: filter with tag:Detox
    }
}
