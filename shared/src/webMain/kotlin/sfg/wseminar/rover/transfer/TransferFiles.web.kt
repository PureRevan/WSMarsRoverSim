package sfg.wseminar.rover.transfer

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64

actual class TransferFiles actual constructor(private val language: sfg.wseminar.rover.i18n.AppLanguage) {
    actual suspend fun open(): ImportFile? = suspendCancellableCoroutine { continuation ->
        val id = browserPick { name, data, error ->
            if (continuation.isActive) {
                if (error.isNotEmpty()) continuation.resumeWithException(IllegalArgumentException(error))
                else if (name.isEmpty()) continuation.resume(null)
                else try { continuation.resume(ImportFile(name, Base64.decode(data))) }
                catch (e: Exception) { continuation.resumeWithException(e) }
            }
        }
        continuation.invokeOnCancellation { browserDispose(id) }
    }
    actual suspend fun save(bytes: ByteArray, name: String, mimeType: String): String? {
        browserSave(bytes, name, mimeType)
        return "Download started: $name"
    }
}
@Composable
actual fun fileDropModifier(onFile: (ImportFile) -> Unit, onError: (String) -> Unit): Modifier {
    val receive by rememberUpdatedState(onFile)
    val error by rememberUpdatedState(onError)
    DisposableEffect(Unit) {
        val id = browserDrop { name, data, message ->
            if (message.isNotEmpty()) error(message)
            else try { receive(ImportFile(name, Base64.decode(data))) } catch (e: Exception) { error(e.message ?: "Could not read file.") }
        }
        onDispose { browserDispose(id) }
    }
    return Modifier
}
internal expect fun browserPick(callback: (String, String, String) -> Unit): Int
internal expect fun browserDrop(callback: (String, String, String) -> Unit): Int
internal expect fun browserDispose(id: Int)
internal expect suspend fun browserSave(bytes: ByteArray, name: String, mime: String)
