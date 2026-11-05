package sfg.wseminar.rover.ui.timeline

import sfg.wseminar.rover.i18n.LocalizedIcon as Icon
import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent
import sfg.wseminar.rover.algorithms.GoToWaypoint
import sfg.wseminar.rover.ui.states.VisualizerState

@Composable
fun PlaybackButtons(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit
) {
    val canReplay = state.algorithm !== GoToWaypoint || state.hasRunAlgorithm
    AssistChip(
        onClick = { onEvent(RoverVisualizerEvent.Restart) },
        enabled = canReplay,
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Replay,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = { Text(if (state.algorithm === GoToWaypoint) "Rewind" else "Restart") }
    )

    AssistChip(
        onClick = { onEvent(RoverVisualizerEvent.StepBackward) },
        enabled = canReplay && state.currentStep > 0,
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.SkipPrevious,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = { Text("Back") }
    )

    AssistChip(
        onClick = { onEvent(RoverVisualizerEvent.PlayPause) },
        enabled = canReplay && (state.algorithm !== GoToWaypoint || state.currentStep < state.maxStep || state.isPlaying),
        leadingIcon = {
            Icon(
                imageVector = if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = { Text(if (state.isPlaying) "Pause" else "Play") }
    )

    AssistChip(
        onClick = { onEvent(RoverVisualizerEvent.StepForward) },
        enabled = canReplay && (state.algorithm !== GoToWaypoint || state.currentStep < state.maxStep),
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.SkipNext,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        label = { Text("Next") }
    )
}
