package com.detox.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.detox.app.R
import kotlinx.coroutines.delay

/**
 * "Wait a moment" dialog before loosening a rule.
 * The Confirm button unlocks only after [seconds]. If you leave the app ([appActive] = false),
 * the countdown starts over: you can't wait it out in Instagram.
 */
@Composable
fun CountdownDialog(
    what: String,
    seconds: Int,
    appActive: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    var left by remember(what) { mutableIntStateOf(seconds) }

    // Re-runs whenever appActive changes: leaving the app resets, coming back counts down again.
    LaunchedEffect(appActive, what) {
        if (!appActive) {
            left = seconds
            return@LaunchedEffect
        }
        while (left > 0) {
            delay(1_000)
            left--
        }
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(painterResource(R.drawable.ic_leaf), contentDescription = null, modifier = Modifier.size(56.dp, 40.dp))
                Spacer(Modifier.height(12.dp))
                Text("Take a breath", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "You're about to $what. If you still want to when the timer ends, tap Confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
                    CircularProgressIndicator(
                        progress = { left.toFloat() / seconds },
                        modifier = Modifier.size(132.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                        strokeWidth = 8.dp,
                    )
                    Text(
                        "%d:%02d".format(left / 60, left % 60),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Leave this screen and the timer starts over.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("Keep my rule", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = left == 0,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Confirm", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
