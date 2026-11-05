package sfg.wseminar.rover.export

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.*
import sfg.wseminar.rover.ui.canvas.drawFittedNodeCost
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.*

class NodeCostLabelTest {
    @Test fun measuredCostsStayInsideNodeAcrossTextScalesAndDensities() {
        for (density in listOf(1f, 2f)) {
            val measurer = TextMeasurer(createFontFamilyResolver(), Density(density), LayoutDirection.Ltr)
            for (label in listOf("0", "123.45", "123456.78")) {
                for (fontSize in listOf(9.5f, 28.5f)) {
                    for (radius in listOf(9f, 10.5f, 24f)) {
                        val layout = measurer.measure(label, TextStyle(color = Color.Black, fontSize = fontSize.sp))
                        val bytes = renderPng(IntSize(80, 80), Density(density), LayoutDirection.Ltr) {
                            drawRect(Color.White)
                            drawFittedNodeCost(layout, Offset(40f, 40f), radius)
                        }
                        val image = ImageIO.read(ByteArrayInputStream(bytes))
                        var ink = 0
                        for (y in 0 until 80) for (x in 0 until 80) {
                            if ((image.getRGB(x, y) and 255) < 240) {
                                ink++
                                val dx = x + 0.5f - 40f
                                val dy = y + 0.5f - 40f
                                assertTrue(dx * dx + dy * dy < radius * radius,
                                    "Cost $label at ${fontSize}sp/$density density overflows radius $radius")
                            }
                        }
                        assertTrue(ink > 0, "Cost must remain visible")
                    }
                }
            }
        }
    }
}
