package sfg.wseminar.rover.computation.wire

/** Versioned little-endian packets. Doubles retain infinity and exact IEEE bits. */
class PacketWriter {
    private var data = ByteArray(1024)
    var size = 0; private set
    private fun ensure(count: Int) {
        require(count >= 0 && count <= Int.MAX_VALUE - size)
        if (size + count > data.size) data = data.copyOf(maxOf(size + count, (data.size.toLong() * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
    }
    fun byte(value: Int) { ensure(1); data[size++] = value.toByte() }
    fun int(value: Int) { ensure(4); repeat(4) { data[size++] = (value ushr (it * 8)).toByte() } }
    fun long(value: Long) { int(value.toInt()); int((value ushr 32).toInt()) }
    fun raw(bytes: ByteArray) { ensure(bytes.size); bytes.copyInto(data, size); size += bytes.size }
    fun double(value: Double) = long(value.toBits())
    fun bool(value: Boolean) = byte(if (value) 1 else 0)
    fun string(value: String) { val bytes = value.encodeToByteArray(); int(bytes.size); raw(bytes) }
    fun bytes() = data.copyOf(size)
}
class PacketReader(private val data: ByteArray) {
    private var position = 0
    val remaining: Int get() = data.size - position
    fun byte(): Int { require(position < data.size) { "Truncated worker packet" }; return data[position++].toInt() and 255 }
    fun int(): Int {
        require(position <= data.size - 4) { "Truncated worker packet" }
        var value = 0
        repeat(4) { value = value or ((data[position++].toInt() and 255) shl (it * 8)) }
        return value
    }
    fun long(): Long = (int().toLong() and 0xffffffffL) or (int().toLong() shl 32)
    fun raw(size: Int): ByteArray { require(size >= 0 && size <= remaining) { "Invalid packet length" }; return data.copyOfRange(position, position + size).also { position += size } }
    fun double() = Double.fromBits(long())
    fun bool() = byte() != 0
    fun count(): Int = int().also { require(it >= 0 && it <= data.size) { "Invalid packet length" } }
    fun string(): String { val size = count(); require(size <= data.size - position); return data.decodeToString(position, position + size).also { position += size } }
}
// Run requests include the sensing radius; reject cached workers that would ignore it.
const val PROTOCOL_VERSION = 5
const val GENERATE = 1
const val RUN = 2
const val NEXT = 3
const val GRAPH = 4
const val BATCH = 5
const val ERROR = 6
fun packet(kind: Int, body: PacketWriter.() -> Unit = {}): ByteArray = PacketWriter().apply { int(PROTOCOL_VERSION); int(kind); body() }.bytes()
fun readPacket(bytes: ByteArray): Pair<Int, PacketReader> {
    val reader = PacketReader(bytes)
    require(reader.int() == PROTOCOL_VERSION) { "Worker protocol mismatch; reload the application." }
    return reader.int() to reader
}
