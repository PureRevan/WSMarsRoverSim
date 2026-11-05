package sfg.wseminar.rover.export

/** Persists an encoded PNG; null means the user cancelled the save dialog. */
expect class ImageExporter() {
    suspend fun exportPng(bytes: ByteArray, fileName: String): String?
}
