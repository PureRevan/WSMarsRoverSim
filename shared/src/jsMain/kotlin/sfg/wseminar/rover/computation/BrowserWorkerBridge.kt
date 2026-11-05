package sfg.wseminar.rover.computation

internal actual fun workerStart(message: (ByteArray) -> Unit, error: (String) -> Unit): Int {
    val bridge: dynamic = js("""(message, error) => {
 let worker;
 try { worker = new Worker(new URL('rover-worker.js', document.baseURI)); }
 catch (failure) { error(String(failure)); return -1; }
 const workers = globalThis.__roverWorkers || (globalThis.__roverWorkers = new Map());
 const id = (globalThis.__roverWorkerId || 0) + 1; globalThis.__roverWorkerId = id;
 workers.set(id, worker);
 worker.onmessage = e => message(new Int8Array(e.data));
 worker.onerror = e => { e.preventDefault(); error(e.message || 'Worker script failed to load'); };
 worker.onmessageerror = () => error('Worker message could not be decoded');
 return id;
}""")
    return bridge(message, error)
}
// The packet is no longer used after send, so its backing buffer can be transferred.
internal actual fun workerSend(id: Int, bytes: ByteArray) {
    val bridge: dynamic = js("""(id, bytes) => { globalThis.__roverWorkers.get(id).postMessage(bytes.buffer, [bytes.buffer]); }""")
    bridge(id, bytes)
}
internal actual fun workerClose(id: Int): Unit {
    val bridge: dynamic = js("""(id) => { const workers = globalThis.__roverWorkers; const worker = workers && workers.get(id); if (worker) { worker.terminate(); workers.delete(id); } }""")
    bridge(id)
}
