package com.detox.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log

/**
 * Small checks about the phone, shown in Settings -> "Keep Detox running",
 * the Intents that open the matching Settings pages, and (only with the one-time
 * WRITE_SECURE_SETTINGS grant from a PC) a few switches Detox can flip by itself.
 */
object SystemStatus {

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** Our bouncer as Android writes it in settings: "com.detox.app/com.detox.app.AppWatcherService". */
    private fun myService(context: Context): String =
        ComponentName(context, AppWatcherService::class.java).flattenToString()

    /** Is Detox the phone's default home screen (needed for strict mode)? */
    fun isDefaultHome(context: Context): Boolean {
        val resolved = context.packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** The phone's own home screen (e.g. HiOS launcher), so strict-mode-OFF can forward to it. */
    fun otherHome(context: Context): ComponentName? {
        @Suppress("DEPRECATION") // the new overload needs Android 13+, we support 8+
        val homes = context.packageManager.queryIntentActivities(homeIntent(), 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
        // Prefer the launcher that came with the phone (system app); then anything else.
        val best = homes.firstOrNull { (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
            ?: homes.firstOrNull()
        return best?.let { ComponentName(it.packageName, it.name) }
    }

    /**
     * Is the floating "Detox shortcut" assigned? That shortcut can switch protection off with
     * one tap. null = Android didn't let us read the setting (then we just show advice).
     */
    fun shortcutAssigned(context: Context): Boolean? = try {
        val me = myService(context)
        listOf(KEY_BUTTON_TARGETS, KEY_SHORTCUT_TARGET).any { key ->
            ComponentList.contains(Settings.Secure.getString(context.contentResolver, key), me)
        }
    } catch (_: SecurityException) {
        null
    }

    /** True = Android's battery optimization will not put Detox to sleep. */
    fun ignoresBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    // ---- "One-tap mode": things Detox can do itself after the one-time PC grant ----------

    /**
     * Did the user run, once, from a PC:
     *   adb shell pm grant com.detox.app android.permission.WRITE_SECURE_SETTINGS
     * Normal apps can never get this permission by themselves. With it, Detox may change
     * the same settings the Accessibility page changes (only for itself, see below).
     */
    fun canWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * Switch the bouncer on/off ourselves, like flipping "Use Detox" in Accessibility.
     * Android keeps ALL enabled services in one string ("a/.A:b/.B"), so we only add or
     * remove OUR entry and leave the others (e.g. Ella Translate) exactly as they were.
     *
     * @return true = done by Detox; false = not allowed (no grant), so the caller opens Settings.
     */
    fun setProtectionEnabled(context: Context, on: Boolean): Boolean {
        if (!canWriteSecureSettings(context)) return false
        return try {
            val resolver = context.contentResolver
            val key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            val current = Settings.Secure.getString(resolver, key)
            val updated = if (on) ComponentList.with(current, myService(context))
            else ComponentList.without(current, myService(context))
            Settings.Secure.putString(resolver, key, updated)
            // The master switch for "accessibility is in use"; without it the list is ignored.
            if (on) Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.i(TAG, "Protection set to $on by Detox itself")
            true
        } catch (e: SecurityException) {
            Log.i(TAG, "Could not change protection: $e")
            false
        }
    }

    /**
     * Remove the floating "Detox shortcut" (the one-tap OFF switch) ourselves.
     * Same idea: drop only our entry from the two shortcut lists.
     *
     * @return true = removed (or wasn't there); false = not allowed, open Settings instead.
     */
    fun removeShortcut(context: Context): Boolean {
        if (!canWriteSecureSettings(context)) return false
        return try {
            val resolver = context.contentResolver
            val me = myService(context)
            for (key in listOf(KEY_BUTTON_TARGETS, KEY_SHORTCUT_TARGET)) {
                val current = Settings.Secure.getString(resolver, key)
                if (ComponentList.contains(current, me)) {
                    Settings.Secure.putString(resolver, key, ComponentList.without(current, me))
                }
            }
            Log.i(TAG, "Floating shortcut removed by Detox itself")
            true
        } catch (e: SecurityException) {
            Log.i(TAG, "Could not remove the shortcut: $e")
            false
        }
    }

    /**
     * Android 13+ "Restricted setting": for apps installed from a file, the Accessibility
     * switch is greyed out until you allow it in App info -> ⋮ -> "Allow restricted settings".
     * Android keeps that as a hidden app-op; MODE_ERRORED = still blocked.
     * The op's name isn't in the public SDK, so we use the text; any error -> "not blocked".
     */
    @SuppressLint("WrongConstant")
    fun isRestrictedSettingsBlocked(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            appOps.unsafeCheckOpNoThrow(OP_RESTRICTED_SETTINGS, Process.myUid(), context.packageName) ==
                AppOpsManager.MODE_ERRORED
        } catch (_: Exception) {
            false
        }
    }

    // ---- Intents to the right Settings pages -----------------------------------------

    /**
     * Detox's own page in Accessibility settings (where the "Detox shortcut" switch is).
     * This action isn't in the public SDK, so we use its name as text. Some phones may refuse it;
     * MainActivity then falls back to the normal Accessibility list.
     */
    fun accessibilityDetailsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, myService(context))
        } else {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }

    /** System dialog "Let Detox always run in background?" */
    fun batteryIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    /** The full "Battery optimization" list (to put Detox back under battery saving). */
    fun batteryListIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** "Default home app" page: pick which app opens when you press Home. */
    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

    /** Detox's App info page (on HiOS the auto-start / battery options are reachable from here). */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    private const val TAG = "Detox"
    private const val KEY_BUTTON_TARGETS = "accessibility_button_targets"
    private const val KEY_SHORTCUT_TARGET = "accessibility_shortcut_target_service"
    private const val OP_RESTRICTED_SETTINGS = "android:access_restricted_settings"
}

/**
 * Android stores some settings as ONE string of components joined by ':', e.g.
 *   "com.transsion.mol/.service.as.ASService:com.detox.app/.AppWatcherService"
 * These helpers add or remove one component and keep every other entry as it was.
 * Pure Kotlin (no Android classes), so it is unit-tested on the PC (ComponentListTest).
 */
internal object ComponentList {

    /** "pkg/.Cls" (short) and "pkg/pkg.Cls" (full) mean the same thing: turn both into the full form. */
    fun normalize(entry: String): String {
        val slash = entry.indexOf('/')
        if (slash <= 0) return entry.trim()
        val pkg = entry.substring(0, slash).trim()
        val cls = entry.substring(slash + 1).trim()
        return if (cls.startsWith(".")) "$pkg/$pkg$cls" else "$pkg/$cls"
    }

    fun contains(list: String?, component: String): Boolean =
        split(list).any { normalize(it) == normalize(component) }

    /** Add [component] at the end (if it's not there yet). */
    fun with(list: String?, component: String): String =
        if (contains(list, component)) split(list).joinToString(":")
        else (split(list) + component).joinToString(":")

    /** Remove [component] (both spellings); everything else stays. */
    fun without(list: String?, component: String): String =
        split(list).filter { normalize(it) != normalize(component) }.joinToString(":")

    private fun split(list: String?): List<String> =
        list.orEmpty().split(':').filter { it.isNotBlank() }
}
