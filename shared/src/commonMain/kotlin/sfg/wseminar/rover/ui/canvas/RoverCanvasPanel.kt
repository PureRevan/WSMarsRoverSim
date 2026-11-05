package sfg.wseminar.rover.ui.canvas

import sfg.wseminar.rover.i18n.LocalizedIcon as Icon
import sfg.wseminar.rover.i18n.LocalizedText as Text
import sfg.wseminar.rover.export.FrameImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent
import sfg.wseminar.rover.ui.states.VisualizerState

@Composable
fun RoverCanvasPanel(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit,
    onOpenGraphGeneration: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    imageCapture: FrameImageCapture? = null
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = if (compact) 4.dp else 0.dp, vertical = if (compact) 4.dp else 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(if (compact) 4.dp else 16.dp)
        ) {
            // Recording progress must not invalidate a scene whose displayed frame is unchanged.
            val sceneState = remember(state.currentFrame, state.options, state.viewport, state.canvasSize,
                state.selectedNode, state.selectedEdge, state.legendEdgePosition) {
                VisualizerState(frames = listOfNotNull(state.currentFrame), options = state.options,
                    viewport = state.viewport, canvasSize = state.canvasSize, selectedNode = state.selectedNode,
                    selectedEdge = state.selectedEdge, legendEdgePosition = state.legendEdgePosition)
            }
            RoverCanvas(
                state = sceneState,
                imageCapture = imageCapture,
                onEvent = onEvent,
                modifier = Modifier.fillMaxSize().clipToBounds().onSizeChanged {
                    onEvent(RoverVisualizerEvent.CanvasSizeChanged(it))
                }
            )

            if (!state.hasFrames) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "No simulation loaded yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Button(
                        onClick = onOpenGraphGeneration
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Hub,
                            contentDescription = null
                        )
                        Text(
                            text = "Generate graph",
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
