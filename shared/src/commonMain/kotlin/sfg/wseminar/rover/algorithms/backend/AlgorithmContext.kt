package sfg.wseminar.rover.algorithms.backend

import sfg.wseminar.rover.algorithms.GlobalAlgorithm
import sfg.wseminar.rover.algorithms.LocalAlgorithm
import sfg.wseminar.rover.algorithms.RoverAlgorithm
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.graph.IDSet
import sfg.wseminar.rover.graph.ImmutableList
import sfg.wseminar.rover.graph.PagedList
import sfg.wseminar.rover.simulation.CostSnapshot
import sfg.wseminar.rover.simulation.NeighborhoodSensor
import sfg.wseminar.rover.simulation.SensingRadius


class AlgorithmContext(
    val algorithm: RoverAlgorithm,
    val graph: Graph,
    val startNode: Graph.Node,
    val goalNode: Graph.Node,
    val knowledgeMode: KnowledgeMode = if (algorithm is LocalAlgorithm) KnowledgeMode.LOCAL else KnowledgeMode.GLOBAL,
    val sensingRadius: Int = SensingRadius.DEFAULT
) {
    init {
        require(sensingRadius in SensingRadius.range)
        require(graph.nodes.withIndex().all { (index, node) -> node.id == index }) {
            "Node IDs must match their indices in graph.nodes."
        }
        require(graph.edges.withIndex().all { (index, edge) -> edge.id == index }) {
            "Edge IDs must match their indices in graph.edges."
        }
        requireNode(startNode)
        requireNode(goalNode)

        graph.edges.forEach { requireNode(it.from); requireNode(it.to) }

        require(when (knowledgeMode) {
            KnowledgeMode.GLOBAL -> algorithm is GlobalAlgorithm
            KnowledgeMode.LOCAL -> algorithm is LocalAlgorithm
        }) { "${algorithm.name} does not support $knowledgeMode exploration." }
    }

    // One live set of buffers, excluding immutable history, graph input and rendering.
    // Portable estimate: 8-byte references plus conservative headers/container allowance.
    private val sensor = if (knowledgeMode == KnowledgeMode.LOCAL) NeighborhoodSensor(graph) else null
    private var queueBytes = 0L
    private var peakWorkingBytes = 0L
    internal fun frontierMemory(capacity: Int) {
        queueBytes = 64L + graph.nodes.size * 4L + capacity * 72L
        sampleAlgorithmMemory()
    }
    internal fun sampleAlgorithmMemory(): Long {
        val n = graph.nodes.size.toLong(); val e = graph.edges.size.toLong()
        val bitsets = 4 * ((n + 63) / 64) * 8 + ((e + 63) / 64) * 8
        val arrays = n * (20 + costFields.size * 8L) + e * 8
        val paths = (currentPathNodes.size.toLong() + currentPathEdges.size) * 24L
        val bytes = 1024L + bitsets + arrays + paths + queueBytes + (sensor?.workingBytes ?: 0L)
        peakWorkingBytes = maxOf(peakWorkingBytes, bytes)
        return peakWorkingBytes
    }

    val knownNodes: IDSet<Graph.Node> = IDSet.create(graph.nodes.size)
    val knownEdges: IDSet<Graph.Edge> = IDSet.create(graph.edges.size)

    val physicallyVisitedNodes: IDSet<Graph.Node> = IDSet.create(graph.nodes.size)
    val visitedNodes: IDSet<Graph.Node> = IDSet.create(graph.nodes.size)
    val frontierNodes: IDSet<Graph.Node> = IDSet.create(graph.nodes.size)

    val nodeCosts = TrackedNodeCosts(graph.nodes.size)
    val edgeCosts = TrackedEdgeCosts(graph.edges.size)

    val previousNode: Array<Graph.Node?> = arrayOfNulls(graph.nodes.size)
    val predecessors = TrackedPredecessors(this)
    private val costFields = linkedMapOf("g" to nodeCosts)
    private var frontierCreated = false
    private val pathVisits = IntArray(graph.nodes.size)
    private var pathVisitGeneration = 0
    private var travelledNodes = PagedList<Graph.Node>().append(listOf(startNode))
    private var travelledEdges = PagedList<Graph.Edge>()

    /** Named tables (g, f, h, etc.) are captured in every frame without Compose state. */
    fun Costs(name: String, initialValue: Double = Double.POSITIVE_INFINITY): TrackedNodeCosts {
        require(name.isNotBlank()) { "Cost fields need a name." }
        return costFields.getOrPut(name) { TrackedNodeCosts(graph.nodes.size, initialValue) }
    }

    internal fun CostSnapshots(): Map<String, CostSnapshot> =
        costFields.mapValues { (_, costs) -> costs.snapshot() }

    internal val allocatedCostBytes: Long get() = costFields.values.sumOf { it.allocatedBytes } + edgeCosts.allocatedBytes

    /** One displayed frontier per run; P can also be a comparable lexicographic key. */
    fun <P : Comparable<P>> PriorityQueue(): TrackedFrontier<P> {
        check(!frontierCreated) { "A context supports one tracked frontier." }
        frontierCreated = true
        return TrackedFrontier(this)
    }

    var runSeed: Int = graph.seed
    var currentPathEdges: List<Graph.Edge> = emptyList()
        private set
    var finalPathEdges: List<Graph.Edge> = emptyList()
        private set

    var roverNode: Graph.Node? = startNode
    var currentPathNodes: List<Graph.Node> = listOf(startNode)
        private set
    var finalPathNodes: List<Graph.Node> = emptyList()
        private set

    init {
        if (knowledgeMode == KnowledgeMode.GLOBAL) {
            graph.nodes.forEach(knownNodes::add)
            graph.edges.forEach { knownEdges.add(it); edgeCosts[it] = it.weight }
        } else {
            observe(startNode)
            physicallyVisitedNodes.add(startNode)
        }
    }

    /** Reveal outgoing connections up to the selected hop radius, retaining earlier knowledge. */
    fun observe(node: Graph.Node): List<Graph.Edge> {
        requireNode(node)
        sensor?.reveal(node, sensingRadius, { knownNodes.add(it) }) { edge ->
            knownEdges.add(edge)
            edgeCosts[edge] = edge.weight
        }
        return graph.outgoingEdges(node)
    }

    fun knownOutgoingEdges(node: Graph.Node): List<Graph.Edge> {
        return graph.outgoingEdges(node).filter { it in knownEdges }
    }

    /** Movement and sensing are separate from a local planner's queue expansions. */
    fun moveTo(node: Graph.Node) {
        val current = checkNotNull(roverNode) { "The rover has no current position." }
        require(node === current || knownOutgoingEdges(current).any { it.neighborOf(current) === node }) {
            "The rover can only move along a known outgoing edge."
        }
        roverNode = node
        physicallyVisitedNodes.add(node)
    }

    fun requireNonNegativeWeights() {
        require(graph.edges.all { it.weight.isFinite() && it.weight >= 0.0 }) {
            "${algorithm.name} requires finite, non-negative edge weights."
        }
    }

    internal fun requireNode(node: Graph.Node) {
        require(graph.nodes.getOrNull(node.id) === node) { "Node does not belong to this graph." }
    }

    /** Append only the new physical movement; unchanged planning frames share the route. */
    fun appendTravel(edge: Graph.Edge, complete: Boolean = false) {
        val from = travelledNodes.last()
        val to = edge.neighborOf(from)
        requireNode(to)
        require(graph.edges.getOrNull(edge.id) === edge && edge in graph.outgoingEdges(from))
        travelledNodes = travelledNodes.append(listOf(to))
        travelledEdges = travelledEdges.append(listOf(edge))
        currentPathNodes = travelledNodes
        currentPathEdges = travelledEdges
        if (complete) completeTravel()
    }

    fun completeTravel() {
        finalPathNodes = currentPathNodes
        finalPathEdges = currentPathEdges
    }

    /** A local rover's physical route may revisit nodes; it is not a predecessor tree. */
    fun travelledPath(nodes: List<Graph.Node>, edges: List<Graph.Edge>, complete: Boolean = false) {
        require(nodes.firstOrNull() === startNode)
        require(edges.size == nodes.size - 1)
        if (currentPathNodes != nodes || currentPathEdges != edges) {
            nodes.forEach(::requireNode)
            edges.forEachIndexed { i, edge ->
                require(graph.edges.getOrNull(edge.id) === edge && edge in graph.outgoingEdges(nodes[i]) && edge.neighborOf(nodes[i]) === nodes[i + 1])
            }
            travelledNodes = PagedList<Graph.Node>().append(nodes)
            travelledEdges = PagedList<Graph.Edge>().append(edges)
            currentPathNodes = travelledNodes
            currentPathEdges = travelledEdges
        }
        if (complete) {
            finalPathNodes = currentPathNodes
            finalPathEdges = currentPathEdges
        }
    }

    fun currentPathTo(node: Graph.Node) {
        val (nodes, edges) = pathTo(node)
        currentPathNodes = nodes
        currentPathEdges = edges
    }

    fun finalPathTo(node: Graph.Node) {
        val (nodes, edges) = pathTo(node)
        finalPathNodes = nodes
        currentPathNodes = finalPathNodes
        finalPathEdges = edges
        currentPathEdges = finalPathEdges
        if (finalPathNodes.isNotEmpty() && knowledgeMode == KnowledgeMode.GLOBAL) roverNode = node
    }

    inline fun <T> use(block: AlgorithmContext.() -> T): T {
        return this.block()
    }

    suspend fun SequenceScope<AlgorithmStep>.snapshot(message: String) =
        yield(AlgorithmStep(message = message))

    private fun pathTo(node: Graph.Node): Pair<List<Graph.Node>, List<Graph.Edge>> {
        requireNode(node)
        val path = mutableListOf<Graph.Node>()
        val edges = mutableListOf<Graph.Edge>()
        if (pathVisitGeneration == Int.MAX_VALUE) { pathVisits.fill(0); pathVisitGeneration = 0 }
        val visit = ++pathVisitGeneration
        var cursor: Graph.Node? = node

        while (cursor != null) {
            requireNode(cursor)
            check(pathVisits[cursor.id] != visit) { "Cycle in predecessor chain at ${cursor.label}." }
            pathVisits[cursor.id] = visit
            path += cursor
            if (cursor === startNode) {
                path.reverse()
                edges.reverse()
                return ImmutableList(path) to ImmutableList(edges)
            }
            predecessors[cursor]?.let(edges::add)
            cursor = previousNode[cursor.id]
        }

        return emptyList<Graph.Node>() to emptyList()
    }
}
