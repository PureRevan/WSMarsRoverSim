package sfg.wseminar.rover.ui.states

@kotlinx.serialization.Serializable
data class VisualizerOptions(
    val appearance: GraphAppearance = GraphAppearance(),
    val graphRenderMode: GraphRenderMode = GraphRenderMode.GRAPH,
    val nodeColoringMode: NodeColoringMode = NodeColoringMode.DISTINCTIVE,
    val showNodeIds: Boolean = false,
    val showEdgeWeights: Boolean = true,
    val showCosts: Boolean = true,
    val showCostsInsideNodes: Boolean = false,
    val showHeuristics: Boolean = false,
    val showFrontier: Boolean = true,
    val showVisited: Boolean = true,
    val showFinalPath: Boolean = true,
    val finalPathScale: Float = 1f,
    val showSensorRadius: Boolean = true,
    val showGridLines: Boolean = false,
    val showCoordinates: Boolean = false,
    val showLegend: Boolean = true,
    val showNodeLegend: Boolean = true,
    val showPathLegend: Boolean = false,
    val showKnowledgeLegend: Boolean = false,
    val showUnknownArea: Boolean = true
)
