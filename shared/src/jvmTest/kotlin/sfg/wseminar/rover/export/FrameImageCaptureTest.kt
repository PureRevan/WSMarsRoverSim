package sfg.wseminar.rover.export

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.*

class FrameImageCaptureTest {
    @Test fun higherResolutionScalesTheEntireSceneAndJpegEncodes() {
        for (format in listOf(ImageFormat.PNG, ImageFormat.JPEG)) {
            val bytes = renderImage(IntSize(80, 40), Density(1f), LayoutDirection.Ltr, ImageExportOptions(3, format, 95, fullDetail = false)) {
                drawRect(Color.White)
                drawRect(Color.Red, Offset(40f, 0f), Size(40f, 40f))
            }
            val image = ImageIO.read(ByteArrayInputStream(bytes))
            assertEquals(240, image.width)
            assertEquals(120, image.height)
            assertTrue((image.getRGB(200, 50) shr 16 and 255) > 240)
            assertTrue((image.getRGB(200, 50) and 255) < 20)
            assertTrue((image.getRGB(20, 50) and 255) > 240)
        }
    }

    @Test fun pngPreservesDimensionsColorsAndClipping() {
        val bytes = renderPng(IntSize(80, 40), Density(2f), LayoutDirection.Ltr) {
            drawRect(Color.White)
            drawRect(Color.Red, Offset(60f, 10f), Size(40f, 10f))
        }
        val decoded = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(80, decoded.width)
        assertEquals(40, decoded.height)
        assertEquals(0xFFFFFFFF.toInt(), decoded.getRGB(10, 10))
        assertEquals(0xFFFF0000.toInt(), decoded.getRGB(79, 15))
        assertEquals(0xFFFFFFFF.toInt(), decoded.getRGB(79, 30))
    }

    @Test fun captureUsesLatestSceneAndReleasesDisposedScene() {
        val capture = FrameImageCapture()
        assertFailsWith<IllegalStateException> { capture.capturePng() }
        capture.update(IntSize(12, 8), Density(1f), LayoutDirection.Ltr) { drawRect(Color.Red) }
        val first = capture.capturePng()
        capture.update(IntSize(20, 10), Density(1f), LayoutDirection.Ltr) { drawRect(Color.Blue) }
        val second = ImageIO.read(ByteArrayInputStream(capture.capturePng()))
        assertEquals(20, second.width)
        assertEquals(0xFF0000FF.toInt(), second.getRGB(0, 0))
        assertEquals(0xFFFF0000.toInt(), ImageIO.read(ByteArrayInputStream(first)).getRGB(0, 0))
        capture.clear()
        assertFailsWith<IllegalStateException> { capture.capturePng() }
    }

    @Test fun oversizedAndEmptyCanvasesFailBeforeDrawing() {
        for (size in listOf(IntSize.Zero, IntSize(-1, 20))) {
            assertFailsWith<IllegalArgumentException> {
                renderPng(size, Density(1f), LayoutDirection.Ltr) { fail("Must not draw an invalid image") }
            }
        }
    }

    @Test fun largeDimensionsAreAllowedWithoutAllocatingRaster() {
        assertEquals(IntSize(100_000, 50_000), exportDimensions(IntSize(800, 400), ImageExportOptions(width = 100_000)))
        assertFailsWith<IllegalArgumentException> { exportDimensions(IntSize(800, 400), ImageExportOptions(scale = Int.MAX_VALUE)) }
    }

    @Test fun svgUsesVectorGeometry() {
        for (format in listOf(ImageFormat.SVG)) {
            val bytes = renderImage(IntSize(80, 40), Density(1f), LayoutDirection.Ltr, ImageExportOptions(format = format, width = 320, fullDetail = true)) {
                assertEquals(Size(320f, 160f), size)
                drawRect(Color.White)
                drawCircle(Color.Red, 10f, Offset(160f, 80f))
            }
            if (format == ImageFormat.SVG) {
                val xml = bytes.decodeToString()
                assertTrue(xml.contains("<svg"))
                assertTrue(xml.contains("<ellipse") || xml.contains("<circle") || xml.contains("<path"))
                assertFalse(xml.contains("<image"))
            } else {
                val image = org.jetbrains.skia.Image.makeFromEncoded(bytes)
                try { assertEquals(320, image.width); assertEquals(160, image.height) } finally { image.close() }
            }
        }
    }

    @Test fun readablePresetProvidesSpaceForTenThousandNodesAndDoesNotChangeLiveSettings() {
        val graph = sfg.wseminar.rover.graph.Graph(List(10_000) { id ->
            sfg.wseminar.rover.graph.Graph.Node((id % 100).toDouble(), (id / 100).toDouble(), id)
        }, emptyList())
        val source = IntSize(800, 600)
        val width = recommendedDetailWidth(source, graph)
        assertTrue(width in (source.width * 8)..(source.width * 30))
        val output = exportDimensions(source, ImageExportOptions(width = width))
        val state = sfg.wseminar.rover.ui.states.VisualizerState(canvasSize = source)
        val exported = exportSceneState(state, ImageExportOptions(fullDetail = true, costs = true, heuristics = true), output)
        val metrics = sfg.wseminar.rover.ui.canvas.graphDisplayMetrics(graph,
            sfg.wseminar.rover.ui.canvas.calculateFitViewport(graph, output),
            output.width.toFloat(), output.height.toFloat(), exported.options.appearance)
        assertTrue(metrics.estimatedSpacing in 95f..100f)
        assertTrue(exported.options.showHeuristics)
        assertFalse(state.options.showHeuristics)
        assertTrue(metrics.showLabels)
        assertFalse(exported.options.appearance.adaptiveSizing)
        assertTrue(state.options.appearance.adaptiveSizing)
        assertTrue(state.options.appearance.hideCrowdedLabels)
        assertEquals(source, state.canvasSize)
    }

    @Test fun asynchronousRasterAndPdfExportsPreservePixels() = runBlocking {
        val capture = FrameImageCapture()
        capture.update(IntSize(80, 40), Density(1f), LayoutDirection.Ltr) {
            drawRect(Color.White)
            drawRect(Color.Red, Offset(40f, 0f), Size(40f, 40f))
        }
        val png = capture.snapshotAsync()(ImageExportOptions(format = ImageFormat.PNG))
        val image = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(80, image.width)
        assertEquals(0xFFFF0000.toInt(), image.getRGB(60, 20))
        val pdf = capture.snapshotAsync()(ImageExportOptions(format = ImageFormat.PDF))
        assertTrue(pdf.decodeToString().startsWith("%PDF-1.4"))
        assertTrue(pdf.decodeToString().contains("/Width 80 /Height 40"))
        val marker = "stream\n".encodeToByteArray()
        val start = pdf.indices.first { at -> at + marker.size <= pdf.size && marker.indices.all { pdf[at + it] == marker[it] } } + marker.size
        val rgb = java.util.zip.InflaterInputStream(ByteArrayInputStream(pdf, start, pdf.size - start)).readBytes()
        assertEquals(80 * 40 * 3, rgb.size)
        assertEquals(255, rgb[(20 * 80 + 60) * 3].toInt() and 255)
        assertEquals(0, rgb[(20 * 80 + 60) * 3 + 1].toInt() and 255)
    }

    @Test fun compactSvgKeepsTenThousandNodeLabelsAsText() {
        val font = org.jetbrains.skia.Font(org.jetbrains.skia.FontMgr.default.matchFamilyStyle("Arial", org.jetbrains.skia.FontStyle.NORMAL), 12f)
        val paint = org.jetbrains.skia.Paint()
        try {
            val bytes = renderImage(IntSize(16000, 16000), Density(1f), LayoutDirection.Ltr, ImageExportOptions(format = ImageFormat.SVG)) {
                drawRect(Color.White)
                repeat(10_000) { i ->
                    val x = (i % 100) * 160f + 20f; val y = (i / 100) * 160f + 20f
                    drawCircle(Color.Green, 10f, Offset(x, y))
                    drawContext.canvas.nativeCanvas.drawString("Node $i g=123.45", x + 16, y, font, paint)
                }
            }
            val svg = bytes.decodeToString()
            assertTrue(svg.contains("<text"))
            assertFalse(svg.contains("<image"))
            assertTrue(bytes.size < 10_000_000, "Text-based SVG should not expand labels into hundreds of MB")
            println("10,000-node SVG with labels: ${bytes.size} bytes")
            java.nio.file.Files.write(java.nio.file.Path.of("/tmp/rover-10000-text.svg"), bytes)
        } finally { font.close(); paint.close() }
    }

    @Test fun presetsAndSnappingChooseGraphDependentResolution() {
        val size = IntSize(800, 600)
        val options = ImageExportOptions(nodeIds = true, costs = true, edgeWeights = true, legend = true)
        assertEquals(1152, presetWidth(size, 1600, ResolutionPreset.BALANCED))
        assertEquals(11520, presetWidth(size, 16000, ResolutionPreset.BALANCED))
        assertEquals(1920, presetWidth(size, 16000, ResolutionPreset.OVERVIEW))
        assertTrue(estimatedImageBytes(size, options.copy(format = ImageFormat.SVG), 10000, 20000) in 30_000_000..80_000_000)
        val overview = options.copy(format = ImageFormat.PNG, width = 1920)
        assertTrue(estimatedImageBytes(size, overview, 10000, 18851) in 500_000..5_000_000)
        assertEquals(20f, snapResolution(19.8f))
        assertEquals(30f, snapResolution(60f))
        assertEquals(ImageFormat.PDF, options.format)
        assertTrue(options.fullDetail && options.wholeGraph)
    }

    @Test fun compactRoundedBackgroundPreservesRasterAppearance() {
        fun raster(compact: Boolean): java.awt.image.BufferedImage {
            val bytes = renderPng(IntSize(100, 60), Density(1f), LayoutDirection.Ltr) {
                drawRect(Color.White)
                if (compact) drawCompactRoundRect(Color.Black, Offset(10f, 10f), Size(80f, 40f), androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                else drawRoundRect(Color.Black, Offset(10f, 10f), Size(80f, 40f), androidx.compose.ui.geometry.CornerRadius(6f, 6f))
            }
            return ImageIO.read(ByteArrayInputStream(bytes))
        }
        val reference = raster(false); val compact = raster(true)
        var error = 0L
        for (y in 0 until 60) for (x in 0 until 100) {
            error += kotlin.math.abs((reference.getRGB(x, y) and 255) - (compact.getRGB(x, y) and 255))
        }
        assertTrue(error / 6000.0 < 1.0, "Compact curves should preserve rounded corners within antialiasing tolerance")
        assertEquals(reference.getRGB(10, 10), compact.getRGB(10, 10))
        assertEquals(reference.getRGB(50, 30), compact.getRGB(50, 30))
    }

    @Test fun normalVisibilityIsNotOverriddenBySelectedAnnotations() = runBlocking {
        val source = IntSize(200, 100)
        val state = sfg.wseminar.rover.ui.states.VisualizerState(canvasSize = source)
        val capture = FrameImageCapture()
        capture.updateScene(source, Density(1f), LayoutDirection.Ltr, null) { options, _ ->
            assertFalse(options.fullDetail)
            assertEquals(source.width.toFloat(), size.width)
            val scene = exportSceneState(state, options, source)
            assertEquals(state.options.appearance, scene.options.appearance)
            assertTrue(scene.options.showCostsInsideNodes)
            assertFalse(state.options.showCostsInsideNodes)
            drawRect(Color.White)
        }
        capture.snapshotAsync()(ImageExportOptions(format = ImageFormat.PNG, width = 400,
            fullDetail = false, nodeIds = true, costs = true, edgeWeights = true, heuristics = true, costsInsideNodes = true))
        val all = exportSceneState(state, ImageExportOptions(fullDetail = true), source)
        assertFalse(all.options.appearance.hideCrowdedLabels)
    }

    @Test fun graphRasterResolutionScalesSymbolsAndKeepsExplicitAnnotations() = runBlocking {
        val graph = sfg.wseminar.rover.graph.Graph(listOf(
            sfg.wseminar.rover.graph.Graph.Node(0.0, 0.0, 0),
            sfg.wseminar.rover.graph.Graph.Node(1.0, 1.0, 1)
        ), emptyList())
        val capture = FrameImageCapture()
        capture.updateScene(IntSize(200, 100), Density(1f), LayoutDirection.Ltr, graph) { options, batch ->
            // Deselecting the legend or one annotation must not hide the others at crowded zooms.
            assertTrue(options.fullDetail)
            if (batch == 0) {
                drawRect(Color.White)
                drawCircle(Color.Red, 10f, center)
            }
        }
        fun diameter(image: java.awt.image.BufferedImage): Int = (0 until image.width).count {
            val color = image.getRGB(it, image.height / 2)
            color shr 16 and 255 > 240 && color and 255 < 40
        }
        val options = ImageExportOptions(format = ImageFormat.PNG, fullDetail = true, nodeIds = true, legend = false)
        val small = ImageIO.read(ByteArrayInputStream(capture.snapshotAsync()(options.copy(width = 800))))
        val large = ImageIO.read(ByteArrayInputStream(capture.snapshotAsync()(options.copy(width = 1600))))
        assertTrue(kotlin.math.abs(diameter(large) - 2 * diameter(small)) <= 2,
            "Increasing resolution must add pixels to node symbols, not just increase their spacing")
    }

}
