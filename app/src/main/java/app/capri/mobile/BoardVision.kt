package app.capri.mobile

import kotlin.math.abs

// Finds a chess board in a screenshot and tells which squares hold white or black pieces, with no AI and no network.
// Works on plain pixel arrays (ARGB ints), so it can be tested without a phone.
class Geo(val x: Float, val y: Float, val cell: Float, val score: Float, val tol: Int = 36)

object BoardVision {
    private fun r(c: Int) = (c shr 16) and 255
    private fun g(c: Int) = (c shr 8) and 255
    private fun b(c: Int) = c and 255
    internal fun lum(c: Int) = (r(c) * 299 + g(c) * 587 + b(c) * 114) / 1000
    internal fun dist(a: Int, c: Int) = abs(r(a) - r(c)) + abs(g(a) - g(c)) + abs(b(a) - b(c))

    // The background colour of one square, read at the middle of its four edges where pieces rarely reach.
    internal fun cellColor(px: IntArray, w: Int, h: Int, x0: Float, y0: Float, s: Float): Int {
        val offs = arrayOf(floatArrayOf(0.5f, 0.09f), floatArrayOf(0.5f, 0.91f), floatArrayOf(0.09f, 0.5f), floatArrayOf(0.91f, 0.5f))
        val rs = IntArray(4); val gs = IntArray(4); val bs = IntArray(4)
        for (i in 0..3) {
            val x = (x0 + offs[i][0] * s).toInt().coerceIn(0, w - 1); val y = (y0 + offs[i][1] * s).toInt().coerceIn(0, h - 1)
            val c = px[y * w + x]; rs[i] = r(c); gs[i] = g(c); bs[i] = b(c)
        }
        rs.sort(); gs.sort(); bs.sort()
        return (255 shl 24) or (((rs[1] + rs[2]) / 2) shl 16) or (((gs[1] + gs[2]) / 2) shl 8) or ((bs[1] + bs[2]) / 2)
    }

    // How checkerboard-like the 8x8 grid at (x0,y0) with cell size s is: the share of squares whose colour matches their kind.
    private fun consistency(px: IntArray, w: Int, h: Int, x0: Float, y0: Float, s: Float, tol: Int = 36): Float {
        if (x0 < 0 || y0 < 0 || x0 + 8 * s > w + 1 || y0 + 8 * s > h + 1) return 0f
        val colors = IntArray(64)
        for (rr in 0..7) for (cc in 0..7) colors[rr * 8 + cc] = cellColor(px, w, h, x0 + cc * s, y0 + rr * s, s)
        fun mean(kind: Int): IntArray {
            val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
            for (i in 0..63) if ((i / 8 + i % 8) % 2 == kind) { rs.add(r(colors[i])); gs.add(g(colors[i])); bs.add(b(colors[i])) }
            rs.sort(); gs.sort(); bs.sort()
            return intArrayOf(rs[rs.size / 2], gs[gs.size / 2], bs[bs.size / 2])        // median, so highlighted squares don't skew it
        }
        val a = mean(0); val bb = mean(1)
        val ca = (255 shl 24) or (a[0] shl 16) or (a[1] shl 8) or a[2]
        val cb = (255 shl 24) or (bb[0] shl 16) or (bb[1] shl 8) or bb[2]
        val minContrast = if (tol > 36) 21 else 30
        if (dist(ca, cb) < minContrast) return 0f                      // two kinds of square must really differ
        var ok = 0
        for (i in 0..63) {
            val mine = if ((i / 8 + i % 8) % 2 == 0) ca else cb
            val other = if ((i / 8 + i % 8) % 2 == 0) cb else ca
            val d1 = dist(colors[i], mine)
            // a last-move highlight tints a square, so allow it to be closer to its own kind than to the other
            if (d1 < tol || d1 < dist(colors[i], other) - 20) ok++
        }
        return ok / 64f
    }

    // Looks for the board. `px` should be about 400-600 px wide (shrink the screenshot first).
    fun detect(px: IntArray, w: Int, h: Int): Geo? {
        val gray = IntArray(w * h) { lum(px[it]) }
        val colE = LongArray(w + 1); val rowE = LongArray(h + 1)
        for (y in 0 until h) for (x in 1 until w) {
            val d = abs(gray[y * w + x] - gray[y * w + x - 1]).toLong()
            if (d > 6) { colE[x] += d; rowE[y] += 0 }
        }
        for (y in 1 until h) for (x in 0 until w) {
            val d = abs(gray[y * w + x] - gray[(y - 1) * w + x]).toLong()
            if (d > 6) rowE[y] += d
        }
        val minS = maxOf(10, minOf(w, h) / 14)
        val maxS = minOf(w, h) / 8 + 1
        // first the strict reading; wood and textured boards that don't pass it get a looser second look
        for ((tol, need) in listOf(Pair(36, 0.85f), Pair(64, 0.82f))) {
            var best: Geo? = null
            for (s in minS..maxS) {
                val xs = top(colE, w, s, 5)
                val ys = top(rowE, h, s, 5)
                for (x0 in xs) for (y0 in ys) {
                    val c = consistency(px, w, h, x0.toFloat(), y0.toFloat(), s.toFloat(), tol)
                    if (c >= need && (best == null || c > best.score + 0.005f || (c >= best.score - 0.005f && s > best.cell))) best = Geo(x0.toFloat(), y0.toFloat(), s.toFloat(), c, tol)
                }
            }
            val g0 = best
            if (g0 != null) return align(px, w, h, refine(px, w, h, g0))
        }
        return null
    }

    private fun top(e: LongArray, len: Int, s: Int, n: Int): List<Int> {
        val scores = ArrayList<Pair<Int, Long>>()
        for (x0 in 0..(len - 8 * s)) {
            var sum = 0L
            for (k in 0..8) sum += e[minOf(len - 1, x0 + k * s)]
            scores.add(Pair(x0, sum))
        }
        return scores.sortedByDescending { it.second }.take(n).map { it.first }
    }

    // Nudge the grid by a pixel or two to the place where the squares fit best.
    private fun refine(px: IntArray, w: Int, h: Int, g0: Geo): Geo {
        var best = g0
        for (dx in -2..2) for (dy in -2..2) for (ds in intArrayOf(-1, 0, 1)) {
            val s = g0.cell + ds * 0.25f
            val c = consistency(px, w, h, g0.x + dx, g0.y + dy, s, g0.tol)
            if (c > best.score + 0.001f) best = Geo(g0.x + dx, g0.y + dy, s, c, g0.tol)
        }
        return best
    }

    private fun grayAt(px: IntArray, w: Int, h: Int, x: Float, y: Float): Float {
        val xi = Math.floor(x.toDouble()).toInt().coerceIn(0, w - 2); val yi = Math.floor(y.toDouble()).toInt().coerceIn(0, h - 2)
        val fx = (x - xi).coerceIn(0f, 1f); val fy = (y - yi).coerceIn(0f, 1f)
        val a = lum(px[yi * w + xi]); val b = lum(px[yi * w + xi + 1]); val c = lum(px[(yi + 1) * w + xi]); val d = lum(px[(yi + 1) * w + xi + 1])
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
    }

    // How sharply the squares change colour exactly at the grid lines (inside the board, away from where pieces stand).
    private fun lineEnergy(px: IntArray, w: Int, h: Int, along: Boolean, a0: Float, b0: Float, s: Float): Float {
        // along = true: vertical lines at a0 + k*s, sampled down the rows starting at b0; false: horizontal lines at a0 + k*s, sampled across columns from b0
        var e = 0f
        for (k in 1..7) {
            val line = a0 + k * s
            for (i in 0..7) for (f in floatArrayOf(0.14f, 0.86f)) {
                val t = b0 + (i + f) * s
                e += if (along) Math.abs(grayAt(px, w, h, line + 1.5f, t) - grayAt(px, w, h, line - 1.5f, t))
                     else Math.abs(grayAt(px, w, h, t, line + 1.5f) - grayAt(px, w, h, t, line - 1.5f))
            }
        }
        return e
    }

    // The coarse grid is within a few pixels; slide it onto the real square boundaries so that pieces can be compared square by square.
    private fun align(px: IntArray, w: Int, h: Int, g: Geo): Geo {
        var bx = g.x; var by = g.y; var bs = g.cell; var best = -1f
        var ds = -0.8f
        while (ds <= 0.801f) {
            var dx = -3f
            while (dx <= 3.001f) {
                val e = lineEnergy(px, w, h, true, g.x + dx, g.y, g.cell + ds)
                if (e > best) { best = e; bx = g.x + dx; bs = g.cell + ds }
                dx += 0.25f
            }
            ds += 0.1f
        }
        best = -1f
        var dy = -3f
        while (dy <= 3.001f) {
            val e = lineEnergy(px, w, h, false, g.y + dy, bx, bs)
            if (e > best) { best = e; by = g.y + dy }
            dy += 0.25f
        }
        val out = Geo(bx, by, bs, g.score, g.tol)
        // never trade a good board for a worse one
        return if (consistency(px, w, h, out.x, out.y, out.cell, out.tol) >= g.score - 0.02f) out else g
    }

    // Does the board still sit where we last found it?
    fun stillThere(px: IntArray, w: Int, h: Int, g0: Geo): Boolean {
        val need = if (g0.tol > 36) 0.76f else 0.8f
        return consistency(px, w, h, g0.x, g0.y, g0.cell, g0.tol) >= need
    }

    // For each square (row-major as seen on the screen): 0 empty, 1 white piece, 2 black piece.
    fun read(px: IntArray, w: Int, h: Int, g0: Geo): IntArray {
        val out = IntArray(64)
        val s = g0.cell
        for (rr in 0..7) for (cc in 0..7) {
            val x0 = g0.x + cc * s; val y0 = g0.y + rr * s
            val base = cellColor(px, w, h, x0, y0, s)
            var diff = 0; var total = 0; var far = 0; var bright = 0; var darkN = 0
            val a = (0.2f * s).toInt(); val z = (0.8f * s).toInt()
            val mid = s / 2f; val farR = 0.23f * s
            var yy = a
            while (yy < z) {
                var xx = a
                while (xx < z) {
                    val x = (x0 + xx).toInt().coerceIn(0, w - 1); val y = (y0 + yy).toInt().coerceIn(0, h - 1)
                    val c = px[y * w + x]
                    total++
                    if (dist(c, base) > 45) {
                        diff++
                        val dx = xx - mid; val dy = yy - mid
                        if (dx * dx + dy * dy > farR * farR) far++
                        val l = lum(c)
                        if (l > 175) bright++ else if (l < 95) darkN++
                    }
                    xx += 2
                }
                yy += 2
            }
            // A piece covers a good part of the square AND reaches out from the middle; a move dot is a small disc in the middle.
            if (total > 0 && diff.toFloat() / total > 0.22f && far.toFloat() / total > 0.06f) out[rr * 8 + cc] = if (bright >= darkN) 1 else 2
        }
        return out
    }
}

// Follows a real game on screen: starts from the opening position (or a known one) and works out each move from what changes.
class ChessTracker(var pos: Pos, var flipped: Boolean) {
    class Result(val moves: List<Move>, val lost: Boolean, val unsure: Boolean)

    companion object {
        // 0 empty, 1 white, 2 black, in board order (a8 = 0)
        fun occupancy(p: Pos): IntArray = IntArray(64) { val v = p.b[it]; if (v == 0) 0 else if (v > 0) 1 else 2 }

        fun mismatches(a: IntArray, b: IntArray): Int { var n = 0; for (i in 0..63) if (a[i] != b[i]) n++; return n }

        // The screen shows the board either way up. Which way fits the opening position?
        fun orientationOfStart(obs: IntArray): Boolean? {
            val st = occupancy(Chess.start())
            val flippedObs = IntArray(64) { obs[63 - it] }
            val m1 = mismatches(obs, st); val m2 = mismatches(flippedObs, st)
            if (m1 <= 3 && m1 < m2) return false
            if (m2 <= 3 && m2 < m1) return true
            return null
        }

        // For a position known from elsewhere (a FEN): which way up is the board on screen? Null when neither fits.
        fun orientationFor(p: Pos, obs: IntArray): Boolean? {
            val o = occupancy(p)
            val m1 = mismatches(obs, o); val m2 = mismatches(IntArray(64) { obs[63 - it] }, o)
            if (m1 <= 3 && m1 <= m2) return false
            if (m2 <= 3) return true
            return null
        }

        fun swapSide(p: Pos) = Pos(p.b, !p.white, p.castle, -1)
    }

    fun boardView(obs: IntArray): IntArray = if (flipped) IntArray(64) { obs[63 - it] } else obs

    // Feed what the screen shows. Returns the moves that were played since last time.
    fun update(obsScreen: IntArray): Result {
        val obs = boardView(obsScreen)
        for (base in listOf(pos, swapSide(pos))) {
            val cur = occupancy(base)
            if (mismatches(obs, cur) <= 1) { pos = base; return Result(emptyList(), false, false) }
            var bestN = 99; var cands = ArrayList<List<Move>>()
            val first = Chess.legal(base)
            for (m1 in first) {
                val p1 = Chess.make(base, m1)
                val n1 = mismatches(obs, occupancy(p1))
                if (n1 < bestN) { bestN = n1; cands = arrayListOf(listOf(m1)) } else if (n1 == bestN) cands.add(listOf(m1))
            }
            if (bestN <= 1) return pick(base, cands, bestN)
            // two moves at once: yours and the opponent's reply
            bestN = 99; cands = ArrayList()
            for (m1 in first) {
                val p1 = Chess.make(base, m1)
                for (m2 in Chess.legal(p1)) {
                    val n = mismatches(obs, occupancy(Chess.make(p1, m2)))
                    if (n < bestN) { bestN = n; cands = arrayListOf(listOf(m1, m2)) } else if (n == bestN) cands.add(listOf(m1, m2))
                }
            }
            if (bestN <= 1) return pick(base, cands, bestN)
        }
        return Result(emptyList(), true, false)
    }

    private fun pick(base: Pos, cands: List<List<Move>>, n: Int): Result {
        // promotions share one look: take the queen. Otherwise two different moves matching equally means we can't tell yet.
        val dests = cands.map { c -> c.joinToString(",") { it.from.toString() + ">" + it.to } }.distinct()
        if (dests.size > 1) return Result(emptyList(), false, true)
        val line = cands.firstOrNull { c -> c.all { it.promo == 0 || it.promo == 5 } } ?: cands[0]
        var p = base
        for (m in line) p = Chess.make(p, m)
        pos = p
        return Result(line, false, false)
    }

    // The centre of a square on the screen, in the same pixels as the Geo it came from.
    fun center(g: Geo, sq: Int): Pair<Float, Float> {
        var c = sq % 8; var r = sq / 8
        if (flipped) { c = 7 - c; r = 7 - r }
        return Pair(g.x + (c + 0.5f) * g.cell, g.y + (r + 0.5f) * g.cell)
    }
}
