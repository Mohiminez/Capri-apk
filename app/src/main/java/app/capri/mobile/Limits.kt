package app.capri.mobile

/**
 * Three settings from capri-desktop's BRAIN section, as pure rules: how long her bubbles stay up (Quick / Normal / Long),
 * how much of the chat she keeps in mind (Short / Normal / Long), and how long her replies run (Short / Normal / Long / No limit).
 */
object Limits {
    /** How long a bubble of [len] characters stays: the usual 7 to 22 seconds, scaled by level 0 = Quick, 1 = Normal, 2 = Long. */
    fun holdMs(len: Int, level: Int): Long {
        val base = (len.coerceAtLeast(0) * 90L).coerceIn(7000L, 22000L)
        val f = when (level) { 0 -> 0.6; 2 -> 1.7; else -> 1.0 }
        return (base * f).toLong()
    }

    /** Past messages sent with each ask. The local brain keeps its small window whatever the level. */
    fun memory(level: Int, local: Boolean): Int = if (local) 6 else when (level) { 0 -> 8; 2 -> 32; else -> 20 }

    /** A line for her instructions; Normal says nothing, so her usual short replies stay as they are. */
    fun replyHint(level: Int): String = when (level) {
        0 -> "Keep every reply to a single short sentence unless they ask for detail."
        2 -> "You may answer at more length when it helps: a short paragraph is fine."
        3 -> "There is no length limit on your replies: answer fully and in detail whenever they ask something that deserves it."
        else -> ""
    }

    /**
     * How long she waits between pop-ins, in ms: a range. Exact times (both > 0 minutes) win; then the desktop's presets
     * 1 Rarely (20 to 40 min), 2 Sometimes (8 to 20), 3 Often (3 to 8); preset 0 is the old "every N minutes" slider.
     */
    fun popRangeMs(preset: Int, lo: Int, hi: Int, slider: Int): Pair<Long, Long> {
        val min = 60_000L
        if (lo > 0 && hi > 0) { val a = minOf(lo, hi); val b = maxOf(lo, hi); return Pair(a * min, b * min) }
        return when (preset) {
            1 -> Pair(20 * min, 40 * min)
            2 -> Pair(8 * min, 20 * min)
            3 -> Pair(3 * min, 8 * min)
            else -> { val pop = slider.coerceAtLeast(5); Pair(pop * 40_000L, pop * 90_000L) }
        }
    }
}
