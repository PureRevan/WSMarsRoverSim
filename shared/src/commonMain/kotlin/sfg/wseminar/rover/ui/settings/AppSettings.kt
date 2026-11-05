package sfg.wseminar.rover.ui.settings

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import sfg.wseminar.rover.algorithms.backend.SimulationLimits
import sfg.wseminar.rover.graph.generator.GenerationBudget

/** Immutable settings are copied into each job when it starts. */
@kotlinx.serialization.Serializable
data class PerformanceSettings(
    val language: sfg.wseminar.rover.i18n.AppLanguage = sfg.wseminar.rover.i18n.AppLanguage.GERMAN,
    val replay: SimulationLimits = SimulationLimits(),
    val generation: GenerationBudget = GenerationBudget(),
    val playback: PlaybackSettings = PlaybackSettings(),
    val nodeSlider: NodeSliderSettings = NodeSliderSettings(),
    val limitsEnabled: Boolean = true,
    val browserWorker: sfg.wseminar.rover.computation.WorkerSettings = sfg.wseminar.rover.computation.WorkerSettings()
)

enum class ThemePreset(val title: String, val description: String, val dark: Boolean, val accent: Color, val background: Color) {
    PAPER("Aurora", "Indigo & lavender · light", false, Color(0xFF5141D9), Color(0xFFF8FAFF)),
    MARS("Mars", "Terracotta & sand · light", false, Color(0xFFAD4829), Color(0xFFFFF8F3)),
    MIDNIGHT("Midnight", "Slate & blue · dark", true, Color(0xFFA9CDFF), Color(0xFF111820)),
    DEEP_SPACE("Deep Space", "Charcoal & teal · dark", true, Color(0xFF80D5CB), Color(0xFF101B1C));

    fun colors() = if (dark) darkColorScheme(
        primary = accent, onPrimary = Color(0xFF102A30), primaryContainer = Color(0xFF304750), onPrimaryContainer = Color(0xFFE0F3FF),
        secondary = Color(0xFFBACCD6), secondaryContainer = Color(0xFF33434B), onSecondaryContainer = Color(0xFFE0EDF4),
        tertiary = Color(0xFFE5BE87), background = background, onBackground = Color(0xFFE4E9EE),
        surface = background, onSurface = Color(0xFFE4E9EE), surfaceVariant = Color(0xFF354047), onSurfaceVariant = Color(0xFFBCC7CF),
        outline = Color(0xFF94A1AB), outlineVariant = Color(0xFF5E6B75),
        surfaceContainerLowest = Color(0xFF0C1217), surfaceContainerLow = Color(0xFF182128),
        surfaceContainer = Color(0xFF1D272F), surfaceContainerHigh = Color(0xFF27323A), surfaceContainerHighest = Color(0xFF313D45)
    ) else lightColorScheme(
        primary = accent, onPrimary = Color.White, primaryContainer = if (this == MARS) Color(0xFFFFDBC9) else Color(0xFFE7E5FF),
        onPrimaryContainer = if (this == MARS) Color(0xFF4C281B) else Color(0xFF292165),
        secondary = if (this == MARS) Color(0xFF806044) else Color(0xFF625D85),
        secondaryContainer = if (this == MARS) Color(0xFFF0E0D0) else Color(0xFFE9E5F5),
        onSecondaryContainer = if (this == MARS) Color(0xFF493524) else Color(0xFF332E49),
        tertiary = if (this == MARS) Color(0xFF74602E) else Color(0xFFAB4967), background = background, onBackground = Color(0xFF20272D),
        surface = background, onSurface = Color(0xFF20272D), surfaceVariant = if (this == MARS) Color(0xFFECE0D7) else Color(0xFFE7E5F0), onSurfaceVariant = Color(0xFF4D5962),
        outline = Color(0xFF727F89), outlineVariant = if (this == MARS) Color(0xFFAAB5BF) else Color(0xFF9CAAC2),
        surfaceContainerLowest = Color.White, surfaceContainerLow = background,
        surfaceContainer = if (this == MARS) Color(0xFFF5ECE5) else Color(0xFFEEF2F7),
        surfaceContainerHigh = if (this == MARS) Color(0xFFEFE4DB) else Color(0xFFE7EDF3), surfaceContainerHighest = Color(0xFFDEE5EC)
    )
}
