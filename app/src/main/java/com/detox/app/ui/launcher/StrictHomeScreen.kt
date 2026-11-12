package com.detox.app.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.detox.app.data.LauncherEntry
import com.detox.app.domain.AppPref
import com.detox.app.domain.HomePrefs
import com.detox.app.domain.HomeTheme
import com.detox.app.ui.theme.DetoxTheme
import java.time.LocalDateTime

/** One app on page 1. favorite = you added it; false = a recent app filling a free slot. */
data class HomeApp(val entry: LauncherEntry, val favorite: Boolean)

/** Everything the two pages list. Paused apps (strict mode) never appear in any of these. */
data class HomeLists(
    val home: List<HomeApp>,              // page 1: up to 5 (favorites first, then recent)
    val all: List<LauncherEntry>,         // page 2: every visible app, A–Z
    val hidden: List<LauncherEntry>,      // page 2: apps you hid yourself
    val prefs: Map<String, AppPref>,      // per-app flags (favorite / hidden / mindful) for the menu
)

/**
 * Everything you can DO on the strict home, in one object, so the screens don't need
 * fifteen separate parameters. The activity fills it in (it knows how to start other apps).
 */
class HomeActions(
    val openApp: (LauncherEntry) -> Unit,
    val toggleHome: (LauncherEntry) -> Unit,      // add to / remove from page 1
    val toggleHidden: (LauncherEntry) -> Unit,
    val mindfulOn: (LauncherEntry) -> Unit,       // only switching ON here (off = in Detox, with countdown)
    val appInfo: (LauncherEntry) -> Unit,
    val openClock: () -> Unit,
    val openCalendar: () -> Unit,
    val openPhone: () -> Unit,
    val openCamera: () -> Unit,
    val openDetox: () -> Unit,
    val openHomeSettings: () -> Unit,
    val openRecents: () -> Unit,
    val openNotifications: () -> Unit,
    val lockScreen: () -> Unit,
)

/**
 * The status line under the date when no quiet time is running.
 * Names the first two hidden apps so you see that strict mode is really doing something.
 */
internal fun strictStatusLine(hiddenNames: List<String>): String = when (hiddenNames.size) {
    0 -> "Strict mode is on"
    1 -> "Strict mode · ${hiddenNames[0]} is hidden"
    2 -> "Strict mode · ${hiddenNames[0]} and ${hiddenNames[1]} are hidden"
    else -> "Strict mode · ${hiddenNames[0]}, ${hiddenNames[1]} and ${hiddenNames.size - 2} more are hidden"
}

/**
 * "Detox Home": the strict-mode home screen. Two pages you swipe between:
 *   page 1 = clock, date, quote, your 5 apps, Phone + Camera
 *   page 2 = search + every app A–Z (+ the apps you hid)
 * Deliberately boring: text only, no app icons, calm colours.
 */
@Composable
fun StrictHomeScreen(
    look: HomeLook,
    wallpaper: ImageBitmap?,            // null = plain colour background
    pagerState: PagerState,
    now: LocalDateTime,
    status: String,
    screenTimeMs: Long?,
    prefs: HomePrefs,
    lists: HomeLists?,                  // null = still loading
    showRecents: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    searchListState: LazyListState,
    actions: HomeActions,
) {
    Box(Modifier.fillMaxSize().background(look.background)) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = null,                 // decoration only
                contentScale = ContentScale.Crop,          // fill the screen, cut off the edges
                modifier = Modifier.fillMaxSize(),
            )
            // A see-through black layer over the photo, so the light text stays readable.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = look.scrimAlpha)))
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) {
                HomePage(
                    look = look,
                    now = now,
                    status = status,
                    screenTimeMs = screenTimeMs,
                    prefs = prefs,
                    home = lists?.home,
                    appPrefs = lists?.prefs.orEmpty(),
                    pageCount = pagerState.pageCount,
                    currentPage = pagerState.currentPage,
                    actions = actions,
                )
            } else {
                SearchPage(
                    look = look,
                    lists = lists,
                    query = query,
                    onQueryChange = onQueryChange,
                    listState = searchListState,
                    showRecents = showRecents,
                    actions = actions,
                )
            }
        }
    }
}

/** One app name, used on both pages. Tap = open it. Long-press = a small menu. */
@OptIn(ExperimentalFoundationApi::class) // combinedClickable was experimental in older Compose versions
@Composable
internal fun AppRow(
    app: LauncherEntry,
    pref: AppPref?,
    look: HomeLook,
    textStyle: TextStyle,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    // The Box is the "anchor": the DropdownMenu pops up right below this row.
    Box(modifier) {
        Text(
            app.label,
            style = textStyle,
            color = look.ink,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { actions.openApp(app) },
                    onLongClick = { menuOpen = true },
                )
                .padding(vertical = 10.dp),
        )
        AppMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            app = app,
            pref = pref,
            look = look,
            actions = actions,
        )
    }
}

/** The long-press menu: home / hide / mindful pause / app info. */
@Composable
private fun AppMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    app: LauncherEntry,
    pref: AppPref?,
    look: HomeLook,
    actions: HomeActions,
) {
    val isFavorite = pref?.favoritePos != null
    val isHidden = pref?.hidden == true
    val isMindful = pref?.mindful == true

    // The theme's menu colour is mint green; the strict home stays neutral.
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = look.menu) {
        if (isHidden) {
            MenuItem("Unhide", look, onDismiss) { actions.toggleHidden(app) }
        } else {
            MenuItem(if (isFavorite) "Remove from home" else "Add to home", look, onDismiss) { actions.toggleHome(app) }
            MenuItem("Hide", look, onDismiss) { actions.toggleHidden(app) }
        }
        if (isMindful) {
            // Switching it OFF loosens a rule, so that lives in Detox (behind the countdown).
            MenuItem("Mindful pause is on (change it in Detox → Apps)", look, onDismiss, enabled = false) {}
        } else {
            MenuItem("Mindful pause", look, onDismiss) { actions.mindfulOn(app) }
        }
        MenuItem("App info", look, onDismiss) { actions.appInfo(app) }
    }
}

/** One menu line. It closes the menu first, then does its job. */
@Composable
private fun MenuItem(
    text: String,
    look: HomeLook,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text, color = if (enabled) look.ink else look.soft) },
        enabled = enabled,
        onClick = {
            onDismiss()
            onClick()
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Previews (Android Studio only)

private val previewActions = HomeActions(
    openApp = {}, toggleHome = {}, toggleHidden = {}, mindfulOn = {}, appInfo = {},
    openClock = {}, openCalendar = {}, openPhone = {}, openCamera = {}, openDetox = {},
    openHomeSettings = {}, openRecents = {}, openNotifications = {}, lockScreen = {},
)

private fun previewLists(): HomeLists {
    val telegram = LauncherEntry("Telegram", "org.telegram")
    val maps = LauncherEntry("Maps", "maps")
    val kaspi = LauncherEntry("Kaspi.kz", "kaspi")
    val chess = LauncherEntry("Chess", "chess")
    val gmail = LauncherEntry("Gmail", "gmail")
    return HomeLists(
        home = listOf(
            HomeApp(telegram, favorite = true), HomeApp(maps, favorite = true),
            HomeApp(kaspi, favorite = false), HomeApp(chess, favorite = false), HomeApp(gmail, favorite = false),
        ),
        all = listOf(
            LauncherEntry("Calculator", "calc"), LauncherEntry("Calendar", "cal"), LauncherEntry("Camera", "cam"),
            chess, LauncherEntry("Clock", "clock"), gmail, kaspi, maps, LauncherEntry("Notes", "notes"),
            LauncherEntry("Phone", "phone"), telegram,
        ),
        hidden = listOf(LauncherEntry("Games", "games")),
        prefs = mapOf(
            "org.telegram" to AppPref("org.telegram", favoritePos = 0),
            "maps" to AppPref("maps", favoritePos = 1),
            "games" to AppPref("games", hidden = true),
        ),
    )
}

@Composable
private fun PreviewHome(theme: HomeTheme, page: Int) {
    val prefs = HomePrefs(theme = theme)
    DetoxTheme {
        StrictHomeScreen(
            look = homeLook(prefs, quietNow = false, hasWallpaper = false),
            wallpaper = null,
            pagerState = rememberPagerState(initialPage = page, pageCount = { 2 }),
            now = LocalDateTime.of(2026, 11, 12, 7, 42),
            status = strictStatusLine(listOf("Instagram", "YouTube", "Threads", "Pinterest")),
            screenTimeMs = 72 * 60_000L,
            prefs = prefs,
            lists = previewLists(),
            showRecents = true,
            query = "",
            onQueryChange = {},
            searchListState = rememberLazyListState(),
            actions = previewActions,
        )
    }
}

@Preview(name = "Home · Paper", widthDp = 400, heightDp = 860)
@Composable
private fun HomePaperPreview() = PreviewHome(HomeTheme.PAPER, page = 0)

@Preview(name = "Home · Night", widthDp = 400, heightDp = 860)
@Composable
private fun HomeNightPreview() = PreviewHome(HomeTheme.NIGHT, page = 0)

@Preview(name = "Search · Sand", widthDp = 400, heightDp = 860)
@Composable
private fun SearchSandPreview() = PreviewHome(HomeTheme.SAND, page = 1)
