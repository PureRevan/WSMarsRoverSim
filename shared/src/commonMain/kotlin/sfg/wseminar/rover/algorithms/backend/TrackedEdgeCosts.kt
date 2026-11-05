package sfg.wseminar.rover.algorithms.backend

import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.simulation.PagedCosts
import sfg.wseminar.rover.simulation.CostSnapshot


class TrackedEdgeCosts(size: Int) {
    private val costs = PagedCosts(size, Double.POSITIVE_INFINITY)

    operator fun get(edge: Graph.Edge): Double =
        costs[edge.id]

    operator fun set(edge: Graph.Edge, cost: Double) {
        costs[edge.id] = cost
    }

    internal val allocatedBytes: Long get() = costs.allocatedBytes
    fun snapshot(): CostSnapshot = costs.snapshot()

    fun copyArray(): DoubleArray =
        costs.snapshot().toDoubleArray()
}