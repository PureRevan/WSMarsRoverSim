package sfg.wseminar.rover.ui.controlpanel

import sfg.wseminar.rover.i18n.LocalizedText as Text
import sfg.wseminar.rover.ui.components.RoverFilterChip
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import sfg.wseminar.rover.ui.components.*
import sfg.wseminar.rover.ui.graph.NodeSpacingControls
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent
import sfg.wseminar.rover.ui.states.GraphAppearance
import sfg.wseminar.rover.ui.states.VisualizerState
import kotlin.math.roundToInt

@Composable
fun GraphAppearanceSection(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit,
    onOpenGeneration: () -> Unit
) {
    val appearance = state.options.appearance
    fun update(next: GraphAppearance) = onEvent(RoverVisualizerEvent.ChangeGraphAppearance(next))

    ControlPanelSection(
        title = "Size and spacing",
        icon = Icons.Outlined.Tune,
        description = "Display changes apply immediately, including during playback."
    ) {
        ChipGroup {
            RoverFilterChip(selected = appearance == GraphAppearance(), onClick = { update(GraphAppearance()) }, label = { Text("Balanced") })
            val overview = GraphAppearance(nodeScale = 0.65f, edgeScale = 0.6f, labelScale = 0.85f)
            RoverFilterChip(selected = appearance == overview, onClick = { update(overview) }, label = { Text("Overview") })
        }
        ToggleRow("Auto-size when zoomed out", appearance.adaptiveSizing,
            supportingText = "Shrinks nodes and lines in crowded views; restores their size as you zoom in.",
            onCheckedChange = { update(appearance.copy(adaptiveSizing = it)) })
        DecimalSliderRow("Node size", appearance.nodeScale, 0.1f..2.5f,
            "${(appearance.nodeScale * 100).roundToInt()}%") { update(appearance.copy(nodeScale = it)) }
        ToggleRow("Enlarge nodes at close zoom", appearance.enlargeNodesAtCloseZoom,
            supportingText = "Grows nodes and inside-node costs when there is space. Overview nodes stay small; edges thicken more gently.",
            onCheckedChange = { update(appearance.copy(enlargeNodesAtCloseZoom = it)) })
        DecimalSliderRow("Edge thickness", appearance.edgeScale, 0.1f..2.5f,
            "${(appearance.edgeScale * 100).roundToInt()}%") { update(appearance.copy(edgeScale = it)) }
        DecimalSliderRow("Label size", appearance.labelScale, 0.6f..1.8f,
            "${(appearance.labelScale * 100).roundToInt()}%") { update(appearance.copy(labelScale = it)) }
        ToggleRow("Hide labels in crowded views", appearance.hideCrowdedLabels,
            supportingText = "Turn off to show enabled labels at every zoom. Selected items always reveal their enabled labels.",
            onCheckedChange = { update(appearance.copy(hideCrowdedLabels = it)) })
        if (appearance.hideCrowdedLabels) {
            DecimalSliderRow("Reveal labels", appearance.labelRevealSpacing, 12f..120f,
                when {
                    appearance.labelRevealSpacing < 40f -> "Earlier"
                    appearance.labelRevealSpacing <= 64f -> "Balanced"
                    else -> "Later"
                }) { update(appearance.copy(labelRevealSpacing = it)) }
            Text("Left: show details while zoomed out. Right: wait until closer. Applies to costs, IDs, coordinates and edge weights; disabled annotations stay off.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HorizontalDivider()
        Text("Layout · next generation", style = MaterialTheme.typography.labelLarge)
        NodeSpacingControls(state.graphGeneration.copy(mode = state.spaceMode)) { next ->
            onEvent(RoverVisualizerEvent.ChangeGraphGeneration(next))
        }
        OutlinedButton(onClick = onOpenGeneration, modifier = Modifier.fillMaxWidth()) {
            Text("Open graph generation")
        }
    }
}
