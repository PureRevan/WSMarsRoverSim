package sfg.wseminar.rover.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import sfg.wseminar.rover.algorithms.GoToWaypoint
import sfg.wseminar.rover.i18n.LocalizedIcon as Icon
import sfg.wseminar.rover.i18n.LocalizedText as Text
import sfg.wseminar.rover.transfer.ExportKind
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent
import sfg.wseminar.rover.ui.states.VisualizerState
import sfg.wseminar.rover.ui.states.waypointCommandIssue
import sfg.wseminar.rover.ui.topbar.LocalDesktopDownloads

internal enum class CompactPanel { CONFIGURATION, DETAILS }

/** Full-size touch controls; secondary actions stay available in the overflow menu. */
@Composable
internal fun CompactVisualizerBar(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit,
    onPanel: (CompactPanel) -> Unit,
    onGenerate: () -> Unit,
    onImport: () -> Unit,
    onExport: (ExportKind) -> Unit,
    onSettings: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val downloads = LocalDesktopDownloads.current
    val links = LocalUriHandler.current
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onPanel(CompactPanel.CONFIGURATION) }) {
                    Icon(Icons.Outlined.Tune, "Configuration")
                }
                IconButton(onClick = onGenerate, enabled = !state.isComputing) { Icon(Icons.Outlined.Hub, "Generate graph") }
                Box(Modifier.weight(1f)) { FilledTonalButton(
                    onClick = { onEvent(if (state.algorithm === GoToWaypoint) RoverVisualizerEvent.SendWaypoint else RoverVisualizerEvent.PlayPause) },
                    enabled = !state.isComputing && if (state.algorithm === GoToWaypoint) state.waypointCommandIssue() == null else state.hasFrames,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (state.algorithm === GoToWaypoint) "Send" else if (state.isPlaying) "Pause" else "Run", maxLines = 1)
                } }
                IconButton(onClick = { onPanel(CompactPanel.DETAILS) }) {
                    Icon(Icons.Outlined.Info, "Simulation details")
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More actions") }
                    DropdownMenu(menu, { menu = false }) {
                        fun action(block: () -> Unit) { menu = false; block() }
                        DropdownMenuItem(text = { Text("Fit view") }, enabled = state.hasFrames,
                            onClick = { action { onEvent(RoverVisualizerEvent.FitViewportToGraph) } })
                        DropdownMenuItem(text = { Text("Reset view") }, onClick = { action { onEvent(RoverVisualizerEvent.ResetViewport) } })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Import") }, enabled = !state.isComputing, onClick = { action(onImport) })
                        DropdownMenuItem(text = { Text("Export image") }, enabled = !state.isComputing && state.hasFrames,
                            onClick = { action { onExport(ExportKind.IMAGE) } })
                        DropdownMenuItem(text = { Text("Export graph") }, enabled = !state.isComputing && state.generatedGraph != null,
                            onClick = { action { onExport(ExportKind.GRAPH) } })
                        DropdownMenuItem(text = { Text("Export settings") }, enabled = !state.isComputing,
                            onClick = { action { onExport(ExportKind.SETTINGS) } })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { action(onSettings) })
                        downloads?.let { desktop ->
                            DropdownMenuItem(text = { Text("Download desktop") }, onClick = {
                                action { links.openUri(desktop.recommended?.let { desktop.url(it) } ?: desktop.releasesUrl) }
                            })
                        }
                    }
                }
            }
            if (state.isComputing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.workMessage, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    IconButton(onClick = { onEvent(RoverVisualizerEvent.CancelWork) }) { Icon(Icons.Outlined.Close, "Cancel computation") }
                }
            }
        }
    }
}

@Composable
internal fun CompactPanelDialog(panel: CompactPanel, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(8.dp).heightIn(max = 820.dp),
            shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (panel == CompactPanel.CONFIGURATION) "Configuration" else "Simulation details",
                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close panel") }
                }
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}
