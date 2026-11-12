package com.detox.app

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.detox.app.domain.UsageCalculator
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * "Usage access" = Android's own log of which app was opened when.
 * Used for: (1) double-checking which app is really in front when a timer rings
 * (HiOS doesn't report going to the home screen), (2) counting time for daily limits.
 * Only package names + times are read, never content.
 */
object UsageAccess {

    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** The app in front right now, or null (no Usage access / nothing in the last 6 hours). */
    fun foregroundPackage(context: Context): String? {
        if (!isGranted(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - SIX_HOURS_MS, end)
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == RESUMED) last = event.packageName
        }
        return last
    }

    /**
     * Milliseconds each of [packages] was on screen since [from] (usually "today 05:00").
     * null = no Usage access, so we can't know.
     */
    fun usageSince(context: Context, from: LocalDateTime, packages: Set<String>): Map<String, Long>? {
        if (!isGranted(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val fromMs = from.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val nowMs = System.currentTimeMillis()

        val list = mutableListOf<UsageCalculator.Event>()
        val events = usm.queryEvents(fromMs, nowMs)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val type = when (e.eventType) {
                RESUMED -> UsageCalculator.Type.RESUMED
                PAUSED -> UsageCalculator.Type.PAUSED
                SCREEN_OFF, SHUTDOWN, STARTUP -> UsageCalculator.Type.STOP_ALL
                else -> continue
            }
            if (type != UsageCalculator.Type.STOP_ALL && e.packageName !in packages) continue
            list += UsageCalculator.Event(e.packageName ?: "", e.className ?: "", type, e.timeStamp)
        }
        return UsageCalculator.foregroundMs(list, fromMs, nowMs)
    }

    /**
     * Apps you opened most recently (newest first, each app once), for the "Recent" list on
     * the strict home screen. Looks back 3 days. Empty list without Usage access.
     *
     * The result may contain apps without a launcher entry (system screens etc.):
     * the caller keeps only apps it can actually show, so ask for a few more than you need.
     */
    fun recentPackages(context: Context, limit: Int, exclude: Set<String>): List<String> {
        if (limit <= 0 || !isGranted(context)) return emptyList()
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - THREE_DAYS_MS, end)
        val event = UsageEvents.Event()

        // Events arrive oldest -> newest, so for every app we simply keep overwriting
        // "last time it was opened". Like a Go map[string]int64 of the latest timestamp.
        val lastOpened = HashMap<String, Long>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != RESUMED) continue
            val pkg = event.packageName ?: continue
            if (pkg in exclude) continue
            lastOpened[pkg] = event.timeStamp
        }
        return lastOpened.entries
            .sortedByDescending { it.value }   // newest first
            .take(limit)
            .map { it.key }
    }

    /**
     * How long the phone was used today (since [from], usually "today 05:00"), all apps together.
     * Home screens (HiOS, Detox Home), Detox itself and the system UI don't count: looking at
     * the clock on your home screen isn't "screen time" in the doom-scrolling sense.
     * null = no Usage access, so we can't know.
     */
    fun screenTimeToday(context: Context, from: LocalDateTime): Long? {
        if (!isGranted(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val fromMs = from.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val nowMs = System.currentTimeMillis()
        val skip = homeScreenPackages(context) + context.packageName + "com.android.systemui"

        val list = mutableListOf<UsageCalculator.Event>()
        val events = usm.queryEvents(fromMs, nowMs)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val type = when (e.eventType) {
                RESUMED -> UsageCalculator.Type.RESUMED
                PAUSED -> UsageCalculator.Type.PAUSED
                SCREEN_OFF, SHUTDOWN, STARTUP -> UsageCalculator.Type.STOP_ALL
                else -> continue
            }
            if (type != UsageCalculator.Type.STOP_ALL && e.packageName in skip) continue
            list += UsageCalculator.Event(e.packageName ?: "", e.className ?: "", type, e.timeStamp)
        }
        // Same calculator as the daily limits; then add all apps together.
        return UsageCalculator.foregroundMs(list, fromMs, nowMs).values.sum()
    }

    /** Every installed home screen app (HiOS launcher, Detox...). */
    private fun homeScreenPackages(context: Context): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION") // the new overload needs Android 13+, we support 8+
        return context.packageManager.queryIntentActivities(home, 0)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    // Android's event numbers (same values on all versions; newer Android only renamed them).
    private const val RESUMED = 1      // MOVE_TO_FOREGROUND / ACTIVITY_RESUMED
    private const val PAUSED = 2       // MOVE_TO_BACKGROUND / ACTIVITY_PAUSED
    private const val SCREEN_OFF = 16  // SCREEN_NON_INTERACTIVE
    private const val SHUTDOWN = 26    // DEVICE_SHUTDOWN
    private const val STARTUP = 27     // DEVICE_STARTUP
    private const val SIX_HOURS_MS = 6 * 60 * 60 * 1000L
    private const val THREE_DAYS_MS = 3 * 24 * 60 * 60 * 1000L
}
