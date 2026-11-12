package com.detox.app.ui.pause

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.detox.app.ui.theme.DetoxTheme
import com.detox.app.ui.theme.ForestDeep
import com.detox.app.ui.theme.NightBottom
import com.detox.app.ui.theme.NightTop
import kotlinx.coroutines.delay

/**
 * Calm green page with a slowly "breathing" circle.
 * The healthy choice ("Not now") is the big white button; opening the app is the small
 * text button, and it only works after the countdown.
 *
 * @param restartKey change it to start the countdown over (a new request came in).
 */
@Composable
fun PauseScreen(
    appLabel: String,
    seconds: Int,
    restartKey: Int,
    onOpen: () -> Unit,
    onNotNow: () -> Unit,
) {
    // Countdown: remember(restartKey) gives a fresh counter for every new request.
    var left by remember(restartKey) { mutableIntStateOf(seconds) }
    LaunchedEffect(restartKey) {
        while (left > 0) {
            delay(1_000)
            left--
        }
    }

    // The circle grows for 4 s (breathe in) and shrinks for 4 s (breathe out), forever.
    val breath = rememberInfiniteTransition(label = "breath")
    val scale by breath.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathScale",
    )

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
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(200.dp)
                        .scale(scale)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                )
                Box(
                    Modifier
                        .size(120.dp)
                        .scale(scale)
                        .background(Color.White.copy(alpha = 0.14f), CircleShape)
                )
            }
            Spacer(Modifier.height(32.dp))
            Text("Take a breath", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(12.dp))
            Text(
                "Still want to open $appLabel?",
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
                onClick = onNotNow,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ForestDeep),
            ) {
                Text("Not now", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onOpen,
                enabled = left == 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    if (left == 0) "Open $appLabel" else "Open $appLabel in $left s",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = if (left == 0) 0.9f else 0.45f),
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun PauseScreenPreview() {
    DetoxTheme {
        PauseScreen(appLabel = "YouTube", seconds = 5, restartKey = 0, onOpen = {}, onNotNow = {})
    }
}
