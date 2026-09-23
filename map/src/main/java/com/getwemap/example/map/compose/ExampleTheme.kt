package com.getwemap.example.map.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The Material 3 palette for this app's Compose screens, so their controls are Wemap blue like the View
 * screens' and like the iOS sample's.
 *
 * Without it the screens get Material 3's baseline scheme, which is purple — the app's `colorPrimary` is an
 * XML theme attribute and nothing reads it into a `ColorScheme`.
 *
 * Light only, and deliberately: every screen using this is a full-screen map, the map style is light, and a
 * dark scheme would put near-white controls and labels on it. The View screens can afford `DayNight` because
 * their controls sit on their own opaque bars.
 */
@Composable
internal fun ExampleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, content = content)
}

private val wemapBlue = Color(0xFF2F7DE1)

private val colorScheme = lightColorScheme(
    primary = wemapBlue,
    onPrimary = Color.White,
    // `FilledTonalButton`'s pair. The fill is the primary at 15% over white, which is what iOS's `tinted`
    // button configuration draws, and the label stays the full-strength blue.
    secondaryContainer = Color(0xFFE0EBFA),
    onSecondaryContainer = wemapBlue,
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E)
)
