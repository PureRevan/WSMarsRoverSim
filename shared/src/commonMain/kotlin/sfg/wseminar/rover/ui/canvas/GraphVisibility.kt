package sfg.wseminar.rover.ui.canvas

import sfg.wseminar.rover.SimulationFrame
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.ui.states.VisualizerOptions

/** Drawing and hit testing must agree; scenario endpoints remain navigation landmarks. */
internal fun SimulationFrame.isNodeVisible(node: Graph.Node, options: VisualizerOptions): Boolean =
    node in knownNodes || node === startNode || node === goalNode || node === roverNode ||
        options.showUnknownArea

internal fun SimulationFrame.isEdgeVisible(edge: Graph.Edge, options: VisualizerOptions): Boolean =
    edge in knownEdges || options.showUnknownArea
