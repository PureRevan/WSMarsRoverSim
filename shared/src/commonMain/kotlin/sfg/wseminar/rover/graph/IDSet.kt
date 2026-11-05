package sfg.wseminar.rover.graph

interface IDSet<T : IDLabeled> {
    val size: Int
    operator fun contains(element: T): Boolean
    fun add(element: T): Boolean
    fun remove(element: T): Boolean
    /** A detached mutable copy (pages are copy-on-write). */
    fun copy(): IDSet<T>
    /** Immutable publication boundary. */
    fun snapshot(): IDSet<T>

    companion object {
        fun <T : IDLabeled> create(size: Int): IDSet<T> = IDBitSet(size)
    }
}

/** 4,096 IDs per page; static global masks are shared across all frames. */
class IDBitSet<T : IDLabeled> internal constructor(
    val capacity: Int,
    internal val pages: Array<LongArray>,
    private var count: Int,
    private val frozen: Boolean,
    sharedPages: Boolean = true
) : IDSet<T> {
    constructor(capacity: Int) : this(capacity,
        Array((capacity + 4095) / 4096) { page -> LongArray(minOf(64, (capacity - page * 4096 + 63) / 64)) }, 0, false, false)
    private val shared = BooleanArray(pages.size) { sharedPages }
    private var cached: IDSet<T>? = null
    override val size: Int get() = count

    override fun contains(element: T): Boolean {
        if (element.id !in 0 until capacity) return false
        return pages[element.id / 4096][element.id / 64 % 64] and (1L shl (element.id % 64)) != 0L
    }
    private fun change(element: T, present: Boolean): Boolean {
        check(!frozen) { "Frame sets are immutable; use copy() before editing." }
        require(element.id in 0 until capacity)
        if ((element in this) == present) return false
        val page = element.id / 4096
        if (shared[page]) {
            pages[page] = pages[page].copyOf()
            shared[page] = false
        }
        val word = element.id / 64 % 64
        val mask = 1L shl (element.id % 64)
        pages[page][word] = if (present) pages[page][word] or mask else pages[page][word] and mask.inv()
        count += if (present) 1 else -1
        cached = null
        return true
    }
    override fun add(element: T): Boolean = change(element, true)
    override fun remove(element: T): Boolean = change(element, false)
    override fun copy(): IDSet<T> {
        shared.fill(true)
        return IDBitSet(capacity, pages.copyOf(), count, false)
    }
    override fun snapshot(): IDSet<T> {
        if (frozen) return this
        return cached ?: IDBitSet<T>(capacity, pages.copyOf(), count, true).also {
            shared.fill(true)
            cached = it
        }
    }
}
