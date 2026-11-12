package com.detox.app.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

/** An app that has an icon in the app drawer. */
data class InstalledApp(val label: String, val packageName: String, val icon: Bitmap)

/** Same, but only the name: the strict home screen shows no icons (and loads much faster). */
data class LauncherEntry(val label: String, val packageName: String)

object InstalledApps {

    /**
     * All apps with a launcher icon, except Detox itself, sorted A–Z.
     * Loading ~100 icons takes a moment -> call this OFF the main thread.
     * (Possible thanks to the <queries> block in AndroidManifest.xml.)
     */
    fun load(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION") // the new overload needs Android 13+, we support 8+
        val activities = pm.queryIntentActivities(launcherIntent, 0)
        return activities
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                InstalledApp(
                    label = info.loadLabel(pm).toString(),
                    packageName = info.packageName,
                    icon = info.loadIcon(pm).toBitmap(ICON_PX, ICON_PX),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Names only, A–Z, without Detox itself. Used by the strict home screen. */
    fun loadLabels(context: Context): List<LauncherEntry> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = pm.queryIntentActivities(launcherIntent, 0)
        return activities
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { LauncherEntry(it.loadLabel(pm).toString(), it.packageName) }
            .sortedBy { it.label.lowercase() }
    }

    private const val ICON_PX = 108
}
