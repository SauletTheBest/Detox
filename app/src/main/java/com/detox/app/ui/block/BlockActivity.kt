package com.detox.app.ui.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.detox.app.domain.Decision
import com.detox.app.domain.Reason
import com.detox.app.ui.theme.DetoxTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.graphics.Color as AndroidColor

/**
 * Full-screen "time to rest" page.
 * Opened by AppWatcherService when a paused app is blocked (quiet time, strict mode or
 * daily limit), or from the Home screen as a preview.
 */
class BlockActivity : ComponentActivity() {

    // mutableStateOf: when these change, the screen redraws by itself.
    private var title by mutableStateOf("Time to rest")
    private var message by mutableStateOf("")
    private var chip by mutableStateOf("")
    private var isPreview by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light (white) status-bar icons on our dark green background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        readExtras(intent)

        // The back gesture must NOT take you back into the paused app.
        onBackPressedDispatcher.addCallback(this) { leave() }

        setContent {
            DetoxTheme {
                BlockScreen(
                    title = title,
                    message = message,
                    chip = chip,
                    buttonText = if (isPreview) "Close preview" else "Go to home screen",
                    onLeave = ::leave,
                )
            }
        }
    }

    // launchMode="singleTask": if the block screen is already open, Android reuses it
    // and delivers the new request here (e.g. you tried YouTube right after Instagram).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras(intent)
    }

    private fun readExtras(intent: Intent) {
        title = intent.getStringExtra(EXTRA_TITLE) ?: "Time to rest"
        message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        chip = intent.getStringExtra(EXTRA_CHIP) ?: ""
        isPreview = intent.getBooleanExtra(EXTRA_PREVIEW, false)
    }

    private fun leave() {
        if (!isPreview) {
            // Real block: go to the home screen (launcher), not back to Instagram.
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_CHIP = "chip"
        private const val EXTRA_PREVIEW = "preview"
        private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** Used by AppWatcherService: turns the decision into the texts on the screen. */
        fun blockIntent(context: Context, decision: Decision.Block): Intent {
            val app = decision.app.name
            // "07:00", or "05:00 tomorrow" if it's on the next day.
            // until = null only in strict mode (no clock time), which doesn't use this text.
            val until = decision.until?.let { time ->
                time.toLocalTime().format(HHMM) +
                    if (time.toLocalDate().isAfter(LocalDate.now())) " tomorrow" else ""
            } ?: "later"
            val (title, message, chip) = when (val reason = decision.reason) {
                is Reason.QuietTime -> Triple(
                    "Time to rest",
                    "$app is taking a break until $until. You can too.",
                    "${reason.window.name} quiet time",
                )
                Reason.Strict -> Triple(
                    "Strict mode is on",
                    "$app stays closed while strict mode is on.",
                    "Until you turn strict mode off",
                )
                is Reason.TotalLimit -> Triple(
                    "That's enough for today",
                    "You've used your ${reason.minutes} minutes on paused apps. $app opens again at $until.",
                    "Daily limit · ${reason.minutes} min",
                )
                is Reason.AppLimit -> Triple(
                    "That's enough for today",
                    "You've had your ${reason.minutes} minutes of $app. It opens again at $until.",
                    "$app limit · ${reason.minutes} min",
                )
            }
            return Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE, message)
                .putExtra(EXTRA_CHIP, chip)
                // A service has no screen of its own, so it must start the page in a new task.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        /** Opened by the "Preview the block screen" button. */
        fun previewIntent(context: Context): Intent =
            Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_TITLE, "Time to rest")
                .putExtra(EXTRA_MESSAGE, "Instagram is taking a break until 07:00. You can too.")
                .putExtra(EXTRA_CHIP, "Night quiet time")
                .putExtra(EXTRA_PREVIEW, true)
    }
}
