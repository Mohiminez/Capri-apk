package app.capri.mobile

import kotlin.math.abs

// Learns what the pieces of one chess app look like (from the opening position, where every piece is known) and then reads
// any position from a screenshot by matching against them. All on the phone: no AI, no network. Different apps and themes
// draw their pieces differently, so each palette gets its own set, kept in a small file.
class PieceSet(val key: String) {
    val alpha = arrayOfNulls<FloatArray>(12)     // slot = (type - 1) + (6 if black): how often each patch pixel belongs to the piece
    fun complete() = alpha.all { it != null }

    fun toBytes(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (i in 0 until 12) {
            val a = alpha[i]
            if (a == null) { out.write(0); continue }
            out.write(1)
            for (v in a) out.write((v * 255f).toInt().coerceIn(0, 255))
        }
        return out.toByteArray()
    }

    companion object {
        fun fromBytes(key: String, b: ByteArray): PieceSet? {
            val n = PieceVision.N * PieceVision.N
            val s = PieceSet(key)
            var p = 0
            try {
                for (i in 0 until 12) {
                    if (b[p++].toInt() == 0) continue
                    s.alpha[i] = FloatArray(n) { (b[p + it].toInt() and 255) / 255f }; p += n
                }
            } catch (e: Exception) { return null }
            return s
        }
    }
}

interface PieceStore {
    fun load(key: String): PieceSet?
    fun save(set: PieceSet)
}

class Reading(val pos: Pos, val flipped: Boolean, val sideKnown: Boolean)

object PieceVision {
    const val N = 24
    internal var dbg: StringBuilder? = null

    private fun r(c: Int) = (c shr 16) and 255
    private fun g(c: Int) = (c shr 8) and 255
    private fun b(c: Int) = c and 255

    private fun median(v: ArrayList<Int>): Int { v.sort(); return v[v.size / 2] }

    // The colours of the light and the dark squares (medians, so highlights and pieces don't skew them).
    private fun kindColors(px: IntArray, w: Int, h: Int, geo: Geo): Pair<Int, Int> {
        val rs = Array(2) { ArrayList<Int>() }; val gs = Array(2) { ArrayList<Int>() }; val bs = Array(2) { ArrayList<Int>() }
        for (rr in 0..7) for (cc in 0..7) {
            val c = BoardVision.cellColor(px, w, h, geo.x + cc * geo.cell, geo.y + rr * geo.cell, geo.cell)
            val k = (rr + cc) % 2
            rs[k].add(r(c)); gs[k].add(g(c)); bs[k].add(b(c))
        }
        val c0 = (255 shl 24) or (median(rs[0]) shl 16) or (median(gs[0]) shl 8) or median(bs[0])
        val c1 = (255 shl 24) or (median(rs[1]) shl 16) or (median(gs[1]) shl 8) or median(bs[1])
        return if (BoardVision.lum(c0) >= BoardVision.lum(c1)) Pair(c0, c1) else Pair(c1, c0)
    }

    // Names one board theme: the quantised light and dark square colours.
    fun paletteKey(px: IntArray, w: Int, h: Int, geo: Geo): String {
        val (l, d) = kindColors(px, w, h, geo)
        fun q(c: Int) = "%x%x%x".format(r(c) / 24, g(c) / 24, b(c) / 24)
        return q(l) + "-" + q(d)
    }

    internal fun patch(px: IntArray, w: Int, h: Int, x0: Float, y0: Float, s: Float): FloatArray {
        val out = FloatArray(N * N * 3)
        for (j in 0 until N) for (i in 0 until N) {
            var rr = 0f; var gg = 0f; var bb = 0f
            for (bq in 0..2) for (aq in 0..2) {
                val u = (i + (aq + 0.5f) / 3f) / N; val v = (j + (bq + 0.5f) / 3f) / N
                // bilinear, so a piece drawn half a pixel differently still gives the same numbers
                val fx = x0 + u * s - 0.5f; val fy = y0 + v * s - 0.5f
                val xi = Math.floor(fx.toDouble()).toInt(); val yi = Math.floor(fy.toDouble()).toInt()
                val tx = fx - xi; val ty = fy - yi
                val xa = xi.coerceIn(0, w - 1); val xb = (xi + 1).coerceIn(0, w - 1); val ya = yi.coerceIn(0, h - 1); val yb = (yi + 1).coerceIn(0, h - 1)
                val c00 = px[ya * w + xa]; val c10 = px[ya * w + xb]; val c01 = px[yb * w + xa]; val c11 = px[yb * w + xb]
                rr += (r(c00) * (1 - tx) + r(c10) * tx) * (1 - ty) + (r(c01) * (1 - tx) + r(c11) * tx) * ty
                gg += (g(c00) * (1 - tx) + g(c10) * tx) * (1 - ty) + (g(c01) * (1 - tx) + g(c11) * tx) * ty
                bb += (b(c00) * (1 - tx) + b(c10) * tx) * (1 - ty) + (b(c01) * (1 - tx) + b(c11) * tx) * ty
            }
            val o = (j * N + i) * 3
            out[o] = rr / 9f; out[o + 1] = gg / 9f; out[o + 2] = bb / 9f
        }
        return out
    }

    // The colour of an empty bit of this square: the median around the rim of the patch, where a piece rarely reaches.
    internal fun baseOf(p: FloatArray): FloatArray {
        val v = Array(3) { ArrayList<Float>() }
        for (y in 1 until N - 1) for (x in 1 until N - 1) {
            if (x > 2 && y > 2 && x < N - 3 && y < N - 3) continue
            for (k in 0..2) v[k].add(p[(y * N + x) * 3 + k])
        }
        return FloatArray(3) { v[it].sort(); v[it][v[it].size / 2] }
    }

    // Which pixels of the square belong to the piece: those unlike the square, the biggest connected blob, with its holes filled.
    internal fun solid(p: FloatArray, base: FloatArray): BooleanArray {
        val n = N * N
        // the outer ring of the square is ignored: grid lines, coordinates and a neighbour's blur live there
        val m = BooleanArray(n) { val x = it % N; val y = it / N
            x > 0 && y > 0 && x < N - 1 && y < N - 1 && abs(p[it * 3] - base[0]) + abs(p[it * 3 + 1] - base[1]) + abs(p[it * 3 + 2] - base[2]) > 30f }
        val comp = IntArray(n) { -1 }
        var best = -1; var bestSize = 0; var id = 0
        val queue = IntArray(n)
        for (s in 0 until n) {
            if (!m[s] || comp[s] != -1) continue
            var head = 0; var tail = 0
            queue[tail++] = s; comp[s] = id
            while (head < tail) {
                val q = queue[head++]; val qx = q % N; val qy = q / N
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = qx + dx; val ny = qy + dy
                    if (nx !in 0 until N || ny !in 0 until N) continue
                    val ni = ny * N + nx
                    if (m[ni] && comp[ni] == -1) { comp[ni] = id; queue[tail++] = ni }
                }
            }
            if (tail > bestSize) { bestSize = tail; best = id }
            id++
        }
        if (best == -1) return BooleanArray(n)
        val keep = BooleanArray(n) { comp[it] == best }
        val outside = BooleanArray(n)
        var head = 0; var tail = 0
        for (i in 0 until n) { val x = i % N; val y = i / N; if ((x == 0 || y == 0 || x == N - 1 || y == N - 1) && !keep[i]) { outside[i] = true; queue[tail++] = i } }
        while (head < tail) {
            val q = queue[head++]; val qx = q % N; val qy = q / N
            val nb = intArrayOf(qx - 1, qy, qx + 1, qy, qx, qy - 1, qx, qy + 1)
            for (k in 0..3) {
                val nx = nb[k * 2]; val ny = nb[k * 2 + 1]
                if (nx !in 0 until N || ny !in 0 until N) continue
                val ni = ny * N + nx
                if (!keep[ni] && !outside[ni]) { outside[ni] = true; queue[tail++] = ni }
            }
        }
        return BooleanArray(n) { keep[it] || !outside[it] }
    }

    // How different the piece's outline on this square is from a template's: the share of pixels where they disagree, in percent.
    // Only the shape counts (not the colours), so the same piece on a light or a dark square, or a tinted one, looks the same. Allows a pixel of drift.
    private fun cost(m: BooleanArray, al: FloatArray): Float {
        var best = Float.MAX_VALUE
        for (dy in -1..1) for (dx in -1..1) {
            var sum = 0f
            for (j in 0 until N) for (i in 0 until N) {
                val ti = i + dx; val tj = j + dy
                val a = if (ti in 0 until N && tj in 0 until N) al[tj * N + ti] else 0f
                sum += if (m[j * N + i]) 1f - a else a
            }
            if (sum < best) best = sum
        }
        return best * 100f / (N * N)
    }

    private class Match(val type: Int, val best: Float, val second: Float)

    private fun classify(m: BooleanArray, black: Boolean, set: PieceSet): Match? {
        var bt = 0; var bc = Float.MAX_VALUE; var sc = Float.MAX_VALUE
        for (t in 1..6) {
            val slot = (t - 1) + (if (black) 6 else 0)
            val c = cost(m, set.alpha[slot] ?: return null)
            if (c < bc) { sc = bc; bc = c; bt = t } else if (c < sc) sc = c
        }
        return Match(bt, bc, sc)
    }

    private fun accept(m: Match) = m.best <= 20f && m.second >= m.best + 1.5f && m.second >= m.best * 1.25f

    // Learn this theme's pieces from a screenshot of the opening position (board geometry and orientation known).
    // Only returns a set that reads that very screenshot back perfectly.
    fun learn(px: IntArray, w: Int, h: Int, geo: Geo, flipped: Boolean): PieceSet? {
        val n = N * N
        val aSum = Array(12) { FloatArray(n) }; val cnt = IntArray(12)
        val start = Chess.start().b
        for (rr in 0..7) for (cc in 0..7) {
            val idx = if (flipped) 63 - (rr * 8 + cc) else rr * 8 + cc
            val pc = start[idx]
            if (pc == 0) continue
            val x0 = geo.x + cc * geo.cell; val y0 = geo.y + rr * geo.cell
            val pt = patch(px, w, h, x0, y0, geo.cell)
            val m = solid(pt, baseOf(pt))
            val slot = (abs(pc) - 1) + (if (pc < 0) 6 else 0)
            cnt[slot]++
            for (i in 0 until n) if (m[i]) aSum[slot][i] += 1f
        }
        val set = PieceSet(paletteKey(px, w, h, geo))
        for (slot in 0 until 12) {
            if (cnt[slot] == 0) return null
            set.alpha[slot] = FloatArray(n) { aSum[slot][it] / cnt[slot] }
        }
        // the proof: read the same picture back
        val obs = BoardVision.read(px, w, h, geo)
        dbg?.append("obs=" + obs.joinToString("") + "\n")
        val back = readPosition(px, w, h, geo, obs, set) ?: return null
        return if (back.flipped == flipped && back.pos.b.contentEquals(start)) set else null
    }

    // Read the whole position from a screenshot. `obs` = which squares hold white/black pieces (BoardVision.read).
    // Null when any piece can't be told with confidence: she'd rather ask than guess.
    fun readPosition(px: IntArray, w: Int, h: Int, geo: Geo, obs: IntArray, set: PieceSet): Reading? {
        val scr = IntArray(64)
        for (sq in 0..63) {
            val o = obs[sq]
            if (o == 0) continue
            val rr = sq / 8; val cc = sq % 8
            val x0 = geo.x + cc * geo.cell; val y0 = geo.y + rr * geo.cell
            val pt = patch(px, w, h, x0, y0, geo.cell)
            val m = classify(solid(pt, baseOf(pt)), o == 2, set) ?: return null
            dbg?.append("sq $sq o=$o type=${m.type} best=${m.best} second=${m.second}\n")
            if (!accept(m)) return null
            scr[sq] = if (o == 2) -m.type else m.type
        }
        if (scr.count { it == 6 } != 1 || scr.count { it == -6 } != 1) return null
        var wRow = 0.0; var wN = 0; var bRow = 0.0; var bN = 0
        for (sq in 0..63) { if (scr[sq] > 0) { wRow += sq / 8; wN++ } else if (scr[sq] < 0) { bRow += sq / 8; bN++ } }
        // the side with its pieces lower on the screen is the one at the bottom
        val unflippedFirst = (wRow / maxOf(1, wN)) >= (bRow / maxOf(1, bN))
        // last move: squares tinted differently from the usual colour of their own parity
        val par = parityColors(px, w, h, geo)
        var moverWhite = 0; var moverBlack = 0
        for (sq in 0..63) {
            if (scr[sq] == 0) continue
            val rr = sq / 8; val cc = sq % 8
            val c = BoardVision.cellColor(px, w, h, geo.x + cc * geo.cell, geo.y + rr * geo.cell, geo.cell)
            if (BoardVision.dist(c, par[(rr + cc) % 2]) > 30) { if (scr[sq] > 0) moverWhite++ else moverBlack++ }
        }
        for (flipped in if (unflippedFirst) listOf(false, true) else listOf(true, false)) {
            val b = if (flipped) IntArray(64) { scr[63 - it] } else scr.copyOf()
            if (!sane(b)) continue
            val bottomIsWhite = !flipped
            var sideKnown = true
            val white = when {
                moverWhite > 0 && moverBlack == 0 -> false         // white just moved, so black is to move
                moverBlack > 0 && moverWhite == 0 -> true
                else -> { sideKnown = false; bottomIsWhite }          // no clue: assume it's the player at the bottom
            }
            for (tryWhite in listOf(white, !white)) {
                val pos = Pos(b, tryWhite, 0, -1, 0)
                if (!Chess.inCheck(pos, !tryWhite)) return Reading(pos, flipped, sideKnown && tryWhite == white)
            }
        }
        return null
    }

    // The usual colour of the squares of each parity of the screen grid (medians).
    private fun parityColors(px: IntArray, w: Int, h: Int, geo: Geo): IntArray {
        val rs = Array(2) { ArrayList<Int>() }; val gs = Array(2) { ArrayList<Int>() }; val bs = Array(2) { ArrayList<Int>() }
        for (rr in 0..7) for (cc in 0..7) {
            val c = BoardVision.cellColor(px, w, h, geo.x + cc * geo.cell, geo.y + rr * geo.cell, geo.cell)
            val k = (rr + cc) % 2
            rs[k].add(r(c)); gs[k].add(g(c)); bs[k].add(b(c))
        }
        return IntArray(2) { (255 shl 24) or (median(rs[it]) shl 16) or (median(gs[it]) shl 8) or median(bs[it]) }
    }

    private fun sane(b: IntArray): Boolean {
        for (i in 0..7) if (abs(b[i]) == 1 || abs(b[56 + i]) == 1) return false
        if (b.count { it == 1 } > 8 || b.count { it == -1 } > 8) return false
        val wk = b.indexOf(6); val bk = b.indexOf(-6)
        if (wk < 0 || bk < 0) return false
        if (abs(wk / 8 - bk / 8) <= 1 && abs(wk % 8 - bk % 8) <= 1) return false
        return true
    }
}
