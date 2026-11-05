package sfg.wseminar.rover.export

import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JOptionPane
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual class ImageExporter actual constructor() {
    actual suspend fun exportPng(bytes: ByteArray, fileName: String): String? {
        val file = chooseFile(fileName) ?: return null
        withContext(Dispatchers.IO) { file.writeBytes(bytes) }
        return "PNG saved to ${file.absolutePath}"
    }

    private suspend fun chooseFile(fileName: String): File? = suspendCancellableCoroutine { continuation ->
        EventQueue.invokeLater {
            if (!continuation.isActive) return@invokeLater
            var dialog: FileDialog? = null
            try {
                val saveDialog = FileDialog(null as Frame?, "Export frame as PNG", FileDialog.SAVE)
                dialog = saveDialog
                continuation.invokeOnCancellation { EventQueue.invokeLater { saveDialog.dispose() } }
                dialog.file = fileName
                dialog.isVisible = true
                val selected = dialog.file?.let { name ->
                    File(dialog.directory, if (name.endsWith(".png", ignoreCase = true)) name else "$name.png")
                }
                val accepted = selected?.takeIf { file ->
                    !file.exists() || JOptionPane.showConfirmDialog(
                        null, "Replace ${file.name}?", "Replace image", JOptionPane.YES_NO_OPTION
                    ) == JOptionPane.YES_OPTION
                }
                if (continuation.isActive) continuation.resume(accepted)
            } catch (exception: Exception) {
                if (continuation.isActive) continuation.resumeWithException(exception)
            } finally { dialog?.dispose() }
        }
    }
}
