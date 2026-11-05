package sfg.wseminar.rover.computation

import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.graph.generator.*
import kotlin.math.*

enum class WorkerMode { AUTO, ALWAYS, NEVER }
@kotlinx.serialization.Serializable
data class WorkerSettings(val mode: WorkerMode = WorkerMode.AUTO, val workThreshold: Long = 100_000) {
    init { require(workThreshold > 0) }
    fun useWorker(estimatedWork: Long): Boolean = when (mode) {
        WorkerMode.ALWAYS -> true
        WorkerMode.NEVER -> false
        WorkerMode.AUTO -> estimatedWork >= workThreshold
    }
}

/** Cheap scheduling heuristic, not a prediction of milliseconds. */
fun estimateGenerationWork(options: GraphGenerationOptions): Long {
    val n = if (options.mode == GraphGenerationMode.GRID) options.gridColumns.toDouble() * options.gridRows else options.nodeCount.toDouble()
    val placement = if (options.mode == GraphGenerationMode.GRID) n * 12 else
        n * log2(maxOf(2.0, n)) * 8 + n * n * (if (options.planar) 0.05 else options.connectionRadius * options.connectionRadius)
    val terrain = if (options.terrain.enabled) n * 6 * options.terrain.samplesPerEdge else 0.0
    return (placement + terrain).toLong().coerceAtLeast(0)
}
fun estimateAlgorithmWork(graph: Graph): Long = (graph.nodes.size * log2(maxOf(2.0, graph.nodes.size.toDouble())) * 4 + graph.edges.size * 8.0).toLong()

expect val browserWorkerSettingsAvailable: Boolean
