package sfg.wseminar.rover.computation.wire

import sfg.wseminar.rover.SimulationFrame
import sfg.wseminar.rover.graph.*
import sfg.wseminar.rover.simulation.CostSnapshot

/** Only changed immutable pages cross the boundary. Each codec belongs to one recording. */
class FrameEncoder {
    private var previous: SimulationFrame? = null
    fun write(out: PacketWriter, frame: SimulationFrame) = with(out) {
        val old = previous
        int(frame.stepIndex); int(frame.roverNode?.id ?: -1); int(frame.startNode?.id ?: -1); int(frame.goalNode?.id ?: -1)
        mask(frame.knownNodes, old?.knownNodes); mask(frame.knownEdges, old?.knownEdges)
        mask(frame.visitedNodes, old?.visitedNodes); mask(frame.frontierNodes, old?.frontierNodes)
        mask(frame.physicallyVisitedNodes, old?.physicallyVisitedNodes)
        ids(frame.currentPathNodes, old?.currentPathNodes); ids(frame.finalPathNodes, old?.finalPathNodes)
        ids(frame.currentPathEdges, old?.currentPathEdges); ids(frame.finalPathEdges, old?.finalPathEdges)
        costs(frame.nodeCosts, old?.nodeCosts); costs(frame.edgeCosts, old?.edgeCosts)
        int(frame.nodeCostFields.size)
        frame.nodeCostFields.forEach { (key, value) ->
            string(key)
            bool(value === frame.nodeCosts)
            if (value !== frame.nodeCosts) costs(value, old?.nodeCostFields?.get(key))
        }
        string(frame.message); string(frame.costMeaning); int(frame.runSeed)
        previous = frame
    }
    private fun PacketWriter.ids(items: List<IDLabeled>, old: List<IDLabeled>?) {
        if (items === old) { int(-1); return }
        var prefix = 0
        if (old != null) while (prefix < minOf(items.size, old.size) && items[prefix] === old[prefix]) prefix++
        int(prefix); int(items.size - prefix)
        for (i in prefix until items.size) int(items[i].id)
    }
    private fun PacketWriter.costs(value: CostSnapshot, previous: CostSnapshot?) {
        if (value === previous) { int(-1); return }
        int(value.size)
        val changed = value.pages.indices.filter { previous?.pages?.getOrNull(it) !== value.pages[it] }
        int(changed.size)
        changed.forEach { page ->
            int(page)
            val values = value.pages[page]
            val old = previous?.pages?.getOrNull(page)
            val changedEntries = if (old?.size == values.size) values.indices.filter { values[it].toBits() != old[it].toBits() } else null
            if (changedEntries != null && changedEntries.size * 12 < values.size * 8) {
                int(changedEntries.size)
                changedEntries.forEach { int(it); double(values[it]) }
            } else {
                int(-1); int(values.size); values.forEach(::double)
            }
        }
    }
    private fun <T : IDLabeled> PacketWriter.mask(value: IDSet<T>, previous: IDSet<T>?) {
        if (value === previous) { int(-1); return }
        val bits = value as IDBitSet<T>; val old = previous as? IDBitSet<T>
        int(bits.capacity); int(bits.size)
        val changed = bits.pages.indices.filter { old?.pages?.getOrNull(it) !== bits.pages[it] }
        int(changed.size)
        changed.forEach { page ->
            int(page)
            val words = bits.pages[page]
            val previousWords = old?.pages?.getOrNull(page)
            val entries = if (previousWords?.size == words.size) words.indices.filter { words[it] != previousWords[it] } else null
            if (entries != null && entries.size * 12 < words.size * 8) {
                int(entries.size)
                entries.forEach { int(it); long(words[it]) }
            } else {
                int(-1); int(words.size); words.forEach(::long)
            }
        }
    }
}

class FrameDecoder(private val graph: Graph) {
    private var previous: SimulationFrame? = null
    fun read(input: PacketReader): SimulationFrame = with(input) {
        val old = previous
        val step = int(); val rover = int(); val start = int(); val goal = int()
        require(listOf(rover, start, goal).all { it == -1 || it in graph.nodes.indices })
        val knownNodes = mask(old?.knownNodes, graph.nodes.size); val knownEdges = mask(old?.knownEdges, graph.edges.size)
        val visited = mask(old?.visitedNodes, graph.nodes.size); val frontier = mask(old?.frontierNodes, graph.nodes.size)
        val physical = mask(old?.physicallyVisitedNodes, graph.nodes.size)
        val path = ids(old?.currentPathNodes, graph.nodes); val final = ids(old?.finalPathNodes, graph.nodes)
        val pathEdges = ids(old?.currentPathEdges, graph.edges); val finalEdges = ids(old?.finalPathEdges, graph.edges)
        val nodeCosts = costs(old?.nodeCosts, graph.nodes.size); val edgeCosts = costs(old?.edgeCosts, graph.edges.size)
        val fieldCount = count()
        val fields = buildMap { repeat(fieldCount) { val key = string(); put(key, if (bool()) nodeCosts else costs(old?.nodeCostFields?.get(key), graph.nodes.size)) } }
        SimulationFrame(graph, step, graph.nodes.getOrNull(rover), graph.nodes.getOrNull(start), graph.nodes.getOrNull(goal),
            knownNodes, knownEdges, visited, frontier, path, final, nodeCosts, edgeCosts, string(), fields,
            pathEdges, finalEdges, physical, string(), int()).also { previous = it }
    }
    private fun <T> PacketReader.ids(previous: List<T>?, values: List<T>): List<T> {
        val prefix = int()
        if (prefix == -1) return checkNotNull(previous)
        require(prefix in 0..(previous?.size ?: 0))
        val added = List(count()) { values[int()] }
        if (previous != null && prefix == previous.size && added.isEmpty()) return previous
        val base = (previous as? PagedList<T>) ?: PagedList<T>().append(previous.orEmpty())
        return base.prefix(prefix).append(added)
    }
    private fun PacketReader.costs(previous: CostSnapshot?, expectedSize: Int): CostSnapshot {
        val size = int()
        if (size == -1) return checkNotNull(previous)
        require(size == expectedSize)
        val pages = previous?.takeIf { it.size == size }?.pages?.copyOf() ?: Array((size + 127) / 128) { DoubleArray(minOf(128, size - it * 128)) }
        val changes = count()
        repeat(changes) {
            val page = int()
            val entries = int()
            pages[page] = if (entries == -1) DoubleArray(count().also { require(it == pages[page].size) }) { double() }
            else pages[page].copyOf().also { values ->
                require(entries in 0..values.size)
                repeat(entries) { values[int()] = double() }
            }
        }
        if (changes == 0 && previous?.size == size) return previous
        return CostSnapshot(size, pages)
    }
    private fun <T : IDLabeled> PacketReader.mask(previous: IDSet<T>?, expectedSize: Int): IDSet<T> {
        val capacity = int()
        if (capacity == -1) return checkNotNull(previous)
        require(capacity == expectedSize)
        val size = int().also { require(it in 0..capacity) }
        val old = previous as? IDBitSet<T>
        val pages = old?.takeIf { it.capacity == capacity }?.pages?.copyOf() ?: Array((capacity + 4095) / 4096) { page -> LongArray(minOf(64, (capacity - page * 4096 + 63) / 64)) }
        val changes = count()
        repeat(changes) {
            val page = int()
            val entries = int()
            pages[page] = if (entries == -1) LongArray(count().also { require(it == pages[page].size) }) { long() }
            else pages[page].copyOf().also { words ->
                require(entries in 0..words.size)
                repeat(entries) { words[int()] = long() }
            }
        }
        if (changes == 0 && old?.capacity == capacity && old.size == size) return old
        return IDBitSet<T>(capacity, pages, size, true)
    }
}
