// app theme entry point. brand colors only, no Material You dynamic color.
package bd.sicip.qavisit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Immutable
data class StatusColors(
    val onVisit: StatusPair,
    val onLeave: StatusPair,
    val office: StatusPair,
    val success: StatusPair,
)

private val LightStatusColors = StatusColors(
    onVisit = LightStatus.onVisit,
    onLeave = LightStatus.onLeave,
    office = LightStatus.office,
    success = LightStatus.success,
)

private val DarkStatusColors = StatusColors(
    onVisit = DarkStatus.onVisit,
    onLeave = DarkStatus.onLeave,
    office = DarkStatus.office,
    success = DarkStatus.success,
)

// fallback default so previews / missing-provider reads don't crash
val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

// M3's lightColorScheme()/darkColorScheme() derive surfaceContainer* tones from `primary`
// when not given explicitly -- that's the lavender-tinted card bug (DESIGN.md wants flat
// white/slate cards, not a primary-tinted surface). Pinning every surface* token stops most
// of that, but `surfaceColorAtElevation()` (what tonal-elevation containers like Card actually
// paint) separately composites `surfaceTint` -- which itself defaults to `primary` -- over
// `surface`, so navy still bled through as a faint tint even with surfaceContainerLow pinned.
// Setting surfaceTint = surface makes that composite a no-op, which is what "flat card, no
// elevation tint" in DESIGN.md means.
private val LightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    background = LightBackground,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightBackground,
    onSurfaceVariant = LightMuted,
    surfaceContainer = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainerHigh = LightSurface,
    surfaceContainerLowest = LightSurface,
    surfaceContainerHighest = LightSurface,
    surfaceTint = LightSurface,
    outline = LightOutline,
)

private val DarkScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkBackground,
    onSurfaceVariant = DarkMuted,
    surfaceContainer = DarkNavBar,
    surfaceContainerLow = DarkSurface,
    surfaceContainerHigh = DarkSurface,
    surfaceContainerLowest = DarkSurface,
    surfaceContainerHighest = DarkSurface,
    surfaceTint = DarkSurface,
    outline = DarkOutline,
)

@Composable
fun SicipTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatusColors else LightStatusColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = SicipTypography,
            content = content,
        )
    }
}
