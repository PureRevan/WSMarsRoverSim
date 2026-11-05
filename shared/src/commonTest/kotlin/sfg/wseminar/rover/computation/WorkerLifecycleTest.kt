package sfg.wseminar.rover.computation

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerLifecycleTest {
    private class WaitingTransport : WorkerTransport {
        val responses = Channel<ByteArray>(1)
        var closed = false
        override fun send(bytes: ByteArray) = Unit
        override suspend fun receive() = responses.receive()
        override fun close() { closed = true; responses.cancel() }
    }
    @Test fun cancellationDuringStartupTerminatesTransport() = runTest {
        val transport = WaitingTransport()
        val connecting = async { WorkerClient.open { transport } }
        runCurrent()
        connecting.cancelAndJoin()
        assertTrue(transport.closed)
    }
    @Test fun anOuterTimeoutIsCancellationNotAnAutomaticFallback() = runTest {
        val transport = WaitingTransport()
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(5) { WorkerClient.open { transport } }
        }
        assertTrue(transport.closed)
    }
    @Test fun aStartupTimeoutClosesTransportAndCanBeHandledAsFallback() = runTest {
        val transport = WaitingTransport()
        assertFailsWith<WorkerStartupException> { WorkerClient.open { transport } }
        assertTrue(transport.closed)
    }
}
