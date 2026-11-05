package sfg.wseminar.rover.simulation

import sfg.wseminar.rover.SimulationFrame

/** Immutable history pages: publishing a batch never copies the whole replay. */
class FrameHistory private constructor(
    private val pages: List<List<SimulationFrame>>,
    override val size: Int
) : AbstractList<SimulationFrame>() {
    constructor() : this(emptyList(), 0)
    override fun get(index: Int): SimulationFrame {
        require(index in 0 until size)
        return pages[index / 128][index % 128]
    }
    fun append(frames: List<SimulationFrame>): FrameHistory {
        if (frames.isEmpty()) return this
        val next = pages.toMutableList()
        var offset = 0
        if (size % 128 != 0) {
            val count = minOf(128 - size % 128, frames.size)
            next[next.lastIndex] = next.last() + frames.subList(0, count)
            offset = count
        }
        while (offset < frames.size) {
            val end = minOf(offset + 128, frames.size)
            next.add(frames.subList(offset, end).toList())
            offset = end
        }
        return FrameHistory(next, size + frames.size)
    }
    fun prefix(count: Int): FrameHistory {
        require(count in 0..size)
        if (count == size) return this
        val full = count / 128
        val next = pages.take(full).toMutableList()
        if (count % 128 != 0) next.add(pages[full].take(count % 128))
        return FrameHistory(next, count)
    }
}
