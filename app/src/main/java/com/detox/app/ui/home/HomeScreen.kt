package com.detox.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detox.app.R
import com.detox.app.domain.LimitSettings
import com.detox.app.domain.PausedApp
import com.detox.app.domain.QuietWindow
import com.detox.app.ui.theme.DetoxTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.time.format.TextStyle as DayNameStyle

/**
 * The main screen. It only SHOWS things and reports clicks upward (onTurnOnProtection, ...).
 * It does not know about Settings or Activities -> easy to preview and test.
 */
@Composable
fun HomeScreen(
    protectionOn: Boolean,
    protectionLocked: Boolean,   // Android's "Restricted setting" lock is on
    usageAccessOn: Boolean,
    windows: List<QuietWindow>,
    apps: List<PausedApp>,
    limits: LimitSettings,
    usedTodayMs: Long?,          // all paused apps together since the day started; null = unknown
    strictOn: Boolean,
    isHomeApp: Boolean,
    shortcutAssigned: Boolean?,
    oneTapMode: Boolean,         // Detox may flip Accessibility switches itself
    onTurnOnProtection: () -> Unit,
    onToggleStrict: (Boolean) -> Unit,
    onFinishStrictSetup: () -> Unit,
    onOpenDefaultApps: () -> Unit,
    onRemoveShortcut: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleLimit: (Boolean) -> Unit,
    onEditLimit: () -> Unit,
    onAllowUsageAccess: () -> Unit,
    onToggleWindow: (id: Int, enabled: Boolean) -> Unit,
    onEditWindow: (QuietWindow) -> Unit,
    onAddWindow: () -> Unit,
    onEditApps: () -> Unit,
    onPreviewBlock: () -> Unit,
) {
    // The rules now come from RulesRepository (via MainActivity), the same place the
    // service reads. Flip a switch here -> the service sees it immediately.

    // "now" refreshes every 30 s, so the status card stays correct while you look at it.
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        LazyColumn(
            // padding(top) = the list STARTS below the status bar, so cards never scroll
            // underneath the clock. (contentPadding would only add space at the very top.)
            modifier = Modifier
                .fillMaxSize()
                .padding(top = insets.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = insets.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Header(onOpenSettings = onOpenSettings) }
            item { ProtectionCard(on = protectionOn, locked = protectionLocked, onTurnOn = onTurnOnProtection) }
            if (shortcutAssigned == true) {
                item { ShortcutWarningCard(canRemoveDirectly = oneTapMode, onRemove = onRemoveShortcut) }
            }
            if (protectionOn && !usageAccessOn) {
                item { UsageAccessCard(onAllow = onAllowUsageAccess) }
            }
            item { NowCard(windows = windows, now = now) }
            item { SectionTitle(title = "Strict mode", hint = if (strictOn) "On" else "Off") }
            item {
                StrictModeCard(
                    on = strictOn,
                    isHomeApp = isHomeApp,
                    hiddenCount = apps.size,
                    onToggle = onToggleStrict,
                    onFinishSetup = onFinishStrictSetup,
                    onOpenDefaultApps = onOpenDefaultApps,
                )
            }
            item { SectionTitle(title = "Quiet times", hint = "Tap to edit") }
            items(windows, key = { it.id }) { window ->
                QuietWindowCard(
                    window = window,
                    onClick = { onEditWindow(window) },
                    onToggle = { on -> onToggleWindow(window.id, on) },
                )
            }
            item {
                OutlinedButton(
                    onClick = onAddWindow,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("+ Add quiet time", style = MaterialTheme.typography.labelLarge)
                }
            }
            item { SectionTitle(title = "Daily limit", hint = "Tap to edit") }
            item {
                DailyLimitCard(
                    limits = limits,
                    usedTodayMs = usedTodayMs,
                    onToggle = onToggleLimit,
                    onClick = onEditLimit,
                )
            }
            item { SectionTitle(title = "Paused apps", hint = "${apps.size} apps") }
            item { PausedAppsRow(apps, onEdit = onEditApps) }
            item {
                OutlinedButton(
                    onClick = onPreviewBlock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    // Newer Material makes outlined-button text grey by default; we want green.
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Preview the block screen", style = MaterialTheme.typography.labelLarge)
                }
            }
            item { PrivacyNote() }
        }
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_leaf),
                contentDescription = null,
                modifier = Modifier.size(width = 38.dp, height = 27.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Detox",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Calm mornings, quiet nights",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Shown when the floating "Detox shortcut" exists: it can switch protection off with one tap. */
@Composable
private fun ShortcutWarningCard(canRemoveDirectly: Boolean, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        border = BorderStroke(1.5.dp, colors.error),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Remove the floating button", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            // One-tap mode: Detox removes it itself. Otherwise we can only open the right page.
            val nextStep = if (canRemoveDirectly) "Detox can remove it for you."
            else "On the next page, turn off \"Detox shortcut\" and keep \"Use Detox\" on."
            Text(
                "The floating Detox button on your screen switches protection off with one tap. $nextStep",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onRemove, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(50)) {
                Text(
                    if (canRemoveDirectly) "Remove it" else "Open the shortcut setting",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * Strict mode = one switch. Three states:
 *  on + Detox is home  -> all good, Home shows the plain list;
 *  on + Detox not home -> unfinished (home was changed elsewhere): "Finish setup" button;
 *  off + Detox is home -> works, but Detox only forwards to HiOS: offer to give Home back.
 */
@Composable
private fun StrictModeCard(
    on: Boolean,
    isHomeApp: Boolean,
    hiddenCount: Int,
    onToggle: (Boolean) -> Unit,
    onFinishSetup: () -> Unit,
    onOpenDefaultApps: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val hiddenText = if (hiddenCount == 1) "1 paused app is" else "$hiddenCount paused apps are"
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, if (on && !isHomeApp) colors.error else colors.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Boring phone", style = type.labelLarge, color = colors.primary)
                    Text(
                        when {
                            !on -> "Off"
                            isHomeApp -> "On · Home is a plain list"
                            else -> "On · setup not finished"
                        },
                        style = type.titleLarge,
                        color = if (on) colors.onSurface else colors.onSurfaceVariant,
                    )
                    Text(
                        when {
                            !on -> "Turn it on and Home becomes a calm text list: no icons, no colors. " +
                                "Paused apps disappear and stay closed all day."
                            isHomeApp -> "No icons, no colors. $hiddenText hidden and closed all day. Press Home to see it."
                            else -> "Detox isn't your home app right now, so Home still opens your normal " +
                                "launcher. Paused apps stay closed all day anyway."
                        },
                        style = type.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Switch(checked = on, onCheckedChange = onToggle)
            }
            if (on && !isHomeApp) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onFinishSetup,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Text("Finish setup: make Detox the home app", style = type.labelLarge)
                }
            }
            if (!on && isHomeApp) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Detox is still your home app and sends you straight to your normal home screen. Want it back?",
                    style = type.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenDefaultApps,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, colors.primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                ) {
                    Text("Default apps", style = type.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun ProtectionCard(on: Boolean, locked: Boolean, onTurnOn: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    if (on) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = colors.primary, contentColor = colors.onPrimary),
        ) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(on = true)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Protection is on", style = type.titleLarge)
                    Text(
                        "Detox is keeping an eye out for paused apps.",
                        style = type.bodyMedium,
                        color = colors.onPrimary.copy(alpha = 0.85f),
                    )
                }
            }
        }
    } else {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.5.dp, colors.primary),
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(on = false)
                    Spacer(Modifier.width(14.dp))
                    Text("Protection is off", style = type.titleLarge, color = colors.onSurface)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (locked) {
                        // Android 13+ "Restricted setting": the switch is greyed out until the user unlocks it.
                        "Android has locked the switch Detox needs. Tap below and Detox shows you the two steps to unlock it."
                    } else {
                        "Detox needs Accessibility to notice when a paused app opens. " +
                            "It can't read your screen, messages or passwords."
                    },
                    style = type.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onTurnOn,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Text("Turn on protection", style = type.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun UsageAccessCard(onAllow: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                "ONE MORE STEP · RECOMMENDED",
                style = type.labelMedium.copy(letterSpacing = 1.2.sp),
                color = colors.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text("Allow Usage access", style = type.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "Detox needs it to count minutes for daily limits, and to notice a quiet time starting " +
                    "while you're already scrolling. It sees app names and times, never what's inside.",
                style = type.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onAllow,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.5.dp, colors.primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
            ) {
                Text("Allow usage access", style = type.labelLarge)
            }
        }
    }
}

@Composable
private fun StatusBadge(on: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (on) colors.onPrimary else colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (on) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Text("!", style = MaterialTheme.typography.titleLarge, color = colors.primary)
        }
    }
}

@Composable
private fun NowCard(windows: List<QuietWindow>, now: LocalDateTime) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val active = windows.firstOrNull { it.isActive(now) }
    val next = windows
        .mapNotNull { w -> w.nextStart(now)?.let { start -> w to start } }
        .minByOrNull { it.second }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                if (active != null) "QUIET TIME NOW" else "NEXT QUIET TIME",
                style = type.labelMedium.copy(letterSpacing = 1.2.sp),
                color = colors.primary,
            )
            Spacer(Modifier.height(6.dp))
            when {
                active != null -> {
                    Text("${active.name} · until ${active.end.hhmm()}", style = type.titleLarge, color = colors.onSurface)
                    Text("Paused apps stay closed until then.", style = type.bodyMedium, color = colors.onSurfaceVariant)
                }
                next != null -> {
                    val (window, start) = next
                    Text(
                        "${window.name} · ${dayWord(start, now)} at ${window.start.hhmm()}",
                        style = type.titleLarge,
                        color = colors.onSurface,
                    )
                    Text(startsIn(now, start), style = type.bodyMedium, color = colors.onSurfaceVariant)
                }
                else -> {
                    Text("No quiet times switched on", style = type.titleLarge, color = colors.onSurface)
                    Text("Add one below for your night or morning.", style = type.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, hint: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuietWindowCard(window: QuietWindow, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (window.kind == QuietWindow.Kind.NIGHT) R.drawable.ic_moon else R.drawable.ic_sun
                    ),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(window.name, style = type.labelLarge, color = colors.primary)
                Text(
                    "${window.start.hhmm()} – ${window.end.hhmm()}",
                    style = type.titleLarge,
                    color = if (window.enabled) colors.onSurface else colors.onSurfaceVariant,
                )
                Text(window.daysLabel(), style = type.bodySmall, color = colors.onSurfaceVariant)
            }
            Switch(checked = window.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun DailyLimitCard(
    limits: LimitSettings,
    usedTodayMs: Long?,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_timer),
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("All paused apps", style = type.labelLarge, color = colors.primary)
                    Text(
                        "${limits.dailyLimitMin} min a day",
                        style = type.titleLarge,
                        color = if (limits.dailyLimitOn) colors.onSurface else colors.onSurfaceVariant,
                    )
                    Text("A new day starts at ${limits.dayStart.hhmm()}", style = type.bodySmall, color = colors.onSurfaceVariant)
                }
                Switch(checked = limits.dailyLimitOn, onCheckedChange = onToggle)
            }
            if (limits.dailyLimitOn) {
                Spacer(Modifier.height(14.dp))
                if (usedTodayMs == null) {
                    Text(
                        "Detox needs Usage access to count your minutes (see the card above).",
                        style = type.bodySmall,
                        color = colors.error,
                    )
                } else {
                    val limitMs = limits.dailyLimitMin * 60_000L
                    val usedMin = usedTodayMs / 60_000
                    LinearProgressIndicator(
                        progress = { (usedTodayMs.toFloat() / limitMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                        color = colors.primary,
                        trackColor = colors.primaryContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (usedTodayMs >= limitMs) "Used up · paused apps stay closed until ${limits.dayStart.hhmm()}"
                        else "$usedMin of ${limits.dailyLimitMin} min used today",
                        style = type.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PausedAppsRow(apps: List<PausedApp>, onEdit: () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        apps.forEach { app ->
            val text = app.dailyLimitMin?.let { "${app.name} · $it min" } ?: app.name
            Pill(text = text, filled = true, onClick = onEdit)
        }
        Pill(text = if (apps.isEmpty()) "+ Choose apps" else "+ Add app", filled = false, onClick = onEdit)
    }
}

@Composable
private fun Pill(text: String, filled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (filled) colors.primaryContainer else colors.surface,
        contentColor = if (filled) colors.onPrimaryContainer else colors.primary,
        border = if (filled) null else BorderStroke(1.dp, colors.outline),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun PrivacyNote() {
    Text(
        "No internet access · Nothing ever leaves this phone",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

// ---- small text helpers ----------------------------------------------------

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun LocalTime.hhmm(): String = format(HHMM)

private fun dayWord(target: LocalDateTime, now: LocalDateTime): String =
    when (ChronoUnit.DAYS.between(now.toLocalDate(), target.toLocalDate())) {
        0L -> "today"
        1L -> "tomorrow"
        else -> target.dayOfWeek.getDisplayName(DayNameStyle.FULL, Locale.ENGLISH)
    }

private fun startsIn(now: LocalDateTime, target: LocalDateTime): String {
    val minutes = Duration.between(now, target).toMinutes()
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "Starts in $m min"
        m == 0L -> "Starts in $h h"
        else -> "Starts in $h h $m min"
    }
}

@Preview(showBackground = true, heightDp = 1100)
@Composable
private fun HomeScreenPreview() {
    DetoxTheme {
        HomeScreen(
            protectionOn = true,
            protectionLocked = false,
            usageAccessOn = false,
            windows = QuietWindow.defaults,
            apps = PausedApp.defaults,
            limits = LimitSettings(dailyLimitOn = true),
            usedTodayMs = 23 * 60_000L,
            strictOn = true,
            isHomeApp = false,
            shortcutAssigned = true,
            oneTapMode = false,
            onTurnOnProtection = {},
            onToggleStrict = {},
            onFinishStrictSetup = {},
            onOpenDefaultApps = {},
            onRemoveShortcut = {},
            onOpenSettings = {},
            onToggleLimit = {},
            onEditLimit = {},
            onAllowUsageAccess = {},
            onToggleWindow = { _, _ -> },
            onEditWindow = {},
            onAddWindow = {},
            onEditApps = {},
            onPreviewBlock = {},
        )
    }
}
