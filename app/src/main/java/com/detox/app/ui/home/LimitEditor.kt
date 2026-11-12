package com.detox.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.detox.app.domain.LimitSettings

/** Choices for the shared daily budget (minutes). */
private val LIMIT_CHOICES = listOf(15, 30, 45, 60, 90, 120)

/** Bottom sheet: how many minutes per day all paused apps may use together, and when a day starts. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LimitEditor(
    initial: LimitSettings,
    onSave: (LimitSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableStateOf(initial.dailyLimitMin) }
    var dayStart by remember { mutableStateOf(initial.dayStart) }
    var pickingTime by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Daily limit", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "One shared budget for all paused apps. When it's used up, they stay closed until a new day starts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Text("Minutes per day", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LIMIT_CHOICES.forEach { choice ->
                    ChoicePill(text = "$choice min", selected = choice == minutes) { minutes = choice }
                }
            }
            Spacer(Modifier.height(20.dp))
            TimeBox("A new day starts at", dayStart, Modifier.fillMaxWidth()) { pickingTime = true }
            Spacer(Modifier.height(6.dp))
            Text(
                "Not midnight on purpose: a fresh budget at 00:00 would arrive right when late-night scrolling starts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSave(initial.copy(dailyLimitMin = minutes, dayStart = dayStart, dailyLimitOn = true)) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Save and turn on", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (pickingTime) {
        TimeDialog(
            initial = dayStart,
            onConfirm = { dayStart = it; pickingTime = false },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun ChoicePill(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) colors.primary else colors.surface,
        contentColor = if (selected) colors.onPrimary else colors.primary,
        border = if (selected) null else BorderStroke(1.dp, colors.outline),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}
