package app.capri.mobile

import kotlin.math.log10
import kotlin.math.roundToInt

// Her voice volume goes from 20 to 200 percent (CapriCore 1.2.2 added a boost up to 200%). Up to 100 it is the player's own volume;
// above 100 the player stays at full and Android's LoudnessEnhancer adds the rest as gain in millibels (200% = +6 dB).
object VoiceGain {
    const val MAX = 200
    const val MIN = 20

    fun clampPercent(p: Int): Int = p.coerceIn(MIN, MAX)

    /** The player volume 0..1 for a percent. */
    fun player(percent: Int): Float = clampPercent(percent).coerceAtMost(100) / 100f

    /** Extra gain in millibels for the LoudnessEnhancer, 0 when there is nothing to boost. */
    fun millibels(percent: Int): Int {
        val p = clampPercent(percent)
        return if (p <= 100) 0 else (2000.0 * log10(p / 100.0)).roundToInt()
    }
}
