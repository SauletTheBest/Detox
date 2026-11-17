package com.detox.app.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.detox.app.domain.DayStat
import com.detox.app.domain.StatsSnapshot
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DAY_TITLE = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)

/**
 * Stats: what Detox saw. Everything here comes from the local block_event table and
 * Android's usage log; nothing leaves the phone.
 *
 *   [ Last night ]  [ Calm nights in a row ]      <- big numbers (the headline)
 *   Today: 3 tries · 24 min in paused apps
 *   Time in paused apps   ▁▃▂▅▁▂█                 <- tap a bar to read it
 *   Tries per day         ▂▁▁▃▁▁▂
 *   Most tried apps / Why Detox stepped in       <- thin horizontal bars
 */
@Composable
fun StatsScreen(
    stats: StatsSnapshot?,            // null = still loading
    usageAccessOn: Boolean,
    onAllowUsageAccess: () -> Unit,
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
                    Text("Stats", style = type.headlineMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, colors.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                    ) { Text("Done") }
                }
            }

            if (stats == null) {
                item { Text("Counting…", style = type.bodyMedium, color = colors.onSurfaceVariant) }
                return@LazyColumn
            }

            // ---- The headline: two big numbers --------------------------------------------
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigNumber(
                        title = "Last night",
                        number = stats.lastNightTries,
                        caption = if (stats.lastNightTries == 0) "calm night 🌙" else if (stats.lastNightTries == 1) "try" else "tries",
                        modifier = Modifier.weight(1f),
                    )
                    BigNumber(
                        title = "Calm nights",
                        number = stats.calmNights,
                        caption = "in a row",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                val minutes = stats.todayPausedMinutes?.let { " · ${minutesText(it)} in paused apps" }.orEmpty()
                Text(
                    "Today: ${triesText(stats.todayTries)}$minutes",
                    style = type.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            // ---- Last 7 days: two small charts, one measure each (never two scales in one chart) ----
            item {
                if (usageAccessOn && stats.days.any { it.pausedMinutes != null }) {
                    DayBars(
                        title = "Time in paused apps",
                        days = stats.days,
                        value = { it.pausedMinutes ?: 0 },
                        valueText = ::minutesText,
                    )
                } else {
                    StatsCard {
                        Text("Time in paused apps", style = type.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Needs Usage access: Android's log of which app was open and for how long.",
                            style = type.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Allow Usage access →",
                            style = type.labelLarge,
                            color = colors.primary,
                            modifier = Modifier.clickable(onClick = onAllowUsageAccess),
                        )
                    }
                }
            }
            item {
                DayBars(
                    title = "Tries per day",
                    days = stats.days,
                    value = { it.tries },
                    valueText = ::triesText,
                )
            }

            // ---- Last 7 days: who and why ---------------------------------------------------
            if (stats.topApps.isNotEmpty()) {
                item {
                    RankedBars(
                        title = "Most tried apps",
                        rows = stats.topApps.take(5).map { it.label to it.tries },
                    )
                }
            }
            if (stats.byReason.isNotEmpty()) {
                item {
                    RankedBars(
                        title = "Why Detox stepped in",
                        rows = stats.byReason.map { (reason, tries) -> reason.label to tries },
                    )
                }
            }
            if (stats.days.all { it.tries == 0 }) {
                item {
                    Text(
                        "No tries in the last 7 days yet. Detox started counting with this update.",
                        style = type.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            item {
                Text(
                    "A try = you opened a paused app and Detox stopped you, a mindful pause, or Reels/Shorts " +
                        "closed by the guard. Opening the same app again within 5 seconds counts once. " +
                        "Kept for 90 days, only on this phone. Days start at your \"new day\" time.",
                    style = type.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

// ---- Pieces ----------------------------------------------------------------------------------

@Composable
private fun StatsCard(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) { content() }
    }
}

/** A headline number: "Last night / 2 / tries". The number is the point, so it's big; the rest is quiet. */
@Composable
private fun BigNumber(title: String, number: Int, caption: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
            Text(
                "$number",
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onPrimaryContainer,
            )
            Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.onPrimaryContainer)
        }
    }
}

/**
 * 7 vertical bars, one per day, all in the accent green (one series -> no legend, the title names it).
 * Tap a bar to read it; today is selected at the start. The readout line above the chart works like a
 * tooltip on a phone (there is no mouse to hover with).
 */
@Composable
private fun DayBars(
    title: String,
    days: List<DayStat>,
    value: (DayStat) -> Int,
    valueText: (Int) -> String,
) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    var selected by remember(days) { mutableIntStateOf(days.lastIndex) }
    val max = (days.maxOfOrNull(value) ?: 0).coerceAtLeast(1)
    val barColor = colors.primary            // Forest green: 3:1+ against white (checked with the dataviz validator)
    val baseColor = colors.outlineVariant    // recessive baseline
    val markColor = colors.outline           // the selected day's thin underline

    // A screen reader gets the whole chart as one sentence (the "table view" of this chart).
    val spoken = days.joinToString(prefix = "$title, last 7 days: ") { d ->
        "${d.day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${valueText(value(d))}"
    }

    StatsCard {
        Text(title, style = type.titleMedium)
        val day = days.getOrNull(selected)
        if (day != null) {
            val dayName = if (selected == days.lastIndex) "Today" else day.day.format(DAY_TITLE)
            Text("$dayName · ${valueText(value(day))}", style = type.bodySmall, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .semantics { contentDescription = spoken }
                // The tap target is the whole column of a day, not only the (maybe tiny) bar.
                .pointerInput(days) {
                    detectTapGestures { offset ->
                        val slot = size.width / days.size
                        selected = (offset.x / slot).toInt().coerceIn(0, days.lastIndex)
                    }
                },
        ) {
            val slot = size.width / days.size
            val barWidth = (slot * 0.42f).coerceAtMost(28.dp.toPx())
            val radius = 4.dp.toPx()
            val top = 6.dp.toPx()                     // room so the tallest bar's round top isn't cut
            val chartHeight = size.height - top - 2.dp.toPx()
            days.forEachIndexed { i, d ->
                val v = value(d)
                if (v <= 0) return@forEachIndexed     // zero = no bar, only the baseline
                val h = (chartHeight * v / max).coerceAtLeast(2.dp.toPx())
                val left = slot * i + (slot - barWidth) / 2
                val bottom = size.height - 2.dp.toPx()
                // Round only the top: the bar stands on the baseline (rounded data end, square base).
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left, top = bottom - h, right = left + barWidth, bottom = bottom,
                            topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                            bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero,
                        )
                    )
                }
                drawPath(path, barColor)
            }
            // Baseline, then a short darker mark under the selected day.
            drawRect(baseColor, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = Size(size.width, 1.dp.toPx()))
            drawRect(
                markColor,
                topLeft = Offset(slot * selected + slot * 0.2f, size.height - 2.dp.toPx()),
                size = Size(slot * 0.6f, 2.dp.toPx()),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, d ->
                val label = if (i == days.lastIndex) "Today"
                else d.day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                Text(
                    label,
                    style = type.labelSmall.copy(fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal),
                    color = if (i == selected) colors.onSurface else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * "Instagram ▬▬▬▬▬ 12": a ranked list with thin bars. The number is always written
 * (bars alone are hard to read exactly), in text colour, never in the bar colour.
 */
@Composable
private fun RankedBars(title: String, rows: List<Pair<String, Int>>) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val max = (rows.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    StatsCard {
        Text(title, style = type.titleMedium)
        Text("Last 7 days", style = type.bodySmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        rows.forEach { (label, count) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = type.bodyMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
                Box(Modifier.width(96.dp)) {
                    Box(
                        Modifier
                            .width(96.dp * (count.toFloat() / max))
                            .height(8.dp)
                            .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                            .background(colors.primary)
                    )
                }
                Text(
                    "$count",
                    style = type.labelLarge,
                    color = colors.onSurface,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(36.dp),
                )
            }
        }
    }
}

private fun triesText(n: Int): String = if (n == 1) "1 try" else "$n tries"

private fun minutesText(min: Int): String = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${min / 60} h ${min % 60} min"
}
