package sfg.wseminar.rover.ui.canvas

import sfg.wseminar.rover.export.ExportEdgeBatchSize
import sfg.wseminar.rover.export.ExportNodeBatchSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import sfg.wseminar.rover.SimulationFrame
import sfg.wseminar.rover.graph.Graph.Node
import sfg.wseminar.rover.graph.Graph.Edge
import sfg.wseminar.rover.ui.states.GraphRenderMode
import sfg.wseminar.rover.ui.states.NodeColoringMode
import sfg.wseminar.rover.ui.states.ViewportState
import sfg.wseminar.rover.ui.states.VisualizerState
import kotlin.math.abs
import kotlin.math.roundToInt

enum class CanvasLabelStyle {
    NODE_ID,
    COORDINATES,
    COST,
    NODE_COST,
    EDGE_WEIGHT
}

typealias CanvasLabelDrawer = DrawScope.(
    text: String,
    position: Offset,
    style: CanvasLabelStyle,
    preferredColor: Color?,
    nodeRadius: Float?
) -> Unit

fun DrawScope.toCanvasOffset(
    node: Node,
    viewport: ViewportState
): Offset =
    transformWorldToCanvas(
        world = Offset(
            x = node.x.toFloat(),
            y = node.y.toFloat()
        ),
        viewport = viewport,
        canvasWidth = size.width,
        canvasHeight = size.height
    )

private fun DrawScope.drawRoute(edges: List<Edge>, geometry: CanvasGeometry, color: Color, strokeWidth: Float) {
    for (edge in edges) {
        val line = geometry.edges[edge.id]
        if (!line.onScreen(size.width, size.height, 12f)) continue
        drawLine(color, line.start, line.bend, strokeWidth)
        drawLine(color, line.bend, line.end, strokeWidth)
    }
}

fun DrawScope.drawEdges(
    frame: SimulationFrame,
    state: VisualizerState,
    geometry: CanvasGeometry,
    colors: RoverCanvasColors,
    showEdgeWeights: Boolean,
    metrics: GraphDisplayMetrics,
    drawLabel: CanvasLabelDrawer,
    edgeRange: IntRange = frame.graph.edges.indices
) {
    edgeRange.forEach { edgeIndex ->
        val edge = frame.graph.edges[edgeIndex]
        val line = geometry.edges[edge.id]
        if (!line.onScreen(size.width, size.height, 150f * state.options.appearance.labelScale)) return@forEach
        val nodeA = line.start
        val nodeB = line.end

        val isKnown = edge in frame.knownEdges
        if (!frame.isEdgeVisible(edge, state.options)) return@forEach
        val isSelected = edge == state.selectedEdge

        val color = when {
            isSelected -> colors.selectedColor
            isKnown -> colors.knownEdgeColor
            else -> colors.unknownEdgeColor
        }
        val width = when {
            isSelected -> (metrics.edgeWidth * 2f).coerceAtLeast(1.5f)
            isKnown -> metrics.edgeWidth
            else -> metrics.edgeWidth * 2f / 3f
        }
        drawLine(color, nodeA, line.bend, width)
        drawLine(color, line.bend, nodeB, width)
        if (!edge.bidirectional) {
            val delta = nodeB - line.bend
            val length = delta.getDistance()
            if (length > 8f) {
                val direction = delta / length
                val tip = line.bend + delta * 0.65f
                val normal = Offset(-direction.y, direction.x)
                val arrow = (width * 3f).coerceIn(4f, 9f)
                drawLine(color, tip, tip - direction * arrow + normal * arrow * 0.55f, width)
                drawLine(color, tip, tip - direction * arrow - normal * arrow * 0.55f, width)
            }
        }

        if (showEdgeWeights && isKnown && (metrics.showLabels || isSelected)) {
            val midpoint = line.bend
            if (midpoint.x < -100f || midpoint.y < -50f || midpoint.x > size.width + 100f || midpoint.y > size.height + 50f) return@forEach

            val perpendicular = edgeLabelOffset(
                start = nodeA,
                end = nodeB
            )

            drawLabel(
                formatCoordinate(edge.weight),
                midpoint + perpendicular,
                CanvasLabelStyle.EDGE_WEIGHT,
                null, null
            )
        }
    }
}

fun DrawScope.drawSimulationFrame(
    frame: SimulationFrame,
    state: VisualizerState,
    colors: RoverCanvasColors,
    geometry: CanvasGeometry,
    drawLabel: CanvasLabelDrawer,
    batch: Int = -1
) {
    val edgeBatches = ((frame.graph.edges.size + ExportEdgeBatchSize - 1) / ExportEdgeBatchSize).coerceAtLeast(1)
    val nodeBatch = batch - edgeBatches
    val nodePositions = geometry.nodes
    val metrics = graphDisplayMetrics(frame.graph, state.viewport, size.width, size.height, state.options.appearance)

    if (batch < edgeBatches) drawEdges(
        frame = frame,
        state = state,
        geometry = geometry,
        colors = colors,
        showEdgeWeights = state.options.showEdgeWeights,
        metrics = metrics,
        drawLabel = drawLabel,
        edgeRange = if (batch < 0) frame.graph.edges.indices else (batch * ExportEdgeBatchSize until minOf((batch + 1) * ExportEdgeBatchSize, frame.graph.edges.size))
    )

    if ((batch < 0 || nodeBatch == 0) && state.options.showFinalPath && state.options.finalPathScale > 0f) {
        drawRoute(frame.finalPathEdges, geometry, colors.finalPathColor, (metrics.edgeWidth * 7f / 3f).coerceAtLeast(1.5f) * state.options.finalPathScale)
    }
    if ((batch < 0 || nodeBatch == 0) && frame.finalPathNodes.isEmpty()) {
        drawRoute(frame.currentPathEdges, geometry, colors.currentPathColor, (metrics.edgeWidth * 5f / 3f).coerceAtLeast(1.2f))
    }
    if (batch < 0 || nodeBatch == 0) state.selectedEdge?.takeIf { frame.isEdgeVisible(it, state.options) }?.let {
        drawRoute(listOf(it), geometry, colors.selectedColor, (metrics.edgeWidth * 7f / 3f).coerceAtLeast(1.5f))
    }

    val nodeRange = if (batch < 0) frame.graph.nodes.indices else if (nodeBatch < 0) IntRange.EMPTY else (nodeBatch * ExportNodeBatchSize until minOf((nodeBatch + 1) * ExportNodeBatchSize, frame.graph.nodes.size))
    nodeRange.forEach { nodeIndex ->
        val node = frame.graph.nodes[nodeIndex]
        val center = nodePositions.getOrNull(node.id) ?: return@forEach

        val isKnown = node in frame.knownNodes
        if (!frame.isNodeVisible(node, state.options)) return@forEach
        val isVisited = node in frame.visitedNodes
        val isFrontier = node in frame.frontierNodes
        val isStart = node == frame.startNode
        val isGoal = node == frame.goalNode
        val isRover = node == frame.roverNode
        val isSelected = node == state.selectedNode
        val radius = metrics.radius(isStart || isGoal || isRover || isSelected)
        val symbolScale = radius / 10.5f
        val showLabels = metrics.showLabels || isSelected
        // Avoid text measurement and circle drawing for off-screen nodes.
        val margin = 150f * state.options.appearance.labelScale + radius
        if (center.x < -margin || center.y < -margin || center.x > size.width + margin || center.y > size.height + margin) return@forEach

        val fillColor = when (state.options.nodeColoringMode) {
            NodeColoringMode.SIMPLE -> when {
                isRover -> colors.roverColor
                isStart -> colors.startColor
                isGoal -> colors.goalColor
                state.options.showVisited && isVisited -> colors.visitedColor
                isKnown -> colors.normalNodeColor
                else -> colors.unknownColor
            }

            NodeColoringMode.DISTINCTIVE -> when {
                isRover -> colors.roverColor
                isStart -> colors.startColor
                isGoal -> colors.goalColor
                state.options.showVisited && isVisited -> colors.visitedColor
                state.options.showFrontier && isFrontier -> colors.frontierColor
                isKnown -> colors.normalNodeColor
                else -> colors.unknownColor
            }
        }

        drawCircle(
            color = Color.White.copy(alpha = 0.95f),
            radius = (if (isSelected) 18f else 14f) * symbolScale,
            center = center
        )

        if (isSelected) {
            drawCircle(
                color = colors.selectedColor,
                radius = 17f * symbolScale,
                center = center,
                style = Stroke(width = (4f * symbolScale).coerceAtLeast(1f))
            )
        }

        drawCircle(
            color = fillColor,
            radius = radius,
            center = center
        )

        drawCircle(
            color = colors.labelColor.copy(alpha = 0.55f),
            radius = radius,
            center = center,
            style = Stroke(width = (1.3f * symbolScale).coerceAtLeast(0.5f))
        )
        if (state.options.showNodeIds && showLabels) {
            drawLabel(
                node.label,
                center + Offset(radius + 5.5f, -radius - 25.5f * state.options.appearance.labelScale),
                CanvasLabelStyle.NODE_ID,
                null, null
            )
        }

        if (state.options.showCoordinates && showLabels) {
            drawLabel(
                "(${formatCoordinate(node.x)}, ${formatCoordinate(node.y)})",
                center + Offset(radius + 7.5f, -14f * state.options.appearance.labelScale),
                CanvasLabelStyle.COORDINATES,
                null, null
            )
        }

        if (showLabels) {
            val cost = frame.nodeCosts.getOrNull(node.id)?.takeIf { state.options.showCosts && it.isFinite() }
            val heuristic = frame.nodeCostFields["h"]?.getOrNull(node.id)?.takeIf { state.options.showHeuristics && it.isFinite() }
            val inside = state.options.graphRenderMode == GraphRenderMode.GRAPH &&
                state.options.showCostsInsideNodes && radius >= 9f
            if (cost != null && inside) {
                drawLabel(formatCompactCost(cost), center, CanvasLabelStyle.NODE_COST, readableTextColorFor(fillColor), radius)
            }
            // One measured multiline block keeps g/h separated at every font and display scale.
            val annotations = listOfNotNull(
                cost?.takeUnless { inside }?.let { "g=${formatCompactCost(it)}" },
                heuristic?.let { "h=${formatCompactCost(it)}" }
            ).joinToString("\n")
            if (annotations.isNotEmpty()) {
                drawLabel(annotations, center + Offset(radius + 7.5f, 10f * state.options.appearance.labelScale), CanvasLabelStyle.COST, null, null)
            }
        }
    }
}

private fun readableTextColorFor(
    background: Color
): Color {
    val luminance = 0.299f * background.red +
            0.587f * background.green +
            0.114f * background.blue

    return if (luminance > 0.62f || background.alpha < 0.45f) {
        Color.Black.copy(alpha = 0.86f)
    } else {
        Color.White.copy(alpha = 0.94f)
    }
}

private fun formatCompactCost(value: Double): String {
    val absValue = abs(value)

    fun format(number: Double, suffix: String): String {
        val rounded = (number * 10).roundToInt() / 10.0
        return if (rounded % 1.0 == 0.0) {
            "${rounded.toInt()}$suffix"
        } else {
            "$rounded$suffix"
        }
    }

    return when {
        absValue >= 1_000_000_000 -> format(value / 1_000_000_000, "B")
        absValue >= 1_000_000 -> format(value / 1_000_000, "M")
        absValue >= 1_000 -> format(value / 1_000, "k")
        else -> formatCoordinate(value)
    }
}

private fun edgeLabelOffset(
    start: Offset,
    end: Offset
): Offset {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)

    return Offset(
        x = -dy / length * 12f + 6f,
        y = dx / length * 12f + 6f
    )
}

private fun formatCoordinate(
    value: Double
): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0 && abs(rounded) < Long.MAX_VALUE.toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}
