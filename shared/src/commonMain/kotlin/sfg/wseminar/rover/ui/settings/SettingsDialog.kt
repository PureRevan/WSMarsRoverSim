package sfg.wseminar.rover.ui.settings

import sfg.wseminar.rover.i18n.LocalizedIcon as Icon
import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun SettingsDialog(
    theme: ThemePreset,
    performance: PerformanceSettings,
    onApply: (ThemePreset, PerformanceSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedLanguage by remember { mutableStateOf(performance.language) }
    var selectedTheme by remember { mutableStateOf(theme) }
    var draft by remember { mutableStateOf(PerformanceDraft(performance)) }
    val updatedPerformance = draft.build()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(20.dp).heightIn(max = 760.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val columns = if (maxWidth >= 420.dp) 2 else 1
                        val cardWidth = (maxWidth - 12.dp * (columns - 1)) / columns
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = columns) {
                            ThemePreset.entries.forEach { preset ->
                                val palette = preset.colors()
                                val isSelected = selectedTheme == preset
                                OutlinedCard(
                                    onClick = { selectedTheme = preset },
                                    modifier = Modifier.width(cardWidth).semantics { selected = isSelected },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                    colors = CardDefaults.outlinedCardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.045f).compositeOver(MaterialTheme.colorScheme.surface) else MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        val icon = when (preset) {
                                            ThemePreset.PAPER -> Icons.Outlined.AutoAwesome
                                            ThemePreset.MARS -> Icons.Outlined.Landscape
                                            ThemePreset.MIDNIGHT -> Icons.Outlined.DarkMode
                                            ThemePreset.DEEP_SPACE -> Icons.Outlined.RocketLaunch
                                        }
                                        Box(Modifier.size(36.dp).background(palette.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                            Icon(icon, contentDescription = null,
                                                tint = if (preset.dark && !theme.dark) palette.primaryContainer else palette.primary, modifier = Modifier.size(22.dp))
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(preset.title, style = MaterialTheme.typography.titleSmall)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                listOf(palette.primary, palette.secondary, palette.tertiary).forEach { color ->
                                                    Box(Modifier.size(12.dp).background(color, CircleShape)
                                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape))
                                                }
                                                Spacer(Modifier.width(2.dp))
                                                Text(if (preset.dark) "Dark" else "Light",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Box(Modifier.size(8.dp).then(if (isSelected)
                                            Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier))
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Performance", style = MaterialTheme.typography.titleMedium)
                    Text("Limits apply to the next generation or algorithm command. Existing replay frames and active runs are preserved. Settings last for this session.", style = MaterialTheme.typography.bodySmall)
                    PerformanceEditor(draft) { draft = it }
                    HorizontalDivider()
                    Text("Language", style = MaterialTheme.typography.titleMedium)
                    sfg.wseminar.rover.ui.components.ChipGroup {
                        sfg.wseminar.rover.i18n.AppLanguage.entries.forEach { language ->
                            sfg.wseminar.rover.ui.components.RoverFilterChip(selectedLanguage == language,
                                { selectedLanguage = language }, label = { Text(if (language == sfg.wseminar.rover.i18n.AppLanguage.ENGLISH) "English" else "Deutsch") })
                        }
                    }
                    HorizontalDivider()
                    Text("Credits / Copyright", style = MaterialTheme.typography.labelMedium)
                    Text("Author: Lucas Wittmann", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Licensed under the MIT License. Third-party components retain their own licenses; see the licenses folder.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("WSMarsRoverSim · Mars Rover Exploration Visualizer\nBuilt with Kotlin Multiplatform, Compose Multiplatform and Skia. Third-party components retain their respective copyrights.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(enabled = updatedPerformance != null, onClick = {
                        updatedPerformance?.let { onApply(selectedTheme, it.copy(language = selectedLanguage)) }
                    }) { Text("Apply settings") }
                }
            }
        }
    }
}
