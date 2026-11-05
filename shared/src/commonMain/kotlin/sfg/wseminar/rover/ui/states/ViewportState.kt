package sfg.wseminar.rover.ui.states


@kotlinx.serialization.Serializable
data class ViewportState(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f
)
