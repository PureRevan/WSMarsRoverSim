package sfg.wseminar.rover.ui.canvas

import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import sfg.wseminar.rover.export.drawCompactRoundRect
import sfg.wseminar.rover.export.FrameImageCapture
import sfg.wseminar.rover.export.exportSceneState
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent
import sfg.wseminar.rover.ui.states.VisualizerState

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RoverCanvas(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit,
    modifier: Modifier = Modifier,
    imageCapture: FrameImageCapture? = null
) {
    val geometryCache = remember { CanvasGeometryCache() }
    val exportGeometryCache = remember { CanvasGeometryCache() }
    val latestState by rememberUpdatedState(state)
    val latestOnEvent by rememberUpdatedState(onEvent)

    val textMeasurer = rememberTextMeasurer()
    val exportTextMeasurer = rememberTextMeasurer(cacheSize = 2048)

    val background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.85f)

    val unknownColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val knownEdgeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val unknownEdgeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val visitedColor = MaterialTheme.colorScheme.primary
    val frontierColor = Color(0xFFFFB300)
    val normalNodeColor = MaterialTheme.colorScheme.surface
    val currentPathColor = Color(0xFF00ACC1)
    val finalPathColor = Color(0xFF43A047)
    val roverColor = Color(0xFFD32F2F)
    val startColor = Color(0xFF2E7D32)
    val goalColor = Color(0xFF62BD48)
    val selectedColor = Color(0xFFFF6D00)
    val labelColor = MaterialTheme.colorScheme.onSurface

    val labelBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
    val coordinateLabelColor = MaterialTheme.colorScheme.tertiary
    val costLabelColor = MaterialTheme.colorScheme.primary
    val edgeWeightLabelColor = MaterialTheme.colorScheme.secondary
    val legendBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
    val legendBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)

    val language = sfg.wseminar.rover.i18n.LocalLanguage.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val surface = MaterialTheme.colorScheme.surface
    val drawScene: DrawScope.(VisualizerState, Int) -> Unit = drawScene@{ scene, batch ->
        val sceneTextMeasurer = if (batch >= 0) exportTextMeasurer else textMeasurer
        val sceneGeometry = if (batch >= 0) exportGeometryCache else geometryCache
        if (batch <= 0) {
            drawRect(surface)
            drawRect(color = background)

            if (scene.options.showGridLines) {
                drawGrid(
                    viewport = scene.viewport,
                    color = gridColor,
                    axisColor = axisColor
                )
            }
        }
        val frame = scene.currentFrame ?: return@drawScene

        val canvasColors = RoverCanvasColors(
            unknownColor = unknownColor,
            knownEdgeColor = knownEdgeColor,
            unknownEdgeColor = unknownEdgeColor,
            visitedColor = visitedColor,
            frontierColor = frontierColor,
            normalNodeColor = normalNodeColor,
            currentPathColor = currentPathColor,
            finalPathColor = finalPathColor,
            roverColor = roverColor,
            startColor = startColor,
            goalColor = goalColor,
            selectedColor = selectedColor,
            labelColor = labelColor
        )

        drawSimulationFrame(
            frame = frame,
            state = scene,
            batch = batch,
            colors = canvasColors,
            geometry = sceneGeometry.get(frame.graph, scene.viewport, IntSize(size.width.toInt(), size.height.toInt())),
            drawLabel = { text, position, style, preferredColor, nodeRadius ->
                val textColor = preferredColor ?: when (style) {
                    CanvasLabelStyle.NODE_ID -> labelColor
                    CanvasLabelStyle.COORDINATES -> coordinateLabelColor
                    CanvasLabelStyle.COST -> costLabelColor
                    CanvasLabelStyle.NODE_COST -> labelColor
                    CanvasLabelStyle.EDGE_WEIGHT -> edgeWeightLabelColor
                }

                val fontSize = when (style) {
                    CanvasLabelStyle.NODE_COST -> (9.5f * scene.options.appearance.labelScale * ((nodeRadius ?: 10.5f) / 10.5f).coerceIn(1f, 1.6f)).sp
                    else -> (11f * scene.options.appearance.labelScale).sp
                }

                val measured = sceneTextMeasurer.measure(
                    text = text,
                    style = TextStyle(
                        color = textColor,
                        fontSize = fontSize
                    )
                )

                val topLeft = when (style) {
                    CanvasLabelStyle.NODE_COST -> Offset(
                        x = position.x - measured.size.width / 2f,
                        y = position.y - measured.size.height / 2f
                    )

                    else -> position
                }

                val drawBackground = style != CanvasLabelStyle.NODE_COST

                if (drawBackground) {
                    drawCompactRoundRect(
                        color = labelBackground,
                        topLeft = topLeft - Offset(4f, 3f),
                        size = Size(
                            width = measured.size.width + 8f,
                            height = measured.size.height + 6f
                        ),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }

                if (style == CanvasLabelStyle.NODE_COST && nodeRadius != null) {
                    drawFittedNodeCost(measured, position, nodeRadius)
                } else {
                    drawText(textLayoutResult = measured, topLeft = topLeft)
                }
            }
        )

        if (scene.options.showLegend && (batch < 0 || batch == sfg.wseminar.rover.export.exportBatchCount(frame.graph) - 1)) {
            drawCanvasLegend(
                language = language,
                textMeasurer = sceneTextMeasurer,
                colors = canvasColors,
                state = scene,
                background = legendBackground,
                border = legendBorder,
                labelColor = labelColor
            )
        }
    }
    SideEffect {
        imageCapture?.hasHeuristics = state.currentFrame?.nodeCostFields?.containsKey("h") == true
        imageCapture?.updateScene(state.canvasSize, density, layoutDirection, state.currentFrame?.graph) { options, batch ->
            drawScene(exportSceneState(state, options, IntSize(size.width.toInt(), size.height.toInt())), batch)
        }
    }
    DisposableEffect(imageCapture) {
        onDispose { imageCapture?.clear() }
    }

    Box(
        modifier = modifier
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val currentState = latestState
                val scrollDelta = event.wheelScrollSteps()

                if (scrollDelta.isFinite() && scrollDelta != 0f && currentState.canvasSize.width > 0 && currentState.canvasSize.height > 0) {
                    val pointerPosition = event.changes.first().position

                    val viewport = calculateCursorAnchoredViewport(
                        oldViewport = currentState.viewport,
                        cursorPosition = pointerPosition,
                        zoomFactor = calculateWheelZoomFactor(scrollDelta),
                        canvasWidth = currentState.canvasSize.width.toFloat(),
                        canvasHeight = currentState.canvasSize.height.toFloat()
                    )

                    latestOnEvent(
                        RoverVisualizerEvent.ChangeViewport(
                            zoom = viewport.zoom,
                            panX = viewport.panX,
                            panY = viewport.panY
                        )
                    )
                    // Compose Web prevents the browser's default scroll/zoom only
                    // when the event is consumed by the canvas.
                    event.changes.forEach { it.consume() }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val currentState = latestState

                    if (currentState.canvasSize.width <= 0 || currentState.canvasSize.height <= 0) {
                        return@detectTransformGestures
                    }

                    val viewport = calculateCentroidAnchoredViewport(
                        oldViewport = currentState.viewport,
                        centroid = centroid,
                        pan = pan,
                        zoomFactor = zoom,
                        canvasWidth = currentState.canvasSize.width.toFloat(),
                        canvasHeight = currentState.canvasSize.height.toFloat()
                    )

                    latestOnEvent(
                        RoverVisualizerEvent.ChangeViewport(
                            zoom = viewport.zoom,
                            panX = viewport.panX,
                            panY = viewport.panY
                        )
                    )
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tapPosition ->
                    val currentState = latestState
                    val frame = currentState.currentFrame

                    if (frame == null) {
                        latestOnEvent(RoverVisualizerEvent.ClearSelection)
                        return@detectTapGestures
                    }

                    val hit = GraphHit.find(
                        graph = frame.graph,
                        viewport = currentState.viewport,
                        canvasSize = size,
                        tapPosition = tapPosition,
                        appearance = currentState.options.appearance,
                        highlightedNodes = listOfNotNull(frame.startNode, frame.goalNode, frame.roverNode, currentState.selectedNode).toSet(),
                        visibleNode = { frame.isNodeVisible(it, currentState.options) },
                        visibleEdge = { frame.isEdgeVisible(it, currentState.options) },
                        geometry = geometryCache.get(frame.graph, currentState.viewport, size)
                    )

                    when (hit) {
                        is GraphHit.NodeHit -> latestOnEvent(RoverVisualizerEvent.SelectNode(hit.node))
                        is GraphHit.EdgeHit -> latestOnEvent(RoverVisualizerEvent.SelectEdge(hit.edge))
                        GraphHit.Empty -> latestOnEvent(RoverVisualizerEvent.ClearSelection)
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawScene(state, -1)

        }
    }
}
