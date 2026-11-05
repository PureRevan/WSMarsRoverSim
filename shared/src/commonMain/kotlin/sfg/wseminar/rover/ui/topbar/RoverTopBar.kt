package sfg.wseminar.rover.ui.topbar

import sfg.wseminar.rover.i18n.LocalizedIcon as Icon
import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.painterResource
import wsmarsroversim.shared.generated.resources.Res
import wsmarsroversim.shared.generated.resources.compose_multiplatform
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.ExpandMore
import sfg.wseminar.rover.transfer.ExportKind
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sfg.wseminar.rover.ui.models.RoverVisualizerEvent
import sfg.wseminar.rover.algorithms.GoToWaypoint
import sfg.wseminar.rover.ui.states.waypointCommandIssue
import sfg.wseminar.rover.ui.states.VisualizerState


val LocalDesktopLogo = staticCompositionLocalOf { false }

@Composable
fun RoverTopBar(
    state: VisualizerState,
    onEvent: (RoverVisualizerEvent) -> Unit,
    onOpenGraphGeneration: () -> Unit,
    modifier: Modifier = Modifier,
    onImport: () -> Unit,
    onExport: (ExportKind) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            val downloads = LocalDesktopDownloads.current
            val actions: @Composable () -> Unit = {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { onEvent(RoverVisualizerEvent.FitViewportToGraph) },
                        enabled = state.currentFrame != null,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.FitScreen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text("Fit view") }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    AssistChip(
                        onClick = { onEvent(if (state.algorithm === GoToWaypoint) RoverVisualizerEvent.SendWaypoint else RoverVisualizerEvent.PlayPause) },
                        enabled = if (state.algorithm === GoToWaypoint) state.waypointCommandIssue() == null else state.currentFrame != null,
                        leadingIcon = {
                            Icon(
                                imageVector = if (state.isPlaying && state.algorithm !== GoToWaypoint) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text(if (state.algorithm === GoToWaypoint) "Send waypoint" else "Run algorithm") }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    AssistChip(onClick = onImport, enabled = !state.isComputing,
                        leadingIcon = { Icon(Icons.Outlined.FileUpload, null, Modifier.size(18.dp)) }, label = { Text("Import") })
                    Spacer(Modifier.width(12.dp))
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        AssistChip(onClick = { expanded = true }, enabled = !state.isComputing,
                            leadingIcon = { Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp)) },
                            trailingIcon = { Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp)) }, label = { Text("Export") })
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("Export image") }, enabled = state.hasFrames && state.canvasSize.width > 0,
                                onClick = { expanded = false; onExport(ExportKind.IMAGE) })
                            DropdownMenuItem(text = { Text("Export graph") }, enabled = state.generatedGraph != null,
                                onClick = { expanded = false; onExport(ExportKind.GRAPH) })
                            DropdownMenuItem(text = { Text("Export settings") }, onClick = { expanded = false; onExport(ExportKind.SETTINGS) })
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    AssistChip(
                        onClick = onOpenGraphGeneration,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Hub,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = { Text("Graph generation") }
                    )
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = onOpenSettings,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = { Text("Settings", maxLines = 1) }
                    )
                }
            }
            if (LocalDesktopLogo.current) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(Res.drawable.compose_multiplatform),
                        contentDescription = "Compose Multiplatform",
                        modifier = Modifier.size(36.dp)
                    )
                    Box(Modifier.weight(1f).padding(start = 16.dp), contentAlignment = Alignment.CenterEnd) {
                        actions()
                    }
                }
            } else if (downloads == null) {
                actions()
            } else if (maxWidth < 1280.dp) {
                Column {
                    DesktopDownloadButton(downloads)
                    actions()
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DesktopDownloadButton(downloads)
                    Box(Modifier.weight(1f).padding(start = 16.dp), contentAlignment = Alignment.CenterEnd) {
                        actions()
                    }
                }
            }
        }
    }
}
