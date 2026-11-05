package sfg.wseminar.rover

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import sfg.wseminar.rover.ui.topbar.DesktopDownloads
import sfg.wseminar.rover.ui.topbar.LocalDesktopDownloads

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        var installer by remember { mutableStateOf(detectDesktopInstaller(browserPlatform())) }
        LaunchedEffect(Unit) {
            // Client Hints can distinguish Apple Silicon from Intel; UA strings often cannot.
            detectPlatform { installer = detectDesktopInstaller(it) }
        }
        val downloads = PublishingConfig.repository.takeIf { it.isNotEmpty() }?.let { DesktopDownloads(it, installer) }
        CompositionLocalProvider(LocalDesktopDownloads provides downloads) { App(allowCompactLayout = true) }
    }
}
