package sfg.wseminar.rover.algorithms.backend

import sfg.wseminar.rover.graph.Graph

/**
 * Indexed min-heap: assignment inserts or replaces a node's priority in O(log n).
 * Priorities are stored values, never comparators over mutable cost tables.
 * Equal priorities are ordered by node ID for reproducible simulation frames.
 */
class TrackedFrontier<P : Comparable<P>> internal constructor(
    private val context: AlgorithmContext
) {
    private data class Entry<P>(val node: Graph.Node, val priority: P)
    private val heap = mutableListOf<Entry<P>>()
    private val positions = IntArray(context.graph.nodes.size) { -1 }

    private var peakEntries = 0
    init { context.frontierMemory(0) }

    val size: Int get() = heap.size
    fun isNotEmpty(): Boolean = heap.isNotEmpty()

    operator fun contains(node: Graph.Node): Boolean {
        return positions[node.id] >= 0
    }

    operator fun set(node: Graph.Node, priority: P) {
        val index = positions[node.id]
        if (index < 0) {
            positions[node.id] = heap.size
            heap += Entry(node, priority)
            up(heap.lastIndex)
        } else {
            heap[index] = Entry(node, priority)
            down(up(index))
        }
        peakEntries = maxOf(peakEntries, heap.size)
        context.frontierMemory(peakEntries)
        context.frontierNodes.add(node)
        context.visitedNodes.remove(node) // Reopening is valid for incremental searches.
    }

    fun peek(): Graph.Node = heap.first().node
    fun peekPriority(): P = heap.first().priority

    /** Extracting a node records the expansion; removal alone does not. */
    fun removeMin(): Graph.Node {
        check(heap.isNotEmpty()) { "The frontier is empty." }
        val node = heap.first().node
        remove(node)
        context.visitedNodes.add(node)
        if (context.knowledgeMode == KnowledgeMode.GLOBAL) {
            context.roverNode = node
            context.currentPathTo(node)
        }
        return node
    }

    fun remove(node: Graph.Node): Boolean {
        val index = positions[node.id]
        if (index < 0) return false
        val last = heap.removeAt(heap.lastIndex)
        positions[node.id] = -1
        if (index < heap.size) {
            heap[index] = last
            positions[last.node.id] = index
            down(up(index))
        }
        context.frontierNodes.remove(node)
        return true
    }

    private fun less(a: Int, b: Int): Boolean {
        val comparison = heap[a].priority.compareTo(heap[b].priority)
        return comparison < 0 || (comparison == 0 && heap[a].node.id < heap[b].node.id)
    }

    private fun swap(a: Int, b: Int) {
        val entry = heap[a]
        heap[a] = heap[b]
        heap[b] = entry
        positions[heap[a].node.id] = a
        positions[heap[b].node.id] = b
    }

    private fun up(start: Int): Int {
        var index = start
        while (index > 0) {
            val parent = (index - 1) / 2
            if (!less(index, parent)) break
            swap(index, parent)
            index = parent
        }
        return index
    }

    private fun down(start: Int) {
        var index = start
        while (2 * index + 1 < heap.size) {
            var child = 2 * index + 1
            if (child + 1 < heap.size && less(child + 1, child)) child++
            if (!less(child, index)) break
            swap(index, child)
            index = child
        }
    }
}
