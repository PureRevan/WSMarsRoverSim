package sfg.wseminar.rover.export

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class BrowserImageExportTest {
    @Test fun encodersRunInRealWorkersAndReturnTheirDeclaredFormats() = runTest(timeout = 120.seconds) {
        withContext(Dispatchers.Default) {
            val pixels = ByteArray(320 * 160 * 4) { index -> if (index % 4 == 0 || index % 4 == 3) 255.toByte() else 0 }

            var heartbeats = 0
            val heartbeat = launch { while (isActive) { delay(10); heartbeats++ } }
            try {
                for (format in listOf(ImageFormat.PNG, ImageFormat.JPEG, ImageFormat.PDF)) {
                    val bytes = encodeRgbaImage(pixels, IntSize(320, 160), ImageExportOptions(format = format, quality = 100))
                    assertTrue(bytes.size > 100, "$format was empty")
                    when (format) {
                        ImageFormat.PNG -> assertEquals("PNG", bytes.copyOfRange(1, 4).decodeToString())
                        ImageFormat.JPEG -> { assertEquals(255, bytes[0].toInt() and 255); assertEquals(216, bytes[1].toInt() and 255) }
                        ImageFormat.PDF -> assertEquals("%PDF-", bytes.copyOfRange(0, 5).decodeToString())
                        else -> error("Unexpected format")
                    }
                }
                assertTrue(heartbeats > 0, "The UI event loop must keep running while encoding")
            } finally { heartbeat.cancel() }
        }
    }
    @Test fun largeBinaryPixelsRoundTripWithoutDetachingCallerBuffer() = runTest(timeout = 120.seconds) {
        withContext(Dispatchers.Default) {
            val pixels = ByteArray(1024 * 1024 * 4)
            val random = kotlin.random.Random(42)
            random.nextBytes(pixels)
            for (i in 3 until pixels.size step 4) pixels[i] = 255.toByte()
            val first = pixels[0]
            var ticks = 0
            val heartbeat = launch { while (isActive) { delay(1); ticks++ } }
            try {
                val png = encodeRgbaImage(pixels, IntSize(1024, 1024), ImageExportOptions(format = ImageFormat.PNG))
                assertTrue(png.size > 1_000_000, "Exercise a large binary response, not just a tiny solid-color PNG")
                assertEquals(first, pixels[0]); assertEquals(1024 * 1024 * 4, pixels.size)
                assertTrue(ticks > 0)
            } finally { heartbeat.cancel() }
        }
    }

}
