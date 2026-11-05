package sfg.wseminar.rover


import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import sfg.wseminar.rover.ui.settings.ThemePreset
import androidx.compose.ui.tooling.preview.Preview
import sfg.wseminar.rover.ui.RoverVisualizerScreen


@Composable
@Preview
fun App(allowCompactLayout: Boolean = false) {
    var theme by remember { mutableStateOf(ThemePreset.PAPER) }
    MaterialTheme(colorScheme = theme.colors()) {
        RoverVisualizerScreen(theme = theme, onThemeChange = { theme = it }, allowCompactLayout = allowCompactLayout)
    }
}
