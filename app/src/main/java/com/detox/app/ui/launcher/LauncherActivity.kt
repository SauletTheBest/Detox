package com.detox.app.ui.launcher

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.detox.app.AppWatcherService
import com.detox.app.MainActivity
import com.detox.app.ProtectionGuard
import com.detox.app.SystemStatus
import com.detox.app.UsageAccess
import com.detox.app.data.InstalledApps
import com.detox.app.data.LauncherEntry
import com.detox.app.data.RulesRepository
import com.detox.app.data.WallpaperStore
import com.detox.app.domain.AppPref
import com.detox.app.domain.RuleEngine
import com.detox.app.ui.theme.DetoxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import android.graphics.Color as AndroidColor

/** Raw data read from the phone. Paused / hidden apps are filtered later (they can change while we're open). */
private data class Loaded(
    val all: List<LauncherEntry>,   // every launchable app, A–Z, Detox itself already left out
    val recent: List<String>,       // package names, newest first
    val screenTimeMs: Long?,        // today's phone use; null = no Usage access
)

/**
 * Detox's home screen ("launcher"). Android opens it every time you press Home
 * (once Detox is set as the default home app).
 *
 *   strict mode ON  -> "Detox Home": a calm 2-page home screen (see StrictHomeScreen)
 *   strict mode OFF -> instantly forward to the phone's normal home screen (HiOS)
 *
 * The window is transparent (Theme.Detox.Launcher), so the forwarding is almost invisible.
 */
class LauncherActivity : ComponentActivity() {

    /** null = not known yet (reading the database), true/false = strict mode. */
    private var strictOn by mutableStateOf<Boolean?>(null)

    /** Bumped on every resume, so lists, recents, screen time and the wallpaper reload. */
    private var reloadCount by mutableIntStateOf(0)

    /** Bumped when Home is pressed -> back to page 1, lists to the top, search cleared. */
    private var homePressCount by mutableIntStateOf(0)

    /** Were we already on screen when Home was pressed? Then slide smoothly; otherwise jump. */
    private var animateHome = false

    /** Can our service open Recents / notifications / lock the screen right now? */
    private var serviceRunning by mutableStateOf(false)

    /** Last list of apps, for the "this phone has no standard clock app" fallback. */
    @Volatile private var lastApps: List<LauncherEntry> = emptyList()

    /** Decoding a photo is slow: keep the last one, decode again only when the file changed. */
    @Volatile private var wallpaperCache: Pair<Long, ImageBitmap>? = null

    /** Which status-bar icon colour is set now (null = not set yet). */
    private var darkBars: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setBars(dark = false)
        // On a home screen, Back has nowhere to go. (On page 2, Back goes to page 1: see BackHandler.)
        onBackPressedDispatcher.addCallback(this) { }

        setContent {
            // Draw ONLY in strict mode. Otherwise the window stays transparent while we forward.
            if (strictOn == true) DetoxHome()
        }
    }

    @Composable
    private fun DetoxHome() {
        // "Subscribe" to the database: any change (pausing an app, a new favorite...) redraws.
        val paused by RulesRepository.apps.collectAsState()
        val windows by RulesRepository.windows.collectAsState()
        val limits by RulesRepository.limits.collectAsState()
        val prefs by RulesRepository.homePrefs.collectAsState()
        val appPrefs by RulesRepository.appPrefs.collectAsState()

        // Reading ~100 app names + the usage log takes a moment -> background thread.
        // produceState keeps the old values on screen while new ones load (no flicker).
        val loaded by produceState<Loaded?>(initialValue = null, reloadCount, limits.dayStart) {
            value = withContext(Dispatchers.Default) { load(limits.dayStart) }
        }
        val wallpaper by produceState<ImageBitmap?>(initialValue = null, reloadCount, prefs.wallpaperOn) {
            value = if (prefs.wallpaperOn) withContext(Dispatchers.IO) { loadWallpaper() } else null
        }
        // The clock: wake up right when the minute changes (not every second: saves battery).
        val now by produceState(initialValue = LocalDateTime.now(), reloadCount) {
            while (true) {
                value = LocalDateTime.now()
                delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
            }
        }

        val quiet = windows.firstOrNull { it.isActive(now) }
        val look = homeLook(prefs, quietNow = quiet != null, hasWallpaper = wallpaper != null)
        // Dark look -> white status-bar icons (clock, battery); light look -> dark icons.
        LaunchedEffect(look.dark) { setBars(look.dark) }

        val pausedSet = remember(paused) { paused.map { it.packageName }.toSet() }
        val lists = remember(loaded, pausedSet, appPrefs) { loaded?.let { buildLists(it, pausedSet, appPrefs) } }
        val pausedNames = remember(loaded, pausedSet) {
            loaded?.all.orEmpty().filter { it.packageName in pausedSet }.map { it.label }
        }
        val status = if (quiet != null) {
            "${quiet.name} quiet time until ${RuleEngine.blockedUntil(now, windows).format(HHMM)}"
        } else {
            strictStatusLine(pausedNames)
        }

        val pagerState = rememberPagerState(pageCount = { 2 })
        val searchListState = rememberLazyListState()
        var query by rememberSaveable { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        val focus = LocalFocusManager.current

        // Home pressed -> page 1, search cleared, list back to the top.
        LaunchedEffect(homePressCount) {
            if (homePressCount == 0) return@LaunchedEffect
            query = ""
            focus.clearFocus()
            if (animateHome) pagerState.animateScrollToPage(0) else pagerState.scrollToPage(0)
            searchListState.scrollToItem(0)
        }
        // Leaving page 2 -> put the keyboard away.
        LaunchedEffect(pagerState.currentPage) {
            if (pagerState.currentPage == 0) focus.clearFocus()
        }
        // Back on page 2 -> page 1. (On page 1 Back does nothing: this IS the home screen.)
        BackHandler(enabled = pagerState.currentPage != 0) {
            scope.launch { pagerState.animateScrollToPage(0) }
        }

        DetoxTheme {
            StrictHomeScreen(
                look = look,
                wallpaper = wallpaper,
                pagerState = pagerState,
                now = now,
                status = status,
                screenTimeMs = loaded?.screenTimeMs,
                prefs = prefs,
                lists = lists,
                showRecents = serviceRunning,
                query = query,
                onQueryChange = { query = it },
                searchListState = searchListState,
                actions = remember { homeActions() },
            )
        }
    }

    // Runs on first start AND every time Home is pressed while we're the home screen.
    override fun onResume() {
        super.onResume()
        reloadCount++
        serviceRunning = AppWatcherService.isRunning()
        // Self-healing: if protection was switched off behind Detox's back, switch it on again
        // (only works after the one-time PC command; otherwise this does nothing).
        // Home is pressed many times a day, so this check runs often.
        lifecycleScope.launch { ProtectionGuard.heal(this@LauncherActivity) }
        lifecycleScope.launch {
            val on = RulesRepository.readStrictModeOn()
            strictOn = on
            if (!on) forwardToNormalHome()
        }
    }

    // LauncherActivity is "singleTask": pressing Home again doesn't create a second copy,
    // Android hands the existing one a new Intent instead.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Already on screen (STARTED = visible) -> slide back smoothly; coming back from an app -> jump.
        animateHome = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        homePressCount++
    }

    // ---- Data ----------------------------------------------------------------------------

    /** Everything the strict home needs from the phone. Runs on a background thread. */
    private fun load(dayStart: LocalTime): Loaded {
        val all = InstalledApps.loadLabels(this)
        lastApps = all
        return Loaded(
            all = all,
            // Ask for more than we show: some recent "apps" are system screens without a launcher entry.
            recent = UsageAccess.recentPackages(this, limit = 20, exclude = setOf(packageName)),
            screenTimeMs = UsageAccess.screenTimeToday(this, RuleEngine.dayStartOf(LocalDateTime.now(), dayStart)),
        )
    }

    /**
     * Turns raw data into what the two pages show.
     *  - paused apps (strict mode) never appear anywhere
     *  - hidden apps only appear in the "Hidden apps" section
     *  - page 1 = favorites in your order, then recent apps fill the free places (max 5)
     */
    private fun buildLists(loaded: Loaded, paused: Set<String>, appPrefs: List<AppPref>): HomeLists {
        val prefsByPackage = appPrefs.associateBy { it.packageName }   // like a Go map[pkg]pref
        val notPaused = loaded.all.filter { it.packageName !in paused }
        val visible = notPaused.filter { prefsByPackage[it.packageName]?.hidden != true }
        val hidden = notPaused.filter { prefsByPackage[it.packageName]?.hidden == true }
        val byPackage = visible.associateBy { it.packageName }

        val favorites = appPrefs
            .filter { it.favoritePos != null }
            .sortedBy { it.favoritePos }
            .mapNotNull { byPackage[it.packageName] }        // uninstalled / paused / hidden: skip it
            .take(RulesRepository.MAX_FAVORITES)
        val favoriteSet = favorites.map { it.packageName }.toSet()
        val recent = loaded.recent
            .mapNotNull { byPackage[it] }                    // only apps we can actually open
            .filter { it.packageName !in favoriteSet }       // no duplicates
            .take(RulesRepository.MAX_FAVORITES - favorites.size)

        return HomeLists(
            home = favorites.map { HomeApp(it, favorite = true) } + recent.map { HomeApp(it, favorite = false) },
            all = visible,
            hidden = hidden,
            prefs = prefsByPackage,
        )
    }

    /** The wallpaper photo, decoded. Same file as last time -> reuse the decoded copy. */
    private fun loadWallpaper(): ImageBitmap? {
        val file = WallpaperStore.file(this)
        if (!file.exists()) return null
        val stamp = file.lastModified()
        wallpaperCache?.let { (cachedStamp, bitmap) -> if (cachedStamp == stamp) return bitmap }
        val bitmap = BitmapFactory.decodeFile(file.path)?.asImageBitmap() ?: return null
        wallpaperCache = stamp to bitmap
        return bitmap
    }

    // ---- Actions -------------------------------------------------------------------------

    private fun homeActions() = HomeActions(
        openApp = ::openApp,
        toggleHome = { app ->
            val isFavorite = RulesRepository.appPrefs.value.any { it.packageName == app.packageName && it.favoritePos != null }
            if (isFavorite) {
                RulesRepository.removeFavorite(app.packageName)
            } else {
                lifecycleScope.launch {
                    if (!RulesRepository.addFavorite(app.packageName)) toast("Home holds 5 apps. Remove one first.")
                }
            }
        },
        toggleHidden = { app ->
            val isHidden = RulesRepository.appPrefs.value.any { it.packageName == app.packageName && it.hidden }
            RulesRepository.setHidden(app.packageName, !isHidden)
            if (!isHidden) toast("${app.label} is hidden. Find it at the bottom of the search page.")
        },
        mindfulOn = { app ->
            RulesRepository.setMindful(app.packageName, true)
            toast("${app.label} now opens after a short breath.")
        },
        appInfo = { app ->
            start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
        },
        openClock = ::openClock,
        openCalendar = ::openCalendar,
        openPhone = { if (!start(Intent(Intent.ACTION_DIAL))) toast("No phone app found.") },
        // "Still image camera" opens the camera app itself, so Detox needs no camera permission.
        openCamera = { if (!start(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) toast("No camera app found.") },
        openDetox = { start(Intent(this, MainActivity::class.java)) },
        openHomeSettings = { start(Intent(this, MainActivity::class.java).putExtra("open", "settings")) },
        // These three go through our Accessibility service (Android lets it press "system buttons").
        openRecents = { AppWatcherService.openRecents() },
        openNotifications = { AppWatcherService.openNotifications() },
        lockScreen = {
            if (!AppWatcherService.lockScreen()) toast("Turn on protection in Detox to lock with a double-tap.")
        },
    )

    private fun openApp(app: LauncherEntry) {
        val intent = packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        start(intent)
    }

    /** Alarms / clock app. Some phones don't answer the standard "show alarms" request. */
    private fun openClock() {
        if (start(Intent(AlarmClock.ACTION_SHOW_ALARMS))) return
        val clock = lastApps.firstOrNull { it.label.contains("clock", ignoreCase = true) }
        if (clock != null) openApp(clock) else toast("No clock app found.")
    }

    /** The calendar app; plan B = "show the calendar at this moment"; plan C = an app called Calendar. */
    private fun openCalendar() {
        if (start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))) return
        val builder = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
        ContentUris.appendId(builder, System.currentTimeMillis())
        if (start(Intent(Intent.ACTION_VIEW, builder.build()))) return
        val calendar = lastApps.firstOrNull { it.label.contains("calendar", ignoreCase = true) }
        if (calendar != null) openApp(calendar) else toast("No calendar app found.")
    }

    /** Start another screen. false = no app on this phone can handle it. */
    private fun start(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Log.i("Detox", "Nothing can open $intent: $e")
        false
    } catch (e: SecurityException) {
        Log.i("Detox", "Not allowed to open $intent: $e")
        false
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    // ---- Home forwarding + system bars ---------------------------------------------------

    private fun forwardToNormalHome() {
        val target = SystemStatus.otherHome(this)
        if (target == null) {
            strictOn = true            // no other launcher on the phone: show ours, never a blank screen
            return
        }
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setComponent(target)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)   // no animation: it should feel like the normal Home
        } catch (e: Exception) {
            Log.i("Detox", "Could not open normal home: $e")
            strictOn = true            // safety net: the user must always have a home screen
        }
    }

    /** dark = white status-bar icons (dark colours / photo); otherwise dark icons. */
    private fun setBars(dark: Boolean) {
        if (darkBars == dark) return
        darkBars = dark
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private companion object {
        val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
