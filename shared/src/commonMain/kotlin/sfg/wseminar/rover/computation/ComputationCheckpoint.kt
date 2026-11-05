package sfg.wseminar.rover.computation

/** Cancellation/fairness checkpoint; only browser event-loop execution needs a timed yield. */
internal expect suspend fun computationCheckpoint()
