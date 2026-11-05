package sfg.wseminar.rover.ui.settings

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

@kotlinx.serialization.Serializable
data class PlaybackSettings(val minimum: Float = 0.25f, val maximum: Float = 4f) {
    init { require(minimum.isFinite() && maximum.isFinite() && minimum > 0 && maximum > minimum) }
    val range get() = minimum..maximum
}

/** The first 40% of the exponential slider is linear, including every integer up to linearUntil. */
@kotlinx.serialization.Serializable
data class NodeSliderSettings(
    val exponential: Boolean = false,
    val maximum: Int = 10_000,
    val linearUntil: Int = 100,
    val growthFactor: Double = 1.0
) {
    init { require(maximum >= 2 && linearUntil >= 2 && growthFactor.isFinite() && growthFactor > 0) }
    @kotlinx.serialization.Transient
    private val split = 0.4f.toDouble()

    companion object {
        const val SimpleMaximum = 100
        const val AdvancedMaximum = SimpleMaximum * 10
    }
    fun maximumFor(advanced: Boolean) = if (exponential) maximum else if (advanced) AdvancedMaximum else SimpleMaximum

    fun nodeCount(position: Float, advanced: Boolean = false): Int {
        val maximum = maximumFor(advanced)
        val p = position.toDouble().coerceIn(0.0, 1.0)
        if (p == 1.0) return maximum
        if (!exponential || maximum <= linearUntil) return (2.0 + p * (maximum - 2)).roundToInt()
        val value = if (p <= split) 2.0 + (linearUntil - 2) * p / split
        else linearUntil * exp(ln(maximum.toDouble() / linearUntil) * ((p - split) / (1.0 - split)).pow(1.0 / growthFactor))
        return value.roundToInt().coerceIn(2, maximum)
    }
    fun position(nodeCount: Int, advanced: Boolean = false): Float {
        val maximum = maximumFor(advanced)
        if (maximum == 2) return 0f
        val count = nodeCount.coerceIn(2, maximum)
        if (!exponential || maximum <= linearUntil) return (count - 2).toFloat() / (maximum - 2)
        return if (count <= linearUntil) {
            if (linearUntil == 2) 0f else (split * (count - 2) / (linearUntil - 2)).toFloat()
        } else (split + (1.0 - split) * (ln(count.toDouble() / linearUntil) / ln(maximum.toDouble() / linearUntil)).pow(growthFactor)).toFloat()
    }
}
