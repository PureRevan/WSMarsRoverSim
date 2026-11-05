package sfg.wseminar.rover.graph.generator

import sfg.wseminar.rover.graph.Graph
import kotlin.math.hypot
import kotlin.random.Random

/** Chooses endpoints independently of layout/weight randomness and stores them on the graph. */
internal fun Graph.withScenarioEndpoints(options: GraphGenerationOptions): Graph {
    if (nodes.size < 2) return this
    val minX = nodes.minOf { it.x }
    val minY = nodes.minOf { it.y }
    val width = nodes.maxOf { it.x } - minX
    val height = nodes.maxOf { it.y } - minY

    fun nearest(x: Double, y: Double, excluding: Graph.Node? = null): Graph.Node =
        nodes.asSequence().filter { it !== excluding }.minBy {
            hypot(it.x - (minX + x.coerceIn(0.0, 1.0) * width),
                it.y - (minY + y.coerceIn(0.0, 1.0) * height))
        }

    val (start, goal) = when (options.endpointPlacement) {
        EndpointPlacement.OPPOSITE_CORNERS -> {
            val start = nodes.minBy { it.x + it.y }
            start to nodes.filter { it !== start }.maxBy { it.x + it.y }
        }
        EndpointPlacement.CENTER_TO_EDGE -> {
            val start = nearest(0.5, 0.5)
            start to nearest(1.0, 0.5, start)
        }
        EndpointPlacement.NEARBY -> {
            val start = nearest(0.4, 0.5)
            start to nearest(0.65, 0.5, start)
        }
        EndpointPlacement.RANDOM -> {
            val random = Random(options.seed xor 0x173AC91)
            val startIndex = random.nextInt(nodes.size)
            val offset = random.nextInt(1, nodes.size)
            nodes[startIndex] to nodes[(startIndex + offset) % nodes.size]
        }
        EndpointPlacement.CUSTOM -> {
            val start = nearest(options.startX, options.startY)
            start to nearest(options.goalX, options.goalY, start)
        }
    }
    return copy(startNodeId = start.id, goalNodeId = goal.id, seed = options.seed)
}
