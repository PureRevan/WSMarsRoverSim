package sfg.wseminar.rover.ui.models

import androidx.compose.ui.unit.IntSize
import sfg.wseminar.rover.algorithms.RoverAlgorithm
import sfg.wseminar.rover.graph.Graph.Edge
import sfg.wseminar.rover.graph.Graph.Node
import sfg.wseminar.rover.ui.states.ExplorationMode
import sfg.wseminar.rover.ui.states.GraphGenerationState
import sfg.wseminar.rover.ui.states.GraphAppearance
import sfg.wseminar.rover.ui.states.NodeColoringMode
import sfg.wseminar.rover.ui.states.SpaceMode

sealed interface RoverVisualizerEvent {
    data class ChangePerformance(val settings: sfg.wseminar.rover.ui.settings.PerformanceSettings) : RoverVisualizerEvent
    data class ChangeAlgorithm(val algorithm: RoverAlgorithm) : RoverVisualizerEvent
    data class ChangeExplorationMode(val mode: ExplorationMode) : RoverVisualizerEvent
    data class ChangeSpaceMode(val mode: SpaceMode) : RoverVisualizerEvent
    data class ChangeSensingRadius(val radius: Int) : RoverVisualizerEvent

    data object CancelWork : RoverVisualizerEvent
    data object GenerateGraph : RoverVisualizerEvent
    data object SendWaypoint : RoverVisualizerEvent
    data object RunAlgorithm : RoverVisualizerEvent
    data class ChangeGraphGeneration(val generation: GraphGenerationState) : RoverVisualizerEvent
    data class ChangeGraphAppearance(val appearance: GraphAppearance) : RoverVisualizerEvent
    data object RandomizeGraphGenerationSeed : RoverVisualizerEvent

    data object PlayPause : RoverVisualizerEvent
    data object StepForward : RoverVisualizerEvent
    data object StepBackward : RoverVisualizerEvent
    data object Restart : RoverVisualizerEvent
    data class SeekToStep(val step: Int) : RoverVisualizerEvent
    data class SetPlaybackSpeed(val speed: Float) : RoverVisualizerEvent

    data object ResetSimulation: RoverVisualizerEvent

    data class ChangeViewport(
        val zoom: Float,
        val panX: Float,
        val panY: Float
    ) : RoverVisualizerEvent

    data class CanvasSizeChanged(val size: IntSize) : RoverVisualizerEvent

    data object ResetViewport : RoverVisualizerEvent
    data object FitViewportToGraph : RoverVisualizerEvent

    data class SelectNode(val node: Node?) : RoverVisualizerEvent
    data class SelectEdge(val edge: Edge?) : RoverVisualizerEvent
    data object ClearSelection : RoverVisualizerEvent

    data class ToggleNodeIds(val enabled: Boolean) : RoverVisualizerEvent
    data class ChangeNodeColoringMode(val mode: NodeColoringMode) : RoverVisualizerEvent
    data class ToggleEdgeWeights(val enabled: Boolean) : RoverVisualizerEvent
    data class ToggleCosts(val enabled: Boolean) : RoverVisualizerEvent
    data class ToggleCostsInsideNodes(val enabled: Boolean) : RoverVisualizerEvent
    data class ToggleHeuristics(val enabled: Boolean) : RoverVisualizerEvent
    data class ToggleFrontier(val enabled: Boolean) : RoverVisualizerEvent
    data class ToggleVisited(val enabled: Boolean) : RoverVisualizerEvent
    data class SetFinalPathScale(val scale: Float) : RoverVisualizerEvent

    data class ToggleFinalPath(val enabled: Boolean) : RoverVisualizerEvent
    data class ToggleSensorRadius(val enabled: Boolean) : RoverVisualizerEvent
    data class ToggleGridLines(val enabled: Boolean) : RoverVisualizerEvent
    data class ToggleCoordinates(val enabled: Boolean) : RoverVisualizerEvent
    data class ToggleLegend(val enabled: Boolean) : RoverVisualizerEvent
    data class ToggleNodeLegend(val enabled: Boolean) : RoverVisualizerEvent
    data class TogglePathLegend(val enabled: Boolean) : RoverVisualizerEvent
    data class ToggleKnowledgeLegend(val enabled: Boolean) : RoverVisualizerEvent
    data class SetLegendEdgePosition(val position: Float) : RoverVisualizerEvent
    data object ResetLegendPosition : RoverVisualizerEvent
    data class ToggleUnknownArea(val enabled: Boolean) : RoverVisualizerEvent

    data object ExportCurrentFrame : RoverVisualizerEvent
}
