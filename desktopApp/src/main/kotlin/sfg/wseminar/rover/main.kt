package sfg.wseminar.rover

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import sfg.wseminar.rover.ui.topbar.LocalDesktopLogo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application


private val StartingWindowWidth = 1500.dp
private val StartingWindowHeight = 900.dp


fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "WSMarsRoverSim",
        state = WindowState(
            width = StartingWindowWidth,
            height = StartingWindowHeight
        )
    ) {
        Box(
            modifier = Modifier.sizeIn(
                minWidth = StartingWindowWidth,
                minHeight = StartingWindowHeight
            )
        ) {
            CompositionLocalProvider(LocalDesktopLogo provides true) {
                App()
            }
        }
    }
}
