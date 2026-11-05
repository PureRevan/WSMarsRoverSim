package sfg.wseminar.rover.ui.states

import sfg.wseminar.rover.algorithms.GoToWaypoint

/** Null means this explicit command can begin using the displayed knowledge. */
fun VisualizerState.waypointCommandIssue(): String? {
    if (algorithm !== GoToWaypoint) return "Choose Go to Waypoint first."
    val frame = currentFrame ?: return "Generate a graph, then select a destination on the canvas."
    if (isComputing) return "Wait for recording to finish, or cancel it before sending another waypoint."
    val target = selectedNode ?: return when {
        isPlaying && frame.goalNode != null -> "Replaying the command to ${frame.goalNode.label}. Select a node to pause and redirect."
        hasRunAlgorithm && currentStep < maxStep -> "Play or step through the recorded command, or select a destination to redirect from this frame."
        else -> "Select a destination on the canvas, then choose Send waypoint."
    }
    if (frame.graph.nodes.getOrNull(target.id) !== target) return "Select a destination on the displayed graph."
    val start = frame.roverNode ?: return "The displayed frame has no rover position."
    if (start === target) return "The rover is already at ${target.label}. Choose another destination."
    val outgoing = frame.graph.outgoingEdges(start).filter { it in frame.knownEdges }
    if (outgoing.isEmpty()) return "The rover has no sensed outgoing connection at ${start.label}. Choose another starting position or reset the scenario."
    if (GoToWaypoint.nextEdge(start, target, outgoing) == null) {
        return "Cannot move toward ${target.label} from ${start.label}: no connected neighbor is closer. Choose a connected neighbor as an intermediate waypoint."
    }
    return null
}
