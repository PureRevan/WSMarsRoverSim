package sfg.wseminar.rover.export

import sfg.wseminar.rover.ui.canvas.drawSimulationFrame
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.*
import kotlin.test.Test
import kotlin.time.measureTimedValue

class GraphImageExportTest {
    @Test fun graphLabelsStayCompactAndRasterExportPreservesDimensions() = kotlinx.coroutines.runBlocking {
        val graph = sfg.wseminar.rover.graph.generator.GraphGenerator.generate(
            sfg.wseminar.rover.ui.states.GraphGenerationState(simpleNodeCount = 1000, seed = 42).toOptions())
        val frame = sfg.wseminar.rover.algorithms.backend.AlgorithmRunner.createTimeline(
            sfg.wseminar.rover.algorithms.Dijkstra, graph, graph.nodes.first(), graph.nodes.last()).frameAt(0)!!
        val source = IntSize(800, 600)
        val original = sfg.wseminar.rover.ui.states.VisualizerState(frames = listOf(frame), canvasSize = source)
        val colors = sfg.wseminar.rover.ui.canvas.RoverCanvasColors(
            Color.Gray, Color.Gray, Color.Gray, Color.Blue, Color.Yellow, Color.White,
            Color.Cyan, Color.Green, Color.Red, Color.Green, Color.Green, Color.Magenta, Color.Black)
        val measurer = TextMeasurer(createFontFamilyResolver(), Density(1f), LayoutDirection.Ltr, cacheSize = 2048)
        val geometry = sfg.wseminar.rover.ui.canvas.CanvasGeometryCache()
        val capture = FrameImageCapture()
        capture.updateScene(source, Density(1f), LayoutDirection.Ltr, graph) { options, batch ->
            val scene = exportSceneState(original, options, IntSize(size.width.toInt(), size.height.toInt()))
            if (batch <= 0) drawRect(Color.White)
            drawSimulationFrame(frame, scene, colors, geometry.get(graph, scene.viewport, scene.canvasSize),
                { text, position, style, color, _ ->
                    val layout = measurer.measure(text, TextStyle(color = color ?: Color.Black, fontSize = 11.sp))
                    drawCompactRoundRect(Color.White, position - Offset(4f, 3f), androidx.compose.ui.geometry.Size(layout.size.width + 8f, layout.size.height + 6f), androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                    drawText(layout, topLeft = position)
                }, batch)
        }
        for (format in listOf(ImageFormat.SVG, ImageFormat.PNG)) {
            val options = ImageExportOptions(format = format, width = 1920, nodeIds = true, costs = true, edgeWeights = true, legend = true)
            val result = measureTimedValue { capture.snapshotAsync()(options) }
            if (format == ImageFormat.SVG) {
                kotlin.test.assertTrue(result.value.size < 6_000_000, "Rounded label geometry must remain compact: ${result.value.size} bytes")
                kotlin.test.assertTrue(result.value.decodeToString().contains("<text"))
            } else {
                val image = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(result.value))
                kotlin.test.assertEquals(1920, image.width)
                kotlin.test.assertEquals(1440, image.height)
            }
        }
    }

}
