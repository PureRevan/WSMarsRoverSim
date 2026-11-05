package sfg.wseminar.rover.simulation

import sfg.wseminar.rover.graph.Graph

object SensingRadius {
    const val DEFAULT = 1
    const val MAX = 10
    val range: IntRange = 1..MAX
}

/** Bounded breadth-first sensing over outgoing connections; buffers are reused between moves. */
internal class NeighborhoodSensor(private val graph: Graph) {
    private val seen = IntArray(graph.nodes.size)
    private val queue = IntArray(graph.nodes.size)
    private var generation = 0
    val workingBytes: Long get() = 128L + graph.nodes.size * 8L

    fun reveal(origin: Graph.Node, radius: Int, node: (Graph.Node) -> Unit, edge: (Graph.Edge) -> Unit) {
        require(radius in SensingRadius.range)
        require(graph.nodes.getOrNull(origin.id) === origin)
        if (generation == Int.MAX_VALUE) { seen.fill(0); generation = 0 }
        val visit = ++generation
        var head = 0
        var tail = 1
        queue[0] = origin.id
        seen[origin.id] = visit
        node(origin)
        repeat(radius) {
            val endOfLayer = tail
            while (head < endOfLayer) {
                val current = graph.nodes[queue[head++]]
                for (connection in graph.outgoingEdges(current)) {
                    edge(connection)
                    val next = connection.neighborOf(current)
                    if (seen[next.id] != visit) {
                        seen[next.id] = visit
                        queue[tail++] = next.id
                        node(next)
                    }
                }
            }
            if (head == tail) return
        }
    }
}
