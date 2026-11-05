package sfg.wseminar.rover.graph.generator

import kotlin.time.TimeSource
import kotlin.coroutines.*

/** Cheap synchronous accounting; create a suspension only when a slice actually expires. */
internal class GenerationWork(val budget: GenerationBudget = GenerationBudget(), private val timeSource: TimeSource = TimeSource.Monotonic,
    private val checkpoint: suspend () -> Unit) {
    private var operations = 0L
    private var nextClockCheck = 0L
    private val started = timeSource.markNow()
    private var clock = timeSource.markNow()
    fun add(amount: Int = 1): Boolean {
        operations += amount
        if (budget.enabled && budget.workLimitEnabled) checkGenerationBudget("work operations", operations, budget.maxWorkOperations, "Reduce node count, radius, or clearance.")
        if (operations < nextClockCheck) return false
        checkTime()
        nextClockCheck = operations + 256
        return clock.elapsedNow().inWholeMilliseconds >= 4
    }
    suspend fun pause() { checkpoint(); clock = timeSource.markNow() }

    fun checkTime() {
        require(!budget.enabled || !budget.timeLimitEnabled || started.elapsedNow().inWholeMilliseconds < budget.maxDurationMillis) {
            "Graph generation stopped: time budget exceeded (${budget.maxDurationMillis} ms). Increase or disable the time limit, or turn on Disable all computation limits in graph generation or Settings."
        }
    }

    /** Stable merge sort with checkpoints even during a large single merge. */
    suspend fun <T> sorted(values: List<T>, comparator: Comparator<T>): List<T> {
        var source = values.toMutableList()
        var target = values.toMutableList()
        var width = 1
        while (width < source.size) {
            var start = 0
            while (start < source.size) {
                val middle = minOf(start + width, source.size)
                val end = minOf(start + width * 2, source.size)
                var a = start; var b = middle
                for (out in start until end) {
                    if (add()) pause()
                    target[out] = if (a < middle && (b >= end || comparator.compare(source[a], source[b]) <= 0)) source[a++] else source[b++]
                }
                start = end
            }
            val swap = source; source = target; target = swap
            width *= 2
        }
        return source
    }
}

internal fun <T> immediate(block: suspend () -> T): T {
    var completion: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(result: Result<T>) { completion = result }
    })
    return checkNotNull(completion) { "Synchronous generation unexpectedly suspended." }.getOrThrow()
}
