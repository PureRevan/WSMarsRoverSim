package sfg.wseminar.rover.export

import sfg.wseminar.rover.computation.wire.PacketWriter

/** A single-page PDF containing an opaque, losslessly compressed RGB image. */
internal fun rasterPdf(width: Int, height: Int, compressedRgb: ByteArray): ByteArray {
    val out = PacketWriter()
    fun text(s: String) = out.raw(s.encodeToByteArray())
    val offsets = mutableListOf(0)
    fun obj(id: Int, body: () -> Unit) { offsets.add(out.size); text("$id 0 obj\n"); body(); text("\nendobj\n") }
    val pageWidth = 842.0
    val pageHeight = pageWidth * height / width
    text("%PDF-1.4\n%Rover\n")
    obj(1) { text("<< /Type /Catalog /Pages 2 0 R >>") }
    obj(2) { text("<< /Type /Pages /Kids [3 0 R] /Count 1 >>") }
    obj(3) { text("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageWidth $pageHeight] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>") }
    obj(4) {
        text("<< /Type /XObject /Subtype /Image /Width $width /Height $height /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode /Length ${compressedRgb.size} >>\nstream\n")
        out.raw(compressedRgb); text("\nendstream")
    }
    val commands = "q $pageWidth 0 0 $pageHeight 0 0 cm /Im0 Do Q\n"
    obj(5) { text("<< /Length ${commands.length} >>\nstream\n${commands}endstream") }
    val xref = out.size
    text("xref\n0 6\n0000000000 65535 f \n")
    offsets.drop(1).forEach { text(it.toString().padStart(10, '0') + " 00000 n \n") }
    text("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
    return out.bytes()
}
