package sfg.wseminar.rover.i18n

import androidx.compose.runtime.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*

@kotlinx.serialization.Serializable
enum class AppLanguage { ENGLISH, GERMAN }
val LocalLanguage = staticCompositionLocalOf { AppLanguage.GERMAN }

/** Presentation-only translation; stable English messages/IDs remain portable in replays. */
fun translate(text: String, language: AppLanguage): String {
    if (language == AppLanguage.ENGLISH) return text
    return translateGerman(text, depth = 0)
}

private val placeholder = Regex("\\{(\\d+)\\}")
private fun translateGerman(text: String, depth: Int): String {
    GermanCatalog.exact[text]?.let { return it }
    for ((regex, replacement) in GermanCatalog.templates) {
        val match = regex.matchEntire(text) ?: continue
        return placeholder.replace(replacement) { token ->
            val value = match.groupValues[token.groupValues[1].toInt()]
            // Errors and section captions can contain another translated message.
            if (depth < 2 && value != text) translateGerman(value, depth + 1) else value
        }
    }
    return text
}

@Composable
fun LocalizedText(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified, fontStyle: FontStyle? = null, fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null, letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null, textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip, softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1, onTextLayout: ((TextLayoutResult) -> Unit)? = null, style: TextStyle = LocalTextStyle.current
) {
    androidx.compose.material3.Text(text = translate(text, LocalLanguage.current), modifier = modifier, color = color,
        fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, onTextLayout = onTextLayout, style = style)
}

@Composable
fun LocalizedIcon(imageVector: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String?,
    modifier: Modifier = Modifier, tint: Color = androidx.compose.material3.LocalContentColor.current) {
    androidx.compose.material3.Icon(imageVector, contentDescription?.let { translate(it, LocalLanguage.current) }, modifier, tint)
}
