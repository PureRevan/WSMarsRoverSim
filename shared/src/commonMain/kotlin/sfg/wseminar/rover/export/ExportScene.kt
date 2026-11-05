package sfg.wseminar.rover.export

import androidx.compose.ui.unit.IntSize
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.ui.canvas.calculateFitViewport
import sfg.wseminar.rover.ui.canvas.graphDisplayMetrics
import sfg.wseminar.rover.ui.states.GraphAppearance
import sfg.wseminar.rover.ui.states.VisualizerState
import kotlin.math.ceil
import kotlin.math.max

/** A density-based recommendation, not a cap or a guarantee for coincident nodes. */
internal fun recommendedDetailWidth(source: IntSize, graph: Graph?): Int {
    if (source.width <= 0 || source.height <= 0 || graph == null) return source.width.coerceAtLeast(1)
    val viewport = calculateFitViewport(graph, source)
    val spacing = graphDisplayMetrics(graph, viewport, source.width.toFloat(), source.height.toFloat(), GraphAppearance()).estimatedSpacing
    // About 64 logical pixels between nodes at 1.5 output pixels per logical pixel:
    // ordinary 11sp annotations render around 16.5px without a 160px spacing target.
    val scale = if (spacing.isFinite() && spacing > 0) max(1.0, 96.0 / spacing) else 1.0
    return ceil(source.width * scale).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
}

/** Export-only overrides never modify the live sidebar or viewport. */
internal fun exportSceneState(state: VisualizerState, options: ImageExportOptions, logicalSize: IntSize): VisualizerState {
    val graph = state.currentFrame?.graph
    val scale = logicalSize.width.toFloat() / state.canvasSize.width.coerceAtLeast(1)
    val viewport = if (options.wholeGraph && graph != null) calculateFitViewport(graph, logicalSize)
        else state.viewport.copy(panX = state.viewport.panX * scale, panY = state.viewport.panY * scale)
    val appearance = if (options.fullDetail) state.options.appearance.copy(hideCrowdedLabels = false, adaptiveSizing = false) else state.options.appearance
    return state.copy(viewport = viewport, canvasSize = logicalSize, options = state.options.copy(
        appearance = appearance,
        showNodeIds = options.nodeIds ?: state.options.showNodeIds,
        showHeuristics = options.heuristics ?: state.options.showHeuristics,
        showCostsInsideNodes = options.costsInsideNodes ?: state.options.showCostsInsideNodes,
        showCosts = options.costs ?: state.options.showCosts,
        showEdgeWeights = options.edgeWeights ?: state.options.showEdgeWeights,
        showLegend = options.legend ?: state.options.showLegend
    ))
}
