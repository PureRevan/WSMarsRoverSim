package sfg.wseminar.rover.export

import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import org.jetbrains.skia.*
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream

internal actual suspend fun renderRasterImage(size: IntSize, density: Density, direction: LayoutDirection,
    options: ImageExportOptions, batches: Int, draw: DrawScope.(Int) -> Unit): ByteArray {
    val output = exportDimensions(size, options)
    val picture = recordPicture(size, density, direction, options, batches, draw)
    try {
        return withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            val bitmap = Bitmap()
            try {
                check(bitmap.allocN32Pixels(output.width, output.height)) { "Could not allocate image. Try SVG or a smaller raster size." }
                bitmap.erase(Color.TRANSPARENT)
                val canvas = Canvas(bitmap)
                try { picture.playback(canvas) { !context.isActive } } finally { canvas.close() }
                context.ensureActive()
                encodeBitmap(bitmap, output, options)
            } finally { bitmap.close() }
        }
    } finally { picture.close() }
}

private suspend fun encodeBitmap(bitmap: Bitmap, size: IntSize, options: ImageExportOptions): ByteArray {
    if (options.format == ImageFormat.PDF) {
        val output = ByteArrayOutputStream()
        DeflaterOutputStream(output).use { zip ->
            val row = ByteArray(Math.multiplyExact(size.width, 3))
            val info = ImageInfo(size.width, 1, ColorType.RGBA_8888, ColorAlphaType.OPAQUE)
            for (y in 0 until size.height) {
                currentCoroutineContext().ensureActive()
                val rgba = checkNotNull(bitmap.readPixels(info, Math.multiplyExact(size.width, 4), 0, y))
                for (x in 0 until size.width) {
                    row[x * 3] = rgba[x * 4]; row[x * 3 + 1] = rgba[x * 4 + 1]; row[x * 3 + 2] = rgba[x * 4 + 2]
                }
                zip.write(row)
            }
        }
        return rasterPdf(size.width, size.height, output.toByteArray())
    }
    val image = Image.makeFromBitmap(bitmap)
    try {
        val encoded = checkNotNull(image.encodeToData(if (options.format == ImageFormat.JPEG) EncodedImageFormat.JPEG else EncodedImageFormat.PNG, options.quality))
        try { return encoded.bytes } finally { encoded.close() }
    } finally { image.close() }
}

private suspend fun recordPicture(size: IntSize, density: Density, direction: LayoutDirection,
    options: ImageExportOptions, batches: Int, draw: DrawScope.(Int) -> Unit): Picture {
    val output = exportDimensions(size, options)
    val recorder = PictureRecorder()
    return try {
        val canvas = recorder.beginRecording(Rect.makeWH(output.width.toFloat(), output.height.toFloat()))
        drawExportBatches(canvas.asComposeCanvas(), size, density, direction, options, batches, draw)
        recorder.finishRecordingAsPicture()
    } finally { recorder.close() }
}

internal actual suspend fun renderVectorImage(size: IntSize, density: Density, direction: LayoutDirection,
    options: ImageExportOptions, batches: Int, draw: DrawScope.(Int) -> Unit): ByteArray {
    val output = exportDimensions(size, options)
    val picture = recordPicture(size, density, direction, options, batches, draw)
    try {
        return withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            val stream = DynamicMemoryWStream()
            try {
                val canvas = org.jetbrains.skia.svg.SVGCanvas.make(0f, 0f, output.width.toFloat(), output.height.toFloat(), stream, false, false)
                try { picture.playback(canvas) { !context.isActive } } finally { canvas.close() }
                context.ensureActive()
                compatibleSvg(ByteArray(stream.bytesWritten()).also { check(stream.read(it, 0, it.size)) }, output)
            } finally { stream.close() }
        }
    } finally { picture.close() }
}
