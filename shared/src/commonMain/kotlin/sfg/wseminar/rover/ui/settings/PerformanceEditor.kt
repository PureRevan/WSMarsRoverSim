package sfg.wseminar.rover.ui.settings

import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal enum class PerformanceField(val label: String) {
    WORKER_THRESHOLD("Worker work threshold"), MEMORY("Replay memory (MB)"), FRAMES("Recorded frames"), RUN_TIME("Algorithm time (ms)"),
    OPERATIONS("Work operations"), GENERATION_TIME("Generation time (ms)"),
    CANDIDATES("Candidate edges"), EDGES("Output edges"),
    SPEED_MIN("Minimum speed (×)"), SPEED_MAX("Maximum speed (×)"),
    NODE_MAX("Exponential maximum (nodes)"), LINEAR_UNTIL("Linear up to (nodes)"), GROWTH("Growth factor")
}

/** Strings stay editable (including temporarily invalid input); Apply is atomic. */
internal data class PerformanceDraft(
    val settings: PerformanceSettings,
    val values: Map<PerformanceField, String> = with(settings) {
        mapOf(
            PerformanceField.WORKER_THRESHOLD to browserWorker.workThreshold.toString(),
            PerformanceField.MEMORY to memoryMegabytes(replay.maxEstimatedBytes),
            PerformanceField.FRAMES to replay.maxFrames.toString(),
            PerformanceField.RUN_TIME to replay.maxDurationMillis.toString(),
            PerformanceField.OPERATIONS to generation.maxWorkOperations.toString(),
            PerformanceField.GENERATION_TIME to generation.maxDurationMillis.toString(),
            PerformanceField.CANDIDATES to generation.maxCandidateEdges.toString(),
            PerformanceField.EDGES to generation.maxEdges.toString(),
            PerformanceField.SPEED_MIN to playback.minimum.toString(),
            PerformanceField.SPEED_MAX to playback.maximum.toString(),
            PerformanceField.NODE_MAX to nodeSlider.maximum.toString(),
            PerformanceField.LINEAR_UNTIL to nodeSlider.linearUntil.toString(),
            PerformanceField.GROWTH to nodeSlider.growthFactor.toString()
        )
    }
) {
    fun enabled(field: PerformanceField): Boolean = when (field) {
        PerformanceField.WORKER_THRESHOLD -> sfg.wseminar.rover.computation.browserWorkerSettingsAvailable && settings.browserWorker.mode == sfg.wseminar.rover.computation.WorkerMode.AUTO
        PerformanceField.FRAMES -> settings.replay.frameLimitEnabled
        PerformanceField.RUN_TIME -> settings.replay.timeLimitEnabled
        PerformanceField.OPERATIONS -> settings.generation.workLimitEnabled
        PerformanceField.GENERATION_TIME -> settings.generation.timeLimitEnabled
        PerformanceField.NODE_MAX, PerformanceField.LINEAR_UNTIL, PerformanceField.GROWTH -> settings.nodeSlider.exponential
        else -> true
    }
    fun error(field: PerformanceField): String? {
        if (!enabled(field)) return null
        val text = values.getValue(field).trim().replace("_", "")
        if (field == PerformanceField.MEMORY) {
            return if (memoryBytes(text) == null) "Enter a positive MB value (up to six decimal places) within the 64-bit byte range." else null
        }
        if (field in listOf(PerformanceField.SPEED_MIN, PerformanceField.SPEED_MAX, PerformanceField.GROWTH)) {
            val number = text.toDoubleOrNull()
            if (number == null || !number.isFinite() || number <= 0) return "Enter a finite number greater than zero."
            if (field != PerformanceField.GROWTH && (!number.toFloat().isFinite() || number.toFloat() <= 0)) return "Outside the supported speed range."
            if (field == PerformanceField.SPEED_MAX) {
                val minimum = values.getValue(PerformanceField.SPEED_MIN).replace("_", "").toFloatOrNull()
                if (minimum != null && number.toFloat() <= minimum) return "Must be greater than minimum speed."
            }
        } else {
            val number = text.toLongOrNull() ?: return "Enter a whole number supported by a 64-bit integer."
            val minimum = if (field in listOf(PerformanceField.NODE_MAX, PerformanceField.LINEAR_UNTIL)) 2 else 1
            if (number < minimum) return "Enter $minimum or more."
            val maximum = when (field) {
                PerformanceField.MEMORY -> Long.MAX_VALUE / 1_000_000
                PerformanceField.WORKER_THRESHOLD, PerformanceField.OPERATIONS, PerformanceField.RUN_TIME, PerformanceField.GENERATION_TIME -> Long.MAX_VALUE
                else -> Int.MAX_VALUE.toLong()
            }
            if (number > maximum) return "Exceeds the backend's numeric capacity ($maximum)."
        }
        return null
    }
    fun build(): PerformanceSettings? {
        if (PerformanceField.entries.any { error(it) != null }) return null
        fun long(field: PerformanceField, fallback: Long = 1) =
            if (!enabled(field)) fallback else values.getValue(field).trim().replace("_", "").toLong()
        fun decimal(field: PerformanceField, fallback: Double = 1.0) =
            if (!enabled(field)) fallback else values.getValue(field).trim().replace("_", "").toDouble()
        return settings.copy(
            browserWorker = settings.browserWorker.copy(workThreshold = long(PerformanceField.WORKER_THRESHOLD, settings.browserWorker.workThreshold)),
            replay = settings.replay.copy(
                maxEstimatedBytes = checkNotNull(memoryBytes(values.getValue(PerformanceField.MEMORY))),
                maxFrames = long(PerformanceField.FRAMES, settings.replay.maxFrames.toLong()).toInt(),
                maxDurationMillis = long(PerformanceField.RUN_TIME, settings.replay.maxDurationMillis)),
            generation = settings.generation.copy(
                maxWorkOperations = long(PerformanceField.OPERATIONS, settings.generation.maxWorkOperations),
                maxDurationMillis = long(PerformanceField.GENERATION_TIME, settings.generation.maxDurationMillis),
                maxCandidateEdges = long(PerformanceField.CANDIDATES).toInt(), maxEdges = long(PerformanceField.EDGES).toInt()),
            playback = PlaybackSettings(decimal(PerformanceField.SPEED_MIN).toFloat(), decimal(PerformanceField.SPEED_MAX).toFloat()),
            nodeSlider = settings.nodeSlider.copy(maximum = long(PerformanceField.NODE_MAX, settings.nodeSlider.maximum.toLong()).toInt(),
                linearUntil = long(PerformanceField.LINEAR_UNTIL, settings.nodeSlider.linearUntil.toLong()).toInt(),
                growthFactor = decimal(PerformanceField.GROWTH, settings.nodeSlider.growthFactor))
        )
    }
}

@Composable
internal fun PerformanceEditor(draft: PerformanceDraft, onChange: (PerformanceDraft) -> Unit) {
    val settings = draft.settings
    fun settingsChanged(next: PerformanceSettings) { onChange(draft.copy(settings = next)) }
    ComputationLimitsSwitch(disabled = !settings.limitsEnabled, emphasized = true) {
        settingsChanged(settings.copy(limitsEnabled = !it))
    }
    BrowserWorkerEditor(settings.browserWorker) { settingsChanged(settings.copy(browserWorker = it)) }
    if (sfg.wseminar.rover.computation.browserWorkerSettingsAvailable) Fields(draft, onChange, PerformanceField.WORKER_THRESHOLD)
    Text("Algorithm recording", style = MaterialTheme.typography.titleSmall)
    LimitSwitch("Frame limit", settings.replay.frameLimitEnabled) {
        settingsChanged(settings.copy(replay = settings.replay.copy(frameLimitEnabled = it)))
    }
    LimitSwitch("Time limit", settings.replay.timeLimitEnabled) {
        settingsChanged(settings.copy(replay = settings.replay.copy(timeLimitEnabled = it)))
    }
    Fields(draft, onChange, PerformanceField.FRAMES, PerformanceField.RUN_TIME, PerformanceField.MEMORY)
    Text("Graph generation", style = MaterialTheme.typography.titleSmall)
    LimitSwitch("Work operation limit", settings.generation.workLimitEnabled) {
        settingsChanged(settings.copy(generation = settings.generation.copy(workLimitEnabled = it)))
    }
    LimitSwitch("Time limit", settings.generation.timeLimitEnabled) {
        settingsChanged(settings.copy(generation = settings.generation.copy(timeLimitEnabled = it)))
    }
    Fields(draft, onChange, PerformanceField.OPERATIONS, PerformanceField.GENERATION_TIME, PerformanceField.CANDIDATES, PerformanceField.EDGES)
    Text("Time is elapsed time, including scheduling overhead, per run or waypoint command. Limits are checked between processing steps; one operation can overrun. Budgets apply unless Disable all computation limits is on. Exceeded budgets trigger a notice.", style = MaterialTheme.typography.bodySmall)
    Text("Playback", style = MaterialTheme.typography.titleSmall)
    Fields(draft, onChange, PerformanceField.SPEED_MIN, PerformanceField.SPEED_MAX)
    Text("Node-count slider", style = MaterialTheme.typography.titleSmall)
    LimitSwitch("Exponential growth", settings.nodeSlider.exponential) {
        settingsChanged(settings.copy(nodeSlider = settings.nodeSlider.copy(exponential = it)))
    }
    Fields(draft, onChange, PerformanceField.NODE_MAX, PerformanceField.LINEAR_UNTIL, PerformanceField.GROWTH)
    Text("Linear mode: up to 100 nodes in simple mode and 10 × as many in advanced mode. The exponential maximum applies to both modes only when enabled. Continuous graphs: the first 40% of the exponential slider counts linearly, then grows to the maximum. A larger growth factor reaches high counts sooner. If the maximum is below the linear threshold, the entire slider is linear. Exact counts can also be typed. Grids use rows and columns.", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { onChange(PerformanceDraft(PerformanceSettings())) }) { Text("Restore performance defaults") }
}

@Composable
private fun LimitSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Fields(draft: PerformanceDraft, onChange: (PerformanceDraft) -> Unit, vararg fields: PerformanceField) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 420.dp) 2 else 1
        val width = (maxWidth - 12.dp * (columns - 1)) / columns
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.forEach { field ->
                val error = draft.error(field)
                OutlinedTextField(
                    value = draft.values.getValue(field),
                    onValueChange = { onChange(draft.copy(values = draft.values + (field to it))) },
                    label = { Text(field.label) }, enabled = draft.enabled(field), singleLine = true,
                    isError = error != null, modifier = Modifier.width(width),
                    supportingText = if (error != null) ({ Text(error) }) else null
                )
            }
        }
    }
}
