package sfg.wseminar.rover.ui.components

import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun InfoRow(
    label: String,
    value: String,
    verbatim: Boolean = false
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (verbatim) androidx.compose.material3.Text(value, style = MaterialTheme.typography.bodyMedium) else Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}