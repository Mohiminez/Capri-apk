package app.capri.mobile

// How the chess board looks. Pure data (no Android classes), so the colours can be tested: the squares must be told apart, and every piece must read on both.
class BoardTheme(
    val name: String, val nameAr: String,
    val light: Int, val dark: Int,          // the two square colours
    val white: Int, val black: Int,         // piece fill
    val outWhite: Int, val outBlack: Int,   // piece outline
    val mark: Int,                          // move dots, selection, hint arrow (opaque; the board adds the alpha)
    val last: Int                           // the last move's squares (opaque)
)

object BoardThemes {
    private fun c(v: Long) = v.toInt()
    val all: List<BoardTheme> = listOf(
        BoardTheme("Neon", "نيون", c(0xFF4A3F66), c(0xFF211A33), c(0xFFF4F0F2), c(0xFFE84246), c(0xFF0A0A10), c(0xFFF2F2F2), c(0xFF5EF0F6), c(0xFFFF2A6D)),
        BoardTheme("Classic", "كلاسيك", c(0xFFEEEED2), c(0xFF769656), c(0xFFFFFFFF), c(0xFF262626), c(0xFF202020), c(0xFFEDEDED), c(0xFF1F5FBF), c(0xFFF5F682)),
        BoardTheme("Wood", "خشب", c(0xFFF0D9B5), c(0xFFB58863), c(0xFFFFFAF0), c(0xFF2B1B10), c(0xFF2B1B10), c(0xFFF5E6CF), c(0xFF1F5FBF), c(0xFFF6E05E)),
        BoardTheme("Ice", "جليد", c(0xFFDEE5EA), c(0xFF7F97A8), c(0xFFFFFFFF), c(0xFF1F2933), c(0xFF1B2530), c(0xFFF0F4F7), c(0xFFD6336C), c(0xFF8FD3F4)),
        BoardTheme("Midnight", "منتصف الليل", c(0xFF39465E), c(0xFF1B2333), c(0xFFEDEFF5), c(0xFF6FA8FF), c(0xFF0A0F1A), c(0xFFEAF2FF), c(0xFFFFD166), c(0xFF3D6FD8))
    )

    fun at(i: Int): BoardTheme = all[if (i in all.indices) i else 0]
    fun next(i: Int): Int = (i + 1) % all.size

    /** Relative luminance 0..1 of an opaque colour. */
    fun lum(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch((c shr 16) and 255) + 0.7152 * ch((c shr 8) and 255) + 0.0722 * ch(c and 255)
    }

    /** WCAG contrast ratio between two colours (1 = identical, 21 = black on white). */
    fun contrast(a: Int, b: Int): Double {
        val x = lum(a); val y = lum(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    /** Which board a player picked, from the saved number (anything out of range falls back to the first). */
    fun saved(i: Int): Int = if (i in all.indices) i else 0
}

/** Pieces taken off the board, for the strips above and below it. */
object Captured {
    private val START = intArrayOf(0, 8, 2, 2, 2, 1)      // pawn, knight, bishop, rook, queen by code 1..5
    private val WORTH = intArrayOf(0, 1, 3, 3, 5, 9)

    /** Codes (1 pawn .. 5 queen) of the pieces of one colour that are gone, biggest first. */
    fun lost(board: IntArray, white: Boolean): List<Int> {
        val out = ArrayList<Int>()
        for (code in 5 downTo 1) {
            val have = board.count { it == (if (white) code else -code) }
            // a promoted pawn means a side can hold more than it started with; that only ever hides a loss, never invents one
            repeat(maxOf(0, START[code] - have)) { out.add(code) }
        }
        return out
    }

    /** White's material lead in pawns (negative = Black is ahead). Kings do not count. */
    fun lead(board: IntArray): Int {
        var s = 0
        for (v in board) { val a = Math.abs(v); if (v != 0 && a in 1..5) s += (if (v > 0) 1 else -1) * WORTH[a] }
        return s
    }
}
