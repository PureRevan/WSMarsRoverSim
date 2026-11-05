package sfg.wseminar.rover.ui.canvas

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import sfg.wseminar.rover.ui.states.NodeColoringMode
import sfg.wseminar.rover.ui.states.VisualizerState

data class LegendEntry(
    val title: String,
    val color: Color
)

fun DrawScope.drawCanvasLegend(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    colors: RoverCanvasColors,
    state: VisualizerState,
    background: Color,
    border: Color,
    labelColor: Color,
    language: sfg.wseminar.rover.i18n.AppLanguage = sfg.wseminar.rover.i18n.AppLanguage.GERMAN
) {
    val roverColor: Color
    val startColor: Color
    val goalColor: Color
    val visitedColor: Color
    val frontierColor: Color
    val unknownColor: Color

    when (state.options.nodeColoringMode) {
        NodeColoringMode.SIMPLE -> {
            roverColor = colors.roverColor
            startColor = colors.startColor
            goalColor = colors.goalColor
            visitedColor = colors.visitedColor
            frontierColor = colors.normalNodeColor
            unknownColor = colors.unknownColor
        }

        NodeColoringMode.DISTINCTIVE -> {
            roverColor = colors.roverColor
            startColor = colors.startColor
            goalColor = colors.goalColor
            visitedColor = colors.visitedColor
            frontierColor = colors.frontierColor
            unknownColor = colors.unknownColor
        }
    }

    val entries = buildList {
        if (state.options.showNodeLegend) {
            add(LegendEntry("Exploring", roverColor))
            add(LegendEntry("Start", startColor))
            add(LegendEntry("Goal", goalColor))

            if (state.options.showVisited) {
                add(LegendEntry("Visited", visitedColor))
            }

            if (state.options.showFrontier) {
                add(LegendEntry("Frontier", frontierColor))
            }
        }

        if (state.options.showPathLegend) {
            add(LegendEntry("Current path", colors.currentPathColor))
            add(LegendEntry("Final path", colors.finalPathColor))
            add(LegendEntry("Selected", colors.selectedColor))
        }

        if (state.options.showKnowledgeLegend) {
            add(LegendEntry("Known edge", colors.knownEdgeColor))
            add(LegendEntry("Unknown edge", colors.unknownEdgeColor))
            add(LegendEntry("Unknown node", unknownColor))
        }
    }

    if (entries.isEmpty()) return

    val titleStyle = TextStyle(
        color = labelColor,
        fontSize = 12.sp
    )

    val itemStyle = TextStyle(
        color = labelColor.copy(alpha = 0.82f),
        fontSize = 11.sp
    )

    val title = sfg.wseminar.rover.i18n.translate("Legend", language)
    val titleLayout = textMeasurer.measure(
        text = title,
        style = titleStyle
    )

    val itemLayouts = entries.map { entry ->
        entry to textMeasurer.measure(
            text = sfg.wseminar.rover.i18n.translate(entry.title, language),
            style = itemStyle
        )
    }

    val horizontalPadding = 12f
    val verticalPadding = 10f
    val swatchSize = 10f
    val itemGap = 7f
    val titleGap = 9f
    val swatchTextGap = 8f

    val contentWidth = maxOf(
        titleLayout.size.width.toFloat(),
        itemLayouts.maxOf { (_, layout) ->
            swatchSize + swatchTextGap + layout.size.width
        }
    )

    val contentHeight =
        titleLayout.size.height +
                titleGap +
                itemLayouts.sumOf { (_, layout) -> layout.size.height.toDouble() }.toFloat() +
                itemGap * (itemLayouts.size - 1).coerceAtLeast(0)

    val panelWidth = contentWidth + horizontalPadding * 2f
    val panelHeight = contentHeight + verticalPadding * 2f

    val topLeft = calculateLegendTopLeftOnEdge(
        edgePosition = state.legendEdgePosition,
        canvasWidth = size.width,
        canvasHeight = size.height,
        panelWidth = panelWidth,
        panelHeight = panelHeight,
        margin = 18f
    )

    drawRoundRect(
        color = background,
        topLeft = topLeft,
        size = Size(panelWidth, panelHeight),
        cornerRadius = CornerRadius(14f, 14f)
    )

    drawRoundRect(
        color = border,
        topLeft = topLeft,
        size = Size(panelWidth, panelHeight),
        cornerRadius = CornerRadius(14f, 14f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
    )

    var cursorY = topLeft.y + verticalPadding

    drawText(
        textMeasurer = textMeasurer,
        text = title,
        topLeft = Offset(
            x = topLeft.x + horizontalPadding,
            y = cursorY
        ),
        style = titleStyle
    )

    cursorY += titleLayout.size.height + titleGap

    itemLayouts.forEach { (entry, layout) ->
        val itemCenterY = cursorY + layout.size.height / 2f

        drawRoundRect(
            color = entry.color,
            topLeft = Offset(
                x = topLeft.x + horizontalPadding,
                y = itemCenterY - swatchSize / 2f
            ),
            size = Size(swatchSize, swatchSize),
            cornerRadius = CornerRadius(3f, 3f)
        )

        drawText(
            textMeasurer = textMeasurer,
            text = sfg.wseminar.rover.i18n.translate(entry.title, language),
            topLeft = Offset(
                x = topLeft.x + horizontalPadding + swatchSize + swatchTextGap,
                y = cursorY
            ),
            style = itemStyle
        )

        cursorY += layout.size.height + itemGap
    }
}

private fun calculateLegendTopLeftOnEdge(
    edgePosition: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    panelWidth: Float,
    panelHeight: Float,
    margin: Float
): Offset {
    val left = margin
    val top = margin
    val right = canvasWidth - panelWidth - margin
    val bottom = canvasHeight - panelHeight - margin

    val clamped = edgePosition.coerceIn(-1f, 1f)

    return if (clamped <= 0f) {
        val t = -clamped

        if (t <= 0.5f) {
            val localT = t / 0.5f
            Offset(
                x = lerp(right, left, localT),
                y = top
            )
        } else {
            val localT = (t - 0.5f) / 0.5f
            Offset(
                x = left,
                y = lerp(top, bottom, localT)
            )
        }
    } else {
        val t = clamped

        if (t <= 0.5f) {
            val localT = t / 0.5f
            Offset(
                x = right,
                y = lerp(top, bottom, localT)
            )
        } else {
            val localT = (t - 0.5f) / 0.5f
            Offset(
                x = lerp(right, left, localT),
                y = bottom
            )
        }
    }
}

private fun lerp(
    start: Float,
    stop: Float,
    fraction: Float
): Float =
    start + (stop - start) * fraction.coerceIn(0f, 1f)
