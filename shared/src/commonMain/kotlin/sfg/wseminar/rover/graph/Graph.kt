package sfg.wseminar.rover.graph


/** Immutable topology. Geometry can safely be shared by multiple graphs. */
class Graph(
    nodes: List<Node>,
    edges: List<Edge>,
    val startNodeId: Int? = null,
    val goalNodeId: Int? = null,
    val seed: Int = 0,
    val generatorVersion: Int = 2
) {
    val nodes: List<Node> = ImmutableList(nodes)
    val edges: List<Edge> = ImmutableList(edges)
    private val outgoing: List<List<Edge>>
    private val lanes: DoubleArray
    fun edgeLane(edge: Edge): Double = lanes[edge.id]
    private val incoming: List<List<Edge>>
    val minX: Double = nodes.minOfOrNull { it.x } ?: 0.0
    val maxX: Double = nodes.maxOfOrNull { it.x } ?: 0.0
    val minY: Double = nodes.minOfOrNull { it.y } ?: 0.0
    val maxY: Double = nodes.maxOfOrNull { it.y } ?: 0.0

    init {
        require(nodes.withIndex().all { (i, n) -> n.id == i && n.x.isFinite() && n.y.isFinite() }) {
            "Nodes need dense IDs and finite coordinates."
        }
        require(startNodeId == null || startNodeId in nodes.indices)
        require(goalNodeId == null || goalNodeId in nodes.indices)
        require(edges.withIndex().all { (i, e) -> e.id == i &&
            nodes.getOrNull(e.from.id) === e.from && nodes.getOrNull(e.to.id) === e.to }) {
            "Edges need dense IDs and endpoints belonging to this graph."
        }
        val out = List(nodes.size) { mutableListOf<Edge>() }
        val into = List(nodes.size) { mutableListOf<Edge>() }
        for (edge in edges) {
            out[edge.from.id].add(edge)
            into[edge.to.id].add(edge)
            if (edge.bidirectional && edge.from !== edge.to) {
                out[edge.to.id].add(edge)
                into[edge.from.id].add(edge)
            }
        }
        outgoing = out.map { ImmutableList(it) }
        incoming = into.map { ImmutableList(it) }
        lanes = DoubleArray(edges.size)
        edges.groupBy { minOf(it.from.id, it.to.id) to maxOf(it.from.id, it.to.id) }.values.forEach { group ->
            group.forEachIndexed { index, edge -> lanes[edge.id] = index - (group.size - 1) / 2.0 }
        }
    }

    fun outgoingEdges(node: Node): List<Edge> {
        require(nodes.getOrNull(node.id) === node) { "Foreign graph node." }
        return outgoing[node.id]
    }

    fun incomingEdges(node: Node): List<Edge> {
        require(nodes.getOrNull(node.id) === node) { "Foreign graph node." }
        return incoming[node.id]
    }

    fun neighbors(node: Node): List<Node> = outgoingEdges(node).map { it.neighborOf(node) }

    fun copy(startNodeId: Int? = this.startNodeId, goalNodeId: Int? = this.goalNodeId,
             seed: Int = this.seed): Graph = Graph(nodes, edges, startNodeId, goalNodeId, seed, generatorVersion)

    class Node(
        val x: Double, val y: Double,
        override val id: Int,
        val label: String = getLabel(id),
    ) : IDLabeled
    {
        override fun equals(other: Any?) = this === other
        override fun hashCode() = id
    }

    class Edge(
        val from: Node,
        val to: Node,
        val weight: Double,
        override val id: Int,
        val bidirectional: Boolean = true,
        val label: String = "${from.label}-${to.label}"
    ): IDLabeled {
        fun neighborOf(node: Node): Node =
            when (node) {
                from -> to
                to -> from
                else -> error("Node ${node.id} is not incident to edge $id")
            }

        override fun equals(other: Any?) = this === other
        override fun hashCode() = id
    }

    companion object {
        object StartGoalSelector {
            fun selectStart(graph: Graph): Node? =
                graph.startNodeId?.let { graph.nodes.getOrNull(it) }
                    ?: graph.nodes.minByOrNull { it.x + it.y }

            fun selectGoal(graph: Graph): Node? =
                graph.goalNodeId?.let { graph.nodes.getOrNull(it) }
                    ?: graph.nodes.maxByOrNull { it.x + it.y }
        }

        private fun getLabel(id: Int): String {
            require(id >= 0) { "ID must be non-negative" }
            var n = id

            return buildString {
                do {
                    append(('A'.code + (n % 26)).toChar())
                    n = n / 26 - 1
                } while (n >= 0)

                reverse()
            }
        }
    }
}
