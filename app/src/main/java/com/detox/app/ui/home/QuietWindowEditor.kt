package com.detox.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.detox.app.domain.QuietWindow
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class TimeField { START, END }

/**
 * Bottom sheet to create or edit a quiet time.
 * [initial] = null -> "New quiet time"; otherwise edit that one (and allow Delete).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietWindowEditor(
    initial: QuietWindow?,
    onSave: (QuietWindow) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var start by remember { mutableStateOf(initial?.start ?: LocalTime.of(22, 0)) }
    var end by remember { mutableStateOf(initial?.end ?: LocalTime.of(7, 0)) }
    var days by remember { mutableStateOf(initial?.days ?: QuietWindow.EVERY_DAY) }
    var picking by remember { mutableStateOf<TimeField?>(null) }   // which clock dialog is open

    val sameTime = start == end
    val canSave = days.isNotEmpty() && !sameTime

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(
                if (initial == null) "New quiet time" else "Edit quiet time",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Paused apps stay closed during this time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                label = { Text("Name") },
                placeholder = { Text("e.g. Night") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeBox("Starts", start, Modifier.weight(1f)) { picking = TimeField.START }
                TimeBox("Ends", end, Modifier.weight(1f)) { picking = TimeField.END }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    sameTime -> "Start and end can't be the same."
                    end < start -> "Crosses midnight: ends the next morning."
                    else -> "Starts and ends on the same day."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (sameTime) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(18.dp))
            Text("Days", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.values().forEach { day ->
                    DayChip(day = day, selected = day in days, modifier = Modifier.weight(1f)) {
                        days = if (day in days) days - day else days + day
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    onSave(
                        QuietWindow(
                            id = initial?.id ?: 0,               // 0 -> database creates a new row
                            name = name.trim().ifEmpty { "Quiet time" },
                            kind = QuietWindow.Kind.forStart(start),
                            start = start,
                            end = end,
                            days = days,
                            enabled = initial?.enabled ?: true,
                        )
                    )
                },
                enabled = canSave,
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete this quiet time", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    picking?.let { field ->
        TimeDialog(
            initial = if (field == TimeField.START) start else end,
            onConfirm = { time ->
                if (field == TimeField.START) start = time else end = time
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Shared with LimitEditor ("internal" = visible inside this app module only). */
@Composable
internal fun TimeBox(label: String, time: LocalTime, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(time.format(HHMM), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DayChip(day: DayOfWeek, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.primary else colors.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).take(2),   // "Mo", "Tu", ...
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
        )
    }
}

/** Material clock dial in a dialog, 24-hour format. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
