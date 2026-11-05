package sfg.wseminar.rover.ui.states

import sfg.wseminar.rover.graph.generator.EndpointPlacement

enum class GenerationPreset(val label: String, val description: String) {
    CLASSIC("Classic", "The familiar corner-to-corner map with gently varying costs."),
    SIDE_BRANCHES("Side branches", "Start near the center and aim right. Sparse paths also lead away from the goal."),
    ROUGH_TERRAIN("Rough terrain", "Occasional expensive edges encourage a longer but cheaper route."),
    CHEAP_DETOURS("Cheap detours", "Cheap edges scattered around a central start can draw a cost-based search away from the goal."),
    SHORT_TRIP("Short trip", "Start and goal are close together, with a larger surrounding world to explore.");

    /** A preset replaces scenario controls, preserving size, space, complexity and seed. */
    fun applyTo(state: GraphGenerationState): GraphGenerationState {
        val baseline = state.copy(
            density = 0.45, planar = true, connectionRadius = 0.28,
            includeGridDiagonals = false, allowDisconnected = false, allowNodeOverlap = false,
            nodeClearanceMultiplier = 1.0, weightJitter = 0.22, bidirectional = true,
            extremeWeightChance = 0.0, extremeWeightFactor = 10.0, cheapWeightShare = 0.5,
            endpointPlacement = EndpointPlacement.OPPOSITE_CORNERS,
            startX = 0.5, startY = 0.5, goalX = 0.9, goalY = 0.5
        )
        return when (this) {
            CLASSIC -> baseline
            SIDE_BRANCHES -> baseline.copy(density = 0.08, endpointPlacement = EndpointPlacement.CENTER_TO_EDGE)
            ROUGH_TERRAIN -> baseline.copy(extremeWeightChance = 0.2, extremeWeightFactor = 20.0, cheapWeightShare = 0.0)
            CHEAP_DETOURS -> baseline.copy(endpointPlacement = EndpointPlacement.CENTER_TO_EDGE,
                extremeWeightChance = 0.4, extremeWeightFactor = 10.0, cheapWeightShare = 1.0)
            SHORT_TRIP -> baseline.copy(endpointPlacement = EndpointPlacement.NEARBY)
        }
    }
}
