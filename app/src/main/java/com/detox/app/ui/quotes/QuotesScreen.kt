package com.detox.app.ui.quotes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.detox.app.domain.QuoteBook
import com.detox.app.domain.QuoteItem

/** Which quotes the list shows. Only a view filter: Detox Home uses the "only ♥" switch instead. */
private enum class QuoteFilter(val label: String) { ALL("All"), LIKED("♥ Liked"), MINE("Mine"), HIDDEN("Hidden") }

/**
 * Your quotes: the built-in ones plus your own.
 *   ♥        = you like it (with "Only ♥ and my own" on, Detox Home shows just these + yours)
 *   Hide     = a built-in quote you never want to see again (Unhide brings it back)
 *   Delete   = one of YOUR quotes (asks first)
 * Quotes are looks, not rules: every change is instant, no countdown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuotesScreen(
    items: List<QuoteItem>,
    onlyLiked: Boolean,
    onSetOnlyLiked: (Boolean) -> Unit,
    onAdd: (text: String, author: String?) -> Unit,
    onToggleLiked: (QuoteItem) -> Unit,
    onToggleHidden: (QuoteItem) -> Unit,   // built-in quotes only
    onDelete: (QuoteItem) -> Unit,         // your own quotes only
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    var filter by rememberSaveable { mutableStateOf(QuoteFilter.ALL) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<QuoteItem?>(null) }

    fun matches(item: QuoteItem, f: QuoteFilter) = when (f) {
        QuoteFilter.ALL -> !item.hidden
        QuoteFilter.LIKED -> item.liked && !item.hidden
        QuoteFilter.MINE -> item.isOwn
        QuoteFilter.HIDDEN -> item.hidden
    }
    val shown = items.filter { matches(it, filter) }

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
                    Text("Quotes", style = type.headlineMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, colors.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                    ) { Text("Done") }
                }
            }

            // ---- What Detox Home shows ----
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.outlineVariant),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Only ♥ and my own", style = type.titleSmall)
                            Text(
                                "The quote of the day on Detox Home comes only from quotes you liked or wrote. " +
                                    "Off: from all quotes except hidden ones.",
                                style = type.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Switch(checked = onlyLiked, onCheckedChange = onSetOnlyLiked)
                    }
                }
            }
            item {
                Button(
                    onClick = { adding = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(50),
                ) { Text("+ Add your own quote", style = type.labelLarge) }
            }

            // ---- Filter chips with counts ----
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuoteFilter.entries.forEach { f ->
                        val selected = f == filter
                        Surface(
                            onClick = { filter = f },
                            shape = RoundedCornerShape(50),
                            color = if (selected) colors.primary else colors.surface,
                            contentColor = if (selected) colors.onPrimary else colors.primary,
                            border = if (selected) null else BorderStroke(1.dp, colors.outline),
                        ) {
                            Text(
                                "${f.label} ${items.count { matches(it, f) }}",
                                style = type.labelLarge,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            if (shown.isEmpty()) {
                item {
                    Text(
                        when (filter) {
                            QuoteFilter.LIKED -> "No ♥ yet. Tap ♡ on a quote you like."
                            QuoteFilter.MINE -> "Nothing yet. Add a line that helps you put the phone down."
                            QuoteFilter.HIDDEN -> "No hidden quotes."
                            QuoteFilter.ALL -> "No quotes to show."
                        },
                        style = type.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            // key = a stable name per quote, so Compose keeps the right card when the list changes.
            items(shown, key = { if (it.isOwn) "own:${it.ownId}" else "builtin:${it.text}" }) { item ->
                QuoteCard(
                    item = item,
                    onToggleLiked = { onToggleLiked(item) },
                    onToggleHidden = { onToggleHidden(item) },
                    onDelete = { toDelete = item },
                )
            }
        }
    }

    if (adding) {
        AddQuoteDialog(
            onSave = { text, author ->
                onAdd(text, author)
                adding = false
                filter = QuoteFilter.MINE      // show it right away
            },
            onDismiss = { adding = false },
        )
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this quote?") },
            text = { Text("“${item.text}”\n\nIt's yours, so it can't be brought back. You can add it again any time.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(item)
                    toDelete = null
                }) { Text("Delete", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun QuoteCard(item: QuoteItem, onToggleLiked: () -> Unit, onToggleHidden: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val ink = if (item.hidden) colors.onSurfaceVariant else colors.onSurface
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (item.isOwn) colors.surfaceVariant else colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Text("“${item.text}”", style = type.bodyLarge.copy(fontStyle = FontStyle.Italic), color = ink)
            val byline = listOfNotNull(item.author?.let { "— $it" }, if (item.isOwn) "yours" else null).joinToString("  ·  ")
            if (byline.isNotEmpty()) {
                Text(byline, style = type.bodySmall, color = colors.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.hidden) {
                    TextButton(onClick = onToggleLiked) {
                        Text(if (item.liked) "♥ Liked" else "♡ Like", color = colors.primary)
                    }
                }
                Spacer(Modifier.weight(1f))
                if (item.isOwn) {
                    TextButton(onClick = onDelete) { Text("Delete", color = colors.error) }
                } else {
                    TextButton(onClick = onToggleHidden) {
                        Text(if (item.hidden) "Unhide" else "Hide", color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Two fields: the quote (required) and who said it (optional). Limits from QuoteBook. */
@Composable
private fun AddQuoteDialog(onSave: (String, String?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var author by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your quote") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(QuoteBook.MAX_TEXT) },
                    label = { Text("Quote") },
                    supportingText = { Text("${text.length}/${QuoteBook.MAX_TEXT}") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it.take(QuoteBook.MAX_AUTHOR) },
                    label = { Text("Who said it (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text, author) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
