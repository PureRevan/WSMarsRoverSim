package sfg.wseminar.rover.ui.controlpanel

import sfg.wseminar.rover.i18n.LocalizedText as Text
import sfg.wseminar.rover.ui.components.RoverFilterChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import sfg.wseminar.rover.algorithms.GoToWaypoint
import sfg.wseminar.rover.simulation.SensingRadius
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sfg.wseminar.rover.algorithms.RoverAlgorithm
import sfg.wseminar.rover.ui.components.DecimalSliderRow
import kotlin.math.roundToInt
import sfg.wseminar.rover.ui.components.ChipGroup
import sfg.wseminar.rover.ui.components.SectionHeader
import sfg.wseminar.rover.ui.components.ToggleRow
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent
import sfg.wseminar.rover.ui.states.ExplorationMode
import sfg.wseminar.rover.ui.states.GraphRenderMode
import sfg.wseminar.rover.ui.states.NodeColoringMode
import sfg.wseminar.rover.ui.states.SpaceMode
import sfg.wseminar.rover.ui.states.VisualizerState
import sfg.wseminar.rover.ui.states.supports


@Composable
fun ControlPanel(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit,
    onOpenGraphGeneration: () -> Unit,
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
                .padding(horizontal = 6.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (showHeading) {
                SectionHeader(
                    title = "Configuration",
                    icon = Icons.Outlined.Settings,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            ControlPanelSection(
                title = "Simulation model",
                initiallyExpanded = true,
                description = "Select the abstract environment and compatible algorithm family.",
                icon = Icons.Outlined.Science
            ) {
                Text(
                    text = "Exploration mode",
                    style = MaterialTheme.typography.labelLarge
                )

                ChipGroup {
                    ExplorationMode.entries.forEach { mode ->
                        RoverFilterChip(
                            selected = state.explorationMode == mode,
                            onClick = {
                                onEvent(RoverVisualizerEvent.ChangeExplorationMode(mode))
                            },
                            label = { Text(mode.nameForUI()) }
                        )
                    }
                }

                Text(
                    text = "Space model",
                    style = MaterialTheme.typography.labelLarge
                )

                ChipGroup {
                    SpaceMode.entries.forEach { mode ->
                        RoverFilterChip(
                            selected = state.spaceMode == mode,
                            onClick = {
                                onEvent(RoverVisualizerEvent.ChangeSpaceMode(mode))
                            },
                            label = { Text(mode.nameForUI()) }
                        )
                    }
                }

                Text(
                    text = "Algorithm family",
                    style = MaterialTheme.typography.labelLarge
                )

                ChipGroup {
                    RoverAlgorithm.algorithms.forEach { algorithm ->
                        RoverFilterChip(
                            selected = state.algorithm == algorithm,
                            enabled = algorithm.supports(state.spaceMode),
                            onClick = {
                                onEvent(RoverVisualizerEvent.ChangeAlgorithm(algorithm))
                            },
                            label = { Text(algorithm.name) }
                        )
                    }
                }
                if (state.algorithm === GoToWaypoint && state.explorationMode == ExplorationMode.LOCAL) {
                    SensingRadiusControl(state.sensingRadius, enabled = !state.isComputing) {
                        onEvent(RoverVisualizerEvent.ChangeSensingRadius(it))
                    }
                }
            }

            Text(
                text = state.algorithm.description,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            if (!state.algorithm.supports(state.explorationMode) || !state.algorithm.supports(state.spaceMode)) {
                Text("Choose a compatible exploration and space mode to run this algorithm.",
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 8.dp))
            }

            ControlPanelSection(
                title = "Node and edge annotations",
                description = "Labels and numerical information drawn on top of the graph.",
                icon = Icons.Outlined.Label
            ) {
                val graphModeAvailable = state.options.graphRenderMode == GraphRenderMode.GRAPH
                val insideCostsAvailable = state.options.showCosts && graphModeAvailable

                ToggleRow(
                    label = "Node IDs",
                    checked = state.options.showNodeIds,
                    onCheckedChange = { onEvent(RoverVisualizerEvent.ToggleNodeIds(it)) }
                )

                ToggleRow(
                    label = "Coordinates",
                    checked = state.options.showCoordinates,
                    onCheckedChange = { onEvent(RoverVisualizerEvent.ToggleCoordinates(it)) }
                )

                ToggleRow(
                    label = "Edge weights",
                    checked = state.options.showEdgeWeights,
                    onCheckedChange = { onEvent(RoverVisualizerEvent.ToggleEdgeWeights(it)) }
                )

                ToggleRow(
                    label = "Costs",
                    checked = state.options.showCosts,
                    onCheckedChange = { onEvent(RoverVisualizerEvent.ToggleCosts(it)) }
                )

                ToggleRow(
                    label = "Costs inside nodes",
                    checked = state.options.showCostsInsideNodes,
                    enabled = insideCostsAvailable,
                    supportingText = when {
                        !state.options.showCosts -> "Enable costs first."
                        !graphModeAvailable -> "Only available in graph rendering mode."
                        else -> null
                    },
                    onCheckedChange = { onEvent(RoverVisualizerEvent.ToggleCostsInsideNodes(it)) }
                )

                ToggleRow(
                    label = "Heuristics",
                    checked = state.options.showHeuristics,
                    onCheckedChange = { onEvent(RoverVisualizerEvent.ToggleHeuristics(it)) },
                    enabled = state.currentFrame?.nodeCostFields?.containsKey("h") == true,
                    supportingText = "A* provides h values after the run starts."
                )
            }

            ControlPanelSection(
                title = "Graph rendering",
                description = "Controls the visual representation of the generated environment.",
                icon = Icons.Outlined.Visibility
            ) {
                ToggleRow(
                    label = "Grid lines",
                    checked = state.options.showGridLines,
                    onCheckedChange = { onEvent(RoverVisualizerEvent.ToggleGridLines(it)) }
                )

                ToggleRow(
                    label = "Reduce node coloring",
                    checked = state.options.nodeColoringMode == NodeColoringMode.SIMPLE,
                    onCheckedChange = {
                        val mode = if (it) NodeColoringMode.SIMPLE else NodeColoringMode.DISTINCTIVE

                        onEvent(RoverVisualizerEvent.ChangeNodeColoringMode(mode))
                    }
                )

                ToggleRow(
                    label = "Unknown area",
                    checked = state.options.showUnknownArea,
                    onCheckedChange = { onEvent(RoverVisualizerEvent.ToggleUnknownArea(it)) }
                )

                androidx.compose.material3.OutlinedButton(
                    onClick = { onEvent(RoverVisualizerEvent.FitViewportToGraph) },
                    enabled = state.hasFrames, modifier = Modifier.fillMaxWidth()
                ) { Text("Fit view") }
                androidx.compose.material3.OutlinedButton(
                    onClick = { onEvent(RoverVisualizerEvent.ResetViewport) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reset view") }

            }

            GraphAppearanceSection(state, onEvent, onOpenGraphGeneration)

            ControlPanelSection(
                title = "Exploration overlays",
                description = "Highlights algorithm state during global or local exploration.",
                icon = Icons.Outlined.Explore
            ) {
                ToggleRow(
                    label = "Visited nodes",
                    checked = state.options.showVisited,
                    onCheckedChange = { onEvent(RoverVisualizerEvent.ToggleVisited(it)) }
                )

                ToggleRow(
                    label = "Frontier nodes",
                    checked = state.options.showFrontier,
                    onCheckedChange = { onEvent(RoverVisualizerEvent.ToggleFrontier(it)) }
                )

                ToggleRow(
                    label = "Final path",
                    checked = state.options.showFinalPath,
                    onCheckedChange = { onEvent(RoverVisualizerEvent.ToggleFinalPath(it)) }
                )

                if (state.options.showFinalPath) {
                    DecimalSliderRow("Final path emphasis", state.options.finalPathScale, 0f..5f,
                        "${(state.options.finalPathScale * 100).roundToInt()}%") {
                        onEvent(RoverVisualizerEvent.SetFinalPathScale(it))
                    }
                }

                ToggleRow(
                    label = "Sensor radius",
                    checked = state.options.showSensorRadius,
                    onCheckedChange = { onEvent(RoverVisualizerEvent.ToggleSensorRadius(it)) },
                    enabled = false,
                    supportingText = "Only useful once local sensing is visualized."
                )
            }

            ControlPanelSection(
                title = "Legend",
                description = "Explains node, edge, and path colors in the canvas.",
                icon = Icons.Outlined.Info
            ) {
                val legendEnabled = state.options.showLegend

                ToggleRow(
                    label = "Show legend",
                    checked = state.options.showLegend,
                    onCheckedChange = { onEvent(RoverVisualizerEvent.ToggleLegend(it)) }
                )

                ToggleRow(
                    label = "Node legend",
                    checked = state.options.showNodeLegend,
                    enabled = legendEnabled,
                    supportingText = if (!legendEnabled) "Enable legend first." else null,
                    onCheckedChange = { onEvent(RoverVisualizerEvent.ToggleNodeLegend(it)) }
                )

                ToggleRow(
                    label = "Path legend",
                    checked = state.options.showPathLegend,
                    enabled = legendEnabled,
                    supportingText = if (!legendEnabled) "Enable legend first." else null,
                    onCheckedChange = { onEvent(RoverVisualizerEvent.TogglePathLegend(it)) }
                )

                ToggleRow(
                    label = "Knowledge legend",
                    checked = state.options.showKnowledgeLegend,
                    enabled = legendEnabled,
                    supportingText = if (!legendEnabled) "Enable legend first." else null,
                    onCheckedChange = { onEvent(RoverVisualizerEvent.ToggleKnowledgeLegend(it)) }
                )

                LegendEdgePositionRow(
                    position = state.legendEdgePosition,
                    enabled = legendEnabled,
                    onPositionChange = {
                        onEvent(RoverVisualizerEvent.SetLegendEdgePosition(it))
                    }
                )

                FilledTonalButton(
                    onClick = { onEvent(RoverVisualizerEvent.ResetLegendPosition) },
                    enabled = legendEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset legend position")
                }
            }
        }
    }
}

@Composable
private fun SensingRadiusControl(radius: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    var draft by remember(radius) { mutableFloatStateOf(radius.toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Sensing radius (edge steps)", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(draft.toInt().toString(), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = draft, onValueChange = { draft = it.roundToInt().toFloat() },
            onValueChangeFinished = { onChange(draft.toInt()) }, enabled = enabled,
            valueRange = 1f..SensingRadius.MAX.toFloat(), steps = SensingRadius.MAX - 2)
        Text("A radius of 1 reveals immediate neighbors. Explored nodes remain known. Changing the radius restarts exploration.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
