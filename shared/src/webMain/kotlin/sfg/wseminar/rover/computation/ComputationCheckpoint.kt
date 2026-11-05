package sfg.wseminar.rover.computation

import kotlinx.coroutines.delay

/** JS/Wasm currently share the UI event loop; allow browser input and rendering between slices. */
internal actual suspend fun computationCheckpoint() { delay(1) }
