package com.detox.app.ui.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.detox.app.data.InstalledApp
import com.detox.app.data.InstalledApps
import com.detox.app.domain.PausedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen list of installed apps. Tick an app -> it is paused (closed during quiet times,
 * after daily limits and all day in strict mode).
 * "Mindful pause" is softer: the app still opens, but only after a 5-second breath.
 * Every change is saved to the database immediately (no "Save" step to forget).
 */
@Composable
fun AppPickerScreen(
    paused: List<PausedApp>,
    mindful: Set<String>,           // package names with mindful pause on
    onToggle: (packageName: String, label: String, paused: Boolean) -> Unit,
    onSetLimit: (packageName: String, minutes: Int?) -> Unit,
    onSetMindful: (packageName: String, label: String, mindful: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    // null while loading. withContext(Dispatchers.Default) = do the slow icon work off the main thread.
    val installed by produceState<List<InstalledApp>?>(initialValue = null) {
        value = withContext(Dispatchers.Default) { InstalledApps.load(context) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val pausedByPackage = paused.associateBy { it.packageName }
    val pausedPackages = pausedByPackage.keys

    // System back gesture closes the picker instead of the whole app.
    BackHandler(onBack = onClose)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        Column(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding())) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Your apps", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${pausedPackages.size} paused · ${mindful.size} with mindful pause",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onClose, shape = RoundedCornerShape(50)) {
                    Text("Done", style = MaterialTheme.typography.labelLarge)
                }
            }
            // The two kinds of "slow down", explained once at the top instead of on every row.
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp)) {
                Text(
                    "Tick = paused: closed in quiet times, after your limits and all day in strict mode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Mindful pause: the app opens after a 5-second breath. It isn't blocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))

            val list = installed
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val installedPackages = list.map { it.packageName }.toSet()
                // Paused apps that are no longer installed still appear, so you can un-tick them.
                val missing = paused.filter { it.packageName !in installedPackages }
                val matches: (String) -> Boolean = { label -> query.isBlank() || label.contains(query, ignoreCase = true) }

                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 12.dp, end = 12.dp, top = 4.dp,
                        bottom = insets.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(missing.filter { matches(it.name) }, key = { "missing-" + it.packageName }) { app ->
                        AppRow(
                            label = app.name,
                            subtitle = "Not installed",
                            icon = null,
                            checked = true,
                            onCheckedChange = { onToggle(app.packageName, app.name, it) },
                            limitMin = app.dailyLimitMin,
                            onLimitChange = { onSetLimit(app.packageName, it) },
                            // Not installed: nothing to open, so no mindful chip.
                            mindfulOn = false,
                            onMindfulChange = null,
                        )
                    }
                    items(list.filter { matches(it.label) }, key = { it.packageName }) { app ->
                        val pausedApp = pausedByPackage[app.packageName]
                        AppRow(
                            label = app.label,
                            subtitle = null,
                            icon = app,
                            checked = pausedApp != null,
                            onCheckedChange = { onToggle(app.packageName, app.label, it) },
                            limitMin = pausedApp?.dailyLimitMin,
                            // The own-limit menu only makes sense for apps that are paused.
                            onLimitChange = if (pausedApp != null) { minutes -> onSetLimit(app.packageName, minutes) } else null,
                            // Mindful pause works for ANY app, paused or not.
                            mindfulOn = app.packageName in mindful,
                            onMindfulChange = { on -> onSetMindful(app.packageName, app.label, on) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppRow(
    label: String,
    subtitle: String?,
    icon: InstalledApp?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    limitMin: Int?,
    onLimitChange: ((Int?) -> Unit)?,
    mindfulOn: Boolean,
    onMindfulChange: ((Boolean) -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            // remember: convert the Android Bitmap to a Compose image once, not on every redraw.
            val image = remember(icon.packageName) { icon.icon.asImageBitmap() }
            Image(bitmap = image, contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onMindfulChange != null || onLimitChange != null) {
                Spacer(Modifier.height(6.dp))
                // FlowRow = a Row that wraps to the next line on narrow screens.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (onMindfulChange != null) MindfulChip(on = mindfulOn, onChange = onMindfulChange)
                    if (onLimitChange != null) LimitChooser(limitMin = limitMin, onChange = onLimitChange)
                }
            }
        }
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Small pill: "Mindful pause: off" / "Mindful pause: on". Tapping it only ASKS for the change:
 * switching it off goes through the countdown in MainActivity, so the pill may stay "on" until then.
 */
@Composable
private fun MindfulChip(on: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = { onChange(!on) },
        shape = RoundedCornerShape(50),
        color = if (on) colors.primary else colors.surface,
        contentColor = if (on) colors.onPrimary else colors.primary,
        border = if (on) null else BorderStroke(1.dp, colors.outline),
    ) {
        Text(
            if (on) "Mindful pause: on" else "Mindful pause: off",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Choices for one app's own limit (minutes per day). */
private val APP_LIMIT_CHOICES = listOf(10, 15, 20, 30, 45, 60, 90)

/** Small "No own limit ▾" text that opens a menu. */
@Composable
private fun LimitChooser(limitMin: Int?, onChange: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            text = (limitMin?.let { "Own limit: $it min a day" } ?: "No own limit") + "  ▾",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(vertical = 4.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("No own limit") }, onClick = { onChange(null); open = false })
            APP_LIMIT_CHOICES.forEach { minutes ->
                DropdownMenuItem(text = { Text("$minutes min a day") }, onClick = { onChange(minutes); open = false })
            }
        }
    }
}
