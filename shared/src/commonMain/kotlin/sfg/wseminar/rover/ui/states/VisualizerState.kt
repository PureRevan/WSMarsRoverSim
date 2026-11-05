package sfg.wseminar.rover.ui.states

import androidx.compose.ui.unit.IntSize
import sfg.wseminar.rover.SimulationFrame
import sfg.wseminar.rover.algorithms.RoverAlgorithm
import sfg.wseminar.rover.graph.Graph.Node
import sfg.wseminar.rover.graph.Graph.Edge
import sfg.wseminar.rover.graph.Graph

data class VisualizerState(
    val performance: sfg.wseminar.rover.ui.settings.PerformanceSettings = sfg.wseminar.rover.ui.settings.PerformanceSettings(),
    val algorithm: RoverAlgorithm = RoverAlgorithm.algorithms.first(),
    val spaceMode: SpaceMode = algorithm.defaultSpaceMode(),
    val explorationMode: ExplorationMode = algorithm.defaultExplorationMode(),
    val sensingRadius: Int = sfg.wseminar.rover.simulation.SensingRadius.DEFAULT,
    val generatedGraph: Graph? = null,
    val generatedRecipe: GraphGenerationState? = null,
    val frames: List<SimulationFrame> = emptyList(),
    val currentStep: Int = 0,
    val isPlaying: Boolean = false,
    val isComputing: Boolean = false,
    val workMessage: String = "",
    val recordingStopReason: String? = null,
    val noticeMessage: String? = null,
    val noticeId: Long = 0,
    val revealedFinalPathCost: Double? = null,
    val hasCompletedRecording: Boolean = false,
    val estimatedReplayBytes: Long = 0,
    val replayBudgetBytes: Long = sfg.wseminar.rover.algorithms.backend.defaultReplayMemoryBudgetBytes,
    val peakAlgorithmBytes: Long? = null,
    val runElapsedNanos: Long? = null,
    val hasRunAlgorithm: Boolean = false,
    val playbackSpeed: Float = 1f,
    val graphGeneration: GraphGenerationState = GraphGenerationState(),
    val options: VisualizerOptions = VisualizerOptions(),
    val viewport: ViewportState = ViewportState(),
    val canvasSize: IntSize = IntSize.Zero,
    val legendEdgePosition: Float = 0f,
    val statusMessage: String = "Ready",
    val selectedNode: Node? = null,
    val selectedEdge: Edge? = null
) {
    val currentFrame: SimulationFrame?
        get() = frames.getOrNull(currentStep)

    val hasFrames: Boolean
        get() = frames.isNotEmpty()

    val maxStep: Int
        get() = frames.lastIndex.coerceAtLeast(0)

    val progress: Float
        get() = if (frames.isEmpty()) 0f else currentStep.toFloat() / maxStep.coerceAtLeast(1)
}
