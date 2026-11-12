package com.detox.app.ui.pause

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.detox.app.domain.Mindful
import com.detox.app.domain.MindfulGate
import com.detox.app.ui.theme.DetoxTheme
import android.graphics.Color as AndroidColor

/**
 * "Take a breath" screen for apps you marked mindful.
 * Opened by AppWatcherService when such an app comes to the front.
 *
 *   wait 5 s -> "Open YouTube"  -> the gate lets YouTube through for 10 minutes
 *            -> "Not now"       -> home screen
 *
 * Unlike the block screen, this one always lets you in. It only makes opening the app
 * a choice instead of a reflex.
 */
class PauseActivity : ComponentActivity() {

    private var packageToOpen by mutableStateOf("")
    private var appLabel by mutableStateOf("")

    // Bumped for every new request, so the 5-second countdown starts over
    // (e.g. you said "Not now" and tried again a minute later).
    private var requestId by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // White status-bar icons on the dark green background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        readExtras(intent)

        // Back = "Not now". It must not slip you into the app behind this screen.
        onBackPressedDispatcher.addCallback(this) { notNow() }

        setContent {
            DetoxTheme {
                PauseScreen(
                    appLabel = appLabel,
                    seconds = MindfulGate.PAUSE_SECONDS,
                    restartKey = requestId,
                    onOpen = ::openApp,
                    onNotNow = ::notNow,
                )
            }
        }
    }

    // singleTask: if the pause screen is already open, Android reuses it and delivers the
    // new request here (maybe for a different app).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras(intent)
        requestId++
    }

    private fun readExtras(intent: Intent) {
        packageToOpen = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        appLabel = intent.getStringExtra(EXTRA_LABEL) ?: "this app"
    }

    /** You waited and still want it: let it through for the next 10 minutes, then open it. */
    private fun openApp() {
        Mindful.gate.allow(packageToOpen, System.currentTimeMillis())
        packageManager.getLaunchIntentForPackage(packageToOpen)?.let { launch ->
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    /** Go to the home screen, not back into the app. */
    private fun notNow() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_LABEL = "label"

        /** Used by AppWatcherService. A service has no screen, so the page starts in a new task. */
        fun pauseIntent(context: Context, packageName: String, label: String): Intent =
            Intent(context, PauseActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_LABEL, label)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
