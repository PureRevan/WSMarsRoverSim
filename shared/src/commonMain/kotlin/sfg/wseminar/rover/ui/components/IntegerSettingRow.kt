package sfg.wseminar.rover.ui.components

import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp


@Composable
fun IntegerSettingRow(
    label: String,
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier.Companion,
    onValueChange: (Int) -> Unit
) {
    var draft by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!focused) draft = value.toString()
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = draft,
            onValueChange = { input ->
                draft = input.filter(Char::isDigit)
                // Keep partial input (including an empty field) editable. Validate
                // the draft on blur rather than clamping each typed digit.
                onValueChange(draft.toIntOrNull() ?: 0)
            },
            isError = draft.toIntOrNull()?.let { it !in range } ?: true,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            modifier = Modifier.width(100.dp).onFocusChanged { focus ->
                if (focused && !focus.isFocused) {
                    val committed = (draft.toIntOrNull() ?: value).coerceIn(range)
                    draft = committed.toString()
                    onValueChange(committed)
                }
                focused = focus.isFocused
            }
        )
    }
}
