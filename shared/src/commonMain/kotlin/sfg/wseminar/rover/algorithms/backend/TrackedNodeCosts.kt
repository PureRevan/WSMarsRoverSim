package sfg.wseminar.rover.algorithms.backend

import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.simulation.PagedCosts
import sfg.wseminar.rover.simulation.CostSnapshot


class TrackedNodeCosts(size: Int, initialValue: Double = Double.POSITIVE_INFINITY) {
    private val costs = PagedCosts(size, initialValue)

    operator fun get(node: Graph.Node): Double =
        costs[node.id]

    operator fun set(node: Graph.Node, cost: Double) {
        costs[node.id] = cost
    }

    internal val allocatedBytes: Long get() = costs.allocatedBytes
    fun snapshot(): CostSnapshot = costs.snapshot()

    fun copyArray(): DoubleArray =
        costs.snapshot().toDoubleArray()
}
