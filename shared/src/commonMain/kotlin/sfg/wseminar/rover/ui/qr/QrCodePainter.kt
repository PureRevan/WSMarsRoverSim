package sfg.wseminar.rover.ui.qr

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import io.github.alexzhirkevich.qrose.QrCodePainter
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrLogoPadding
import io.github.alexzhirkevich.qrose.options.QrLogoShape
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.brush
import io.github.alexzhirkevich.qrose.options.circle
import io.github.alexzhirkevich.qrose.options.dsl.QrOptionsBuilderScope
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.painterResource
import wsmarsroversim.shared.generated.resources.Res
import wsmarsroversim.shared.generated.resources.compose_multiplatform


@Composable
internal inline fun rememberQrPainter(
    qrValue: String,
    logoPainter: Painter = painterResource(Res.drawable.compose_multiplatform),
    crossinline options: QrOptionsBuilderScope.() -> Unit = {}
): QrCodePainter =
    rememberQrCodePainter(qrValue) {
        logo {
            painter = logoPainter
            padding = QrLogoPadding.Natural(.1f)
            shape = QrLogoShape.circle()
            size = 0.2f
        }

        shapes {
            ball = QrBallShape.circle()
            darkPixel = QrPixelShape.roundCorners()
            frame = QrFrameShape.roundCorners(.25f)
        }

        colors {
            dark = QrBrush.brush {
                Brush.linearGradient(
                    0f to Color.Red,
                    1f to Color.Blue,
                    end = Offset(it, it)
                )
            }
            frame = QrBrush.solid(Color.Black)
        }

        options()
    }

