package sfg.wseminar.rover.ui.settings

import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import sfg.wseminar.rover.computation.*
import sfg.wseminar.rover.ui.components.RoverFilterChip

@Composable
internal fun BrowserWorkerEditor(settings: WorkerSettings, onChange: (WorkerSettings) -> Unit) {
    if (!browserWorkerSettingsAvailable) return
    Text("Web computation", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WorkerMode.entries.forEach { mode ->
            RoverFilterChip(selected = settings.mode == mode, onClick = { onChange(settings.copy(mode = mode)) },
                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) })
        }
    }
    // Inline editing is part of PerformanceDraft so invalid values disable Apply atomically.
    Text("Auto uses a quick node/edge workload estimate. Small jobs stay on the UI thread; large jobs use a worker. Always is useful for testing. Never keeps cooperative UI execution.", style = MaterialTheme.typography.bodySmall)
}
