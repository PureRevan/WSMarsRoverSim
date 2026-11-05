package sfg.wseminar.rover.transfer

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.*
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import javax.swing.JOptionPane
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun readImport(file: File): ImportFile = withContext(Dispatchers.IO) {
    ImportFile(file.name, file.readBytes())
}
actual class TransferFiles actual constructor(private val language: sfg.wseminar.rover.i18n.AppLanguage) {
    actual suspend fun open(): ImportFile? = choose(null)?.let { readImport(it) }
    actual suspend fun save(bytes: ByteArray, name: String, mimeType: String): String? {
        val file = choose(name) ?: return null
        withContext(Dispatchers.IO) { file.writeBytes(bytes) }
        return "Saved ${file.name}"
    }
    private suspend fun choose(name: String?): File? = suspendCancellableCoroutine { continuation ->
        EventQueue.invokeLater {
            if (!continuation.isActive) return@invokeLater
            val dialog = FileDialog(null as Frame?, sfg.wseminar.rover.i18n.translate(if (name == null) "Import Rover bundle" else "Export Rover file", language), if (name == null) FileDialog.LOAD else FileDialog.SAVE)
            continuation.invokeOnCancellation { EventQueue.invokeLater { dialog.dispose() } }
            try {
                if (name != null) dialog.file = name
                else dialog.setFilenameFilter { _, file -> (file.endsWith(".json", true) || file.endsWith(".rover", true)) }
                dialog.isVisible = true
                val selected = dialog.file?.let { picked ->
                    val extension = name?.substringAfterLast('.')
                    File(dialog.directory, if (extension != null && !picked.endsWith(".$extension", true)) "$picked.$extension" else picked)
                }
                val accepted = selected?.takeIf {
                    name == null || !it.exists() || JOptionPane.showConfirmDialog(null, sfg.wseminar.rover.i18n.translate("Replace ${it.name}?", language), sfg.wseminar.rover.i18n.translate("Replace file", language), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION
                }
                if (continuation.isActive) continuation.resume(accepted)
            } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            finally { dialog.dispose() }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun fileDropModifier(onFile: (ImportFile) -> Unit, onError: (String) -> Unit): Modifier {
    val receive by rememberUpdatedState(onFile)
    val error by rememberUpdatedState(onError)
    val scope = rememberCoroutineScope()
    val target = remember { object : DragAndDropTarget {
        override fun onDrop(event: DragAndDropEvent): Boolean {
            val paths = (event.dragData() as? DragData.FilesList)?.readFiles() ?: return false
            if (paths.size != 1) { error("Drop one Rover bundle at a time."); return false }
            scope.launch {
                try { receive(readImport(File(URI(paths.single())))) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error(e.message ?: "Could not read the dropped file.") }
            }
            return true
        }
    } }
    return Modifier.dragAndDropTarget(shouldStartDragAndDrop = { it.dragData() is DragData.FilesList }, target = target)
}
