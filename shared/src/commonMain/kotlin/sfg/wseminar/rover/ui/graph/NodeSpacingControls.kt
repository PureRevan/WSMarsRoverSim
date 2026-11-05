package sfg.wseminar.rover.ui.graph

import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import sfg.wseminar.rover.ui.components.DecimalSliderRow
import sfg.wseminar.rover.ui.states.GraphGenerationState
import sfg.wseminar.rover.ui.states.SpaceMode
import sfg.wseminar.rover.graph.generator.GraphLayoutMetrics
import kotlin.math.roundToInt

@Composable
fun NodeSpacingControls(generation: GraphGenerationState, onChange: (GraphGenerationState) -> Unit) {
    if (generation.mode == SpaceMode.CONTINUOUS && !(generation.advanced && generation.allowNodeOverlap)) {
        DecimalSliderRow("Node spacing", generation.nodeSpacing.toFloat(), 0f..1f,
            when {
                generation.nodeSpacing < 0.33 -> "Loose"
                generation.nodeSpacing < 0.67 -> "Natural"
                else -> "Evenly spread"
            }) { onChange(generation.copy(nodeSpacing = it.toDouble())) }
        Text("Higher spacing keeps centers farther apart and reduces clusters. Applied when you generate a new map; the distance adapts to its node count.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (generation.advanced && generation.resolvedNodeCount() >= 2) {
            val separation = GraphLayoutMetrics.minNodeSeparation(generation.resolvedNodeCount(), 1.0,
                generation.nodeClearanceMultiplier, generation.nodeSpacing)
            val percent = (separation * 10_000).roundToInt() / 100.0
            Text("Minimum center distance: $percent% of map width.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Text(if (generation.mode == SpaceMode.DISCRETE) {
            "Grid centers are already evenly spaced. Use smaller display sizes for a clearer overview."
        } else {
            "Disable overlapping nodes in advanced generation settings to control minimum spacing."
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
