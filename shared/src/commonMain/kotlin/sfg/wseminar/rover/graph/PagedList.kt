package sfg.wseminar.rover.graph

/** Immutable lists with shared 128-element pages, including path prefixes. */
internal class PagedList<T> private constructor(
    private val pages: List<List<T>>, override val size: Int
) : AbstractList<T>() {
    constructor() : this(emptyList(), 0)
    override fun get(index: Int): T {
        require(index in 0 until size)
        return pages[index / 128][index % 128]
    }
    fun append(values: List<T>): PagedList<T> {
        if (values.isEmpty()) return this
        val next = pages.toMutableList()
        var offset = 0
        if (size % 128 != 0) {
            val count = minOf(128 - size % 128, values.size)
            next[next.lastIndex] = next.last() + values.subList(0, count)
            offset = count
        }
        while (offset < values.size) {
            val end = minOf(offset + 128, values.size)
            next.add(values.subList(offset, end).toList())
            offset = end
        }
        return PagedList(next, size + values.size)
    }
    fun prefix(count: Int): PagedList<T> {
        require(count in 0..size)
        if (count == size) return this
        val next = pages.take(count / 128).toMutableList()
        if (count % 128 != 0) next.add(pages[count / 128].take(count % 128))
        return PagedList(next, count)
    }
}
