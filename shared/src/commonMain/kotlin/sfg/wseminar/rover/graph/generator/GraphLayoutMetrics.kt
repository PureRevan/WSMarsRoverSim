package sfg.wseminar.rover.graph.generator

import kotlin.math.sqrt
import kotlin.math.ceil

internal object GraphLayoutMetrics {
    const val BaselineCanvasWidthPx = 960.0
    const val FitPaddingFraction = 0.18
    const val NodeRadiusPx = 18.0
    const val NodePaddingPx = 16.0
    const val EdgeHalfWidthPx = 4.0

    private const val PackingEfficiency = 0.62

    fun availableFitFraction(): Double =
        1.0 - FitPaddingFraction * 2.0

    fun minNodeSeparation(
        nodeCount: Int,
        worldSpan: Double,
        clearanceMultiplier: Double,
        spacing: Double = 0.5
    ): Double {
        val pixelSeparation = (NodeRadiusPx * 2.0 + NodePaddingPx) * clearanceMultiplier.coerceIn(0.55, 1.8)
        val visualWorld = pixelSeparation * worldSpan / (availableFitFraction() * BaselineCanvasWidthPx)
        val packingWorld = sqrt(PackingEfficiency / nodeCount.coerceAtLeast(1).toDouble()) * worldSpan

        val baseline = minOf(visualWorld, packingWorld * 0.92)
        // A 6% margin leaves 88% of the plane for sampling. The cap also fits
        // the fallback grid, so stronger separation never silently drops nodes.
        val feasible = worldSpan * 0.88 / ceil(sqrt(nodeCount.toDouble())) * 0.9
        return minOf(baseline * (0.25 + spacing.coerceIn(0.0, 1.0) * 1.5), feasible)
    }

    fun edgeNodeClearance(
        worldSpan: Double,
        clearanceMultiplier: Double
    ): Double {
        val pixels = (NodeRadiusPx + EdgeHalfWidthPx) * clearanceMultiplier.coerceIn(0.55, 1.8)
        return pixels * worldSpan / (availableFitFraction() * BaselineCanvasWidthPx)
    }
}
