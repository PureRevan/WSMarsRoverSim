package sfg.wseminar.rover.ui.controlpanel

import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import sfg.wseminar.rover.ui.components.InlineEditableNumber
import kotlin.math.roundToInt

@Composable
fun LegendEdgePositionRow(
    position: Float,
    enabled: Boolean,
    onPositionChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Legend edge position",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )

                Text(
                    text = legendPositionDescription(position),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }

            InlineEditableNumber(
                displayText = formatLegendPosition(position),
                editableText = formatLegendPosition(position),
                minEditingWidthDp = 48,
                keyboardType = KeyboardType.Decimal,
                sanitize = { input ->
                    sanitizeSignedDecimalInput(input)
                },
                onCommit = { input ->
                    input.replace(',', '.')
                        .toFloatOrNull()
                        ?.let { parsed ->
                            onPositionChange(parsed.coerceIn(-1f, 1f))
                        }
                }
            )
        }

        Slider(
            value = position.coerceIn(-1f, 1f),
            onValueChange = onPositionChange,
            valueRange = -1f..1f,
            enabled = enabled
        )
    }
}

private fun legendPositionDescription(
    position: Float
): String =
    when {
        position < -0.75f -> "Bottom left"
        position < -0.25f -> "Top left"
        position < 0.25f -> "Top right"
        position < 0.75f -> "Bottom right"
        else -> "Bottom left"
    }

private fun formatLegendPosition(
    position: Float
): String {
    val rounded = (position * 100f).roundToInt() / 100f

    return if (rounded % 1f == 0f) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
}

private fun sanitizeSignedDecimalInput(
    input: String
): String {
    var hasSeparator = false
    var hasSign = false

    return input.filterIndexed { index, char ->
        when {
            char.isDigit() -> true

            (char == '.' || char == ',') && !hasSeparator -> {
                hasSeparator = true
                true
            }

            char == '-' && index == 0 && !hasSign -> {
                hasSign = true
                true
            }

            else -> false
        }
    }
}