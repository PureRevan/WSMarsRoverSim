package sfg.wseminar.rover.ui.states

/** Display-only settings: changing these never changes graph coordinates or weights. */
@kotlinx.serialization.Serializable
data class GraphAppearance(
    val nodeScale: Float = 1f,
    val edgeScale: Float = 1f,
    val labelScale: Float = 1f,
    val adaptiveSizing: Boolean = true,
    val hideCrowdedLabels: Boolean = true,
    val labelRevealSpacing: Float = 52f,
    val enlargeNodesAtCloseZoom: Boolean = true
) {
    fun constrained() = copy(
        nodeScale = nodeScale.coerceIn(0.1f, 2.5f),
        edgeScale = edgeScale.coerceIn(0.1f, 2.5f),
        labelScale = labelScale.coerceIn(0.6f, 1.8f),
        labelRevealSpacing = labelRevealSpacing.coerceIn(12f, 120f)
    )
}
