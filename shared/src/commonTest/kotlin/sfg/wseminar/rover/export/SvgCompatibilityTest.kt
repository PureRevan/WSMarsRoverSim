package sfg.wseminar.rover.export

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SvgCompatibilityTest {
    @Test fun decimalCommaCoordinatesAreNormalizedWithoutChangingTextOrListSeparators() = runTest {
        val source = """<svg width="600" height="200"><g transform="matrix(2 0 0 2 1924,47 2443,68)"><path d="M1,25 2,5L3,75 4,5"/><text x="0, 12,45, 24,9, " y="21,5, " font-size="11,5">Cost 12,34</text></g></svg>"""
        val result = compatibleSvg(source.encodeToByteArray(), IntSize(600, 200)).decodeToString()
        assertTrue(result.contains("matrix(2 0 0 2 1924.47 2443.68)"))
        assertTrue(result.contains("M1.25 2.5L3.75 4.5"))
        assertTrue(result.contains("x=\"0, 12.45, 24.9\" y=\"21.5\""))
        assertTrue(result.contains("font-size=\"11.5\""))
        assertTrue(result.contains("Cost 12,34"))
        assertEquals(result, compatibleSvg(result.encodeToByteArray(), IntSize(600, 200)).decodeToString())
    }

    @Test fun browserSizingAndSystemFontAliasesAreNormalizedWithoutChangingText() = runTest {
        val source = """<?xml version="1.0"?><svg width="600" height="200"><text font-family=".SF NS" x="0, 20, " y="21, ">Node &amp; ∞</text><text font-family="Arial" x="1">A</text></svg>"""
        val result = compatibleSvg(source.encodeToByteArray(), IntSize(600, 200)).decodeToString()
        assertTrue(result.contains("viewBox=\"0 0 600 200\""))
        assertTrue(result.contains("preserveAspectRatio=\"xMidYMid meet\""))
        assertTrue(result.contains("font-family=\"system-ui\""))
        assertTrue(result.contains("font-family=\"Arial\""))
        assertTrue(result.contains("x=\"0, 20\" y=\"21\""))
        assertTrue(result.contains("Node &amp; ∞"))
        assertEquals(result, compatibleSvg(result.encodeToByteArray(), IntSize(600, 200)).decodeToString())
    }
}
