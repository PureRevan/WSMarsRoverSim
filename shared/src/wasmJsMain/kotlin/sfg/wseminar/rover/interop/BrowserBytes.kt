@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package sfg.wseminar.rover.interop
import kotlin.js.JsAny

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
internal suspend fun copyFromBrowser(bytes: JsAny): ByteArray {
    val result = ByteArray(length(bytes))
    var slice = kotlin.time.TimeSource.Monotonic.markNow()
    var i = 0
    while (i + 4 <= result.size) {
        val word = getWord(bytes, i)
        repeat(4) { result[i + it] = (word ushr (it * 8)).toByte() }
        i += 4
        if (i % 65536 == 0 && slice.elapsedNow().inWholeMilliseconds >= 8) { kotlinx.coroutines.delay(1); slice = kotlin.time.TimeSource.Monotonic.markNow() }
    }
    while (i < result.size) { result[i] = getByte(bytes, i).toByte(); i++ }
    return result
}
internal suspend fun copyToBrowser(bytes: ByteArray): JsAny {
    val result = allocate(bytes.size)
    var slice = kotlin.time.TimeSource.Monotonic.markNow()
    var i = 0
    while (i + 4 <= bytes.size) {
        var word = 0
        repeat(4) { word = word or ((bytes[i + it].toInt() and 255) shl (it * 8)) }
        setWord(result, i, word)
        i += 4
        if (i % 65536 == 0 && slice.elapsedNow().inWholeMilliseconds >= 8) { kotlinx.coroutines.delay(1); slice = kotlin.time.TimeSource.Monotonic.markNow() }
    }
    while (i < bytes.size) { setByte(result, i, bytes[i].toInt()); i++ }
    return result
}
