package com.detox.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Android 13+ greys out the Accessibility switch for apps installed from a file
 * ("Restricted setting"). Only the user can unlock it, in App info. We explain how.
 */
@Composable
fun RestrictedSettingsDialog(onOpenAppInfo: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Android locked this switch") },
        text = {
            Text(
                "Android protects the Accessibility switch for apps that didn't come from a store. " +
                    "You can unlock it in two steps:\n\n" +
                    "1. Open App info (button below).\n" +
                    "2. Tap ⋮ in the top-right corner, then \"Allow restricted settings\".\n\n" +
                    "Then come back and switch protection on again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = { TextButton(onClick = onOpenAppInfo) { Text("Open App info") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } },
    )
}

/** Shown when strict mode was switched on, but Android's "home app" question was answered with no. */
@Composable
fun StrictNeedsHomeDialog(onTryAgain: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Detox needs to be your home app") },
        text = {
            Text(
                "Strict mode swaps your home screen for a calm text list. Without it, Home still opens " +
                    "your normal launcher with all its colorful icons, so strict mode stays off for now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = { TextButton(onClick = onTryAgain) { Text("Try again") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
