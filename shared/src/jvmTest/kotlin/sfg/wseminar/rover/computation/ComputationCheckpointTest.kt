package sfg.wseminar.rover.computation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import sfg.wseminar.rover.graph.generator.*
import sfg.wseminar.rover.ui.models.VisualizerViewModel
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent.GenerateGraph

@OptIn(ExperimentalCoroutinesApi::class)
class ComputationCheckpointTest {
    @Test fun desktopCheckpointsDoNotScheduleTimers() = runTest {
        val before = testScheduler.currentTime
        repeat(100) { computationCheckpoint() }
        assertEquals(before, testScheduler.currentTime)
        val child = async {
            currentCoroutineContext().cancel()
            computationCheckpoint()
            fail("Cancelled checkpoint continued")
        }
        assertFailsWith<CancellationException> { child.await() }
    }

    @Test fun desktopGenerationActuallyRunsOffTheUiOwnerThread(): Unit = runBlocking {
        val owner = Thread.currentThread()
        val workerThread = CompletableDeferred<Thread>()
        val cache = GeneratedGraphCache { options ->
            workerThread.complete(Thread.currentThread())
            GraphGenerator.generate(options)
        }
        val model = VisualizerViewModel(graphCache = cache, scope = this)
        try {
            model.onEvent(GenerateGraph)
            assertNotSame(owner, withTimeout(10_000) { workerThread.await() })
            withTimeout(10_000) { while (model.state.isComputing) delay(1) }
            assertNotNull(model.state.generatedGraph)
        } finally { model.close() }
    }
}
