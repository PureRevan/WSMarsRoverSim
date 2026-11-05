package sfg.wseminar.rover.algorithms.backend

import kotlin.time.TimeSource
import sfg.wseminar.rover.SimulationFrame
import sfg.wseminar.rover.simulation.FrameHistory

/** Conservative accounting budget, not a measurement of process heap usage. */
@kotlinx.serialization.Serializable
data class SimulationLimits(
    val maxFrames: Int = 250_000,
    val maxEstimatedBytes: Long = defaultReplayMemoryBudgetBytes,
    val frameLimitEnabled: Boolean = true,
    val timeLimitEnabled: Boolean = false,
    val maxDurationMillis: Long = 120_000,
    val enabled: Boolean = true
) {
    init { require(maxFrames > 0 && maxEstimatedBytes > 0 && maxDurationMillis > 0) }
}

class CachedSimulationTimeline(
    private val context: AlgorithmContext,
    steps: Sequence<AlgorithmStep>,
    private val limits: SimulationLimits = SimulationLimits(),
    timeSource: TimeSource = TimeSource.Monotonic,
    private val retainHistory: Boolean = true
) {
    private val started = timeSource.markNow()
    private val iterator = steps.iterator()
    private var cache = FrameHistory()
    private var exhausted = false
    private var generatedCount = 0
    private var overheadBytes = 0L
    var stopReason: String? = null
        private set
    val peakAlgorithmBytes: Long get() = context.sampleAlgorithmMemory()
    val estimatedBytes: Long get() = overheadBytes + context.allocatedCostBytes
    val cachedFrames: FrameHistory get() = cache
    val isExhausted: Boolean get() = exhausted
    val lastCachedIndex: Int get() = cache.lastIndex

    fun frameAt(index: Int): SimulationFrame? {
        require(index >= 0)
        generateUntil(index)
        return cache.getOrNull(index)
    }

    fun generateNext(): SimulationFrame? {
        if (exhausted) return null
        if (limits.enabled && ((limits.frameLimitEnabled && generatedCount >= limits.maxFrames) || estimatedBytes >= limits.maxEstimatedBytes)) {
            stopReason = if (estimatedBytes >= limits.maxEstimatedBytes)
                "Replay memory budget reached. Recording stopped; recorded history is preserved. Increase the limit in Settings or turn on Disable all computation limits, then start a new run."
            else "Replay frame budget reached (${generatedCount} frames). Recording stopped; recorded history is preserved. Increase the limit in Settings or turn on Disable all computation limits, then start a new run."
            exhausted = true
            return null
        }
        if (stopIfTimedOut()) return null
        val hasNext = iterator.hasNext()
        context.sampleAlgorithmMemory()
        if (stopIfTimedOut()) return null
        if (!hasNext) { exhausted = true; return null }
        val frame = SimulationFrameBuilder.build(context, generatedCount, iterator.next())
        if (stopIfTimedOut()) return null
        // Includes conservative allowance for set pages, paths, maps, strings and history pointers.
        overheadBytes += 2048L + (context.graph.nodes.size * 4L + context.graph.edges.size) / 8 +
            16L * (frame.currentPathNodes.size + frame.finalPathNodes.size)
        generatedCount++
        if (retainHistory) cache = cache.append(listOf(frame))
        return frame
    }

    private fun stopIfTimedOut(): Boolean {
        if (!limits.enabled || !limits.timeLimitEnabled || started.elapsedNow().inWholeMilliseconds < limits.maxDurationMillis) return false
        stopReason = "Algorithm time budget reached (${limits.maxDurationMillis} ms). Recording stopped; recorded history is preserved. Increase the limit in Settings or turn on Disable all computation limits, then start a new run."
        exhausted = true
        return true
    }

    fun generateUntil(index: Int) {
        require(index >= 0)
        while (!exhausted && cache.lastIndex < index) generateNext()
    }
    fun generateAll(): List<SimulationFrame> {
        while (!exhausted) generateNext()
        return cache
    }
}
