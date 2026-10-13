package io.github.halilozel1903.hackernews.sample

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Hacker News orange. */
val HnOrange = Color(0xFFFF6600)

private val LightColors = lightColorScheme(
    primary = Color(0xFFC24E00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC9),
    onPrimaryContainer = Color(0xFF341000),
    secondary = Color(0xFF76574A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF2B160C),
    background = Color(0xFFFFFBF8),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFFFBF8),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF4E6DD),
    onSurfaceVariant = Color(0xFF53443C),
    surfaceContainer = Color(0xFFF8EEE8),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFDDCBC1),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB68E),
    onPrimary = Color(0xFF552000),
    primaryContainer = Color(0xFF793000),
    onPrimaryContainer = Color(0xFFFFDBC9),
    secondary = Color(0xFFE6BEAC),
    onSecondary = Color(0xFF432B1E),
    secondaryContainer = Color(0xFF5C4033),
    onSecondaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF181210),
    onBackground = Color(0xFFF0DFD8),
    surface = Color(0xFF181210),
    onSurface = Color(0xFFF0DFD8),
    surfaceVariant = Color(0xFF2E2420),
    onSurfaceVariant = Color(0xFFD8C2B8),
    surfaceContainer = Color(0xFF241C18),
    outline = Color(0xFFA08D84),
    outlineVariant = Color(0xFF53443C),
)

@Composable
fun ReaderTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
