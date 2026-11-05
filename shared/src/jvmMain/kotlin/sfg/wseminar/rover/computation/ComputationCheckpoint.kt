package sfg.wseminar.rover.computation

import kotlinx.coroutines.yield

/** Heavy work already runs on Dispatchers.Default. Yield without introducing a timer. */
internal actual suspend fun computationCheckpoint() { yield() }
