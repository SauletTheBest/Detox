package com.detox.app

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.detox.app.data.RulesRepository
import com.detox.app.domain.Decision
import com.detox.app.domain.Mindful
import com.detox.app.domain.RuleEngine
import com.detox.app.ui.block.BlockActivity
import com.detox.app.ui.pause.PauseActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

/**
 * The "bouncer". Android calls onAccessibilityEvent every time a new window opens.
 *
 *  app opened -> RuleEngine.decide() -> Block?  -> show BlockActivity on top
 *                                    -> Allow but it's a paused app -> set ONE timer for
 *                                       whichever comes first: the next quiet time, or
 *                                       the moment today's limit runs out
 *
 * In strict mode every paused app is blocked all day, so no timer is needed for them.
 */
class AppWatcherService : AccessibilityService() {

    // Handler = a to-do list for the main thread; postDelayed = "run this in N ms".
    private val handler = Handler(Looper.getMainLooper())

    /** Last real app we saw in front (keyboard / notification shade ignored). */
    private var currentPackage: String? = null

    private val recheck = Runnable { onTimer() }

    // Our own little "goroutine group" for watching strict mode. Cancelled in onDestroy,
    // so nothing keeps running after Android stops the service.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onServiceConnected() {
        Log.i(TAG, "Service connected")
        instance = this
        // The bouncer is running, so clearly you want protection on. (Only switching it off
        // INSIDE Detox sets this to false; see ProtectionGuard.)
        RulesRepository.setProtectionWanted(true)

        // Strict mode switched on while Instagram is open? Don't wait for the next app switch:
        // look at the app in front right away.
        scope.launch {
            RulesRepository.strict
                .map { it.strictModeOn }
                .distinctUntilChanged()
                .drop(1)                     // skip the value we already have; react to CHANGES only
                .collect { on ->
                    if (on) {
                        Log.i(TAG, "Strict mode switched on: re-checking the app in front")
                        onTimer()
                    }
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == currentPackage) return
        // Keyboard or notification shade opened ON TOP of the app: the app is still in front.
        if (isOverlay(pkg)) return

        currentPackage = pkg
        Log.i(TAG, "Foreground app: $pkg")
        check(pkg)
    }

    private fun check(pkg: String) {
        handler.removeCallbacks(recheck)   // a new app is in front -> old timer is useless
        if (pkg == packageName) return     // our own screens (Home, block screen, pause screen)

        val apps = RulesRepository.apps.value
        val app = apps.firstOrNull { it.packageName == pkg }
        if (app == null) {
            // Not paused: it can never be blocked. Only the gentle "mindful pause" may apply.
            pauseIfMindful(pkg)
            return
        }
        val windows = RulesRepository.windows.value
        val limits = RulesRepository.limits.value
        val strictOn = RulesRepository.strict.value.strictModeOn
        val now = LocalDateTime.now()

        // Only ask Android's usage log when a limit actually applies (it costs a little time).
        // In strict mode the app is blocked anyway, so limits don't matter.
        val limitsInUse = !strictOn && (limits.dailyLimitOn || app.dailyLimitMin != null)
        val dayStart = RuleEngine.dayStartOf(now, limits.dayStart)
        val used = if (limitsInUse) {
            UsageAccess.usageSince(this, dayStart, apps.map { it.packageName }.toSet()).orEmpty()
        } else {
            emptyMap()
        }

        when (val decision = RuleEngine.decide(pkg, now, apps, windows, limits, used, strictOn)) {
            is Decision.Block -> block(decision)
            Decision.Allow -> {
                // A paused app that is allowed right now can ALSO be mindful. After you tap
                // "Open" on the pause screen, the app opens again, check() runs again, and
                // this time we get past here and set the timer below.
                if (pauseIfMindful(pkg)) return
                val quietAt = RuleEngine.nextBlockStart(pkg, now, apps, windows)
                val limitAt = RuleEngine.timeLeftMs(pkg, apps, limits, used)?.let { now.plusNanos(it * 1_000_000) }
                val wakeAt = listOfNotNull(quietAt, limitAt).minOrNull() ?: return
                // +1 s so we land safely INSIDE the quiet time / past the limit.
                handler.postDelayed(recheck, Duration.between(now, wakeAt).toMillis() + 1_000)
                Log.i(TAG, "Allowed for now: $pkg (used ${used[pkg]?.div(60_000) ?: 0} min today). Re-check at $wakeAt")
            }
        }
    }

    /** The timer rang (or strict mode came on). Are you still inside a paused app? */
    private fun onTimer() {
        // HiOS doesn't report "went to home screen", so ask Android's usage log what is
        // REALLY in front. Without Usage access, fall back to the last app we saw.
        val front = UsageAccess.foregroundPackage(this) ?: currentPackage ?: return
        Log.i(TAG, "Re-check. App in front: $front")
        currentPackage = front
        check(front)
    }

    private fun block(decision: Decision.Block) {
        Log.i(TAG, "BLOCK ${decision.app.packageName}: ${decision.reason} until ${decision.until ?: "strict mode is off"}")
        // Forget the current app: if it pushes a second screen on top of ours a moment
        // later (splash -> main screen), that event is NOT skipped as "same app" and we
        // block again. Re-opening the block screen is cheap (singleTask reuses it).
        currentPackage = null
        startActivity(BlockActivity.blockIntent(this, decision))
    }

    /**
     * Mindful pause: if you marked this app "mindful" and haven't just said "yes, open it",
     * show the 5-second "Take a breath" screen first.
     * @return true = the pause screen was shown.
     */
    private fun pauseIfMindful(pkg: String): Boolean {
        val mindful = RulesRepository.appPrefs.value.any { it.packageName == pkg && it.mindful }
        if (!mindful) return false
        if (!Mindful.gate.needsPause(pkg, System.currentTimeMillis())) return false   // still in the 10-min grace

        Log.i(TAG, "Mindful pause for $pkg")
        // Same trick as block(): forget the app, so its next screen (splash -> main) is checked again.
        currentPackage = null
        startActivity(PauseActivity.pauseIntent(this, pkg, labelOf(pkg)))
        return true
    }

    /** "YouTube" instead of "com.google.android.youtube". Falls back to the package name. */
    private fun labelOf(pkg: String): String = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    private fun isOverlay(pkg: String): Boolean =
        pkg == SYSTEM_UI || pkg in keyboardPackages()

    private fun keyboardPackages(): Set<String> =
        getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList
            ?.map { it.packageName }
            ?.toSet()
            ?: emptySet()

    // Required by Android (called when the system interrupts feedback). Nothing to stop here.
    override fun onInterrupt() {}

    // Android disconnects us (protection switched off): forget the instance right away.
    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)   // cancel pending timers
        scope.cancel()                             // stop watching strict mode
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Detox" // filter Logcat with: tag:Detox (Log.i: HiOS hides Log.d by default)
        private const val SYSTEM_UI = "com.android.systemui"

        /**
         * The running service, so screens can ask it for small favours (open Recents).
         * @Volatile: written by the service, read by screens — always see the latest value.
         * Cleared in onUnbind/onDestroy, so we never hold on to a dead service.
         */
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: AppWatcherService? = null

        /** Is the bouncer connected right now? */
        fun isRunning(): Boolean = instance != null

        /**
         * Open Android's recent-apps screen, like the Recents button/gesture.
         * Useful on the strict home screen: with a third-party home app, some phones'
         * recent-apps gesture is unreliable. false = service not running.
         */
        fun openRecents(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) ?: false

        /** Pull down the notification shade (strict home: swipe down). false = service not running. */
        fun openNotifications(): Boolean =
            instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) ?: false

        /**
         * Turn the screen off and lock the phone, like the power button (strict home: double-tap).
         * Android 9+ only; older phones and "service not running" return false.
         */
        fun lockScreen(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            return instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) ?: false
        }
    }
}
