package com.detox.app.ui.launcher

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.detox.app.R
import com.detox.app.domain.AppPref
import com.detox.app.domain.HomePrefs
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_24 = DateTimeFormatter.ofPattern("HH:mm")
private val TIME_12 = DateTimeFormatter.ofPattern("h:mm")
private val DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

/** "1 h 12 min on your phone today". */
internal fun screenTimeText(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "Under a minute on your phone today"
        minutes < 60 -> "$minutes min on your phone today"
        minutes % 60 == 0L -> "${minutes / 60} h on your phone today"
        else -> "${minutes / 60} h ${minutes % 60} min on your phone today"
    }
}

/**
 * Page 1: the calm front page.
 *
 *   07:42                      <- tap: alarms / clock app
 *   Thursday, 12 November      <- tap: calendar
 *   1 h 12 min on your phone   <- tap: Detox
 *   Strict mode · ...
 *   "Quote of the day"         <- tap: next quote
 *
 *   Telegram ... (5 apps)
 *
 *   [phone]    • •    [camera]
 *
 * Gestures on empty space: swipe down = notifications, double-tap = lock the screen.
 */
@Composable
fun HomePage(
    look: HomeLook,
    now: LocalDateTime,
    status: String,
    screenTimeMs: Long?,
    prefs: HomePrefs,
    home: List<HomeApp>?,               // null = still loading
    appPrefs: Map<String, AppPref>,
    pageCount: Int,
    currentPage: Int,
    actions: HomeActions,
) {
    val context = LocalContext.current
    val timeFormat = if (DateFormat.is24HourFormat(context)) TIME_24 else TIME_12
    // Tapping the quote shows the next one. Saved, so it survives turning the phone sideways.
    var quoteOffset by rememberSaveable { mutableIntStateOf(0) }
    val quote = Quotes.forDay(now.toLocalDate(), quoteOffset)

    // The gesture code below is started once; always call the newest settings and actions.
    val swipeOn by rememberUpdatedState(prefs.swipeDownNotifications)
    val doubleTapOn by rememberUpdatedState(prefs.doubleTapLock)
    val currentActions by rememberUpdatedState(actions)

    Box(
        Modifier
            .fillMaxSize()
            // Double-tap on empty space -> lock. Taps on an app name or the clock are used by
            // those texts themselves, so they never reach this detector.
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { if (doubleTapOn) currentActions.lockScreen() })
            }
            // Swipe DOWN -> notifications. A sideways swipe is left to the pager (page 2).
            .pointerInput(Unit) {
                val threshold = 64.dp.toPx()
                var pulled = 0f
                var fired = false
                detectVerticalDragGestures(
                    onDragStart = {
                        pulled = 0f
                        fired = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        pulled += dragAmount          // positive = finger moves down
                        if (!fired && pulled > threshold && swipeOn) {
                            fired = true              // once per swipe, not on every pixel
                            currentActions.openNotifications()
                        }
                        change.consume()
                    },
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            Text(
                now.format(timeFormat),
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Light, fontSize = 72.sp),
                color = look.ink,
                modifier = Modifier.clickable(onClick = actions.openClock),
            )
            Text(
                now.format(DATE),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                color = look.soft,
                modifier = Modifier.clickable(onClick = actions.openCalendar),
            )
            if (prefs.screenTimeOn && screenTimeMs != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    screenTimeText(screenTimeMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = look.soft,
                    modifier = Modifier.clickable(onClick = actions.openDetox),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium, color = look.soft)

            if (prefs.quoteOn) {
                Spacer(Modifier.height(28.dp))
                Column(Modifier.clickable { quoteOffset++ }) {
                    Text(
                        "“${quote.text}”",
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        color = look.ink,
                    )
                    quote.author?.let {
                        Text("— $it", style = MaterialTheme.typography.bodySmall, color = look.soft)
                    }
                }
            }

            // Push the apps into the lower half: easier to reach with a thumb.
            Spacer(Modifier.weight(1f))

            if (home != null) {
                if (home.isEmpty()) {
                    Text(
                        "Swipe left for all apps. Long-press one → Add to home.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = look.soft,
                    )
                } else {
                    home.forEach { item ->
                        AppRow(
                            app = item.entry,
                            pref = appPrefs[item.entry.packageName],
                            look = look,
                            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Normal),
                            actions = actions,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(0.5f))
            BottomRow(look, pageCount, currentPage, actions)
        }
    }
}

/** Phone on the left, page dots in the middle, Camera on the right. Plain line icons, no colour. */
@Composable
private fun BottomRow(look: HomeLook, pageCount: Int, currentPage: Int, actions: HomeActions) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.openPhone) {
            Icon(painterResource(R.drawable.ic_phone), contentDescription = "Phone", tint = look.ink)
        }
        PageDots(look, pageCount, currentPage)
        IconButton(onClick = actions.openCamera) {
            Icon(painterResource(R.drawable.ic_camera), contentDescription = "Camera", tint = look.ink)
        }
    }
}

/** Two small dots: which page you're on. */
@Composable
internal fun PageDots(look: HomeLook, pageCount: Int, currentPage: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pageCount) { page ->
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (page == currentPage) look.ink else look.soft.copy(alpha = 0.5f)),
            )
        }
    }
}
