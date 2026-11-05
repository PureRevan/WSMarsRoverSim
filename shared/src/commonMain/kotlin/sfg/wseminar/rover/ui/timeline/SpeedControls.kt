package sfg.wseminar.rover.ui.timeline

import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import sfg.wseminar.rover.ui.components.InlineEditableNumber
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent
import sfg.wseminar.rover.ui.states.VisualizerState

@Composable
fun SpeedControls(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit,
    sliderModifier: Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Speed",
            style = MaterialTheme.typography.bodyMedium
        )

        Slider(
            value = state.playbackSpeed,
            onValueChange = {
                onEvent(RoverVisualizerEvent.SetPlaybackSpeed(roundSpeed(it)))
            },
            valueRange = state.performance.playback.range,
            modifier = sliderModifier
        )

        SpeedValue(
            state = state,
            onEvent = onEvent
        )
    }
}

@Composable
fun SpeedLabel(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Speed",
            style = MaterialTheme.typography.bodyMedium
        )

        SpeedValue(
            state = state,
            onEvent = onEvent
        )
    }
}

@Composable
private fun SpeedValue(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        InlineEditableNumber(
            displayText = formatSpeedNumber(state.playbackSpeed),
            editableText = formatSpeedNumber(state.playbackSpeed),
            minEditingWidthDp = 36,
            keyboardType = KeyboardType.Decimal,
            sanitize = { input ->
                sanitizeDecimalInput(input)
            },
            onCommit = { input ->
                val parsed = input.replace(',', '.').toFloatOrNull()
                if (parsed != null && parsed.isFinite()) {
                    onEvent(
                        RoverVisualizerEvent.SetPlaybackSpeed(
                            roundSpeed(parsed.coerceIn(state.performance.playback.range))
                        )
                    )
                }
            }
        )

        Text(
            text = "×",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun sanitizeDecimalInput(
    input: String
): String {
    var hasSeparator = false

    return input.filter { char ->
        when {
            char.isDigit() -> true
            (char == '.' || char == ',') && !hasSeparator -> {
                hasSeparator = true
                true
            }

            else -> false
        }
    }
}

fun roundSpeed(
    speed: Float
): Float = if (speed < 0.01f) speed else (kotlin.math.round(speed.toDouble() * 100) / 100).toFloat()

private fun formatSpeedNumber(
    speed: Float
): String {
    val rounded = roundSpeed(speed)
    return if (rounded % 1f == 0f) {
        rounded.toString().removeSuffix(".0")
    } else {
        rounded.toString()
    }
}
