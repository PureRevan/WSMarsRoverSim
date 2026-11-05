package sfg.wseminar.rover.computation.wire

import sfg.wseminar.rover.algorithms.RoverAlgorithm
import sfg.wseminar.rover.algorithms.backend.*
import sfg.wseminar.rover.graph.generator.GraphGenerator
import kotlin.time.TimeSource

/** Runs entirely inside a dedicated worker; NEXT provides one-batch backpressure. */
class WorkerEngine {
    private var timeline: CachedSimulationTimeline? = null
    private var encoder = FrameEncoder()
    private var graph: sfg.wseminar.rover.graph.Graph? = null
    fun handle(bytes: ByteArray): ByteArray = try {
        val (kind, input) = readPacket(bytes)
        when (kind) {
            GENERATE -> {
                val generated = GraphGenerator.generate(input.readGraphGenerationOptions())
                graph = generated
                packet(GRAPH) { graph(generated) }
            }
            RUN -> {
                if (input.bool()) graph = input.graph()
                val graph = checkNotNull(graph) { "Worker has no graph" }
                val algorithm = RoverAlgorithm.algorithms[input.int()]
                val start = graph.nodes[input.int()]; val goal = graph.nodes[input.int()]
                val knowledge = KnowledgeMode.entries[input.int()]; val seed = input.int()
                val limits = input.readSimulationLimits()
                val initial = if (input.bool()) FrameDecoder(graph).read(input) else null
                val sensingRadius = input.int()
                timeline = AlgorithmRunner.createTimeline(algorithm, graph, start, goal, knowledge, seed, initial, limits,
                    retainHistory = false, sensingRadius = sensingRadius)
                encoder = FrameEncoder()
                next()
            }
            NEXT -> next()
            else -> error("Unknown worker request: $kind")
        }
    } catch (failure: Exception) { packet(ERROR) { string(failure.message ?: "Worker computation failed") } }

    private fun next(): ByteArray {
        val timeline = checkNotNull(timeline)
        val frames = PacketWriter()
        var count = 0
        val clock = TimeSource.Monotonic.markNow()
        // Include encoding in the time slice. The byte cap bounds UI decoding and queued memory.
        while (count < 2048 && frames.size < 512 * 1024 &&
            (count == 0 || clock.elapsedNow().inWholeMilliseconds < 12)) {
            val frame = timeline.generateNext() ?: break
            encoder.write(frames, frame)
            count++
        }
        val result = packet(BATCH) {
            bool(timeline.isExhausted); long(timeline.estimatedBytes); long(timeline.peakAlgorithmBytes)
            bool(timeline.stopReason != null); timeline.stopReason?.let(::string)
            int(count); raw(frames.bytes())
        }
        if (timeline.isExhausted) {
            this.timeline = null
            encoder = FrameEncoder()
        }
        return result
    }
}
