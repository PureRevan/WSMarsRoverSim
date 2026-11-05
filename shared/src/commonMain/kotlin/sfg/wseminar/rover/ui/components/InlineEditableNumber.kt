package sfg.wseminar.rover.ui.components

import sfg.wseminar.rover.i18n.LocalizedText as Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp


@Composable
fun InlineEditableNumber(
    displayText: String,
    editableText: String,
    minEditingWidthDp: Int,
    keyboardType: KeyboardType,
    sanitize: (String) -> String,
    onCommit: (String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var value by remember {
        mutableStateOf(
            TextFieldValue(
                text = editableText,
                selection = TextRange(0, editableText.length)
            )
        )
    }
    var hasReceivedFocus by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    val textStyle = MaterialTheme.typography.bodyMedium.merge(
        TextStyle(
            color = MaterialTheme.colorScheme.primary
        )
    )

    LaunchedEffect(displayText, editableText, isEditing) {
        if (!isEditing) {
            value = TextFieldValue(
                text = editableText,
                selection = TextRange(0, editableText.length)
            )
            hasReceivedFocus = false
        }
    }

    fun startEditing() {
        value = TextFieldValue(
            text = editableText,
            selection = TextRange(0, editableText.length)
        )
        hasReceivedFocus = false
        isEditing = true
    }

    fun commit() {
        val committedText = value.text
        isEditing = false
        hasReceivedFocus = false
        onCommit(committedText)
    }

    fun cancel() {
        value = TextFieldValue(
            text = editableText,
            selection = TextRange(0, editableText.length)
        )
        isEditing = false
        hasReceivedFocus = false
    }

    if (!isEditing) {
        Text(
            text = displayText,
            modifier = Modifier.clickable {
                startEditing()
            },
            style = textStyle
        )
    } else {
        LaunchedEffect(isEditing) {
            focusRequester.requestFocus()
        }

        BasicTextField(
            value = value,
            onValueChange = { input ->
                val sanitized = sanitize(input.text)

                value = input.copy(
                    text = sanitized,
                    selection = input.selection.coerceInText(sanitized)
                )
            },
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { commit() }
            ),
            modifier = Modifier
                .width(minEditingWidthDp.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        hasReceivedFocus = true
                    } else if (isEditing && hasReceivedFocus) {
                        commit()
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                commit()
                                true
                            }

                            Key.Escape -> {
                                cancel()
                                true
                            }

                            else -> false
                        }
                    }
                }
        )
    }
}

private fun TextRange.coerceInText(
    text: String
): TextRange {
    val start = start.coerceIn(0, text.length)
    val end = end.coerceIn(0, text.length)
    return TextRange(start, end)
}
