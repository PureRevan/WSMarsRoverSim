package sfg.wseminar.rover.transfer

import sfg.wseminar.rover.i18n.LocalizedIcon as Icon
import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import sfg.wseminar.rover.export.*
import sfg.wseminar.rover.ui.infopanel.formatMemory
import sfg.wseminar.rover.ui.settings.ThemePreset
import sfg.wseminar.rover.ui.states.VisualizerState

enum class ExportKind { IMAGE, GRAPH, SETTINGS }

@Composable
private fun TransferDialog(title: String, subtitle: String, busy: Boolean, error: String?, onDismiss: () -> Unit,
    action: String, enabled: Boolean, onAction: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier.widthIn(max = 660.dp).fillMaxWidth().padding(16.dp).heightIn(max = 820.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
                    Button(onClick = onAction, enabled = enabled && !busy) { Text(if (busy) "Working…" else action) }
                }
            }
        }
    }
}

private fun countValues(value: JsonElement): Int = when (value) {
    is JsonObject -> value.values.sumOf(::countValues)
    is JsonArray -> value.size
    else -> 1
}
@Composable
private fun SectionPicker(available: Set<String>, selected: Set<String>, counts: Map<String, String>, enabled: Boolean,
    graphFirst: Boolean = false, estimates: Map<String, Long>? = null, onChange: (Set<String>) -> Unit) {
    val language = sfg.wseminar.rover.i18n.LocalLanguage.current
    val all = available.filter { k -> sectionGroups.any { g -> g.children.any { it.first == k } } }.toSet()
    fun update(keys: Set<String>, checked: Boolean) {
        var next = if (checked) selected + keys else selected - keys
        if (!checked && ("topology" in keys || "setup" in keys)) next = next - "replay"
        onChange(normalizeSelection(next, available))
    }
    Row(Modifier.fillMaxWidth().toggleable(selected.containsAll(all), enabled = enabled, role = Role.Checkbox,
        onValueChange = { onChange(if (it) all else emptySet()) }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(selected.containsAll(all), onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(horizontal = 12.dp))
        Text("All available information", style = MaterialTheme.typography.titleSmall)
    }
    val groups = if (graphFirst) sectionGroups.sortedBy { if (it.title == "Current graph") 0 else 1 } else sectionGroups
    groups.forEach { group -> key(group.title) {
        val children = group.children.filter { it.first in available }
        if (children.isNotEmpty()) {
            var expanded by remember { mutableStateOf(children.any { it.first in selected }) }
            var manuallyCollapsed by remember { mutableStateOf(false) }
            var autoExpanded by remember { mutableStateOf(false) }
            val keys = children.map { it.first }.toSet()
            val selectedCount = keys.count { it in selected }
            LaunchedEffect(selectedCount) {
                if (selectedCount == keys.size && !expanded && !manuallyCollapsed && !autoExpanded) {
                    expanded = true; autoExpanded = true
                }
            }
            val checkState = when (selectedCount) { 0 -> ToggleableState.Off; keys.size -> ToggleableState.On; else -> ToggleableState.Indeterminate }
            OutlinedCard(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button,
                    onClickLabel = sfg.wseminar.rover.i18n.translate(if (expanded) "Collapse ${group.title}" else "Expand ${group.title}", language),
                    onClick = { expanded = !expanded; if (!expanded) manuallyCollapsed = true }).padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TriStateCheckbox(checkState, onClick = { update(keys, selectedCount != keys.size) }, enabled = enabled)
                    Spacer(Modifier.width(12.dp))
                    Text(group.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$selectedCount/${keys.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        estimates?.let { Text("≈ ${formatMemory(keys.filter { it in selected }.sumOf { itMap -> it[itMap] ?: 0L })}", style = MaterialTheme.typography.labelSmall) }
                    }
                    IconButton(enabled = enabled, onClick = { expanded = !expanded; if (!expanded) manuallyCollapsed = true }) {
                        Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Collapse ${group.title}" else "Expand ${group.title}")
                    }
                }
                if (expanded) children.forEach { (itemKey, title) ->
                    val required = itemKey == "topology" && "replay" in selected || itemKey == "setup" && selected.any { it in setOf("replay", "topology", "recipe") }
                    Row(Modifier.fillMaxWidth().toggleable(itemKey in selected, enabled = enabled && !required, role = Role.Checkbox,
                        onValueChange = { update(setOf(itemKey), it) }).padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(itemKey in selected, onCheckedChange = null, enabled = enabled && !required)
                        Spacer(Modifier.width(12.dp))
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Column(Modifier.padding(start = 8.dp), horizontalAlignment = Alignment.End) {
                            Text(counts[itemKey].orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            estimates?.get(itemKey)?.let { Text("≈ ${formatMemory(it)}", style = MaterialTheme.typography.labelSmall) }
                            if (required) Text("Required", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BundleExportDialog(kind: ExportKind, state: VisualizerState, theme: ThemePreset, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val snapshot = remember { state }
    val available = remember { availableSections(snapshot) }
    var selected by remember { mutableStateOf(defaultBundleSelection(snapshot, kind == ExportKind.GRAPH)) }
    var name by remember { mutableStateOf(if (kind == ExportKind.GRAPH) "rover-graph" else "rover-settings") }
    var format by remember { mutableStateOf(BundleFormat.BINARY) }
    var estimates by remember { mutableStateOf<BundleEstimates?>(null) }
    var estimatedFormat by remember { mutableStateOf<BundleFormat?>(null) }
    var estimateError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val language = sfg.wseminar.rover.i18n.LocalLanguage.current
    val files = remember(language) { TransferFiles(language) }
    LaunchedEffect(format) {
        estimates = null; estimateError = false
        try { estimates = withContext(Dispatchers.Default) { estimateBundle(snapshot, theme, format) }; estimatedFormat = format }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { estimateError = true }
    }
    val currentEstimate = estimates.takeIf { estimatedFormat == format }
    val counts = remember {
        val small = readArchive(exportArchive(snapshot, theme, available - setOf("recipe", "topology", "replay"), false))
        small.sections.mapValues { "${countValues(it.value)} settings" } + mapOf("recipe" to "Seed ${snapshot.generatedGraph?.seed}", "topology" to "${snapshot.generatedGraph?.nodes?.size ?: 0} nodes", "replay" to "${snapshot.frames.size} frames")
    }
    TransferDialog("Export Bundle", "Choose exactly what to include in a portable Rover bundle.", busy, error, onDismiss,
        "Export bundle", selected.isNotEmpty() && name.isNotBlank() && !name.contains('/') && !name.contains('\\'), {
            busy = true; error = null
            scope.launch {
                try {
                    val data = withContext(Dispatchers.Default) { exportBundle(snapshot, theme, selected, format) }
                    files.save(data, "${name.removeSuffix(".json").removeSuffix(".rover")}.${format.extension}", format.mime)?.let { onSaved("$it · ${formatMemory(data.size.toLong())}") }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "Could not export the bundle." }
                finally { busy = false }
            }
        }) {
        Text("Format", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BundleFormat.entries.forEach { choice -> FilterChip(selected = format == choice, onClick = { format = choice }, enabled = !busy, label = { Text(choice.label) }) }
        }
        Text(if (format == BundleFormat.BINARY) "Packed graph and replay data · recommended for compact files." else "Portable JSON · readable and compact variants contain the same information.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(name, { name = it }, label = { Text("File name") }, suffix = { Text(".${format.extension}") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Text(currentEstimate?.let { "Estimated total: ${formatMemory(it.total(selected))}" } ?: if (estimateError) "Size estimate unavailable" else "Estimating size…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("Estimates include the selected sections. Graph JSON and replay sizes are sampled; actual sizes may differ.", style = MaterialTheme.typography.bodySmall)
        if ("topology" in available) Text("Serialized graph is initially selected up to 10 MB in binary form, or whenever no generation recipe is available. You can change this selection.", style = MaterialTheme.typography.bodySmall)
        SectionPicker(available, selected, counts, !busy, graphFirst = kind == ExportKind.GRAPH, estimates = currentEstimate?.sections) { selected = it }
        if ("topology" in available) Text("Replay includes its serialized graph and algorithm. A recipe without graph data regenerates the graph when imported. Replays open paused.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun BundleImportDialog(state: VisualizerState, onImport: suspend (PreparedImport) -> Unit, onDismiss: () -> Unit, onImported: () -> Unit) {
    var archive by remember { mutableStateOf<Archive?>(null) }
    var name by remember { mutableStateOf<String?>(null) }
    var bytes by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val language = sfg.wseminar.rover.i18n.LocalLanguage.current
    val files = remember(language) { TransferFiles(language) }
    fun receive(file: ImportFile) {
        if (busy) return
        busy = true; error = null; archive = null
        scope.launch {
            try {
                val parsed = withContext(Dispatchers.Default) { readArchive(file.bytes) }
                archive = parsed; name = file.name; bytes = file.bytes.size; selected = parsed.sections.keys
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not read this Rover bundle." }
            finally { busy = false }
        }
    }
    val drop = fileDropModifier(::receive) { error = it }
    TransferDialog("Import", "Open or drop a Rover binary or JSON bundle, then choose what to restore.", busy, error, onDismiss,
        "Import selected", archive != null && selected.isNotEmpty(), {
            busy = true; error = null
            scope.launch {
                try {
                    val prepared = withContext(Dispatchers.Default) { prepareImport(checkNotNull(archive), selected, state) }
                    onImport(prepared); onImported()
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = "Import failed: ${e.message ?: "Invalid bundle"}" }
                finally { busy = false }
            }
        }, modifier = drop) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name ?: "Drop a file here", style = MaterialTheme.typography.titleMedium)
                Text(if (archive == null) "Rover bundles (.rover or .json)" else "${formatMemory(bytes.toLong())} · bundle version ${archive?.version}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = !busy, onClick = {
                    scope.launch {
                        try { files.open()?.let(::receive) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "Could not open file." }
                    }
                }) { Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(if (archive == null) "Open file…" else "Choose another file…") }
            }
        }
        archive?.let { a ->
            val counts = a.sections.mapValues { (key, value) -> when (key) {
                "topology" -> "${value.jsonObject["nodes"]?.jsonArray?.size ?: 0} nodes"
                "replay" -> "${value.jsonObject["frameCount"]?.jsonPrimitive?.content.orEmpty()} frames"
                "recipe" -> "Generation recipe"
                else -> "${countValues(value)} settings"
            } }
            SectionPicker(a.sections.keys, selected, counts, !busy, graphFirst = "topology" in a.sections || "recipe" in a.sections) { selected = it }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ImageExportDialog(capture: FrameImageCapture, step: Int, visuals: sfg.wseminar.rover.ui.states.VisualizerOptions, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
    val render = remember { capture.snapshotAsync() }
    val size = remember { capture.size }
    val detailWidth = remember { capture.suggestedDetailWidth }
    val nodeCount = remember { capture.nodeCount }
    val edgeCount = remember { capture.edgeCount }
    var useViewerVisibility by remember { mutableStateOf(true) }
    val hasHeuristics = remember { capture.hasHeuristics }
    fun viewerAnnotations(value: ImageExportOptions) = value.copy(nodeIds = visuals.showNodeIds, costs = visuals.showCosts,
        edgeWeights = visuals.showEdgeWeights, legend = visuals.showLegend, costsInsideNodes = visuals.showCostsInsideNodes, heuristics = hasHeuristics && visuals.showHeuristics)
    var options by remember { mutableStateOf(viewerAnnotations(ImageExportOptions())) }
    var preset by remember { mutableStateOf<ResolutionPreset?>(ResolutionPreset.BALANCED) }
    var widthText by remember { mutableStateOf(presetWidth(size, detailWidth, ResolutionPreset.BALANCED).toString()) }
    var encoded by remember { mutableStateOf<ByteArray?>(null) }
    var encodedFor by remember { mutableStateOf<ImageExportOptions?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val language = sfg.wseminar.rover.i18n.LocalLanguage.current
    val files = remember(language) { TransferFiles(language) }
    val request = options.copy(width = if (options.format.vector) detailWidth else widthText.toIntOrNull())
    val dimensions = if (request.width == null) null else runCatching { exportDimensions(size, request) }.getOrNull()
    val cached = encoded.takeIf { encodedFor == request }
    val rough = dimensions?.let { estimatedImageBytes(size, request, nodeCount, edgeCount) }
    LaunchedEffect(request) { if (encodedFor != request) { encoded = null; encodedFor = null }; error = null }
    LaunchedEffect(options, preset) {
        preset?.let { widthText = presetWidth(size, detailWidth, it).toString() }
    }
    fun annotations(next: ImageExportOptions) {
        options = next
    }
    fun encode(save: Boolean) {
        busy = true; error = null
        scope.launch {
            try {
                val data = cached ?: render(request).also { encoded = it; encodedFor = request }
                if (save) files.save(data, "rover-frame-$step.${request.format.extension}", request.format.mime)?.let(onSaved)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not export the image." }
            finally { busy = false }
        }
    }
    TransferDialog("Export image", "Frame $step · choose framing, annotations and output quality.", busy, error, onDismiss,
        "Save image", dimensions != null, { encode(true) }) {
        Text("Format", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImageFormat.entries.forEach { format -> sfg.wseminar.rover.ui.components.RoverFilterChip(selected = options.format == format, enabled = !busy, onClick = {
                options = options.copy(format = format)
                preset?.let { widthText = presetWidth(size, detailWidth, it).toString() }
            }, label = { Text(format.name) }) }
        }
        Text(when (options.format) {
            ImageFormat.PNG -> "Lossless pixels. Higher resolution adds detail to nodes and labels; zooming beyond the saved pixel resolution will still pixelate. SVG stays sharp at any zoom."
            ImageFormat.JPEG -> "Lossy compression with broad compatibility; small labels may soften."
            ImageFormat.PDF -> "Portable document with a lossless raster image at your chosen resolution. Choose SVG for vector geometry."
            ImageFormat.SVG -> "Ideal for large graphs: vector geometry and compact text stay sharp at any zoom. Keeps its aspect ratio when resized; system fonts use browser-compatible names."
        }, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Whole graph")
                Text("Fit every node into the export. Turn off to use your current pan and zoom.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(options.wholeGraph, { options = options.copy(wholeGraph = it) }, enabled = !busy)
        }
        Text("Resolution", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResolutionPreset.entries.forEach { choice -> sfg.wseminar.rover.ui.components.RoverFilterChip(selected = preset == choice, enabled = !busy && !options.format.vector,
                onClick = { preset = choice; widthText = presetWidth(size, detailWidth, choice).toString() }, label = { Text(choice.label) }) }
        }
        if (preset == ResolutionPreset.BALANCED && !options.format.vector) Text("Targets readable labels at about 12 pixels for ordinary text. Estimated file size does not reduce the readability target.", style = MaterialTheme.typography.bodySmall)
        if (preset == ResolutionPreset.OVERVIEW && !options.format.vector) Text("Compact overview, targeting a 1,920-pixel longest side. Use Highly detailed for readable individual labels on large graphs.", style = MaterialTheme.typography.bodySmall)
        val scale = ((widthText.toIntOrNull() ?: size.width).toFloat() / size.width.coerceAtLeast(1)).coerceIn(1f, 30f)
        Text("${(scale * 10).toInt() / 10f}×", style = MaterialTheme.typography.labelLarge)
        Slider(value = scale, onValueChange = { preset = null; widthText = (size.width.toDouble() * snapResolution(it)).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toString() }, valueRange = 1f..30f, enabled = !busy && !options.format.vector)
        Text("Snaps near 2× · 4× · 8× · 10× · 20× · 30×", style = MaterialTheme.typography.bodySmall)
        if (options.format.vector) Text("SVG is a vector graphic and does not use pixel resolution. Its drawing area is sized automatically for the graph.", style = MaterialTheme.typography.bodySmall)
        else {
            OutlinedTextField(widthText, { widthText = it; preset = null }, enabled = !busy, singleLine = true, label = { Text("Custom width (pixels)") },
                supportingText = { Text("Height follows the canvas aspect ratio.") }, isError = dimensions == null, modifier = Modifier.fillMaxWidth())
            dimensions?.let { Text("${it.width} × ${it.height} pixels", style = MaterialTheme.typography.bodyMedium) }
        }
        Text("Annotations", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Use viewer visibility settings")
                Text("Use the annotations selected in the left sidebar. Turn off to customize this export.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(useViewerVisibility, {
                useViewerVisibility = it
                options = if (it) viewerAnnotations(options) else options.copy(nodeIds = false, costs = true, edgeWeights = true, legend = true, heuristics = hasHeuristics)
            }, enabled = !busy)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Include labels hidden in normal view")
                Text("Show all selected labels, even when normal zoom or crowding rules would hide them.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(options.fullDetail, { options = options.copy(fullDetail = it) }, enabled = !busy)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Costs inside nodes")
                Text("Place costs inside nodes when graph rendering and node size allow it.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(options.costsInsideNodes == true, { options = options.copy(costsInsideNodes = it) }, enabled = !busy && options.costs == true)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sfg.wseminar.rover.ui.components.RoverFilterChip(options.nodeIds == true, { annotations(options.copy(nodeIds = options.nodeIds != true)) }, label = { Text("Node IDs") }, enabled = !busy && !useViewerVisibility)
            sfg.wseminar.rover.ui.components.RoverFilterChip(options.costs == true, { annotations(options.copy(costs = options.costs != true)) }, label = { Text("Costs") }, enabled = !busy && !useViewerVisibility)
            sfg.wseminar.rover.ui.components.RoverFilterChip(options.edgeWeights == true, { annotations(options.copy(edgeWeights = options.edgeWeights != true)) }, label = { Text("Edge weights") }, enabled = !busy && !useViewerVisibility)
            sfg.wseminar.rover.ui.components.RoverFilterChip(options.legend == true, { annotations(options.copy(legend = options.legend != true)) }, label = { Text("Legend") }, enabled = !busy && !useViewerVisibility)
            if (hasHeuristics) sfg.wseminar.rover.ui.components.RoverFilterChip(options.heuristics == true,
                { annotations(options.copy(heuristics = options.heuristics != true)) }, label = { Text("Heuristics") }, enabled = !busy && !useViewerVisibility)
        }
        if (options.format == ImageFormat.JPEG) {
            Text("Quality · ${options.quality}% · lossy")
            Slider(options.quality.toFloat(), { options = options.copy(quality = it.toInt()) }, valueRange = 1f..100f, enabled = !busy)
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(cached?.let { "Exact file size: ${formatMemory(it.size.toLong())}" } ?: rough?.let { "Estimated file size: ≈ ${formatMemory(it)}" }.orEmpty())
                Text("The quick estimate is approximate. Calculate encodes the file in the background for an exact size.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = dimensions != null && !busy, onClick = { encode(false) }) { Text("Calculate exact file size") }
            }
        }
    }
}
