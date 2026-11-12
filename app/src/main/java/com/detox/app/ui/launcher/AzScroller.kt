package com.detox.app.ui.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** First letter used for the A–Z scroller. Digits, emoji etc. all go under "#". */
internal fun letterOf(label: String): Char {
    val c = label.trim().firstOrNull()?.uppercaseChar() ?: return '#'
    return if (c.isLetter()) c else '#'
}

/**
 * The thin letter column on the right edge of the search page.
 * Touch a letter (or slide along the column) and the list jumps to the first app with that letter.
 *
 * Every letter gets the same height, so "which letter is under the finger" is simple maths:
 *   index = finger y / column height * number of letters
 */
@Composable
fun AzScroller(
    letters: List<Char>,
    ink: Color,
    soft: Color,
    onLetter: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    var heightPx by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf<Char?>(null) }
    val haptics = LocalHapticFeedback.current
    // The gesture code below lives longer than one redraw; always call the newest callback.
    val currentOnLetter by rememberUpdatedState(onLetter)

    fun pick(y: Float) {
        if (letters.isEmpty() || heightPx == 0) return
        val index = (y / heightPx * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[index]
        if (letter != active) {
            active = letter
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)   // a tiny tick per letter
            currentOnLetter(letter)
        }
    }

    // BoxWithConstraints tells us how much height we may use. Normally every letter gets 18 dp;
    // when space is short (small screen), the letters squeeze together so they all still fit.
    BoxWithConstraints(modifier) {
        val rowHeight = minOf(18.dp, maxHeight / letters.size.coerceAtLeast(1))
        Column(
            modifier = Modifier
                .width(28.dp)
                .onSizeChanged { heightPx = it.height }
                .pointerInput(letters) {
                    // One gesture = finger down, maybe slide, finger up.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()                 // the pager must not swipe pages from here
                        pick(down.position.y)
                        drag(down.id) { change ->
                            change.consume()
                            pick(change.position.y)
                        }
                        active = null                  // finger lifted: back to plain grey
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { letter ->
                Box(Modifier.height(rowHeight), contentAlignment = Alignment.Center) {
                    Text(
                        letter.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (letter == active) FontWeight.ExtraBold else FontWeight.SemiBold,
                        ),
                        color = if (letter == active) ink else soft,
                    )
                }
            }
        }
    }
}
