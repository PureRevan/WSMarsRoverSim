package sfg.wseminar.rover.transfer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier


data class ImportFile(val name: String, val bytes: ByteArray)

expect class TransferFiles(language: sfg.wseminar.rover.i18n.AppLanguage = sfg.wseminar.rover.i18n.AppLanguage.GERMAN) {
    suspend fun open(): ImportFile?
    suspend fun save(bytes: ByteArray, name: String, mimeType: String): String?
}

/** Installed only while the import dialog is open. */
@Composable
expect fun fileDropModifier(onFile: (ImportFile) -> Unit, onError: (String) -> Unit): Modifier
