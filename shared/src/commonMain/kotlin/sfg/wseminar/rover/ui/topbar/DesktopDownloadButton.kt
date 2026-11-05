package sfg.wseminar.rover.ui.topbar

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import sfg.wseminar.rover.i18n.LocalizedIcon as Icon
import sfg.wseminar.rover.i18n.LocalizedText as Text

/** Only the Wasm entry point provides this; Desktop and JS have no download control. */
val LocalDesktopDownloads = staticCompositionLocalOf<DesktopDownloads?> { null }

enum class DesktopInstaller(val label: String, val target: String, private val installerExtension: String, private val portableExtension: String = "zip") {
    WINDOWS_X64("Windows · 64-bit", "windows-x64", "msi"),
    MACOS_ARM64("macOS · Apple Silicon", "macos-arm64", "dmg"),
    MACOS_X64("macOS · Intel", "macos-x64", "dmg"),
    LINUX_X64("Linux · 64-bit", "linux-x64", "deb", "tar.gz");

    val portableAsset: String get() = "$target-WSMarsRoverSim-portable.$portableExtension"
    val installerAsset: String get() = "$target-WSMarsRoverSim-installer.$installerExtension"
}

data class DesktopDownloads(val repository: String, val recommended: DesktopInstaller?) {
    val releasesUrl: String get() = "https://github.com/$repository/releases/latest"
    fun url(platform: DesktopInstaller, portable: Boolean = true): String =
        "$releasesUrl/download/${if (portable) platform.portableAsset else platform.installerAsset}"
}

@Composable
internal fun DesktopDownloadButton(downloads: DesktopDownloads) {
    var expanded by remember { mutableStateOf(false) }
    val links = LocalUriHandler.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            AssistChip(
                onClick = { downloads.recommended?.let { links.openUri(downloads.url(it)) } ?: run { expanded = true } },
                leadingIcon = { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                label = { Text("Download desktop") }
            )
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                Text("Portable · no installation", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DesktopInstaller.entries.forEach { installer ->
                    DropdownMenuItem(text = { Text(installer.label) }, onClick = {
                        expanded = false
                        links.openUri(downloads.url(installer))
                    })
                }
                HorizontalDivider()
                Text("Installers · optional", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DesktopInstaller.entries.forEach { installer ->
                    DropdownMenuItem(text = { Text(installer.label) }, onClick = {
                        expanded = false
                        links.openUri(downloads.url(installer, portable = false))
                    })
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("All downloads and release notes") }, onClick = {
                    expanded = false
                    links.openUri(downloads.releasesUrl)
                })
            }
        }
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.ExpandMore, "Choose a desktop version", Modifier.size(18.dp))
        }
    }
}
