package app.capri.mobile

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * A look back at a finished (or half-played) game: for each move, how much worse it was than the engine's best (in centipawns),
 * graded, with an accuracy percentage per side (the win-chance method lichess uses). Pure: the caller does the engine work.
 */
object Review {
    enum class Grade { BEST, GOOD, INACCURACY, MISTAKE, BLUNDER, FORCED }

    class Row(val ply: Int, val white: Boolean, val san: String, val best: String?, val loss: Int, val grade: Grade, val accuracy: Double) {
        val number get() = ply / 2 + 1
    }

    class Side(val moves: Int, val accuracy: Double, val best: Int, val good: Int, val inaccuracies: Int, val mistakes: Int, val blunders: Int, val avgLoss: Int)

    /** Scores from a mate are huge; for judging moves they count as +-1000. */
    fun clamp(cp: Int) = cp.coerceIn(-1000, 1000)

    fun grade(loss: Int): Grade = when {
        loss <= 12 -> Grade.BEST
        loss <= 40 -> Grade.GOOD
        loss <= 90 -> Grade.INACCURACY
        loss <= 220 -> Grade.MISTAKE
        else -> Grade.BLUNDER
    }

    /** Chance of winning, 0 to 100, from a centipawn score for the side that has it. */
    fun winPct(cp: Int): Double = 50 + 50 * (2 / (1 + exp(-0.00368208 * clamp(cp))) - 1)

    /** Accuracy of one move from the win chance before it (had the best move been played) and after what was played. */
    fun moveAccuracy(winBest: Double, winPlayed: Double): Double {
        val drop = max(0.0, winBest - winPlayed)
        return min(100.0, max(0.0, 103.1668 * exp(-0.04354 * drop) - 3.1669 + 1.0))
    }

    /**
     * @param bestEval the mover's score (centipawns) in the position before the move, with the engine's best move played
     * @param afterEval the mover's score after the move that was played (so it is minus the next position's score for the side to move)
     */
    fun row(ply: Int, white: Boolean, san: String, best: String?, bestEval: Int, afterEval: Int, forced: Boolean): Row {
        if (forced) return Row(ply, white, san, null, 0, Grade.FORCED, 100.0)
        val b = clamp(bestEval); val a = min(clamp(afterEval), b)
        val loss = b - a
        val g = if (best != null && best == san) Grade.BEST else grade(loss)
        return Row(ply, white, san, if (g == Grade.BEST) null else best, loss, g, moveAccuracy(winPct(b), winPct(a)))
    }

    fun side(rows: List<Row>, white: Boolean): Side {
        val r = rows.filter { it.white == white && it.grade != Grade.FORCED }
        if (r.isEmpty()) return Side(0, 100.0, 0, 0, 0, 0, 0, 0)
        return Side(r.size, r.map { it.accuracy }.average(),
            r.count { it.grade == Grade.BEST }, r.count { it.grade == Grade.GOOD }, r.count { it.grade == Grade.INACCURACY },
            r.count { it.grade == Grade.MISTAKE }, r.count { it.grade == Grade.BLUNDER }, r.sumOf { it.loss } / r.size)
    }

    /** The moves worth looking at: the worst ones first, up to [n], then in game order. */
    fun keyMoments(rows: List<Row>, n: Int = 6): List<Row> =
        rows.filter { it.grade == Grade.BLUNDER || it.grade == Grade.MISTAKE }.sortedByDescending { it.loss }.take(n).sortedBy { it.ply }

    fun mark(g: Grade) = when (g) { Grade.BEST -> "!"; Grade.GOOD -> ""; Grade.INACCURACY -> "?!"; Grade.MISTAKE -> "?"; Grade.BLUNDER -> "??"; Grade.FORCED -> "" }

    fun label(g: Grade, ar: Boolean) = when (g) {
        Grade.BEST -> if (ar) "ممتازة" else "Best"; Grade.GOOD -> if (ar) "كويسة" else "Good"
        Grade.INACCURACY -> if (ar) "دقة ناقصة" else "Inaccuracy"; Grade.MISTAKE -> if (ar) "غلطة" else "Mistake"
        Grade.BLUNDER -> if (ar) "غلطة فادحة" else "Blunder"; Grade.FORCED -> if (ar) "إجبارية" else "Forced"
    }

    /** "12. Nf3" for White, "12... Nc6" for Black. */
    fun moveText(r: Row) = if (r.white) "${r.number}. ${r.san}" else "${r.number}... ${r.san}"
}
