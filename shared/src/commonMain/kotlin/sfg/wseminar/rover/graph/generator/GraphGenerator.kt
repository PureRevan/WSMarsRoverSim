package sfg.wseminar.rover.graph.generator

import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.graph.generator.GraphGeometry.distance
import sfg.wseminar.rover.graph.generator.GraphGeometry.orderedPair
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

object GraphGenerator {
    fun generate(options: GraphGenerationOptions = GraphGenerationOptions()): Graph =
        immediate { generateSuspending(options) {} }

    suspend fun generateSuspending(options: GraphGenerationOptions, checkpoint: suspend () -> Unit): Graph {
        options.terrain.validate()
        val work = GenerationWork(options.budget, checkpoint = checkpoint)
        val count = when (options.mode) {
            GraphGenerationMode.GRID -> options.gridColumns.toLong() * options.gridRows
            GraphGenerationMode.CONTINUOUS -> options.nodeCount.toLong()
        }
        checkGenerationBudget("nodes", count, GenerationLimits.MaxNodes.toLong(), "Reduce node count.")
        require(count in 2L..GenerationLimits.MaxNodes.toLong()) { "Choose between 2 and ${GenerationLimits.MaxNodes} nodes." }
        require(options.mode != GraphGenerationMode.GRID ||
            (options.gridColumns in 2..GenerationLimits.MaxGridSide && options.gridRows in 2..GenerationLimits.MaxGridSide))
        val graph = when (options.mode) {
            GraphGenerationMode.GRID -> generateGrid(options, work)
            GraphGenerationMode.CONTINUOUS -> generateContinuous(options, work)
        }.withScenarioEndpoints(options)
        require(graph.nodes.size.toLong() == count) { "Spacing constraints could not place the requested number of nodes." }
        if (work.budget.enabled) checkGenerationBudget("output edges", graph.edges.size.toLong(), work.budget.maxEdges.toLong(), "Reduce density or radius.")
        if (!options.allowDisconnected && !options.terrain.enabled) {
            val visited = BooleanArray(graph.nodes.size)
            val pending = ArrayDeque<Graph.Node>()
            pending.add(graph.nodes[checkNotNull(graph.startNodeId)])
            visited[pending.first().id] = true
            while (pending.isNotEmpty()) {
                if (work.add()) work.pause()
                val node = pending.removeFirst()
                // Connected means weakly connected for directed graphs, independent of endpoints.
                for (edge in graph.outgoingEdges(node) + graph.incomingEdges(node)) {
                    val next = edge.neighborOf(node)
                    if (!visited[next.id]) { visited[next.id] = true; pending.add(next) }
                }
            }
            require(visited.all { it }) { "Connectivity conflicts with clearance/planarity constraints. Reduce spacing or enable disconnected graphs." }
        }
        work.checkTime()
        return graph
    }

    private suspend fun generateGrid(
        options: GraphGenerationOptions,
        work: GenerationWork
    ): Graph {
        val columns = options.gridColumns.coerceAtLeast(2)
        val rows = options.gridRows.coerceAtLeast(2)
        val random = Random(options.seed)

        val nodes = buildList {
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    if (work.add()) work.pause()
                    add(
                        Graph.Node(
                            x = column.toDouble(),
                            y = row.toDouble(),
                            id = row * columns + column
                        )
                    )
                }
            }
        }

        val orthogonal = mutableListOf<Pair<Int, Int>>()
        val diagonals = mutableListOf<Pair<Int, Int>>()

        fun index(column: Int, row: Int): Int = row * columns + column

        for (row in 0 until rows) {
            for (column in 0 until columns) {
                if (work.add()) work.pause()
                if (column + 1 < columns) {
                    orthogonal += orderedPair(index(column, row), index(column + 1, row))
                }

                if (row + 1 < rows) {
                    orthogonal += orderedPair(index(column, row), index(column, row + 1))
                }

                if (options.includeGridDiagonals && column + 1 < columns && row + 1 < rows) {
                    diagonals += orderedPair(index(column, row), index(column + 1, row + 1))
                    diagonals += orderedPair(index(column + 1, row), index(column, row + 1))
                }
            }
        }

        val candidates = if (options.planar) {
            orthogonal + planarDiagonals(diagonals, columns, random)
        } else {
            orthogonal + diagonals
        }

        val edgeSpecs = selectEdges(
            nodes = nodes,
            candidates = candidates,
            density = options.density,
            planar = options.planar,
            allowDisconnected = options.allowDisconnected,
            clearance = 0.0,
            enforceClearance = false,
            random = random,
            work = work
        )

        return graphFromEdgeSpecs(
            sourceNodes = nodes,
            edgeSpecs = edgeSpecs,
            options = options,
            random = Random(options.seed.xor(0x5F3759DF)),
            work = work
        )
    }

    private fun planarDiagonals(
        diagonals: List<Pair<Int, Int>>,
        columns: Int,
        random: Random
    ): List<Pair<Int, Int>> {
        if (diagonals.isEmpty()) return emptyList()

        val byCell = diagonals.groupBy { (from, to) ->
            val fromColumn = min(from % columns, to % columns)
            val fromRow = min(from / columns, to / columns)
            fromRow to fromColumn
        }

        return byCell.values.map { pair ->
            pair.random(random)
        }
    }

    private suspend fun generateContinuous(
        options: GraphGenerationOptions,
        work: GenerationWork
    ): Graph {
        val nodeCount = options.nodeCount.coerceAtLeast(2)
        val random = Random(options.seed)
        val worldSpan = 1.0
        val minSeparation = if (options.allowNodeOverlap) {
            0.0
        } else {
            GraphLayoutMetrics.minNodeSeparation(
                nodeCount = nodeCount,
                worldSpan = worldSpan,
                clearanceMultiplier = options.nodeClearanceMultiplier,
                spacing = options.nodeSpacing
            )
        }

        val nodes = sampleContinuousNodes(
            nodeCount = nodeCount,
            worldSpan = worldSpan,
            minSeparation = minSeparation,
            random = random,
            work = work
        )

        val visualClearance = GraphLayoutMetrics.edgeNodeClearance(
            worldSpan = worldSpan,
            clearanceMultiplier = options.nodeClearanceMultiplier
        )
        val clearance = if (nodeCount > 140) minOf(visualClearance, minSeparation * 0.4) else visualClearance

        val candidates = if (options.planar) {
            DelaunayTriangulation.undirectedEdges(nodes, work)
        } else {
            radiusCandidates(
                nodes = nodes,
                radius = options.connectionRadius.coerceIn(0.08, 0.85) * worldSpan,
                work = work
            )
        }

        val edgeSpecs = selectEdges(
            nodes = nodes,
            candidates = candidates,
            density = options.density,
            planar = options.planar,
            allowDisconnected = options.allowDisconnected,
            clearance = clearance,
            enforceClearance = !options.allowNodeOverlap,
            random = random,
            work = work
        )

        return graphFromEdgeSpecs(
            sourceNodes = nodes,
            edgeSpecs = edgeSpecs,
            options = options,
            random = Random(options.seed.xor(0x5F3759DF)),
            work = work
        )
    }

    private suspend fun sampleContinuousNodes(
        nodeCount: Int,
        worldSpan: Double,
        minSeparation: Double,
        random: Random,
        work: GenerationWork
    ): List<Graph.Node> {
        val margin = worldSpan * 0.06
        val min = margin
        val max = worldSpan - margin

        if (minSeparation <= 0.0 || max <= min) {
            return buildList {
                for (id in 0 until nodeCount) {
                    if (work.add()) work.pause()
                    add(Graph.Node(
                    x = random.nextDouble(from = min, until = maxOf(max, min + 1e-6)),
                    y = random.nextDouble(from = min, until = maxOf(max, min + 1e-6)),
                    id = id
                    ))
                }
            }
        }

        val nodes = mutableListOf<Graph.Node>()
        val buckets = mutableMapOf<Pair<Int, Int>, MutableList<Graph.Node>>()
        val maxAttempts = nodeCount.toLong() * 400

        for (attempt in 0L until maxAttempts) {
            if (attempt % 128 == 0L) if (work.add(128)) work.pause()
            if (nodes.size >= nodeCount) return nodes

            val candidate = Graph.Node(
                x = random.nextDouble(from = min, until = max),
                y = random.nextDouble(from = min, until = max),
                id = nodes.size
            )

            val bx = (candidate.x / minSeparation).toInt()
            val by = (candidate.y / minSeparation).toInt()
            val farEnough = (-1..1).all { dx ->
                (-1..1).all { dy ->
                    buckets[bx + dx to by + dy].orEmpty().all { existing ->
                        distance(candidate, existing) >= minSeparation
                    }
                }
            }

            if (farEnough) {
                nodes += candidate
                buckets.getOrPut(bx to by) { mutableListOf() } += candidate
            }
        }

        if (nodes.size < nodeCount) {
            // If rejection sampling saturates, use a complete jittered layout rather
            // than silently returning fewer nodes than requested.
            return jitteredGridNodes(
                remaining = nodeCount,
                existing = emptyList(),
                min = min,
                max = max,
                minSeparation = minSeparation,
                random = random,
                work = work
            )
        }

        return nodes
    }

    private suspend fun jitteredGridNodes(
        remaining: Int,
        existing: List<Graph.Node>,
        min: Double,
        max: Double,
        minSeparation: Double,
        random: Random,
        work: GenerationWork
    ): List<Graph.Node> {
        val total = existing.size + remaining
        val span = (max - min).coerceAtLeast(1e-6)
        val columns = ceil(sqrt(total.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(total / columns.toDouble()).toInt().coerceAtLeast(1)
        val cell = min(span / columns, span / rows)
        val jitter = minOf(cell * 0.18, ((cell - minSeparation) / 2.0).coerceAtLeast(0.0))
        require(cell >= minSeparation) { "The requested spacing cannot fit all nodes. Reduce spacing or node count." }
        val result = mutableListOf<Graph.Node>()
        var nextId = existing.size

        for (row in 0 until rows) {
            for (column in 0 until columns) {
                if (work.add()) work.pause()
                if (result.size >= remaining) return result

                val x = (min + (column + 0.5) * cell + (random.nextDouble() * 2.0 - 1.0) * jitter)
                    .coerceIn(min, max)
                val y = (min + (row + 0.5) * cell + (random.nextDouble() * 2.0 - 1.0) * jitter)
                    .coerceIn(min, max)

                val candidate = Graph.Node(x = x, y = y, id = nextId)
                // Neighbor cells are at least cell - 2*jitter apart; no quadratic rescan.
                result += candidate
                nextId++
            }
        }

        return result
    }

    private suspend fun radiusCandidates(nodes: List<Graph.Node>, radius: Double, work: GenerationWork): List<Pair<Int, Int>> {
        val index = NodeSpatialIndex(nodes)
        val candidates = mutableListOf<Pair<Int, Int>>()
        val radiusSquared = radius * radius
        for (from in nodes) {
            if (work.add()) work.pause()
            for (to in index.query(from.x - radius, from.y - radius, from.x + radius, from.y + radius)) {
                if (work.add()) work.pause()
                if (to.id <= from.id) continue
                val dx = from.x - to.x
                val dy = from.y - to.y
                if (dx * dx + dy * dy <= radiusSquared) {
                    candidates += from.id to to.id
                    if (work.budget.enabled) checkGenerationBudget("candidate edges", candidates.size.toLong(), work.budget.maxCandidateEdges.toLong(), "Reduce radius or use a planar graph.")
                }
            }
        }
        return candidates
    }

    private suspend fun selectEdges(
        nodes: List<Graph.Node>,
        candidates: List<Pair<Int, Int>>,
        density: Double,
        planar: Boolean,
        allowDisconnected: Boolean,
        clearance: Double,
        enforceClearance: Boolean,
        random: Random,
        work: GenerationWork
    ): List<Pair<Int, Int>> {
        if (work.budget.enabled) checkGenerationBudget("candidate edges", candidates.size.toLong(), work.budget.maxCandidateEdges.toLong(), "Reduce radius or use a planar graph.")
        val spatial = NodeSpatialIndex(nodes)
        // Compute each geometric distance once rather than inside the sorting comparator.
        val distances = mutableMapOf<Pair<Int, Int>, Double>()
        val filtered = candidates
            .map { orderedPair(it.first, it.second) }
            .distinct()
            .filter { pair ->
                if (work.add()) work.pause()
                val (from, to) = pair
                distances[pair] = distance(nodes[from], nodes[to])
                from != to && (!enforceClearance || spatial.clears(nodes[from], nodes[to], clearance, work))
            }
        val usable = work.sorted(filtered, compareBy { distances.getValue(it) })

        if (usable.isEmpty()) {
            require(allowDisconnected) { "No connections satisfy the requested clearance. Reduce spacing or allow disconnected graphs." }
            return emptyList()
        }

        val accepted = linkedSetOf<Pair<Int, Int>>()

        if (!allowDisconnected) {
            accepted += kruskalSpanningTree(
                nodes = nodes,
                candidates = usable,
                work = work
            )
        }

        val extras = usable.filter { it !in accepted }
        val extraBudget = (extras.size * density.coerceIn(0.0, 1.0)).roundToInt()
        var addedExtras = 0

        for (candidate in extras.shuffled(random)) {
            if (addedExtras >= extraBudget) break
            if (work.add()) work.pause()

            // Candidate construction already guarantees planarity.

            if (work.budget.enabled) checkGenerationBudget("output edges", accepted.size.toLong() + 1, work.budget.maxEdges.toLong(), "Reduce density or radius.")
            accepted += candidate
            addedExtras++
        }

        return accepted.toList()
    }

    private suspend fun kruskalSpanningTree(
        nodes: List<Graph.Node>,
        candidates: List<Pair<Int, Int>>,
        work: GenerationWork
    ): List<Pair<Int, Int>> {
        val parent = IntArray(nodes.size) { it }

        fun find(value: Int): Int {
            var current = value

            while (parent[current] != current) {
                parent[current] = parent[parent[current]]
                current = parent[current]
            }

            return current
        }

        fun union(first: Int, second: Int): Boolean {
            val firstRoot = find(first)
            val secondRoot = find(second)

            if (firstRoot == secondRoot) return false

            parent[secondRoot] = firstRoot
            return true
        }

        val tree = linkedSetOf<Pair<Int, Int>>()
        val target = nodes.size - 1

        suspend fun consider(candidate: Pair<Int, Int>) {
            if (work.add()) work.pause()
            if (tree.size >= target || candidate in tree) return

            // Grid/Delaunay candidates do not cross.

            if (union(candidate.first, candidate.second)) {
                tree += candidate
            }
        }

        for (candidate in candidates) {
            if (tree.size == target) break
            consider(candidate)
        }
        require(tree.size == target) { "Cannot connect all nodes within the candidate/clearance constraints. Increase radius, reduce spacing, or allow disconnected graphs." }
        return tree.toList()
    }

    private suspend fun graphFromEdgeSpecs(
        sourceNodes: List<Graph.Node>,
        edgeSpecs: List<Pair<Int, Int>>,
        options: GraphGenerationOptions,
        random: Random,
        work: GenerationWork
    ): Graph {
        val metresPerUnit = if (!options.terrain.enabled) 1.0 else if (options.mode == GraphGenerationMode.GRID)
            options.terrain.cellSizeMetres else options.terrain.mapWidthMetres
        val nodes = if (metresPerUnit == 1.0) sourceNodes else sourceNodes.map {
            Graph.Node(it.x * metresPerUnit, it.y * metresPerUnit, it.id, it.label)
        }
        val unique = edgeSpecs.distinct()

        if (unique.isEmpty()) {
            return Graph(nodes = nodes, edges = emptyList(), generatorVersion = if (options.terrain.enabled) 3 else 2)
        }

        if (options.terrain.enabled) {
            val simulation = sfg.wseminar.rover.terrain.TerrainSimulation(options.terrain, options.seed, 1.0)
            val edges = mutableListOf<Graph.Edge>()
            for ((a, b) in unique) {
                if (work.add(options.terrain.samplesPerEdge)) work.pause()
                val evaluation = simulation.evaluate(nodes[a], nodes[b])
                if (evaluation.passable) edges += Graph.Edge(nodes[a], nodes[b], evaluation.cost, edges.size, options.bidirectional)
            }
            return Graph(nodes, edges, generatorVersion = 3)
        }

        val distances = unique.map { (from, to) -> distance(nodes[from], nodes[to]) }
        val median = work.sorted(distances, naturalOrder())[distances.size / 2].coerceAtLeast(1e-6)
        val scale = 14.0 / median
        val jitter = options.weightJitter.coerceIn(0.0, 0.8)
        // Separate stream preserves layout and base weights when only extremes change.
        val extremes = Random(options.seed xor 0x34AB172)

        val edges = unique.mapIndexed { id, (fromIndex, toIndex) ->
            if (work.add()) work.pause()
            val from = nodes[fromIndex]
            val to = nodes[toIndex]
            val geometric = distance(from, to) * scale
            val noise = 1.0 + (random.nextDouble() - 0.5) * 2.0 * jitter
            val discreteBias = if (options.mode == GraphGenerationMode.GRID && random.nextDouble() > 0.82) {
                1.0
            } else {
                0.0
            }

            val baseWeight = (geometric * noise + discreteBias).coerceAtLeast(1.0).roundToInt().toDouble()
            val factor = options.extremeWeightFactor.coerceIn(1.0, 100.0)
            val extremeRoll = extremes.nextDouble()
            val cheapRoll = extremes.nextDouble()
            val multiplier = if (extremeRoll < options.extremeWeightChance.coerceIn(0.0, 1.0)) {
                if (cheapRoll < options.cheapWeightShare.coerceIn(0.0, 1.0)) 1.0 / factor else factor
            } else 1.0

            Graph.Edge(
                from = from,
                to = to,
                weight = kotlin.math.round(baseWeight * multiplier * 100.0) / 100.0,
                id = id,
                bidirectional = options.bidirectional
            )
        }

        return Graph(nodes = nodes, edges = edges)
    }
}
