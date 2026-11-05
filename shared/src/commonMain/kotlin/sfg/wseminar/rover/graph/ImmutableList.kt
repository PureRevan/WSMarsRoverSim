package sfg.wseminar.rover.graph

/** Defensive copy with no exposed mutable-list implementation. */
internal class ImmutableList<T>(values: Collection<T>) : AbstractList<T>() {
    private val elements = values.toList()
    override val size: Int get() = elements.size
    override fun get(index: Int): T = elements[index]
}
