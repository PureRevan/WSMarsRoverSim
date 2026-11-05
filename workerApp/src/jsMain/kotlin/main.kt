import sfg.wseminar.rover.computation.wire.*

fun main() {
    val engine = WorkerEngine()
    val install: dynamic = js("""(handle, ready, imageExport) => {
        const send = bytes => self.postMessage(bytes.buffer, [bytes.buffer]);
        self.onmessage = e => {
          if (e.data && e.data.imageExport) {
            imageExport(e.data).then(bytes => { const data = bytes.byteOffset === 0 && bytes.byteLength === bytes.buffer.byteLength ? bytes.buffer : bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength); self.postMessage({imageResult:data}, [data]); })
              .catch(error => self.postMessage({imageError: String(error.message || error)}));
          } else send(handle(new Int8Array(e.data)));
        };
        send(ready);
    }""")
    install({ input: ByteArray -> engine.handle(input) }, packet(7), imageExporter())
}
