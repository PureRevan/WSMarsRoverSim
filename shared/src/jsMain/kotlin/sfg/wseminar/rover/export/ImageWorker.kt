package sfg.wseminar.rover.export
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal actual suspend fun encodeRgbaImage(pixels: ByteArray, size: IntSize, options: ImageExportOptions): ByteArray = suspendCancellableCoroutine { continuation ->
    val start: dynamic = js("""(pixels, width, height, format, quality, callback) => {
 const registry = window.__roverImageWorkers || (window.__roverImageWorkers = {next:1, workers:{}});
 const id=registry.next++; const worker=new Worker(new URL('rover-worker.js', document.baseURI)); registry.workers[id]=worker;
 let active = true;
 const finish=(bytes,error) => {if (!active) return; active=false; worker.terminate();delete registry.workers[id];callback(bytes,error);};
 worker.onerror=e=>finish(new Int8Array(0),e.message || 'Image worker failed');
 worker.onmessageerror=()=>finish(new Int8Array(0),'Image worker response could not be decoded');
 worker.onmessage=e=>{
   if(e.data.imageError) finish(new Int8Array(0),e.data.imageError);
   else if(e.data.imageResult) finish(new Int8Array(e.data.imageResult),'');
 };
 worker.postMessage({imageExport:true,pixels:pixels.buffer,width,height,format,quality},[pixels.buffer]);return id;
}""")
    val callback: (ByteArray, String) -> Unit = { data, error ->
        if (continuation.isActive) {
            if (error.isEmpty()) continuation.resume(data)
            else continuation.resumeWithException(IllegalStateException(error))
        }
    }
    // Preserve the caller's bytes; the worker owns this transferred copy.
    val id: Int = start(pixels.copyOf(), size.width, size.height, options.format.name, options.quality, callback)
    continuation.invokeOnCancellation { stop(id) }
}
private fun stop(id: Int) { val call: dynamic = js("""(id) => {const r=window.__roverImageWorkers;if(r&&r.workers[id]){r.workers[id].terminate();delete r.workers[id];}}"""); call(id) }
