@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package sfg.wseminar.rover.computation

import kotlin.js.JsAny

@JsFun("""(message, error) => {
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
private external fun workerStartBridge(message: (JsAny) -> Unit, error: (String) -> Unit): Int
internal actual fun workerStart(message: (ByteArray) -> Unit, error: (String) -> Unit): Int =
    workerStartBridge({ message(copyFromJs(it)) }, error)
@JsFun("""(id, bytes) => { globalThis.__roverWorkers.get(id).postMessage(bytes.buffer, [bytes.buffer]); }""")
private external fun workerSendBridge(id: Int, bytes: JsAny)
internal actual fun workerSend(id: Int, bytes: ByteArray) = workerSendBridge(id, copyToJs(bytes))

// Kotlin/Wasm arrays live in GC memory. Copy binary words across interop; no strings/Base64.
@JsFun("(size) => new Int8Array(size)")
private external fun allocate(size: Int): JsAny
@JsFun("(bytes) => bytes.length")
private external fun length(bytes: JsAny): Int
@JsFun("(bytes, i) => bytes[i]")
private external fun getByte(bytes: JsAny, i: Int): Int
@JsFun("(bytes, i, v) => { bytes[i] = v; }")
private external fun setByte(bytes: JsAny, i: Int, v: Int)
@JsFun("(b, i) => (b[i] & 255) | ((b[i+1] & 255) << 8) | ((b[i+2] & 255) << 16) | (b[i+3] << 24)")
private external fun getWord(bytes: JsAny, i: Int): Int
@JsFun("(b, i, v) => { b[i] = v; b[i+1] = v >>> 8; b[i+2] = v >>> 16; b[i+3] = v >>> 24; }")
private external fun setWord(bytes: JsAny, i: Int, v: Int)
private fun copyFromJs(bytes: JsAny): ByteArray {
    val result = ByteArray(length(bytes))
    var i = 0
    while (i + 4 <= result.size) {
        val word = getWord(bytes, i)
        repeat(4) { result[i + it] = (word ushr (it * 8)).toByte() }
        i += 4
    }
    while (i < result.size) { result[i] = getByte(bytes, i).toByte(); i++ }
    return result
}
private fun copyToJs(bytes: ByteArray): JsAny {
    val result = allocate(bytes.size)
    var i = 0
    while (i + 4 <= bytes.size) {
        var word = 0
        repeat(4) { word = word or ((bytes[i + it].toInt() and 255) shl (it * 8)) }
        setWord(result, i, word)
        i += 4
    }
    while (i < bytes.size) { setByte(result, i, bytes[i].toInt()); i++ }
    return result
}
@JsFun("""(id) => { const workers = globalThis.__roverWorkers; const worker = workers && workers.get(id); if (worker) { worker.terminate(); workers.delete(id); } }""")
private external fun workerCloseBridge(id: Int): Unit
internal actual fun workerClose(id: Int): Unit = workerCloseBridge(id)
