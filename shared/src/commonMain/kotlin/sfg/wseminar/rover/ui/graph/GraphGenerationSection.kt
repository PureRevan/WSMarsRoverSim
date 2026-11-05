package sfg.wseminar.rover.ui.graph

import sfg.wseminar.rover.i18n.LocalizedIcon as Icon
import sfg.wseminar.rover.i18n.LocalizedText as Text
import sfg.wseminar.rover.ui.components.RoverFilterChip
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.ScatterPlot
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sfg.wseminar.rover.ui.components.ChipGroup
import sfg.wseminar.rover.ui.components.DecimalSliderRow
import sfg.wseminar.rover.ui.components.IntegerSettingRow
import sfg.wseminar.rover.ui.components.SectionHeader
import sfg.wseminar.rover.ui.components.ToggleRow
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent
import sfg.wseminar.rover.ui.states.GraphGenerationState
import sfg.wseminar.rover.ui.states.SpaceMode
import sfg.wseminar.rover.ui.states.VisualizerState
import sfg.wseminar.rover.graph.generator.GenerationLimits
import kotlin.math.roundToInt

@Composable
fun GraphGenerationSection(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit
) {
    val generation = state.graphGeneration

    fun update(block: GraphGenerationState.() -> GraphGenerationState) {
        onEvent(RoverVisualizerEvent.ChangeGraphGeneration(generation.block()))
    }

    TerrainModelControls(generation) { next -> onEvent(RoverVisualizerEvent.ChangeGraphGeneration(next)) }

    SectionHeader(
        title = "Complexity",
        icon = Icons.Outlined.Tune
    )

    ChipGroup {
        RoverFilterChip(
            selected = !generation.advanced,
            onClick = { update { withAdvanced(false) } },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Hub,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { Text("Simple") }
        )

        RoverFilterChip(
            selected = generation.advanced,
            onClick = { update { withAdvanced(true) } },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = { Text("Advanced") }
        )
    }

    Text(
        text = if (generation.advanced) {
            "Fine-tune placement, terrain and connections. Larger maps may take longer to generate and simulate."
        } else {
            "A short set of controls for typical maps. Internals such as spacing and connectivity are chosen automatically."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (!generation.terrain.enabled) GenerationPresetPicker(generation) { next ->
        onEvent(RoverVisualizerEvent.ChangeGraphGeneration(next))
    }

    SectionHeader(
        title = "Space",
        icon = if (generation.mode == SpaceMode.DISCRETE) {
            Icons.Outlined.GridOn
        } else {
            Icons.Outlined.ScatterPlot
        }
    )

    ChipGroup {
        SpaceMode.entries.forEach { mode ->
            RoverFilterChip(
                selected = generation.mode == mode,
                onClick = { update { copy(mode = mode) } },
                leadingIcon = {
                    Icon(
                        imageVector = when (mode) {
                            SpaceMode.DISCRETE -> Icons.Outlined.GridOn
                            SpaceMode.CONTINUOUS -> Icons.Outlined.ScatterPlot
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        when (mode) {
                            SpaceMode.DISCRETE -> "Discrete grid"
                            SpaceMode.CONTINUOUS -> "Continuous plane"
                        }
                    )
                }
            )
        }
    }

    if (generation.mode == SpaceMode.CONTINUOUS) {
        val scale = state.performance.nodeSlider
        DecimalSliderRow(
            label = "Node count",
            value = scale.position(generation.resolvedNodeCount(), generation.advanced),
            valueRange = 0f..1f,
            displayText = "${generation.resolvedNodeCount()} nodes",
            onValueChange = { position -> update {
                val count = scale.nodeCount(position, generation.advanced)
                copy(nodeCount = count, simpleNodeCount = count)
            } }
        )
        IntegerSettingRow(
            label = "Exact node count",
            value = generation.resolvedNodeCount(),
            range = 2..GenerationLimits.MaxNodes,
            onValueChange = { update { copy(nodeCount = it, simpleNodeCount = it) } }
        )
    } else if (!generation.advanced) {
        DecimalSliderRow(
            label = "Map size",
            value = generation.sizeFactor.toFloat(),
            valueRange = 0f..1f,
            displayText = sizeSummary(generation),
            onValueChange = { update { copy(sizeFactor = it.toDouble()) } }
        )
    } else {
        IntegerSettingRow("Columns", generation.gridColumns, 2..GenerationLimits.MaxGridSide,
            onValueChange = { update { copy(gridColumns = it) } })
        IntegerSettingRow("Rows", generation.gridRows, 2..GenerationLimits.MaxGridSide,
            onValueChange = { update { copy(gridRows = it) } })
    }

    sfg.wseminar.rover.ui.settings.ComputationLimitsSwitch(disabled = !state.performance.limitsEnabled) { disabled ->
        onEvent(RoverVisualizerEvent.ChangePerformance(state.performance.copy(limitsEnabled = !disabled)))
    }

    ScenarioGenerationControls(generation) { next ->
        onEvent(RoverVisualizerEvent.ChangeGraphGeneration(next))
    }

    NodeSpacingControls(generation) { next ->
        onEvent(RoverVisualizerEvent.ChangeGraphGeneration(next))
    }

    SectionHeader(
        title = "Structure",
        icon = Icons.Outlined.AccountTree
    )

    DecimalSliderRow(
        label = "Density",
        value = generation.density.toFloat(),
        valueRange = 0f..1f,
        displayText = densitySummary(generation.density),
        onValueChange = { update { copy(density = it.toDouble()) } }
    )

    ToggleRow(
        label = "Planar drawing",
        checked = generation.planar,
        supportingText = if (generation.planar) {
            "Edges will not cross, and nodes keep a clearance box around them."
        } else {
            "Edges may cross. Nodes still keep a clearance box unless overlap is allowed."
        },
        onCheckedChange = { update { copy(planar = it) } }
    )

    if (generation.advanced && generation.mode == SpaceMode.DISCRETE) {
        ToggleRow(
            label = "Diagonal connections",
            checked = generation.includeGridDiagonals,
            supportingText = if (generation.planar) {
                "At most one diagonal per cell, so the drawing stays planar."
            } else {
                "Both cell diagonals may be added, which creates crossings."
            },
            onCheckedChange = { update { copy(includeGridDiagonals = it) } }
        )
    }

    if (generation.advanced && generation.mode == SpaceMode.CONTINUOUS && !generation.planar) {
        DecimalSliderRow(
            label = "Connection radius",
            value = generation.connectionRadius.toFloat(),
            valueRange = 0.1f..0.7f,
            displayText = "${(generation.connectionRadius * 100.0).roundToInt()}% of map",
            onValueChange = { update { copy(connectionRadius = it.toDouble()) } }
        )
    }

    if (generation.advanced) {
        SectionHeader(
            title = "Constraints",
            icon = Icons.Outlined.Layers
        )

        ToggleRow(
            label = "Allow disconnected graph",
            checked = generation.allowDisconnected,
            supportingText = if (generation.terrain.enabled)
                "Controls connectivity before impassable edges are removed. Terrain can still disconnect the graph."
            else "If off, all nodes must be connected ignoring edge direction; incompatible settings report an error.",
            onCheckedChange = { update { copy(allowDisconnected = it) } }
        )

        ToggleRow(
            label = "Allow overlapping nodes",
            checked = generation.allowNodeOverlap,
            supportingText = "Turn on only if you want nodes to share the same visual space.",
            onCheckedChange = { update { copy(allowNodeOverlap = it) } }
        )

        if (!generation.allowNodeOverlap) {
            DecimalSliderRow(
                label = "Edge clearance around nodes",
                value = generation.nodeClearanceMultiplier.toFloat(),
                valueRange = 0.6f..1.6f,
                displayText = "${(generation.nodeClearanceMultiplier * 100.0).roundToInt()}%",
                onValueChange = { update { copy(nodeClearanceMultiplier = it.toDouble()) } }
            )
        }

        ToggleRow(
            label = "Bidirectional edges",
            checked = generation.bidirectional,
            supportingText = "One-way edges point toward higher node IDs. A connected graph may have an unreachable goal.",
            onCheckedChange = { update { copy(bidirectional = it) } }
        )
    }

    SeedRow(
        state = state,
        onEvent = onEvent
    )
}

private fun sizeSummary(
    generation: GraphGenerationState
): String =
    when (generation.mode) {
        SpaceMode.DISCRETE -> {
            val columns = generation.resolvedGridColumns()
            val rows = generation.resolvedGridRows()
            "$columns × $rows cells"
        }

        SpaceMode.CONTINUOUS -> {
            "${generation.resolvedNodeCount()} nodes"
        }
    }

private fun densitySummary(
    density: Double
): String =
    when {
        density < 0.2 -> "Sparse"
        density < 0.4 -> "Light"
        density < 0.65 -> "Medium"
        density < 0.85 -> "Dense"
        else -> "Very dense"
    }
