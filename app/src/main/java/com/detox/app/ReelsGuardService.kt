package com.detox.app

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.detox.app.data.RulesRepository
import com.detox.app.data.StatsRepository
import com.detox.app.domain.BackCooldown
import com.detox.app.domain.TryReason
import com.detox.app.domain.SeenView
import com.detox.app.domain.ShortsRules
import com.detox.app.ui.reels.ReelsNoticeActivity

/**
 * The optional "Reels & Shorts guard": a SECOND, separate accessibility service.
 *
 * Why separate? The main bouncer (AppWatcherService) stays blind on purpose: it only hears
 * "which app opened". This one must look at screen parts, so it gets its own switch in Android,
 * and its config (res/xml/reels_guard_config.xml) limits it to Instagram + YouTube:
 * Android doesn't even send it events from other apps.
 *
 *   Instagram/YouTube screen changed -> ask Android for the marker IDs (ShortsRules)
 *     -> Reels tab selected / Shorts player on screen -> press Back once
 *     -> 0.4 s later: the "Reels are paused" notice (OK = back to the app, or close it)
 *
 * It only asks "is a view with this ID here, and is it selected?". It never reads texts.
 */
class ReelsGuardService : AccessibilityService() {

    private val cooldown = BackCooldown()

    // A to-do list for the main thread: "show the notice in 400 ms".
    private val handler = Handler(Looper.getMainLooper())

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
            // 1) Leave Reels. 2) A moment later (Back needs time to finish), show the notice.
            //    The other order would loop: "OK" on a notice above Reels -> Reels -> guard -> notice...
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({ showNotice(pkg) }, NOTICE_DELAY_MS)
            StatsRepository.recordTry(pkg, TryReason.REELS)
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

    private fun showNotice(pkg: String) {
        startActivity(ReelsNoticeActivity.noticeIntent(this, pkg, labelOf(pkg), ShortsRules.feedName(pkg)))
    }

    /** "Instagram" instead of "com.instagram.android". */
    private fun labelOf(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        "the app"
    }

    // Required by Android. Nothing to stop: we only react to events.
    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)   // guard switched off: no notice after that
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Detox" // same Logcat tag as the bouncer: filter with tag:Detox
        private const val NOTICE_DELAY_MS = 400L
    }
}
