package com.detox.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Always green & white. The template had "dynamic color" ON: on Android 12+ it
// repaints the app with colors taken from your wallpaper. We turned that off.
private val DetoxColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = ForestDeep,
    secondary = Leaf,
    onSecondary = Color.White,
    secondaryContainer = Mint,
    onSecondaryContainer = ForestDeep,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = MintSoft,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = MintSoft,
    surfaceContainer = MintSoft,
    surfaceContainerHigh = Mint,
    surfaceContainerHighest = Mint,
    outline = LineStrong,
    outlineVariant = LineSoft,
)

// Soft, rounded corners everywhere.
private val DetoxShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun DetoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DetoxColors,
        typography = Typography,
        shapes = DetoxShapes,
        content = content,
    )
}
