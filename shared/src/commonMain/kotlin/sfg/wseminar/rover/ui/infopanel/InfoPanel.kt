package sfg.wseminar.rover.ui.infopanel

import sfg.wseminar.rover.i18n.LocalizedIcon as Icon
import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalButton
import sfg.wseminar.rover.algorithms.GoToWaypoint
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import sfg.wseminar.rover.ui.components.InfoRow
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent
import sfg.wseminar.rover.ui.states.waypointCommandIssue
import sfg.wseminar.rover.ui.states.VisualizerState
import kotlin.math.roundToInt

@Composable
fun InfoPanel(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit,
    modifier: Modifier = Modifier,
    showHeading: Boolean = true
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxHeight()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showHeading) {
                InfoPanelHeading(
                    title = "Simulation details",
                    icon = Icons.Outlined.Info
                )

                HorizontalDivider()
            }

            if (state.isComputing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(state.workMessage, style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(onClick = { onEvent(RoverVisualizerEvent.CancelWork) }) { Text("Cancel computation") }
            }
            state.recordingStopReason?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            InfoRow("Algorithm", state.algorithm.name)
            InfoRow("Step", "${state.currentStep} / ${state.maxStep}")
            InfoRow("Run time", state.runElapsedNanos?.let(::formatRunTime) ?: "Not run")
            AlgorithmMemoryInfo(state.peakAlgorithmBytes)
            InfoRow("Replay memory (est.)", formatMemory(state.estimatedReplayBytes))
            InfoRow("Speed", formatSpeed(state.playbackSpeed))
            InfoRow("Zoom", formatZoom(state.viewport.zoom))

            HorizontalDivider()

            InfoPanelHeading(
                title = "Selection",
                icon = Icons.Outlined.TouchApp
            )

            when {
                state.selectedNode != null -> {
                    val node = state.selectedNode
                    InfoRow("Type", "Node")
                    InfoRow("ID", node.id.toString())
                    InfoRow("Label", node.label, verbatim = true)
                    InfoRow("Position", "(${formatDecimal(node.x)}, ${formatDecimal(node.y)})")

                    val frame = state.currentFrame
                    if (frame != null) {
                        frame.nodeCostFields.forEach { (name, values) ->
                            values.getOrNull(node.id)?.let { InfoRow(costFieldName(name, frame.costMeaning), formatDecimal(it)) }
                        }

                        InfoRow(
                            "State",
                            when {
                                node in frame.visitedNodes -> "Visited"
                                node in frame.frontierNodes -> "Frontier"
                                node in frame.knownNodes -> "Known"
                                else -> "Unknown"
                            }
                        )
                    }
                }

                state.selectedEdge != null -> {
                    val edge = state.selectedEdge
                    InfoRow("Type", "Edge")
                    InfoRow("ID", edge.id.toString())
                    InfoRow("From", edge.from.label, verbatim = true)
                    InfoRow("To", edge.to.label, verbatim = true)
                    InfoRow("Direction", if (edge.bidirectional) "Both ways" else "From → To")
                    InfoRow("Weight", if (state.currentFrame?.let { edge in it.knownEdges } == true) formatDecimal(edge.weight) else "Unknown")

                    state.generatedRecipe?.takeIf { it.terrain.enabled }?.let { recipe ->
                        if (state.currentFrame?.let { edge in it.knownEdges } == true) {
                            val terrain = androidx.compose.runtime.remember(edge, recipe) {
                                sfg.wseminar.rover.terrain.TerrainSimulation(recipe.terrain, recipe.seed, 1.0).evaluate(edge.from, edge.to)
                            }
                            InfoRow("Distance (m)", formatDecimal(terrain.distanceMetres))
                            InfoRow("Terrain rating qG", formatDecimal(terrain.geometry))
                            InfoRow("Slip rating qS", formatDecimal(terrain.slip))
                            InfoRow("Maximum slope (°)", formatDecimal(terrain.maximumSlopeDegrees))
                        }
                    }

                    val frame = state.currentFrame
                    if (frame != null) {
                        InfoRow(
                            "State",
                            if (edge in frame.knownEdges) "Known" else "Unknown"
                        )
                    }
                }

                else -> {
                    Text(
                        text = "Click a node or edge in the canvas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.algorithm === GoToWaypoint) {
                val issue = state.waypointCommandIssue()
                Text("Select a destination, near or far. The rover follows connections that get closer to it; this local controller cannot plan a detour. Selecting a node pauses playback. A new command starts from the displayed rover and replaces future steps.",
                    style = MaterialTheme.typography.bodySmall)
                state.currentFrame?.roverNode?.let { InfoRow("Command from", it.label, verbatim = true) }
                if (state.hasRunAlgorithm) state.currentFrame?.goalNode?.let { InfoRow("Recorded target", it.label, verbatim = true) }
                state.selectedNode?.let { InfoRow("Destination", it.label, verbatim = true) }
                if (issue != null) Text(issue, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(
                    onClick = { onEvent(RoverVisualizerEvent.SendWaypoint) },
                    enabled = issue == null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Send waypoint") }
            }

            HorizontalDivider()

            InfoPanelHeading(
                title = "Current frame",
                icon = Icons.Outlined.Analytics
            )

            val frame = state.currentFrame

            if (frame == null) {
                Text(
                    text = "No frame selected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                InfoRow("Expanded nodes", frame.visitedNodes.size.toString())
                InfoRow("Physical visits", frame.physicallyVisitedNodes.size.toString())
                InfoRow("Final path cost", when {
                    state.algorithm === GoToWaypoint && state.revealedFinalPathCost == Double.POSITIVE_INFINITY -> "Waypoint not reached"
                    else -> state.revealedFinalPathCost?.let(::formatDecimal) ?: "?"
                })
                InfoRow("Run seed", frame.runSeed.toString())
                InfoRow("Frontier nodes", frame.frontierNodes.size.toString())
                InfoRow("Known nodes", frame.knownNodes.size.toString())
                InfoRow("Known edges", frame.knownEdges.size.toString())

                Text(
                    text = frame.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlgorithmMemoryInfo(bytes: Long?) {
    val tooltip = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            InfoRow("Algorithm memory · peak (est.)", bytes?.let(::formatMemory) ?: "Not run")
        }
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            state = tooltip,
            enableUserInput = false,
            tooltip = { PlainTooltip {
                Text("Working memory estimate: search tables, queue, knowledge and current path. Excludes graph input, recorded frames and UI; not process heap usage.")
            } }
        ) {
            IconButton(onClick = { scope.launch { tooltip.show() } }) {
                Icon(Icons.Outlined.HelpOutline, "About algorithm memory", Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun InfoPanelHeading(
    title: String,
    icon: ImageVector
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

private fun formatSpeed(
    speed: Float
): String = "${formatDecimal(speed)}×"

private fun formatZoom(
    zoom: Float
): String {
    return when {
        zoom >= 10f -> "${zoom.roundToInt()}×"
        zoom < 0.01f -> "1:${(1f / zoom).roundToInt()}"
        else -> "${formatDecimal(zoom)}×"
    }
}

private fun formatDecimal(value: Double): String {
    if (value == Double.POSITIVE_INFINITY) return "∞"
    if (value == Double.NEGATIVE_INFINITY) return "−∞"
    if (value.isNaN()) return "—"
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0 && kotlin.math.abs(rounded) < Long.MAX_VALUE.toDouble()) rounded.toLong().toString() else rounded.toString()
}

private fun formatDecimal(value: Float): String = formatDecimal(value.toDouble())

internal fun costFieldName(name: String, costMeaning: String): String = when (name) {
    "g" -> "$costMeaning (g)"
    "h" -> "Heuristic (h)"
    "f" -> "Estimated total cost (f)"
    else -> name
}

internal fun formatRunTime(nanos: Long): String {
    val millis = nanos / 1_000_000.0
    return when {
        millis < 1 -> "< 1 ms"
        millis < 1000 -> "${formatDecimal(millis)} ms"
        millis < 60_000 -> "${formatDecimal(millis / 1000)} s"
        else -> "${nanos / 60_000_000_000} min ${formatDecimal((nanos % 60_000_000_000) / 1_000_000_000.0)} s"
    }
}

internal fun formatMemory(bytes: Long): String = when {
    bytes < 1000 -> "$bytes B"
    bytes < 1000 * 1000 -> "${formatDecimal(bytes / 1000.0)} kB"
    bytes < 1000L * 1000 * 1000 -> "${formatDecimal(bytes / (1000.0 * 1000))} MB"
    else -> "${formatDecimal(bytes / (1000.0 * 1000 * 1000))} GB"
}
