package sfg.wseminar.rover.transfer

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import sfg.wseminar.rover.SimulationFrame
import sfg.wseminar.rover.algorithms.RoverAlgorithm
import sfg.wseminar.rover.computation.wire.*
import sfg.wseminar.rover.graph.Graph
import sfg.wseminar.rover.ui.settings.*
import sfg.wseminar.rover.ui.states.*

internal val archiveJson = Json { encodeDefaults = true; ignoreUnknownKeys = true; prettyPrint = true }
private val compactArchiveJson = Json(archiveJson) { prettyPrint = false }

@Serializable
internal data class Archive(val format: String = "WSMarsRoverSim", val version: Int = 1, val sections: Map<String, JsonElement>, val generatorVersion: Int = 2)

@Serializable
internal data class NodeData(val x: Double, val y: Double, val label: String)
@Serializable
internal data class EdgeData(val from: Int, val to: Int, val weight: Double, val bidirectional: Boolean, val label: String)
@Serializable
internal data class GraphData(val nodes: List<NodeData>, val edges: List<EdgeData>, val start: Int?, val goal: Int?, val seed: Int, val generatorVersion: Int) {
    fun graph(): Graph {
        require(nodes.isNotEmpty()) { "The graph must contain nodes." }
        val ns = nodes.mapIndexed { id, n -> Graph.Node(n.x, n.y, id, n.label) }
        val es = edges.mapIndexed { id, e ->
            require(e.weight.isFinite() && e.weight >= 0) { "Edge weights must be finite and nonnegative." }
            Graph.Edge(ns[e.from], ns[e.to], e.weight, id, e.bidirectional, e.label)
        }
        return Graph(ns, es, start, goal, seed, generatorVersion)
    }
    companion object {
        fun from(g: Graph) = GraphData(g.nodes.map { NodeData(it.x, it.y, it.label) },
            g.edges.map { EdgeData(it.from.id, it.to.id, it.weight, it.bidirectional, it.label) },
            g.startNodeId, g.goalNodeId, g.seed, g.generatorVersion)
    }
}
@Serializable
internal data class Setup(val algorithm: String, val space: SpaceMode, val exploration: ExplorationMode,
    val sensingRadius: Int = sfg.wseminar.rover.simulation.SensingRadius.DEFAULT)
@Serializable
internal data class ViewData(val viewport: ViewportState, val legendPosition: Float)
@Serializable
internal data class ReplayData(
    val codec: Int = 2, val frameCount: Int, val data: String, val currentStep: Int,
    val selectedNode: Int?, val selectedEdge: Int?, val completed: Boolean,
    val hasRun: Boolean, val elapsedNanos: Long?, val estimatedBytes: Long,
    val stopReason: String?, val speed: Float, val peakAlgorithmBytes: Long? = null
)

internal data class SectionGroup(val title: String, val children: List<Pair<String, String>>)
internal val sectionGroups = listOf(
    SectionGroup("Visual settings · left sidebar", listOf("rendering" to "Graph rendering", "appearance" to "Size and spacing", "annotations" to "Node and edge annotations", "overlays" to "Exploration overlays", "legend" to "Legend", "view" to "Viewport and legend position")),
    SectionGroup("General settings", listOf("language" to "Language", "theme" to "Theme", "performance" to "Performance limits", "worker" to "Web computation", "playback" to "Playback bounds", "nodeSlider" to "Node-count slider")),
    SectionGroup("Simulation and generation", listOf("setup" to "Algorithm and exploration mode", "generation" to "Generation dialog settings")),
    SectionGroup("Current graph", listOf("recipe" to "Original generation recipe", "topology" to "Serialized nodes and edges", "replay" to "All frames, current step and selection"))
)
internal val visualKeys = mapOf(
    "rendering" to setOf("graphRenderMode", "nodeColoringMode", "showGridLines", "showUnknownArea"),
    "appearance" to setOf("appearance"),
    "annotations" to setOf("showNodeIds", "showEdgeWeights", "showCosts", "showCostsInsideNodes", "showHeuristics", "showCoordinates"),
    "overlays" to setOf("showFrontier", "showVisited", "showFinalPath", "finalPathScale", "showSensorRadius"),
    "legend" to setOf("showLegend", "showNodeLegend", "showPathLegend", "showKnowledgeLegend")
)
internal fun availableSections(state: VisualizerState): Set<String> = buildSet {
    addAll(sectionGroups.flatMap { it.children }.map { it.first }.filter { it !in setOf("recipe", "topology", "replay") })
    if (state.generatedRecipe != null) add("recipe")
    if (state.generatedGraph != null) add("topology")
    if (state.frames.isNotEmpty()) add("replay")
}
internal fun normalizeSelection(keys: Set<String>, available: Set<String>): Set<String> {
    val selected = keys.intersect(available).toMutableSet()
    if ("replay" in selected) selected.addAll(setOf("topology", "setup").intersect(available))
    if ("topology" in selected || "recipe" in selected) selected.addAll(setOf("setup").intersect(available))
    return selected
}

internal fun exportArchive(state: VisualizerState, theme: ThemePreset, requested: Set<String>, pretty: Boolean): ByteArray {
    val keys = normalizeSelection(requested, availableSections(state))
    require(keys.isNotEmpty()) { "Select something to export." }
    val sections = buildMap<String, JsonElement> {
        fun putIf(key: String, value: () -> JsonElement) { if (key in keys) put(key, value()) }
        val visuals = archiveJson.encodeToJsonElement(state.options).jsonObject
        for ((key, fields) in visualKeys) putIf(key) { JsonObject(visuals.filterKeys { it in fields }) }
        putIf("view") { archiveJson.encodeToJsonElement(ViewData(state.viewport, state.legendEdgePosition)) }
        putIf("theme") { JsonPrimitive(theme.name) }
        val performance = archiveJson.encodeToJsonElement(state.performance).jsonObject
        putIf("language") { archiveJson.encodeToJsonElement(state.performance.language) }
        putIf("performance") { JsonObject(performance.filterKeys { it in setOf("limitsEnabled", "replay", "generation") }) }
        putIf("worker") { performance.getValue("browserWorker") }
        putIf("playback") { performance.getValue("playback") }
        putIf("nodeSlider") { performance.getValue("nodeSlider") }
        putIf("generation") { archiveJson.encodeToJsonElement(state.graphGeneration) }
        putIf("setup") { archiveJson.encodeToJsonElement(Setup(state.algorithm.name, state.spaceMode, state.explorationMode, state.sensingRadius)) }
        putIf("recipe") { archiveJson.encodeToJsonElement(checkNotNull(state.generatedRecipe)) }
        putIf("topology") { archiveJson.encodeToJsonElement(GraphData.from(checkNotNull(state.generatedGraph))) }
        putIf("replay") {
            val encoder = FrameEncoder()
            val out = PacketWriter()
            state.frames.forEach { encoder.write(out, it) }
            archiveJson.encodeToJsonElement(ReplayData(frameCount = state.frames.size, data = Base64.encode(out.bytes()),
                currentStep = state.currentStep, selectedNode = state.selectedNode?.id, selectedEdge = state.selectedEdge?.id,
                completed = state.hasCompletedRecording, hasRun = state.hasRunAlgorithm, elapsedNanos = state.runElapsedNanos, peakAlgorithmBytes = state.peakAlgorithmBytes,
                estimatedBytes = state.estimatedReplayBytes, stopReason = state.recordingStopReason, speed = state.playbackSpeed))
        }
    }
    return (if (pretty) archiveJson else compactArchiveJson).encodeToString(Archive(sections = sections, generatorVersion = state.generatedGraph?.generatorVersion ?: 2)).encodeToByteArray()
}
internal fun readArchive(bytes: ByteArray): Archive {
    val archive = if (isBinaryBundle(bytes)) readBinaryBundle(bytes) else archiveJson.decodeFromString<Archive>(bytes.decodeToString(throwOnInvalidSequence = true))
    // Retain compatibility with the published paper data and existing JSON/binary bundles.
    require(archive.format == "WSMarsRoverSim" || archive.format == "WSRoverSim") { "This is not a WSMarsRoverSim bundle." }
    require(archive.version == 1) { "Unsupported bundle version ${archive.version}." }
    require(archive.sections.keys.any { key -> sectionGroups.any { group -> group.children.any { it.first == key } } }) { "No supported sections found." }
    require("replay" !in archive.sections || ("topology" in archive.sections && "setup" in archive.sections)) { "Replay data requires its graph and algorithm settings." }
    return archive
}

internal data class PreparedImport(
    val options: VisualizerOptions?, val performance: PerformanceSettings?, val theme: ThemePreset?,
    val view: ViewData?, val generation: GraphGenerationState?, val setup: Setup?, val recipe: GraphGenerationState?,
    val graph: Graph?, val frames: List<SimulationFrame>?, val replay: ReplayData?
)
internal fun prepareImport(archive: Archive, requested: Set<String>, current: VisualizerState): PreparedImport {
    val keys = normalizeSelection(requested, archive.sections.keys)
    require(keys.isNotEmpty()) { "Select something to import." }
    val sections = archive.sections.filterKeys { it in keys }
    fun merged(base: JsonObject, changes: List<JsonObject>) = JsonObject(base.toMutableMap().apply { changes.forEach { putAll(it) } })
    val optionChanges = visualKeys.mapNotNull { (key, fields) -> sections[key]?.jsonObject?.let { JsonObject(it.filterKeys { it in fields }) } }
    val options = if (optionChanges.isEmpty()) null else archiveJson.decodeFromJsonElement<VisualizerOptions>(merged(archiveJson.encodeToJsonElement(current.options).jsonObject, optionChanges)).also {
        require(it.appearance.nodeScale.isFinite() && it.appearance.edgeScale.isFinite() && it.appearance.labelScale.isFinite() && it.appearance.labelRevealSpacing.isFinite())
        // Accept the former 600% maximum in older bundles, then clamp to the current range.
        require(it.finalPathScale.isFinite() && it.finalPathScale in 0f..6f)
    }.let { it.copy(appearance = it.appearance.constrained(), finalPathScale = it.finalPathScale.coerceIn(0f, 5f)) }
    val perfChanges = mutableListOf<JsonObject>()
    sections["language"]?.let { perfChanges += JsonObject(mapOf("language" to it)) }
    sections["performance"]?.let { perfChanges += JsonObject(it.jsonObject.filterKeys { k -> k in setOf("limitsEnabled", "replay", "generation") }) }
    for ((key, field) in listOf("worker" to "browserWorker", "playback" to "playback", "nodeSlider" to "nodeSlider")) sections[key]?.let { perfChanges += JsonObject(mapOf(field to it)) }
    val performance = if (perfChanges.isEmpty()) null else archiveJson.decodeFromJsonElement<PerformanceSettings>(merged(archiveJson.encodeToJsonElement(current.performance).jsonObject, perfChanges))
    fun generation(key: String) = sections[key]?.let { archiveJson.decodeFromJsonElement<GraphGenerationState>(it).let { g ->
        if ("terrain" !in it.jsonObject) g.copy(terrain = g.terrain.copy(enabled = false)) else g
    }.also { g ->
        g.terrain.validate()
        require(g.validationMessage() == null) { g.validationMessage().orEmpty() }
        // Validate all numeric generation parameters before committing any imported state.
        require(listOf(g.sizeFactor, g.density, g.connectionRadius, g.nodeClearanceMultiplier, g.nodeSpacing, g.weightJitter, g.extremeWeightChance, g.extremeWeightFactor, g.cheapWeightShare, g.startX, g.startY, g.goalX, g.goalY).all { n -> n.isFinite() })
        require(g.sizeFactor in 0.0..1.0 && g.density in 0.0..1.0 && g.nodeSpacing in 0.0..1.0 && g.connectionRadius > 0 && g.nodeClearanceMultiplier >= 0 && g.weightJitter >= 0 && g.extremeWeightChance in 0.0..1.0 && g.extremeWeightFactor >= 1 && g.cheapWeightShare in 0.0..1.0)
    } }
    val setup = sections["setup"]?.let { archiveJson.decodeFromJsonElement<Setup>(it).also { s -> require(RoverAlgorithm.algorithms.any { it.name == s.algorithm }) { "Unknown algorithm: ${s.algorithm}" }; require(s.sensingRadius in sfg.wseminar.rover.simulation.SensingRadius.range) { "Invalid sensing radius." } } }
    val graph = sections["topology"]?.let { archiveJson.decodeFromJsonElement<GraphData>(it).graph() }
    require("recipe" !in sections || graph != null || archive.generatorVersion in 2..3) { "This recipe uses a different graph generator version. Import a bundle with serialized graph data instead." }
    val replay = sections["replay"]?.let { archiveJson.decodeFromJsonElement<ReplayData>(it) }
    val frames = replay?.let { r ->
        require(r.codec == 2) { "Unsupported replay encoding." }
        require(r.frameCount > 0 && r.currentStep in 0 until r.frameCount)
        require(r.peakAlgorithmBytes == null || r.peakAlgorithmBytes >= 0)
        require(r.speed.isFinite() && r.speed > 0 && r.estimatedBytes >= 0 && (r.elapsedNanos == null || r.elapsedNanos >= 0))
        val g = checkNotNull(graph)
        require(r.selectedNode == null || r.selectedNode in g.nodes.indices)
        require(r.selectedEdge == null || r.selectedEdge in g.edges.indices)
        val reader = PacketReader(Base64.decode(r.data)); val decoder = FrameDecoder(g)
        List(r.frameCount) { index -> decoder.read(reader).also { require(it.stepIndex == index) { "Replay frame order is invalid." } } }.also { require(reader.remaining == 0) { "Unexpected trailing replay data." } }
    }
    val view = sections["view"]?.let { archiveJson.decodeFromJsonElement<ViewData>(it).also { v ->
        require(v.viewport.zoom.isFinite() && v.viewport.zoom in sfg.wseminar.rover.ui.CanvasConstants.MinZoom..sfg.wseminar.rover.ui.CanvasConstants.MaxZoom && v.viewport.panX.isFinite() && v.viewport.panY.isFinite() && v.legendPosition.isFinite())
    } }
    return PreparedImport(options, performance, sections["theme"]?.let { ThemePreset.valueOf(it.jsonPrimitive.content) }, view,
        generation("generation"), setup, generation("recipe"), graph, frames, replay)
}
