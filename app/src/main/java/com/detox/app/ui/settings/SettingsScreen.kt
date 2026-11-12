package com.detox.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.detox.app.domain.AppPref
import com.detox.app.domain.HomePrefs
import com.detox.app.domain.HomeTheme
import com.detox.app.domain.StrictSettings

/** The one-time PC command that unlocks "one-tap mode" (see OneTapCard). */
private const val GRANT_COMMAND = "adb shell pm grant com.detox.app android.permission.WRITE_SECURE_SETTINGS"

/**
 * Everything that keeps Detox working, as real switches, plus strict-mode options.
 * Each switch shows the REAL state (read in MainActivity.onResume). Flipping it asks MainActivity
 * to change it: ON happens right away, OFF goes through the countdown first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    protectionOn: Boolean,
    protectionLocked: Boolean,      // Android's "Restricted setting" lock is on
    usageAccessOn: Boolean,
    batteryOk: Boolean,
    shortcutAssigned: Boolean?,     // null = Android didn't let us check
    isHomeApp: Boolean,
    strictOn: Boolean,
    oneTapMode: Boolean,            // Detox may flip Accessibility switches itself (granted from a PC once)
    countdownSec: Int,
    homePrefs: HomePrefs,           // how the strict home screen looks
    appPrefs: List<AppPref>,        // per-app extras: home favorites, hidden, mindful pause
    maxFavorites: Int,
    onSaveHomePrefs: (HomePrefs) -> Unit,
    onChooseWallpaper: () -> Unit,
    onRemoveWallpaper: () -> Unit,
    onSetProtection: (Boolean) -> Unit,
    onSetUsageAccess: (Boolean) -> Unit,
    onSetBattery: (Boolean) -> Unit,
    onSetShortcut: (Boolean) -> Unit,   // true = bring the floating button back, false = remove it
    onSetHomeApp: (Boolean) -> Unit,
    onOpenAppInfo: () -> Unit,
    onSetCountdown: (Int) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography

    Scaffold(containerColor = colors.background) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = insets.calculateTopPadding()),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = insets.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "←",
                        style = type.headlineMedium,
                        color = colors.primary,
                        modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(horizontal = 8.dp),
                    )
                    Text("Settings", style = type.headlineMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, colors.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                    ) { Text("Done") }
                }
            }

            // ---- Keep Detox running ----------------------------------------------------
            item {
                Section(
                    "Keep Detox running",
                    "Android can quietly switch Detox off in a few ways. Keep every dot green. " +
                        "Switching something off here starts the countdown first.",
                )
            }
            item {
                SwitchRow(
                    title = "Protection",
                    why = "Lets Detox notice which app you open, so it can close paused apps on time. " +
                        "Android calls this Accessibility. Detox can't read your screen.",
                    checked = protectionOn,
                    good = protectionOn,
                    hint = when {
                        protectionOn || oneTapMode -> null
                        protectionLocked -> "Android has locked this switch. Tap it to see how to unlock it."
                        else -> "Opens Android's Accessibility page: tap Detox there and switch it on."
                    },
                    onCheckedChange = onSetProtection,
                )
            }
            item {
                SwitchRow(
                    title = "Usage access",
                    why = "Lets Detox count minutes for your daily limits, and spot a quiet time starting " +
                        "while you're already scrolling. It sees app names and times only.",
                    checked = usageAccessOn,
                    good = usageAccessOn,
                    hint = "Opens Android's Usage access page.",
                    onCheckedChange = onSetUsageAccess,
                )
            }
            item {
                SwitchRow(
                    title = "Run in background",
                    why = "Stops the battery saver from putting Detox to sleep, so it keeps working at night.",
                    checked = batteryOk,
                    good = batteryOk,
                    hint = if (batteryOk) "Opens Android's battery list." else "Android asks you to confirm.",
                    onCheckedChange = onSetBattery,
                )
            }
            item {
                ButtonRow(
                    title = "Auto-start (HiOS)",
                    why = "HiOS closes background apps that aren't allowed to auto-start. In App info, allow " +
                        "Auto-start and set Battery to \"No restrictions\". Detox can't check this one for you.",
                    good = null,
                    action = "App info",
                    onAction = onOpenAppInfo,
                )
            }
            item {
                if (shortcutAssigned == null) {
                    ButtonRow(
                        title = "Floating shortcut button",
                        why = "A floating Detox button can switch protection off with one tap. Android didn't let " +
                            "Detox check for it. If you see one, turn off \"Detox shortcut\" on this page.",
                        good = null,
                        action = "Open",
                        onAction = { onSetShortcut(false) },
                    )
                } else {
                    SwitchRow(
                        title = "Floating shortcut button",
                        why = "A floating Accessibility button that switches protection off with one tap. " +
                            "Best kept off.",
                        checked = shortcutAssigned,
                        good = !shortcutAssigned,
                        hint = if (shortcutAssigned && !oneTapMode) "Opens Detox's Accessibility page: turn off \"Detox shortcut\"." else null,
                        checkedIsBad = true,
                        onCheckedChange = onSetShortcut,
                    )
                }
            }

            // ---- Home screen (strict mode) ---------------------------------------------
            // These are looks, not rules: every change here is saved right away, no countdown.
            item {
                Section(
                    "Home screen (strict mode)",
                    "How your calm home screen looks. These are looks, not rules, so changes apply right away.",
                )
            }
            item { ThemeCard(selected = homePrefs.theme, onSelect = { onSaveHomePrefs(homePrefs.copy(theme = it)) }) }
            item {
                WallpaperCard(
                    on = homePrefs.wallpaperOn,
                    onChoose = onChooseWallpaper,
                    onRemove = onRemoveWallpaper,
                )
            }
            item {
                // One card, several switches. Each `copy(...)` changes ONE field and keeps the rest.
                SettingsCard {
                    PrefRow(
                        title = "Quote of the day",
                        why = "A short, calm quote under the date. A new one each day; tap it for the next.",
                        checked = homePrefs.quoteOn,
                        onCheckedChange = { onSaveHomePrefs(homePrefs.copy(quoteOn = it)) },
                    )
                    HorizontalDivider(color = colors.outlineVariant)
                    PrefRow(
                        title = "Dark during quiet time",
                        why = "The home screen turns Night (dark) while a quiet time is on.",
                        checked = homePrefs.darkDuringQuiet,
                        onCheckedChange = { onSaveHomePrefs(homePrefs.copy(darkDuringQuiet = it)) },
                    )
                    HorizontalDivider(color = colors.outlineVariant)
                    PrefRow(
                        title = "Screen time under the date",
                        why = "A small line like \"1 h 12 min today\". Tap it to open Detox. Needs Usage access.",
                        checked = homePrefs.screenTimeOn,
                        onCheckedChange = { onSaveHomePrefs(homePrefs.copy(screenTimeOn = it)) },
                    )
                    HorizontalDivider(color = colors.outlineVariant)
                    PrefRow(
                        title = "Swipe down for notifications",
                        why = "Swipe down anywhere on the home screen to open notifications. Works while protection is on.",
                        checked = homePrefs.swipeDownNotifications,
                        onCheckedChange = { onSaveHomePrefs(homePrefs.copy(swipeDownNotifications = it)) },
                    )
                    HorizontalDivider(color = colors.outlineVariant)
                    PrefRow(
                        title = "Double-tap to lock",
                        why = "Double-tap an empty spot to turn the screen off. Works while protection is on.",
                        checked = homePrefs.doubleTapLock,
                        onCheckedChange = { onSaveHomePrefs(homePrefs.copy(doubleTapLock = it)) },
                    )
                }
            }
            item {
                val favorites = appPrefs.count { it.favoritePos != null }
                val hidden = appPrefs.count { it.hidden }
                val mindful = appPrefs.count { it.mindful }
                SettingsCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "Home apps $favorites/$maxFavorites · Hidden $hidden · Mindful $mindful",
                            style = type.titleSmall,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Long-press any app on the strict home to change these. " +
                                "Mindful pause is also in Detox's app list (Paused apps → Add app).",
                            style = type.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- Strict mode -----------------------------------------------------------
            item { Section("Strict mode") }
            item {
                SwitchRow(
                    title = "Detox as home app",
                    why = "Strict mode swaps your home screen for a calm text list. For that, Detox has to be the home app.",
                    checked = isHomeApp,
                    // Not being the home app is only a problem while strict mode is on.
                    good = if (isHomeApp) true else if (strictOn) false else null,
                    hint = if (isHomeApp) "Switching off opens Android's Default apps page." else "Android asks you to confirm.",
                    onCheckedChange = onSetHomeApp,
                )
            }
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.outlineVariant),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("Countdown before loosening", style = type.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Before you switch off a quiet time, raise a limit, unpause an app or turn off " +
                                "strict mode, Detox makes you wait this long. Picking a shorter countdown waits too.",
                            style = type.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StrictSettings.COUNTDOWN_CHOICES.forEach { sec ->
                                val selected = sec == countdownSec
                                Surface(
                                    onClick = { onSetCountdown(sec) },
                                    shape = RoundedCornerShape(50),
                                    color = if (selected) colors.primary else colors.surface,
                                    contentColor = if (selected) colors.onPrimary else colors.primary,
                                    border = if (selected) null else BorderStroke(1.dp, colors.outline),
                                ) {
                                    Text(
                                        if (sec < 60) "$sec s" else "${sec / 60} min",
                                        style = type.labelLarge,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Leave Detox while it counts and it starts over.",
                            style = type.labelSmall,
                            color = colors.primary,
                        )
                    }
                }
            }

            // ---- One-tap mode ----------------------------------------------------------
            item { Section("One-tap mode", "Optional. Needs a computer once.") }
            item { OneTapCard(on = oneTapMode) }

            // ---- Privacy ---------------------------------------------------------------
            item { Section("Privacy") }
            item {
                Text(
                    "Detox has no internet permission, so nothing can leave your phone. It only notices which " +
                        "app is open and for how long, never what's on the screen. Your rules live in a small " +
                        "database on this phone.",
                    style = type.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The status dot: true = green (all good), false = red (needs you), null = grey (optional / can't check). */
@Composable
private fun StatusDot(good: Boolean?) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(
                when (good) {
                    true -> colors.primary
                    false -> colors.error
                    null -> colors.outline
                }
            )
    )
}

/** Card frame shared by all rows: a red border when something needs fixing. */
@Composable
private fun RowCard(good: Boolean?, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (good == false) colors.surfaceVariant else colors.surface),
        border = BorderStroke(1.dp, if (good == false) colors.error else colors.outlineVariant),
    ) { content() }
}

/**
 * One checklist row with a real switch.
 * The switch always shows the REAL state. Tapping it only ASKS for a change, so it may
 * snap back until Android has really changed the setting (e.g. after you return from Settings).
 * [checkedIsBad] = the switch being on is the bad state (floating shortcut) -> red instead of green.
 */
@Composable
private fun SwitchRow(
    title: String,
    why: String,
    checked: Boolean,
    good: Boolean?,
    hint: String?,
    onCheckedChange: (Boolean) -> Unit,
    checkedIsBad: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    RowCard(good) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(good)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(why, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (hint != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(hint, style = MaterialTheme.typography.labelSmall, color = colors.primary)
                }
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = if (checkedIsBad) SwitchDefaults.colors(checkedTrackColor = colors.error) else SwitchDefaults.colors(),
            )
        }
    }
}

/** A row for things Detox can't read (e.g. HiOS auto-start): just a button to the right page. */
@Composable
private fun ButtonRow(title: String, why: String, good: Boolean?, action: String, onAction: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    RowCard(good) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(good)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                Text(why, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(action, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Plain white card for a group of settings (no status dot: these are choices, not problems). */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

/** One on/off choice inside a SettingsCard. */
@Composable
private fun PrefRow(title: String, why: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }   // the whole row is a big tap target
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(why, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** "PAPER" -> "Paper". Works for any theme Agent A adds later, without a `when` to update. */
private fun themeLabel(theme: HomeTheme): String = theme.name.lowercase().replaceFirstChar { it.uppercase() }

/** Theme chips, each with a little dot in that theme's background colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeCard(selected: HomeTheme, onSelect: (HomeTheme) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    SettingsCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("Theme", style = type.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Soft colours only. Night is dark and easy on the eyes in bed.",
                style = type.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeTheme.entries.forEach { theme ->
                    val isSelected = theme == selected
                    Surface(
                        onClick = { onSelect(theme) },
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) colors.primary else colors.surface,
                        contentColor = if (isSelected) colors.onPrimary else colors.primary,
                        border = if (isSelected) null else BorderStroke(1.dp, colors.outline),
                    ) {
                        Row(
                            Modifier.padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Color(Long) reads 0xAARRGGBB, the same format HomeTheme stores.
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(theme.background))
                                    .border(1.dp, colors.outline, CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(themeLabel(theme), style = type.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

/** Choose / remove your own photo behind the clock. */
@Composable
private fun WallpaperCard(on: Boolean, onChoose: () -> Unit, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    SettingsCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(if (on) "Wallpaper photo is on" else "Wallpaper photo", style = type.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Your own photo behind the clock, dimmed so the text stays easy to read.",
                style = type.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Detox only gets the one photo you pick. No storage permission needed.",
                style = type.labelSmall,
                color = colors.primary,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onChoose, shape = RoundedCornerShape(50)) {
                    Text(if (on) "Change photo" else "Choose photo", style = type.labelLarge)
                }
                if (on) {
                    OutlinedButton(
                        onClick = onRemove,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, colors.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                    ) { Text("Remove photo", style = type.labelLarge) }
                }
            }
        }
    }
}

/**
 * "One-tap mode" = Android's WRITE_SECURE_SETTINGS permission. A normal app can never get it from
 * a dialog; only a computer can grant it, with one USB command. With it, Detox can flip its own
 * Accessibility switch, so the switches above work instantly and self-healing can kick in.
 */
@Composable
private fun OneTapCard(on: Boolean) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    RowCard(good = if (on) true else null) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(if (on) true else null)
                Spacer(Modifier.width(14.dp))
                Text(if (on) "One-tap mode is on" else "One-tap mode is off", style = type.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (on) {
                    "Detox switches protection on and off by itself, and switches it straight back on " +
                        "if someone turns it off behind its back."
                } else {
                    "Android lets an app flip its own Accessibility switch only after one USB command from a " +
                        "computer. Then the switches above work instantly, and Detox switches protection back on " +
                        "if someone turns it off behind its back."
                },
                style = type.bodySmall,
                color = colors.onSurfaceVariant,
            )
            if (!on) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Connect the phone with USB debugging on, then run this once on the computer:",
                    style = type.bodySmall,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                // SelectionContainer = long-press the command to copy it.
                SelectionContainer {
                    Text(
                        GRANT_COMMAND,
                        style = type.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = colors.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceVariant)
                            .padding(12.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "To undo it later, run the same command with \"revoke\" instead of \"grant\".",
                    style = type.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
