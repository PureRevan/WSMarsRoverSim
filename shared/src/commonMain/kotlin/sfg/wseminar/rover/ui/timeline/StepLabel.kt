package sfg.wseminar.rover.ui.timeline

import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import sfg.wseminar.rover.ui.components.InlineEditableNumber
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent
import sfg.wseminar.rover.ui.states.VisualizerState

@Composable
fun StepLabel(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Step",
            style = MaterialTheme.typography.bodyMedium
        )

        InlineEditableNumber(
            displayText = state.currentStep.toString(),
            editableText = state.currentStep.toString(),
            minEditingWidthDp = 32,
            keyboardType = KeyboardType.Number,
            sanitize = { input -> input.filter { it.isDigit() } },
            onCommit = { input ->
                val parsed = input.toIntOrNull()
                if (parsed != null) {
                    onEvent(RoverVisualizerEvent.SeekToStep(parsed.coerceIn(0, state.maxStep)))
                }
            }
        )

        Text(
            text = "/ ${state.maxStep}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}