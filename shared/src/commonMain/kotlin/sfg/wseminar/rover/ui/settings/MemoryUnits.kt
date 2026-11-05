package sfg.wseminar.rover.ui.settings

/** Decimal MB with byte-exact round trips, including pre-existing binary-unit budgets. */
internal fun memoryMegabytes(bytes: Long): String {
    val fraction = (bytes % 1_000_000).toString().padStart(6, '0').trimEnd('0')
    return (bytes / 1_000_000).toString() + if (fraction.isEmpty()) "" else ".$fraction"
}

internal fun memoryBytes(text: String): Long? {
    val parts = text.trim().replace("_", "").split('.')
    if (parts.size !in 1..2 || parts[0].isEmpty() || parts.any { part -> part.any { it !in '0'..'9' } }) return null
    val fraction = parts.getOrElse(1) { "" }
    if (fraction.length > 6) return null
    return (parts[0] + fraction.padEnd(6, '0')).toLongOrNull()?.takeIf { it > 0 }
}
