package sfg.wseminar.rover.export

import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal enum class ResolutionPreset(val label: String) { DETAILED("Highly detailed"), BALANCED("Balanced"), OVERVIEW("Overview") }
internal val resolutionStops = listOf(1f, 2f, 4f, 8f, 10f, 20f, 30f)
internal fun snapResolution(scale: Float): Float = resolutionStops.minBy { kotlin.math.abs(it - scale) }.let {
    if (kotlin.math.abs(it - scale) <= 0.45f) it else (scale * 10).roundToInt() / 10f
}.coerceIn(1f, 30f)

/** Instant heuristic, deliberately labelled approximate; exact size requires encoding. */
internal fun estimatedImageBytes(source: IntSize, options: ImageExportOptions, nodes: Int, edges: Int): Long {
    val size = exportDimensions(source, options)
    val labels = (if (options.nodeIds == true) nodes else 0).toLong() +
        (if (options.costs == true) nodes else 0) + (if (options.heuristics == true) nodes else 0) + (if (options.edgeWeights == true) edges else 0)
    if (options.format == ImageFormat.SVG) return 512L + nodes * 360L + edges * 280L + labels * 1_100L
    val pixels = size.width.toDouble() * size.height
    val detail = sqrt(pixels / (nodes + edges).coerceAtLeast(1)).coerceIn(1.0, 200.0)
    val content = nodes * 18.0 + edges * detail * 1.2 + labels * 24
    val quality = (options.quality / 90.0).coerceAtLeast(0.1)
    val estimate = when (options.format) {
        ImageFormat.JPEG -> (pixels * 0.06 + content * 0.7) * quality
        ImageFormat.PNG -> pixels * 0.015 + content
        ImageFormat.PDF -> pixels * 0.025 + content * 1.5
        ImageFormat.SVG -> error("Handled above")
    }
    // Separate raster compression from vector command volume; calibrated on the graph renderer.
    return estimate.coerceIn(512.0, Long.MAX_VALUE.toDouble()).toLong()
}
internal fun presetWidth(source: IntSize, detailedWidth: Int, preset: ResolutionPreset): Int {
    val maxWidth = (source.width.toLong() * 30).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val detailed = (detailedWidth * 0.90).roundToInt().coerceIn(source.width.coerceAtLeast(1), maxWidth)
    if (preset == ResolutionPreset.OVERVIEW) {
        // A compact whole-scene image, targeting a 1,920-pixel longest side.
        val overview = (1920.0 * source.width / maxOf(source.width, source.height)).roundToInt()
        return overview.coerceIn(source.width.coerceAtLeast(1), detailed)
    }
    if (preset == ResolutionPreset.DETAILED) return detailed
    // Target roughly 12px ordinary text instead of Highly detailed's 15px.
    // File-size estimates and edge counts must never lower the readability target.
    return (detailedWidth * 0.72).roundToInt().coerceIn(source.width.coerceAtLeast(1), maxWidth)
}
