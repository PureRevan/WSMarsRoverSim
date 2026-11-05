package sfg.wseminar.rover.export

import kotlin.io.encoding.Base64

actual class ImageExporter actual constructor() {
    actual suspend fun exportPng(bytes: ByteArray, fileName: String): String? {
        downloadPng(Base64.encode(bytes), fileName)
        return "PNG download started: $fileName"
    }
}

internal expect fun downloadPng(base64: String, fileName: String)
