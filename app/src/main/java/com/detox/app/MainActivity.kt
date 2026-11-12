package com.detox.app

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.detox.app.data.RulesRepository
import com.detox.app.data.WallpaperStore
import com.detox.app.domain.QuietWindow
import com.detox.app.domain.RuleEngine
import com.detox.app.ui.apps.AppPickerScreen
import com.detox.app.ui.block.BlockActivity
import com.detox.app.ui.common.CountdownDialog
import com.detox.app.ui.common.RestrictedSettingsDialog
import com.detox.app.ui.common.StrictNeedsHomeDialog
import com.detox.app.ui.home.HomeScreen
import com.detox.app.ui.home.LimitEditor
import com.detox.app.ui.home.QuietWindowEditor
import com.detox.app.ui.launcher.LauncherActivity
import com.detox.app.ui.settings.SettingsScreen
import com.detox.app.ui.theme.DetoxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import android.graphics.Color as AndroidColor

/** Which full screen is showing. Simple "navigation": just a variable. */
private enum class Screen { HOME, APPS, SETTINGS }

/** The editor sheet is open; window = null means "new quiet time". */
private data class EditorRequest(val window: QuietWindow?)

/** A loosening change waiting behind the countdown. */
private data class PendingChange(val what: String, val apply: () -> Unit)

class MainActivity : ComponentActivity() {

    // Compose redraws the screen automatically when these values change.
    // They're refreshed in onResume (e.g. when you come back from a Settings page).
    private var protectionOn by mutableStateOf(false)
    private var protectionLocked by mutableStateOf(false)   // Android's "Restricted setting" lock
    private var usageAccessOn by mutableStateOf(false)
    private var batteryOk by mutableStateOf(false)
    private var isHomeApp by mutableStateOf(false)
    private var shortcutAssigned by mutableStateOf<Boolean?>(null)
    private var oneTapMode by mutableStateOf(false)         // WRITE_SECURE_SETTINGS was granted from a PC
    private var appActive by mutableStateOf(false)          // the countdown only runs while true

    // Help dialogs. They live here (not inside Compose) because plain functions like
    // turnOnProtection() decide when to show them.
    private var showRestrictedHelp by mutableStateOf(false)
    private var showStrictNeedsHome by mutableStateOf(false)

    /** We just switched strict mode on and asked Android to make Detox the home app. Waiting for the answer. */
    private var strictWaitingForHome = false

    /** The strict home's "Home settings" link asked us to open Settings (see [EXTRA_OPEN]). */
    private var openSettingsRequested by mutableStateOf(false)

    // "Become the home app" system dialog; when it closes, check what the user chose.
    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshStatus()
        finishStrictSetup()
    }

    // Android's photo picker. Detox gets ONLY the one photo you tap: no storage permission at all.
    // uri = null when you close the picker without choosing.
    private val pickWallpaper = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            // The picker's permission to read the photo is short-lived, so copy it right now.
            if (WallpaperStore.save(this@MainActivity, uri)) {
                RulesRepository.saveHomePrefs(RulesRepository.homePrefs.value.copy(wallpaperOn = true))
            } else {
                Toast.makeText(this@MainActivity, "Couldn't use that photo. Try another one.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // savedInstanceState != null = Android re-created us (e.g. after rotation); the screen we were on
        // is restored by rememberSaveable, so don't jump to Settings again.
        if (savedInstanceState == null) handleOpenRequest(intent)
        // Dark status-bar icons, because our background is white
        // (even if the phone itself is in dark mode).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            // collectAsState: "subscribe" to the repository (= the database); any change redraws the screen.
            val windows by RulesRepository.windows.collectAsState()
            val apps by RulesRepository.apps.collectAsState()
            val limits by RulesRepository.limits.collectAsState()
            val strict by RulesRepository.strict.collectAsState()
            val homePrefs by RulesRepository.homePrefs.collectAsState()
            val appPrefs by RulesRepository.appPrefs.collectAsState()
            var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
            var editor by remember { mutableStateOf<EditorRequest?>(null) }
            var limitEditorOpen by remember { mutableStateOf(false) }
            var pending by remember { mutableStateOf<PendingChange?>(null) }

            // Opened from the strict home's "Home settings" link -> show Settings.
            // LaunchedEffect runs again whenever the flag changes, also for a later onNewIntent.
            LaunchedEffect(openSettingsRequested) {
                if (openSettingsRequested) {
                    screen = Screen.SETTINGS
                    openSettingsRequested = false
                }
            }

            // Every change that LOOSENS a rule goes through here: first the countdown, then the change.
            // Changes that make rules stricter call their action directly (no waiting).
            fun guard(what: String, apply: () -> Unit) {
                pending = PendingChange(what, apply)
            }

            // Minutes used today by all paused apps together, refreshed every 30 s.
            // Restarts automatically when apps, limits or the Usage-access permission change (the "keys").
            val usedTodayMs by produceState<Long?>(initialValue = null, apps, limits, usageAccessOn) {
                while (true) {
                    value = withContext(Dispatchers.IO) {
                        val dayStart = RuleEngine.dayStartOf(LocalDateTime.now(), limits.dayStart)
                        UsageAccess.usageSince(this@MainActivity, dayStart, apps.map { it.packageName }.toSet())
                            ?.let { RuleEngine.totalUsedMs(apps, it) }
                    }
                    delay(30_000)
                }
            }

            DetoxTheme {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        protectionOn = protectionOn,
                        protectionLocked = protectionLocked,
                        usageAccessOn = usageAccessOn,
                        windows = windows,
                        apps = apps,
                        limits = limits,
                        usedTodayMs = usedTodayMs,
                        strictOn = strict.strictModeOn,
                        isHomeApp = isHomeApp,
                        shortcutAssigned = shortcutAssigned,
                        oneTapMode = oneTapMode,
                        onTurnOnProtection = ::turnOnProtection,
                        onAllowUsageAccess = ::openUsageAccessSettings,
                        onToggleWindow = { id, on ->
                            if (on) {
                                RulesRepository.setWindowEnabled(id, true)
                            } else {
                                val name = windows.firstOrNull { it.id == id }?.name ?: "this quiet time"
                                guard("switch off \"$name\"") { RulesRepository.setWindowEnabled(id, false) }
                            }
                        },
                        onEditWindow = { editor = EditorRequest(it) },
                        onAddWindow = { editor = EditorRequest(null) },
                        onEditApps = { screen = Screen.APPS },
                        onToggleLimit = { on ->
                            if (on) RulesRepository.saveLimits(limits.copy(dailyLimitOn = true))
                            else guard("switch off your daily limit") { RulesRepository.saveLimits(limits.copy(dailyLimitOn = false)) }
                        },
                        onEditLimit = { limitEditorOpen = true },
                        onToggleStrict = { on ->
                            if (on) turnOnStrict()
                            else guard("turn off strict mode") { RulesRepository.setStrictMode(false) }
                        },
                        // Strict mode is already on, but Detox isn't the home app (e.g. it was changed
                        // in Android Settings). Saying "no" here must NOT switch strict mode off:
                        // that would skip the countdown.
                        onFinishStrictSetup = ::askToBeHomeApp,
                        // Strict mode is off, so handing Home back to HiOS loosens nothing: no countdown.
                        onOpenDefaultApps = { open(SystemStatus.homeSettingsIntent()) },
                        onRemoveShortcut = ::removeShortcut,
                        onOpenSettings = { screen = Screen.SETTINGS },
                        onPreviewBlock = { startActivity(BlockActivity.previewIntent(this)) },
                    )

                    Screen.APPS -> AppPickerScreen(
                        paused = apps,
                        mindful = appPrefs.filter { it.mindful }.map { it.packageName }.toSet(),
                        // Mindful pause ON = stricter -> instant. OFF = looser -> countdown first.
                        onSetMindful = { pkg, label, on ->
                            if (on) RulesRepository.setMindful(pkg, true)
                            else guard("turn off mindful pause for $label") { RulesRepository.setMindful(pkg, false) }
                        },
                        onToggle = { pkg, label, paused ->
                            if (paused) RulesRepository.setAppPaused(pkg, label, true)
                            else guard("unpause $label") { RulesRepository.setAppPaused(pkg, label, false) }
                        },
                        onSetLimit = { pkg, minutes ->
                            val app = apps.firstOrNull { it.packageName == pkg }
                            val old = app?.dailyLimitMin
                            // Looser = removing the limit, or raising it. Adding or lowering is instant.
                            val looser = old != null && (minutes == null || minutes > old)
                            if (looser) guard("loosen your ${app?.name ?: "app"} limit") { RulesRepository.setAppLimit(pkg, minutes) }
                            else RulesRepository.setAppLimit(pkg, minutes)
                        },
                        onClose = { screen = Screen.HOME },
                    )

                    Screen.SETTINGS -> SettingsScreen(
                        protectionOn = protectionOn,
                        protectionLocked = protectionLocked,
                        usageAccessOn = usageAccessOn,
                        batteryOk = batteryOk,
                        shortcutAssigned = shortcutAssigned,
                        isHomeApp = isHomeApp,
                        strictOn = strict.strictModeOn,
                        oneTapMode = oneTapMode,
                        countdownSec = strict.countdownSec,
                        homePrefs = homePrefs,
                        appPrefs = appPrefs,
                        maxFavorites = RulesRepository.MAX_FAVORITES,
                        // Home screen looks are not rules: saved right away, no countdown.
                        onSaveHomePrefs = { RulesRepository.saveHomePrefs(it) },
                        onChooseWallpaper = {
                            pickWallpaper.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onRemoveWallpaper = {
                            WallpaperStore.clear(this)
                            RulesRepository.saveHomePrefs(homePrefs.copy(wallpaperOn = false))
                        },
                        // Every switch: ON = right away, OFF = countdown first (it weakens Detox).
                        onSetProtection = { on ->
                            if (on) turnOnProtection()
                            else guard("switch off protection") { turnOffProtection() }
                        },
                        onSetUsageAccess = { on ->
                            if (on) openUsageAccessSettings()
                            else guard("switch off Usage access") { openUsageAccessSettings() }
                        },
                        onSetBattery = { on ->
                            if (on) open(SystemStatus.batteryIntent(this))
                            else guard("let the battery saver put Detox to sleep") { open(SystemStatus.batteryListIntent()) }
                        },
                        onSetShortcut = { wantIt ->
                            if (!wantIt) removeShortcut()
                            else guard("bring back the floating shortcut") { openProtectionPage() }
                        },
                        onSetHomeApp = { on ->
                            when {
                                on -> askToBeHomeApp()
                                // While strict mode is on, giving Home back weakens it -> countdown.
                                strict.strictModeOn ->
                                    guard("give Home back to your normal launcher") { open(SystemStatus.homeSettingsIntent()) }
                                else -> open(SystemStatus.homeSettingsIntent())
                            }
                        },
                        onOpenAppInfo = { open(SystemStatus.appDetailsIntent(this)) },
                        onSetCountdown = { sec ->
                            if (sec >= strict.countdownSec) RulesRepository.setCountdown(sec)
                            else guard("shorten the countdown") { RulesRepository.setCountdown(sec) }
                        },
                        onBack = { screen = Screen.HOME },
                    )
                }

                editor?.let { request ->
                    QuietWindowEditor(
                        initial = request.window,
                        onSave = { window ->
                            val old = request.window
                            editor = null
                            // New window, or the edit blocks at least everything it did before -> instant.
                            if (old == null || RuleEngine.isAtLeastAsStrict(window, old)) RulesRepository.saveWindow(window)
                            else guard("loosen \"${old.name}\"") { RulesRepository.saveWindow(window) }
                        },
                        // Delete only exists for an existing window.
                        onDelete = request.window?.let { existing ->
                            {
                                editor = null
                                if (!existing.enabled) RulesRepository.deleteWindow(existing.id)
                                else guard("delete \"${existing.name}\"") { RulesRepository.deleteWindow(existing.id) }
                            }
                        },
                        onDismiss = { editor = null },
                    )
                }

                if (limitEditorOpen) {
                    LimitEditor(
                        initial = limits,
                        onSave = { newLimits ->
                            limitEditorOpen = false
                            val stricter = !limits.dailyLimitOn ||
                                (newLimits.dailyLimitMin <= limits.dailyLimitMin && newLimits.dayStart == limits.dayStart)
                            if (stricter) RulesRepository.saveLimits(newLimits)
                            else guard("loosen your daily limit") { RulesRepository.saveLimits(newLimits) }
                        },
                        onDismiss = { limitEditorOpen = false },
                    )
                }

                pending?.let { change ->
                    CountdownDialog(
                        what = change.what,
                        seconds = strict.countdownSec,
                        appActive = appActive,
                        onConfirm = {
                            change.apply()
                            pending = null
                        },
                        onCancel = { pending = null },
                    )
                }

                if (showRestrictedHelp) {
                    RestrictedSettingsDialog(
                        onOpenAppInfo = {
                            showRestrictedHelp = false
                            open(SystemStatus.appDetailsIntent(this))
                        },
                        onDismiss = { showRestrictedHelp = false },
                    )
                }

                if (showStrictNeedsHome) {
                    StrictNeedsHomeDialog(
                        onTryAgain = {
                            showStrictNeedsHome = false
                            turnOnStrict()
                        },
                        onDismiss = { showStrictNeedsHome = false },
                    )
                }
            }
        }
    }

    // onResume runs every time you come back to the app (e.g. from Settings),
    // so every status card is always fresh.
    override fun onResume() {
        super.onResume()
        appActive = true
        refreshStatus()
        // Only does something after the "Home app" Settings page (that path returns no result).
        finishStrictSetup()
        // Self-healing: protection was switched off somewhere else (not with the countdown here)?
        // In one-tap mode Detox switches it straight back on.
        lifecycleScope.launch {
            if (ProtectionGuard.heal(this@MainActivity)) refreshStatus()
        }
    }

    // Leaving the app (Home, another app, screen off) -> the countdown starts over.
    override fun onPause() {
        appActive = false
        super.onPause()
    }

    // Detox was already open and the strict home opened it again (SINGLE_TOP / CLEAR_TOP):
    // Android hands us the new Intent here instead of creating a second copy.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenRequest(intent)
    }

    /** "open" = "settings" -> jump to the Settings screen (the Compose LaunchedEffect does the switch). */
    private fun handleOpenRequest(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_SETTINGS) openSettingsRequested = true
    }

    private fun refreshStatus() {
        protectionOn = AccessibilityStatus.isWatcherEnabled(this)
        protectionLocked = !protectionOn && SystemStatus.isRestrictedSettingsBlocked(this)
        usageAccessOn = UsageAccess.isGranted(this)
        batteryOk = SystemStatus.ignoresBatteryOptimizations(this)
        isHomeApp = SystemStatus.isDefaultHome(this)
        shortcutAssigned = SystemStatus.shortcutAssigned(this)
        oneTapMode = SystemStatus.canWriteSecureSettings(this)
    }

    // ---- Protection -------------------------------------------------------------------

    /**
     * One place for "switch protection on" (Home card and Settings switch both use it):
     *  1) one-tap mode -> Detox flips the switch itself, done;
     *  2) Android locked the switch ("Restricted setting") -> explain how to unlock it;
     *  3) otherwise -> open Detox's page in Accessibility settings.
     */
    private fun turnOnProtection() {
        RulesRepository.setProtectionWanted(true)
        when {
            SystemStatus.setProtectionEnabled(this, true) -> refreshStatus()
            SystemStatus.isRestrictedSettingsBlocked(this) -> showRestrictedHelp = true
            else -> openProtectionPage()
        }
    }

    /** Runs AFTER the countdown. Without one-tap mode Android only lets us open the right page. */
    private fun turnOffProtection() {
        // Tell self-healing "this was on purpose", so it doesn't switch protection back on.
        RulesRepository.setProtectionWanted(false)
        if (SystemStatus.setProtectionEnabled(this, false)) refreshStatus() else openProtectionPage()
    }

    /** The floating Accessibility button is a one-tap OFF switch, so removing it needs no countdown. */
    private fun removeShortcut() {
        if (SystemStatus.removeShortcut(this)) refreshStatus() else openProtectionPage()
    }

    /** Detox's own page in Accessibility settings; some phones only open the full list. */
    private fun openProtectionPage() {
        open(SystemStatus.accessibilityDetailsIntent(this), Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    // ---- Strict mode ------------------------------------------------------------------

    /**
     * Strict mode is ONE switch. Order matters: save strict = ON first, because the moment Detox
     * becomes the home app, Android jumps to Home, and that Home must already show the boring screen.
     * (Bug on the phone: it jumped to Home while strict was still off, so Detox passed you on to HiOS.)
     */
    private fun turnOnStrict() {
        RulesRepository.setStrictMode(true)
        lifecycleScope.launch {
            // The save runs on a background thread. Wait until the database really says ON:
            // the home screen reads it the moment it opens and must not see the old "off".
            withTimeoutOrNull(3_000) { RulesRepository.strict.first { it.strictModeOn } }
            if (isHomeApp) {
                openStrictHome()
            } else {
                strictWaitingForHome = true
                askToBeHomeApp()
            }
        }
    }

    /**
     * After the "home app" question: did Detox really become the home app?
     * If not, strict mode never really started, so switching it back off needs no countdown.
     */
    private fun finishStrictSetup() {
        if (!strictWaitingForHome) return
        strictWaitingForHome = false
        refreshStatus()
        if (!isHomeApp) {
            RulesRepository.setStrictMode(false)
            showStrictNeedsHome = true
        }
        // If it worked, Android has usually opened Home (= the boring screen) already. Nothing to do.
    }

    private fun openStrictHome() {
        startActivity(Intent(this, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Android 10+: the system "Set Detox as home app?" dialog. Older / if unavailable: Settings page. */
    private fun askToBeHomeApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                homeRoleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        open(SystemStatus.homeSettingsIntent())
    }

    // ---- Helpers ----------------------------------------------------------------------

    private fun openUsageAccessSettings() {
        // Try to open OUR page in the Usage access list; some phones only support the list itself.
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName"))
        try {
            startActivity(direct)
        } catch (_: ActivityNotFoundException) {
            open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    /**
     * Opens a Settings page. If this phone doesn't have that exact page (or refuses to open it
     * for us), opens [fallback] instead (default: Detox's App info page).
     */
    private fun open(intent: Intent, fallback: Intent = SystemStatus.appDetailsIntent(this)) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(fallback)
        } catch (_: SecurityException) {
            startActivity(fallback)
        }
    }

    companion object {
        /** Intent extra: which Detox screen to open. Used by the strict home's "Home settings" link. */
        const val EXTRA_OPEN = "open"
        const val OPEN_SETTINGS = "settings"
    }
}
