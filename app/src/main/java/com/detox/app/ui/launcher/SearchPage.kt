package com.detox.app.ui.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.detox.app.data.LauncherEntry
import kotlinx.coroutines.launch

/** Show the A–Z scroller only when the list is long enough to need it. */
private const val SCROLLER_MIN_APPS = 12

/**
 * Page 2: find any app.
 *
 *   Search apps______________
 *   Calculator            A
 *   Calendar              B
 *   ...                   C   <- A–Z scroller
 *   Hidden apps (3)  Show
 *   Recents · Detox · Home settings
 *
 * The keyboard does NOT open by itself (you asked for that): tap the search line to type.
 * Enter opens the first match.
 */
@Composable
fun SearchPage(
    look: HomeLook,
    lists: HomeLists?,                  // null = still loading
    query: String,
    onQueryChange: (String) -> Unit,
    listState: LazyListState,
    showRecents: Boolean,
    actions: HomeActions,
) {
    val scope = rememberCoroutineScope()
    var hiddenOpen by rememberSaveable { mutableStateOf(false) }

    val all = lists?.all.orEmpty()
    // Filter by name, case doesn't matter ("tel" finds Telegram).
    val shown = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { it.label.contains(q, ignoreCase = true) }
    }
    // Letters for the scroller + where each letter starts in the list (like a book's index).
    val firstIndex = remember(shown) {
        val map = LinkedHashMap<Char, Int>()
        shown.forEachIndexed { index, app -> map.putIfAbsent(letterOf(app.label), index) }
        map
    }
    val appStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal)

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()                   // keyboard open -> the bottom links move up above it
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            look = look,
            onGo = { shown.firstOrNull()?.let(actions.openApp) },
        )
        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Room on the right for the letter column, so it never covers a name.
                contentPadding = PaddingValues(end = 32.dp, bottom = 16.dp),
            ) {
                if (lists != null && shown.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            if (query.isBlank()) "No apps to show." else "No app called “${query.trim()}”.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = look.soft,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                // The apps come FIRST in the list, so "item index" = "index in shown" (the scroller needs that).
                items(shown, key = { "app:" + it.packageName }) { app ->
                    AppRow(app, lists?.prefs?.get(app.packageName), look, appStyle, actions)
                }

                // Apps you hid: only while not searching, folded away by default.
                val hidden = lists?.hidden.orEmpty()
                if (query.isBlank() && hidden.isNotEmpty()) {
                    item(key = "hidden:header") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { hiddenOpen = !hiddenOpen }
                                .padding(top = 24.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "HIDDEN APPS (${hidden.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = look.soft,
                                modifier = Modifier.weight(1f),
                            )
                            Text(if (hiddenOpen) "Fold" else "Show", style = MaterialTheme.typography.labelLarge, color = look.soft)
                        }
                    }
                    if (hiddenOpen) {
                        items(hidden, key = { "hidden:" + it.packageName }) { app ->
                            HiddenRow(app, look, actions)
                        }
                    }
                }
            }

            // While you type, the list is short anyway: no scroller.
            if (query.isBlank() && shown.size >= SCROLLER_MIN_APPS) {
                AzScroller(
                    letters = firstIndex.keys.toList(),
                    ink = look.ink,
                    soft = look.soft,
                    onLetter = { letter ->
                        firstIndex[letter]?.let { index -> scope.launch { listState.scrollToItem(index) } }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }

        // Quiet links, always reachable (also with the keyboard open).
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Why a Recents link: with a third-party home app, the recent-apps gesture is
            // unreliable on some phones (HiOS included). Our service can open that screen.
            if (showRecents) BottomLink("Recents", look, actions.openRecents)
            BottomLink("Detox", look, actions.openDetox)
            BottomLink("Home settings", look, actions.openHomeSettings)
        }
    }
}

/** A plain search line: no box, no icon, just text and a thin line under it. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, look: HomeLook, onGo: () -> Unit) {
    Column {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal, color = look.ink),
            cursorBrush = SolidColor(look.ink),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            decorationBox = { field ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            "Search apps",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
                            color = look.soft,
                        )
                    }
                    field()   // the real, editable text
                }
            },
        )
        HorizontalDivider(color = look.soft.copy(alpha = 0.5f))
    }
}

/** A hidden app: tap opens it, "Unhide" puts it back in the list. */
@Composable
private fun HiddenRow(app: LauncherEntry, look: HomeLook, actions: HomeActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            app.label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
            color = look.soft,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .clickable { actions.openApp(app) }
                .padding(vertical = 10.dp),
        )
        Text(
            "Unhide",
            style = MaterialTheme.typography.labelLarge,
            color = look.ink,
            modifier = Modifier
                .clickable { actions.toggleHidden(app) }
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun BottomLink(text: String, look: HomeLook, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = look.soft,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    )
}
