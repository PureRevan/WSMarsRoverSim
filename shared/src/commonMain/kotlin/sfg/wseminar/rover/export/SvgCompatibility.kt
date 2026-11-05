package sfg.wseminar.rover.export

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.time.TimeSource

private val fontFamilyAttribute = Regex("font-family=\"([^\"]*)\"")
// Skia separates coordinate lists with comma + space; decimal commas occur between digits.
private val decimalComma = Regex("([0-9]),(?=[0-9])")
private val numericAttribute = Regex("""(transform|d|points|x|y|dx|dy|x1|x2|y1|y2|cx|cy|r|rx|ry|width|height|stroke-width|stroke-miterlimit|stroke-dasharray|stroke-dashoffset|font-size|letter-spacing|opacity|fill-opacity|stroke-opacity)="([^"]*)""" + "\"")
private val textPositionAttribute = Regex("([xy])=\"([^\"]*)\"")

/** Normalize Skia numeric attributes, private macOS fonts and resized SVG aspect ratios.
 * Copies binary spans; avoids decoding or rebuilding an entire large SVG as a UTF-16 string.
 */
internal suspend fun compatibleSvg(bytes: ByteArray, size: IntSize): ByteArray {
    val prefix = bytes.decodeToString(0, minOf(bytes.size, 1024))
    val root = prefix.indexOf("<svg ")
    if (root < 0) return bytes
    val rootEnd = prefix.indexOf('>', root)
    if (rootEnd < 0) return bytes
    val metadata = if (prefix.substring(root, rootEnd).contains("viewBox=")) "" else
        " viewBox=\"0 0 ${size.width} ${size.height}\" preserveAspectRatio=\"xMidYMid meet\""
    val extra = metadata.encodeToByteArray()
    var output = ByteArray(bytes.size + extra.size)
    var written = 0
    fun ensure(count: Int) {
        require(count <= Int.MAX_VALUE - written) { "SVG exceeds the platform byte-array capacity." }
        if (written + count > output.size) output = output.copyOf(maxOf(written.toLong() + count, output.size.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
    fun copy(from: Int, until: Int) {
        ensure(until - from)
        bytes.copyInto(output, written, from, until)
        written += until - from
    }
    val insertion = root + 4 // The generated XML prefix is ASCII.
    copy(0, insertion)
    extra.copyInto(output, written); written += extra.size
    var copied = insertion
    var index = insertion
    var slice = TimeSource.Monotonic.markNow()
    var checkedAt = index
    while (index + 5 < bytes.size) {
        if (bytes[index] == 60.toByte()) {
            var end = index + 1
            while (end < bytes.size && bytes[end] != 62.toByte()) end++
            if (end == bytes.size) break
            val tag = bytes.decodeToString(index, end + 1)
            val numeric = numericAttribute.replace(tag) { match -> "${match.groupValues[1]}=\"${decimalComma.replace(match.groupValues[2], "$1.")}\"" }
            val normalized = textPositionAttribute.replace(fontFamilyAttribute.replace(numeric) { match ->
                val family = match.groupValues[1]
                // These private AppKit aliases are not CSS font names. system-ui selects the
                // same system face in browsers, instead of falling back to Times New Roman.
                if (family.contains(".SF") || family.contains(".AppleSystemUIFont"))
                    "font-family=\"system-ui\"" else match.value
            }) { match -> "${match.groupValues[1]}=\"${match.groupValues[2].trim().trimEnd(',').trimEnd()}\"" }
            if (normalized != tag) {
                copy(copied, index)
                val encoded = normalized.encodeToByteArray()
                ensure(encoded.size)
                encoded.copyInto(output, written); written += encoded.size
                copied = end + 1
            }
            index = end
        }
        index++
        if (index - checkedAt >= 65536) {
            currentCoroutineContext().ensureActive()
            if (slice.elapsedNow().inWholeMilliseconds >= 8) { delay(1); slice = TimeSource.Monotonic.markNow() }
            checkedAt = index
        }
    }
    copy(copied, bytes.size)
    return output.copyOf(written)
}
