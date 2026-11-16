package th.privatenotes.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Brand palette: deep green (icon) + gold (lock), on warm notebook-paper cream. */
fun appColorScheme(isDark: Boolean): ColorScheme =
    if (isDark) darkColorScheme(
        primary = Color(0xFFF7B733),
        onPrimary = Color(0xFF3A2A00),
        primaryContainer = Color(0xFF2B5A4D),
        onPrimaryContainer = Color(0xFFD7F2E8),
        secondary = Color(0xFF8FD3BC),
        onSecondary = Color(0xFF0B2E24),
        background = Color(0xFF0E2622),
        onBackground = Color(0xFFE6EEE9),
        surface = Color(0xFF133029),
        onSurface = Color(0xFFE6EEE9),
        surfaceVariant = Color(0xFF25453D),
        onSurfaceVariant = Color(0xFFB7C6BE),
        outline = Color(0xFF7F9088),
        outlineVariant = Color(0xFF3A5A51),
        surfaceContainerLowest = Color(0xFF0A1F1B),
        surfaceContainerLow = Color(0xFF112B25),
        surfaceContainer = Color(0xFF163630),
        surfaceContainerHigh = Color(0xFF1C3F38),
        surfaceContainerHighest = Color(0xFF224741)
    ) else lightColorScheme(
        primary = Color(0xFF1B5E4B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC9E8DC),
        onPrimaryContainer = Color(0xFF0B2E24),
        secondary = Color(0xFFF7B733),
        onSecondary = Color(0xFF3A2A00),
        secondaryContainer = Color(0xFFFFE7A8),
        onSecondaryContainer = Color(0xFF3A2A00),
        background = Color(0xFFF8F5EC),
        onBackground = Color(0xFF1C2420),
        surface = Color(0xFFFBF9F2),
        onSurface = Color(0xFF1C2420),
        surfaceVariant = Color(0xFFE8E4D6),
        onSurfaceVariant = Color(0xFF4B554F),
        outline = Color(0xFF8B948E),
        outlineVariant = Color(0xFFD3D0C2),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F2E8),
        surfaceContainer = Color(0xFFF0EDE2),
        surfaceContainerHigh = Color(0xFFEBE8DC),
        surfaceContainerHighest = Color(0xFFE6E3D7)
    )

/** Top-to-bottom colours of the app background. */
fun appBackgroundColors(isDark: Boolean): List<Color> =
    if (isDark) listOf(Color(0xFF133029), Color(0xFF0B221E))
    else listOf(Color(0xFFF9F6EE), Color(0xFFEEE9DA))
