package app.capri.mobile

import android.graphics.Bitmap
import android.graphics.Color

// "Her colors": remaps her hair, accents, eyes, edges and body to colours you pick. Ported from the desktop's ArtColors.
object Recolor {
    val slots = listOf("hair", "accent", "eyes", "edges", "body")
    private val hues = doubleArrayOf(348.0, 145.0, 215.0, 262.0)
    private var sig = ""
    private var table: IntArray? = null

    val named = listOf(
        "Original" to "", "Red" to "#FF0046", "Pink" to "#FF5FB8", "Orange" to "#FF7A1A", "Yellow" to "#FFD60A",
        "Green" to "#00E86E", "Mint" to "#3DFFC0", "Cyan" to "#00D5FF", "Blue" to "#1870F0", "Violet" to "#8A5CFF",
        "White" to "#F2F2F2", "Black" to "#0A0A0F"
    )

    fun colours(p: Prefs): List<String> = slots.map { p.getS("col_$it", "") }

    // Changes the pixels of a mutable bitmap in place.
    fun apply(p: Prefs, bmp: Bitmap) {
        val cs = colours(p)
        if (cs.all { it.isEmpty() }) return
        val map = tableFor(cs)
        val w = bmp.width
        val row = IntArray(w)
        for (y in 0 until bmp.height) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val px = row[x]
                if ((px ushr 24) == 0) continue
                val key = ((px shr 18) and 63 shl 12) or ((px shr 10) and 63 shl 6) or ((px shr 2) and 63)
                row[x] = (px and 0xFF000000.toInt()) or map[key]
            }
            bmp.setPixels(row, 0, w, 0, y, w, 1)
        }
    }

    @Synchronized
    private fun tableFor(cs: List<String>): IntArray {
        val key = cs.joinToString(",")
        table?.let { if (sig == key) return it }
        val to = cs.map { if (it.matches(Regex("#[0-9A-Fa-f]{6}"))) Color.parseColor(it) and 0xFFFFFF else -1 }
        val made = IntArray(1 shl 18)
        for (k in made.indices) {
            made[k] = map(mid(k shr 12), mid((k shr 6) and 63), mid(k and 63), to)
        }
        table = made
        sig = key
        return made
    }

    private fun mid(six: Int) = if (six == 63) 255 else if (six == 0) 0 else six * 4 + 2

    private fun map(r: Int, g: Int, b: Int, to: List<Int>): Int {
        val high = maxOf(r, maxOf(g, b))
        val low = minOf(r, minOf(g, b))
        val v = high / 255.0
        val s = if (high == 0) 0.0 else (high - low) / high.toDouble()
        if (s > 0.55 || (s > 0.25 && v > 0.35)) {
            val slot = nearest(r, g, b, high, low)
            if (to[slot] < 0) return pack(r, g, b)
            val t = to[slot]
            val colourful = minOf(1.0, s / 0.9)
            val grey = v * 255
            return pack(
                (grey + (((t shr 16) and 255) * v - grey) * colourful).toInt(),
                (grey + (((t shr 8) and 255) * v - grey) * colourful).toInt(),
                (grey + ((t and 255) * v - grey) * colourful).toInt())
        }
        if (v < 0.38 && to[4] >= 0) {
            val t = to[4]
            val depth = minOf(1.0, 0.72 + v * 1.6)
            return pack((((t shr 16) and 255) * depth).toInt(), (((t shr 8) and 255) * depth).toInt(), ((t and 255) * depth).toInt())
        }
        return pack(r, g, b)
    }

    private fun nearest(r: Int, g: Int, b: Int, high: Int, low: Int): Int {
        val range = maxOf(1, high - low).toDouble()
        val hue = when (high) {
            r -> 60 * (((g - b) / range + 6) % 6)
            g -> 60 * ((b - r) / range + 2)
            else -> 60 * ((r - g) / range + 4)
        }
        var best = 0
        var closest = 999.0
        for (i in hues.indices) {
            var away = Math.abs(hue - hues[i])
            away = minOf(away, 360 - away)
            if (away < closest) { closest = away; best = i }
        }
        return best
    }

    private fun pack(r: Int, g: Int, b: Int) = (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}
