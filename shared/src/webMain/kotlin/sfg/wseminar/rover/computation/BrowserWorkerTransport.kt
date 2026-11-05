package sfg.wseminar.rover.computation

import kotlinx.coroutines.channels.Channel

internal expect fun workerStart(message: (ByteArray) -> Unit, error: (String) -> Unit): Int
internal expect fun workerSend(id: Int, bytes: ByteArray)
internal expect fun workerClose(id: Int)

actual fun createWorkerTransport(): WorkerTransport? = BrowserWorkerTransport()
private class BrowserWorkerTransport : WorkerTransport {
    private val responses = Channel<ByteArray>(1)
    private var closed = false
    private val id = workerStart({ message ->
        if (!closed) {
            try {
                if (!responses.trySend(message).isSuccess) responses.close(IllegalStateException("Worker sent an unexpected extra batch"))
            } catch (e: Exception) { responses.close(e) }
        }
    }, { error -> if (!closed) responses.close(IllegalStateException(error)) })
    override fun send(bytes: ByteArray) { check(!closed); workerSend(id, bytes) }
    override suspend fun receive(): ByteArray = responses.receive()
    override fun close() {
        if (!closed) { closed = true; workerClose(id); responses.cancel() }
    }
}
