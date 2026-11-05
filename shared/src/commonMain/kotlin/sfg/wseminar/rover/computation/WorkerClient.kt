package sfg.wseminar.rover.computation

import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import sfg.wseminar.rover.SimulationFrame
import sfg.wseminar.rover.algorithms.RoverAlgorithm
import sfg.wseminar.rover.algorithms.backend.*
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.graph.generator.GraphGenerationOptions
import sfg.wseminar.rover.computation.wire.*

interface WorkerTransport {
    fun send(bytes: ByteArray)
    suspend fun receive(): ByteArray
    fun close()
}
expect fun createWorkerTransport(): WorkerTransport?
class WorkerStartupException(cause: Throwable? = null) : Exception("Web Worker could not start. Check the worker bundle or choose Never in Web computation settings.", cause)

class WorkerClient private constructor(private val transport: WorkerTransport) {
    companion object {
        suspend fun open(factory: () -> WorkerTransport? = ::createWorkerTransport): WorkerClient {
            val transport = try { factory() ?: throw WorkerStartupException() }
            catch (e: Exception) { throw WorkerStartupException(e) }
            try {
                withTimeout(15_000) { require(readPacket(transport.receive()).first == 7) { "Worker handshake failed" } }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                transport.close()
                if (cancelled is kotlinx.coroutines.TimeoutCancellationException) {
                    currentCoroutineContext().ensureActive()
                    throw WorkerStartupException(cancelled)
                }
                throw cancelled
            } catch (e: Exception) { transport.close(); throw WorkerStartupException(e) }
            return WorkerClient(transport)
        }
    }
    private var decoder: FrameDecoder? = null
    private var cachedGraph: Graph? = null
    private var pendingBatch = false
    suspend fun generate(options: GraphGenerationOptions): Graph {
        check(!pendingBatch) { "Finish or cancel the recording before generating another graph" }
        decoder = null
        transport.send(packet(GENERATE) { write(options) })
        val (kind, input) = response()
        require(kind == GRAPH)
        return input.graph().also { cachedGraph = it }
    }
    suspend fun start(algorithm: RoverAlgorithm, graph: Graph, start: Graph.Node, goal: Graph.Node,
                      knowledge: KnowledgeMode, limits: SimulationLimits, initial: SimulationFrame?,
                      sensingRadius: Int = sfg.wseminar.rover.simulation.SensingRadius.DEFAULT): WorkerBatch {
        check(!pendingBatch) { "Finish or cancel the previous recording first" }
        decoder = FrameDecoder(graph)
        transport.send(packet(RUN) {
            bool(cachedGraph !== graph)
            if (cachedGraph !== graph) graph(graph)
            int(RoverAlgorithm.algorithms.indexOf(algorithm)); int(start.id); int(goal.id)
            int(knowledge.ordinal); int(graph.seed); write(limits); bool(initial != null)
            initial?.let { FrameEncoder().write(this, it) }
            int(sensingRadius)
        })
        cachedGraph = graph
        pendingBatch = true
        return batch()
    }
    suspend fun next(): WorkerBatch { check(pendingBatch); return batch() }
    private suspend fun response(): Pair<Int, PacketReader> {
        val (kind, reader) = readPacket(transport.receive())
        if (kind == ERROR) error(reader.string())
        return kind to reader
    }
    private suspend fun batch(): WorkerBatch {
        val (kind, reader) = response(); require(kind == BATCH)
        val done = reader.bool(); val bytes = reader.long(); val peak = reader.long(); val reason = if (reader.bool()) reader.string() else null
        pendingBatch = !done
        // One response can arrive while this batch is decoded/published. Never queue unbounded work.
        if (pendingBatch) transport.send(packet(NEXT))
        val frames = List(reader.count()) { checkNotNull(decoder).read(reader) }
        if (done) decoder = null
        return WorkerBatch(frames, done, bytes, reason, peak)
    }
    fun close() = transport.close()
}
data class WorkerBatch(val frames: List<SimulationFrame>, val done: Boolean, val estimatedBytes: Long, val stopReason: String?, val peakAlgorithmBytes: Long = 0)
