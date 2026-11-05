package sfg.wseminar.rover.simulation

private const val PAGE_SIZE = 128

/** Immutable primitive pages. Unchanged pages are shared by successive frames. */
class CostSnapshot internal constructor(
    override val size: Int,
    internal val pages: Array<DoubleArray>
) : AbstractList<Double>() {
    override fun get(index: Int): Double {
        require(index in 0 until size)
        return pages[index / PAGE_SIZE][index % PAGE_SIZE]
    }
    fun toDoubleArray(): DoubleArray = DoubleArray(size) { get(it) }

    companion object {
        fun of(values: DoubleArray): CostSnapshot = CostSnapshot(values.size,
            Array((values.size + PAGE_SIZE - 1) / PAGE_SIZE) { i ->
                values.copyOfRange(i * PAGE_SIZE, minOf(values.size, (i + 1) * PAGE_SIZE))
            })
    }
}

/** Single writer. Publishing a snapshot makes its pages copy-on-write. */
internal class PagedCosts(val size: Int, initial: Double) {
    private var pages = Array((size + PAGE_SIZE - 1) / PAGE_SIZE) { i ->
        DoubleArray(minOf(PAGE_SIZE, size - i * PAGE_SIZE)) { initial }
    }
    private val shared = BooleanArray(pages.size)
    private var cached: CostSnapshot? = null
    var allocatedBytes: Long = size * 8L
        private set

    operator fun get(index: Int): Double = pages[index / PAGE_SIZE][index % PAGE_SIZE]
    operator fun set(index: Int, value: Double) {
        if (get(index) == value) return
        val page = index / PAGE_SIZE
        if (shared[page]) {
            pages[page] = pages[page].copyOf()
            allocatedBytes += pages[page].size * 8L
            shared[page] = false
        }
        pages[page][index % PAGE_SIZE] = value
        cached = null
    }
    fun snapshot(): CostSnapshot = cached ?: CostSnapshot(size, pages.copyOf()).also {
        allocatedBytes += pages.size * 8L + 32
        shared.fill(true)
        cached = it
    }
}
