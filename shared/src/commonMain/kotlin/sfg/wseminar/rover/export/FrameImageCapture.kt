package sfg.wseminar.rover.export

import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.TimeSource
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.DynamicMemoryWStream
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.svg.SVGCanvas
import sfg.wseminar.rover.graph.Graph

/** Re-renders a frozen scene on demand; no persistent offscreen raster is allocated. */
class FrameImageCapture {
    private var capture: ((ImageExportOptions) -> ByteArray)? = null
    private var asyncCapture: (suspend (ImageExportOptions) -> ByteArray)? = null
    var hasHeuristics: Boolean = false
    var nodeCount: Int = 0; private set
    var edgeCount: Int = 0; private set
    var size: IntSize = IntSize.Zero; private set
    var suggestedDetailWidth: Int = 1; private set

    fun update(size: IntSize, density: Density, direction: LayoutDirection, draw: DrawScope.() -> Unit) =
        updateScene(size, density, direction, null) { _, _ -> draw() }

    fun updateScene(size: IntSize, density: Density, direction: LayoutDirection, graph: Graph?, draw: DrawScope.(ImageExportOptions, Int) -> Unit) {
        this.size = size
        nodeCount = graph?.nodes?.size ?: 0
        edgeCount = graph?.edges?.size ?: 0
        suggestedDetailWidth = recommendedDetailWidth(size, graph)
        capture = if (size.width <= 0 || size.height <= 0) null else {
            { options -> renderImage(size, density, direction, options) { draw(options, -1) } }
        }
        val frozenDetailWidth = suggestedDetailWidth
        asyncCapture = if (capture == null) null else { options ->
            val detailRequested = options.fullDetail
            val framing = if (options.format == ImageFormat.SVG) options.copy(width = frozenDetailWidth) else options
            val effective = framing.copy(fullDetail = detailRequested, detailLayoutWidth = if (detailRequested && graph != null)
                (frozenDetailWidth.toLong() * 2 / 3).toInt().coerceAtLeast(size.width) else null)
            if (effective.format == ImageFormat.SVG) renderVectorImage(size, density, direction, effective, exportBatchCount(graph)) { batch -> draw(effective, batch) }
            else renderRasterImage(size, density, direction, effective, exportBatchCount(graph)) { batch -> draw(effective, batch) }
        }
    }

    fun snapshotAsync(): suspend (ImageExportOptions) -> ByteArray = checkNotNull(asyncCapture) { "The canvas is not ready." }
    fun clear() { capture = null; asyncCapture = null; size = IntSize.Zero }
    fun snapshot(): (ImageExportOptions) -> ByteArray = checkNotNull(capture) { "The canvas is not ready to export yet." }
    fun capturePng(): ByteArray = snapshot()(ImageExportOptions(format = ImageFormat.PNG))
}

enum class ImageFormat(val extension: String, val mime: String, val vector: Boolean = false) {
    PNG("png", "image/png"), JPEG("jpg", "image/jpeg"), PDF("pdf", "application/pdf"), SVG("svg", "image/svg+xml", true)
}
data class ImageExportOptions(
    val scale: Int = 1,
    val format: ImageFormat = ImageFormat.PDF,
    val quality: Int = 90,
    val width: Int? = null,
    val fullDetail: Boolean = true,
    val wholeGraph: Boolean = true,
    val nodeIds: Boolean? = null,
    val costs: Boolean? = null,
    val edgeWeights: Boolean? = null,
    val legend: Boolean? = null,
    val heuristics: Boolean? = null,
    val costsInsideNodes: Boolean? = null,
    /** Internal logical layout: raster resolution scales the complete scene, including labels. */
    val detailLayoutWidth: Int? = null
)

/** Only numeric representation limits; no app-imposed file-size or megapixel limit. */
fun exportDimensions(source: IntSize, options: ImageExportOptions): IntSize {
    require(source.width > 0 && source.height > 0) { "The canvas has no drawable area." }
    require(options.scale > 0 && options.quality in 1..100)
    val width = options.width?.toLong() ?: source.width.toLong() * options.scale
    require(width in 1..Int.MAX_VALUE.toLong()) { "Width must fit the renderer's positive 32-bit pixel dimensions." }
    val height = (width * source.height + source.width / 2) / source.width
    require(height in 1..Int.MAX_VALUE.toLong()) { "Height must fit the renderer's positive 32-bit pixel dimensions." }
    require(width * height <= Long.MAX_VALUE / 4) { "Raster dimensions exceed the renderer's addressable size." }
    return IntSize(width.toInt(), height.toInt())
}

internal fun renderPng(size: IntSize, density: Density, direction: LayoutDirection, draw: DrawScope.() -> Unit): ByteArray =
    renderImage(size, density, direction, ImageExportOptions(format = ImageFormat.PNG), draw)

internal fun renderImage(
    size: IntSize,
    density: Density,
    direction: LayoutDirection,
    options: ImageExportOptions,
    draw: DrawScope.() -> Unit
): ByteArray {
    val output = exportDimensions(size, options)
    fun paint(canvas: Canvas) {
        canvas.save()
        try {
            val logicalSize = exportLogicalSize(size, options)
            canvas.scale(output.width.toFloat() / logicalSize.width, output.height.toFloat() / logicalSize.height)
            CanvasDrawScope().draw(density, direction, canvas, Size(logicalSize.width.toFloat(), logicalSize.height.toFloat()), draw)
        } finally { canvas.restore() }
    }
    if (options.format == ImageFormat.SVG) {
        val stream = DynamicMemoryWStream()
        try {
            // Keep text as text rather than repeating every glyph outline.
            val canvas = SVGCanvas.make(0f, 0f, output.width.toFloat(), output.height.toFloat(), stream, convertTextToPaths = false, prettyXML = false)
            try { paint(canvas.asComposeCanvas()) } finally { canvas.close() }
            return ByteArray(stream.bytesWritten()).also { check(stream.read(it, 0, it.size)) }
        } finally { stream.close() }
    }
    val bitmap = ImageBitmap(output.width, output.height)
    try {
        paint(Canvas(bitmap))
        val image = Image.makeFromBitmap(bitmap.asSkiaBitmap())
        try {
            val format = when (options.format) {
                ImageFormat.PNG -> EncodedImageFormat.PNG
                ImageFormat.JPEG -> EncodedImageFormat.JPEG
                ImageFormat.PDF -> error("Use asynchronous image export for this format")
                ImageFormat.SVG -> error("Vector images use a separate renderer")
            }
            val data = checkNotNull(image.encodeToData(format, options.quality)) { "The image could not be encoded at this size on this platform. Try SVG or a smaller raster size." }
            try { return data.bytes } finally { data.close() }
        } finally { image.close() }
    } finally { bitmap.asSkiaBitmap().close() }
}


internal const val ExportEdgeBatchSize = 64
internal const val ExportNodeBatchSize = 32

internal fun exportBatchCount(graph: Graph?): Int = if (graph == null) 1 else
    ((graph.edges.size + ExportEdgeBatchSize - 1) / ExportEdgeBatchSize).coerceAtLeast(1) + ((graph.nodes.size + ExportNodeBatchSize - 1) / ExportNodeBatchSize).coerceAtLeast(1)

/** Yields between small batches of drawing commands, including on the single-threaded Web UI. */
internal suspend fun renderSvgChunks(size: IntSize, density: Density, direction: LayoutDirection,
    options: ImageExportOptions, batches: Int, draw: DrawScope.(Int) -> Unit): ByteArray {
    val output = exportDimensions(size, options)
    val stream = DynamicMemoryWStream()
    try {
        val native = SVGCanvas.make(0f, 0f, output.width.toFloat(), output.height.toFloat(), stream, false, false)
        try {
            drawExportBatches(native.asComposeCanvas(), size, density, direction, options, batches, draw)
        } finally { native.close() }
        val result = ByteArray(stream.bytesWritten())
        val chunk = ByteArray(minOf(result.size, 262144))
        var offset = 0
        var slice = TimeSource.Monotonic.markNow()
        while (offset < result.size) {
            currentCoroutineContext().ensureActive()
            val count = minOf(chunk.size, result.size - offset)
            check(stream.read(chunk, offset, count))
            chunk.copyInto(result, offset, 0, count)
            offset += count
            if (slice.elapsedNow().inWholeMilliseconds >= 8) { delay(1); slice = TimeSource.Monotonic.markNow() }
        }
        return result
    } finally { stream.close() }
}


/** Record on the owning UI thread, yielding by elapsed work rather than sleeping per batch. */
internal suspend fun drawExportBatches(canvas: Canvas, source: IntSize, density: Density,
    direction: LayoutDirection, options: ImageExportOptions, batches: Int, draw: DrawScope.(Int) -> Unit) {
    val output = exportDimensions(source, options)
    canvas.save()
    try {
        val logical = exportLogicalSize(source, options)
        canvas.scale(output.width.toFloat() / logical.width, output.height.toFloat() / logical.height)
        val scope = CanvasDrawScope()
        var slice = TimeSource.Monotonic.markNow()
        repeat(batches) { batch ->
            currentCoroutineContext().ensureActive()
            scope.draw(density, direction, canvas, Size(logical.width.toFloat(), logical.height.toFloat())) { draw(batch) }
            if (slice.elapsedNow().inWholeMilliseconds >= 8 && batch + 1 < batches) {
                delay(1)
                slice = TimeSource.Monotonic.markNow()
            }
        }
    } finally { canvas.restore() }
}

internal expect suspend fun renderRasterImage(size: IntSize, density: Density, direction: LayoutDirection,
    options: ImageExportOptions, batches: Int, draw: DrawScope.(Int) -> Unit): ByteArray

internal expect suspend fun renderVectorImage(size: IntSize, density: Density, direction: LayoutDirection,
    options: ImageExportOptions, batches: Int, draw: DrawScope.(Int) -> Unit): ByteArray

internal fun exportLogicalSize(source: IntSize, options: ImageExportOptions): IntSize = when {
    !options.fullDetail -> source
    options.detailLayoutWidth != null -> exportDimensions(source, options.copy(width = options.detailLayoutWidth))
    else -> exportDimensions(source, options)
}
