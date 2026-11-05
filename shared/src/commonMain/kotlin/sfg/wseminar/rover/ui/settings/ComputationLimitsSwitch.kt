package sfg.wseminar.rover.ui.settings

import sfg.wseminar.rover.i18n.LocalizedIcon as Icon
import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComputationLimitsSwitch(
    disabled: Boolean,
    emphasized: Boolean = false,
    onChange: (Boolean) -> Unit
) {
    val tooltip = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (emphasized) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
    ) {
        Row(
            Modifier.padding(horizontal = if (emphasized) 12.dp else 0.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Disable all computation limits", Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                state = tooltip,
                enableUserInput = false,
                tooltip = { PlainTooltip {
                    Text("Bypasses time, recorded-frame, replay-memory, generation-work and edge limits for new jobs. Applies to both graph generation and algorithm runs. Individual limits are preserved; turn this off to restore them.")
                } }
            ) {
                IconButton(onClick = { scope.launch { tooltip.show() } }) {
                    Icon(Icons.Outlined.HelpOutline, "About disabling computation limits", Modifier.size(18.dp))
                }
            }
            Switch(checked = disabled, onCheckedChange = onChange)
        }
    }
}
