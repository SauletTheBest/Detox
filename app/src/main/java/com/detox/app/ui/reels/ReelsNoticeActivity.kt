package com.detox.app.ui.reels

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.detox.app.R
import com.detox.app.ui.theme.DetoxTheme
import com.detox.app.ui.theme.ForestDeep
import com.detox.app.ui.theme.NightBottom
import com.detox.app.ui.theme.NightTop
import android.graphics.Color as AndroidColor

/**
 * "Reels are paused" notice, shown by ReelsGuardService right AFTER it pressed Back.
 *
 *   OK, back to Instagram  -> Instagram comes back, on the screen before Reels (feed, DMs...)
 *   Close Instagram        -> home screen
 *
 * Why Back first, notice second? If the notice opened on top of Reels, "OK" would land you
 * in Reels again, the guard would fire again, and you'd be stuck in a loop.
 */
class ReelsNoticeActivity : ComponentActivity() {

    private var appPackage by mutableStateOf("")   // the app to return to (Instagram / YouTube)
    private var appLabel by mutableStateOf("")
    private var feedName by mutableStateOf("Reels")   // "Reels" or "Shorts"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // White status-bar icons on the dark green background.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        readExtras(intent)
        // Back = OK: you continue in the app (not in Reels: the guard already left it).
        onBackPressedDispatcher.addCallback(this) { backToApp() }

        setContent {
            DetoxTheme {
                ReelsNoticeScreen(
                    appLabel = appLabel,
                    feedName = feedName,
                    onOk = ::backToApp,
                    onClose = ::closeApp,
                )
            }
        }
    }

    // singleTask: already open? Android reuses it and hands us the new request here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readExtras(intent)
    }

    private fun readExtras(intent: Intent) {
        appPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        appLabel = intent.getStringExtra(EXTRA_LABEL) ?: "the app"
        feedName = intent.getStringExtra(EXTRA_FEED) ?: "Reels"
    }

    /** Bring the app back exactly where it was (the screen before Reels). */
    private fun backToApp() {
        packageManager.getLaunchIntentForPackage(appPackage)?.let { launch ->
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }

    /** Go to the home screen. */
    private fun closeApp() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_FEED = "feed"

        /** Used by ReelsGuardService. A service has no screen, so the notice starts in a new task. */
        fun noticeIntent(context: Context, packageName: String, label: String, feedName: String): Intent =
            Intent(context, ReelsNoticeActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_FEED, feedName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Same calm look as the block and pause screens: deep green, the leaf, one big white button. */
@Composable
fun ReelsNoticeScreen(appLabel: String, feedName: String, onOk: () -> Unit, onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NightTop, NightBottom)))
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_leaf),
                contentDescription = null,
                modifier = Modifier.size(width = 84.dp, height = 60.dp),
            )
            Spacer(Modifier.height(28.dp))
            Text("$feedName are paused", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(
                "$appLabel is still open for messages, stories and posts. Just not the endless feed.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onOk,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ForestDeep),
            ) {
                Text("OK, back to $appLabel", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Close $appLabel", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun ReelsNoticePreview() {
    DetoxTheme { ReelsNoticeScreen(appLabel = "Instagram", feedName = "Reels", onOk = {}, onClose = {}) }
}
