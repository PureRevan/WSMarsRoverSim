package sfg.wseminar.rover.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import sfg.wseminar.rover.algorithms.GoToWaypoint
import sfg.wseminar.rover.i18n.LocalizedIcon as Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent
import sfg.wseminar.rover.ui.states.VisualizerState

@Composable
fun TimelineControls(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp
    ) {
        if (compact) {
            CompactTimelineControls(state = state, onEvent = onEvent)
        } else {
            ExpandedTimelineControls(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun ExpandedTimelineControls(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        PlaybackButtons(
            state = state,
            onEvent = onEvent
        )

        StepLabel(
            state = state,
            onEvent = onEvent
        )

        Slider(
            value = state.currentStep.toFloat(),
            onValueChange = {
                onEvent(RoverVisualizerEvent.SeekToStep(it.toInt()))
            },
            valueRange = 0f..state.maxStep.toFloat().coerceAtLeast(1f),
            // Avoid allocating and drawing a tick for every frame in large runs.
            steps = if (state.maxStep <= 100) (state.maxStep - 1).coerceAtLeast(0) else 0,
            modifier = Modifier.width(260.dp)
        )

        SpeedControls(
            state = state,
            onEvent = onEvent,
            sliderModifier = Modifier.width(180.dp)
        )
    }
}

@Composable
private fun CompactTimelineControls(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit
) {
    val canReplay = state.hasFrames && (state.algorithm !== GoToWaypoint || state.hasRunAlgorithm)
    var showSpeed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onEvent(RoverVisualizerEvent.Restart) }, enabled = canReplay) {
                Icon(Icons.Outlined.Replay, if (state.algorithm === GoToWaypoint) "Rewind" else "Restart")
            }
            IconButton(onClick = { onEvent(RoverVisualizerEvent.StepBackward) }, enabled = canReplay && state.currentStep > 0) {
                Icon(Icons.Outlined.SkipPrevious, "Back")
            }
            FilledTonalIconButton(onClick = { onEvent(RoverVisualizerEvent.PlayPause) },
                enabled = canReplay && !state.isComputing && (state.algorithm !== GoToWaypoint || state.currentStep < state.maxStep || state.isPlaying)) {
                Icon(if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (state.isPlaying) "Pause" else "Play")
            }
            IconButton(onClick = { onEvent(RoverVisualizerEvent.StepForward) },
                enabled = canReplay && !state.isComputing && (state.algorithm !== GoToWaypoint || state.currentStep < state.maxStep)) {
                Icon(Icons.Outlined.SkipNext, "Next")
            }
            Box {
                IconButton(onClick = { showSpeed = true }) { Icon(Icons.Outlined.Speed, "Playback speed") }
                DropdownMenu(showSpeed, { showSpeed = false }) {
                    Column(Modifier.width(240.dp).padding(16.dp)) {
                        SpeedLabel(state, onEvent)
                        Slider(state.playbackSpeed, { onEvent(RoverVisualizerEvent.SetPlaybackSpeed(roundSpeed(it))) },
                            valueRange = state.performance.playback.range)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepLabel(state, onEvent)
            Slider(state.currentStep.toFloat(), { onEvent(RoverVisualizerEvent.SeekToStep(it.toInt())) },
                enabled = state.hasFrames,
                valueRange = 0f..state.maxStep.toFloat().coerceAtLeast(1f),
                steps = if (state.maxStep <= 100) (state.maxStep - 1).coerceAtLeast(0) else 0,
                modifier = Modifier.weight(1f))
        }
    }
}
