package sfg.wseminar.rover.transfer

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import sfg.wseminar.rover.computation.wire.*
import sfg.wseminar.rover.ui.settings.ThemePreset
import sfg.wseminar.rover.ui.states.VisualizerState

internal enum class BundleFormat(val label: String, val extension: String, val mime: String) {
    BINARY("Binary", "rover", "application/octet-stream"),
    JSON("Readable JSON", "json", "application/json"),
    COMPACT_JSON("Compact JSON", "json", "application/json")
}
private val bundleMagic = byteArrayOf(87, 83, 82, 66) // WSRB
private val compactJson = Json(archiveJson) { prettyPrint = false }
internal fun isBinaryBundle(bytes: ByteArray) = bytes.size >= 4 && bundleMagic.indices.all { bytes[it] == bundleMagic[it] }
private fun replayMetadata(s: VisualizerState) = ReplayData(frameCount = s.frames.size, data = "", currentStep = s.currentStep,
    selectedNode = s.selectedNode?.id, selectedEdge = s.selectedEdge?.id, completed = s.hasCompletedRecording,
    hasRun = s.hasRunAlgorithm, elapsedNanos = s.runElapsedNanos, peakAlgorithmBytes = s.peakAlgorithmBytes, estimatedBytes = s.estimatedReplayBytes,
    stopReason = s.recordingStopReason, speed = s.playbackSpeed)

/** Binary container v1: compact settings JSON, packed graph records, raw delta replay (no Base64). */
internal fun exportBundle(state: VisualizerState, theme: ThemePreset, requested: Set<String>, format: BundleFormat): ByteArray {
    if (format != BundleFormat.BINARY) return exportArchive(state, theme, requested, format == BundleFormat.JSON)
    val keys = normalizeSelection(requested, availableSections(state))
    require(keys.isNotEmpty()) { "Select something to export." }
    val small = readArchive(exportArchive(state, theme, keys - setOf("topology", "replay"), false))
    val out = PacketWriter()
    out.raw(bundleMagic); out.int(1)
    out.string(compactJson.encodeToString(small))
    out.bool("topology" in keys)
    if ("topology" in keys) out.graph(checkNotNull(state.generatedGraph))
    out.bool("replay" in keys)
    if ("replay" in keys) {
        out.string(compactJson.encodeToString(replayMetadata(state)))
        val encoder = FrameEncoder()
        state.frames.forEach { encoder.write(out, it) }
    }
    return out.bytes()
}
internal fun readBinaryBundle(bytes: ByteArray): Archive {
    val reader = PacketReader(bytes)
    require(reader.raw(4).contentEquals(bundleMagic)) { "Not a Rover binary bundle." }
    require(reader.int() == 1) { "Unsupported binary bundle version." }
    val small = archiveJson.decodeFromString<Archive>(reader.string())
    require("topology" !in small.sections && "replay" !in small.sections) { "Duplicate binary sections." }
    val sections = small.sections.toMutableMap()
    if (reader.bool()) sections["topology"] = archiveJson.encodeToJsonElement(GraphData.from(reader.graph()))
    if (reader.bool()) {
        val metadata = archiveJson.decodeFromString<ReplayData>(reader.string())
        require(metadata.data.isEmpty()) { "Unexpected replay data in metadata." }
        sections["replay"] = archiveJson.encodeToJsonElement(metadata.copy(data = Base64.encode(reader.raw(reader.remaining))))
    }
    require(reader.remaining == 0) { "Unexpected trailing bundle data." }
    return small.copy(sections = sections)
}

/** Exact packed topology size; no graph serialization or replay traversal needed. */
internal fun binaryGraphSize(state: VisualizerState): Long = state.generatedGraph?.let { g ->
    24L + g.nodes.sumOf { 20L + it.label.encodeToByteArray().size } + g.edges.sumOf { 21L + it.label.encodeToByteArray().size }
} ?: 0L
internal const val DEFAULT_GRAPH_BYTES = 10_000_000L
internal fun defaultBundleSelection(state: VisualizerState, graphOnly: Boolean): Set<String> {
    val available = availableSections(state)
    val base = if (graphOnly) setOf("recipe", "setup") else available - setOf("topology", "replay")
    // Without a recipe, omitting the topology would lose the graph entirely.
    val graph = if (binaryGraphSize(state) <= DEFAULT_GRAPH_BYTES || state.generatedRecipe == null) setOf("topology") else emptySet()
    return normalizeSelection(base + graph, available)
}
internal data class BundleEstimates(val sections: Map<String, Long>, val overhead: Long) {
    fun total(keys: Set<String>) = overhead + keys.sumOf { sections[it] ?: 0L }
}

/** Samples graph JSON and replay deltas so opening the dialog never serializes a full recording. */
internal fun estimateBundle(state: VisualizerState, theme: ThemePreset, format: BundleFormat): BundleEstimates {
    val pretty = format == BundleFormat.JSON
    val json = if (pretty) archiveJson else compactJson
    val small = readArchive(exportArchive(state, theme, availableSections(state) - setOf("topology", "replay"), false))
    val sizes = small.sections.mapValues { (key, value) ->
        json.encodeToString(value).encodeToByteArray().size.toLong() + key.length + 8
    }.toMutableMap()
    state.generatedGraph?.let { g ->
        sizes["topology"] = if (format == BundleFormat.BINARY) binaryGraphSize(state) else {
            fun <T> sample(items: List<T>, encode: (T) -> JsonElement): Long {
                if (items.isEmpty()) return 0
                val n = minOf(64, items.size)
                val sum = (0 until n).sumOf { i -> json.encodeToString(encode(items[(i.toLong() * items.size / n).toInt()])).encodeToByteArray().size.toLong() + if (pretty) 40 else 1 }
                return sum * items.size / n
            }
            160L + sample(g.nodes) { archiveJson.encodeToJsonElement(NodeData(it.x, it.y, it.label)) } +
                sample(g.edges) { archiveJson.encodeToJsonElement(EdgeData(it.from.id, it.to.id, it.weight, it.bidirectional, it.label)) }
        }
    }
    if (state.frames.isNotEmpty()) {
        val out = PacketWriter(); val encoder = FrameEncoder()
        encoder.write(out, state.frames.first())
        val initial = out.size.toLong()
        val n = minOf(16, state.frames.size - 1)
        repeat(n) { encoder.write(out, state.frames[it + 1]) }
        val raw = initial + if (n == 0) 0L else (out.size - initial) * (state.frames.size - 1) / n
        val payload = if (format == BundleFormat.BINARY) raw else ((raw + 2) / 3) * 4
        sizes["replay"] = payload + json.encodeToString(replayMetadata(state)).encodeToByteArray().size + 16
    }
    return BundleEstimates(sizes, 128L)
}
