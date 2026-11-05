package sfg.wseminar.rover.graph.generator

import sfg.wseminar.rover.graph.Graph

/** Cache mutation belongs to the UI owner; workers only build private graphs. */
class GeneratedGraphCache(private val generate: ((GraphGenerationOptions) -> Graph)? = null) {
    private data class Entry(val options: GraphGenerationOptions, val graph: Graph)
    private val entries = mutableMapOf<GraphGenerationMode, Entry>()
    fun cached(options: GraphGenerationOptions): Graph? = entries[options.mode]?.takeIf { it.options == options }?.graph
    fun store(options: GraphGenerationOptions, graph: Graph) { entries[options.mode] = Entry(options, graph) }
    suspend fun build(options: GraphGenerationOptions, checkpoint: suspend () -> Unit): Graph =
        generate?.invoke(options) ?: GraphGenerator.generateSuspending(options, checkpoint)
    fun getOrGenerate(options: GraphGenerationOptions): Graph {
        cached(options)?.let { return it }
        return (generate?.invoke(options) ?: GraphGenerator.generate(options)).also { store(options, it) }
    }
    fun latest(mode: GraphGenerationMode): Graph? = entries[mode]?.graph
}
