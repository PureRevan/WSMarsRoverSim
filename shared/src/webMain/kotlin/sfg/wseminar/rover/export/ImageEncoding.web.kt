package sfg.wseminar.rover.export

import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import org.jetbrains.skia.*
import kotlin.time.TimeSource

internal actual suspend fun renderRasterImage(size: IntSize, density: Density, direction: LayoutDirection,
    options: ImageExportOptions, batches: Int, draw: DrawScope.(Int) -> Unit): ByteArray {
    val output = exportDimensions(size, options)
    val byteCount = output.width.toLong() * output.height * 4
    require(byteCount <= Int.MAX_VALUE) { "This raster exceeds the browser's contiguous pixel-buffer capacity. Choose SVG or a smaller raster size." }
    val pixels: ByteArray
    val bitmap = Bitmap()
    try {
        check(bitmap.allocN32Pixels(output.width, output.height)) { "Could not allocate image. Try SVG or a smaller raster size." }
        bitmap.erase(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        try { drawExportBatches(canvas.asComposeCanvas(), size, density, direction, options, batches, draw) }
        finally { canvas.close() }
        pixels = ByteArray(byteCount.toInt())
        val rowBytes = output.width * 4
        // Bound each readback copy; allow UI events between expensive chunks.
        val rowsPerChunk = (262144 / rowBytes).coerceAtLeast(1)
        var slice = TimeSource.Monotonic.markNow()
        var y = 0
        while (y < output.height) {
            currentCoroutineContext().ensureActive()
            val rows = minOf(rowsPerChunk, output.height - y)
            val info = ImageInfo(output.width, rows, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
            checkNotNull(bitmap.readPixels(info, rowBytes, 0, y)).copyInto(pixels, y * rowBytes)
            y += rows
            if (slice.elapsedNow().inWholeMilliseconds >= 8) { delay(1); slice = TimeSource.Monotonic.markNow() }
        }
    } finally { bitmap.close() }
    return encodeRgbaImage(pixels, output, options)
}

/** Transfers binary pixels to a dedicated encoder; no SVG or Base64 intermediate. */
internal expect suspend fun encodeRgbaImage(pixels: ByteArray, size: IntSize, options: ImageExportOptions): ByteArray

internal actual suspend fun renderVectorImage(size: IntSize, density: Density, direction: LayoutDirection,
    options: ImageExportOptions, batches: Int, draw: DrawScope.(Int) -> Unit): ByteArray =
    compatibleSvg(renderSvgChunks(size, density, direction, options, batches, draw), exportDimensions(size, options))
