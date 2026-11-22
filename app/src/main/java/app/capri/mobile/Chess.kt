package app.capri.mobile

import kotlin.math.abs

// A small, complete chess engine: every rule (castling, en passant, promotion, check, mate, stalemate) and a search she plays with.
// Squares are 0..63, a8 = 0, h1 = 63. Pieces: 1 pawn, 2 knight, 3 bishop, 4 rook, 5 queen, 6 king; white positive, black negative.
class Move(val from: Int, val to: Int, val promo: Int = 0) {
    override fun toString() = Chess.name(from) + Chess.name(to) + (if (promo != 0) "nbrq"[promo - 2].toString() else "")
}

class Pos(val b: IntArray, val white: Boolean, val castle: Int, val ep: Int, val half: Int = 0) {
    fun kingSq(w: Boolean): Int { val k = if (w) 6 else -6; for (i in 0..63) if (b[i] == k) return i; return -1 }
}

object Chess {
    fun name(sq: Int): String = "" + ('a' + sq % 8) + (8 - sq / 8)

    fun start(): Pos {
        val b = IntArray(64)
        val back = intArrayOf(4, 2, 3, 5, 6, 3, 2, 4)
        for (i in 0..7) { b[i] = -back[i]; b[8 + i] = -1; b[48 + i] = 1; b[56 + i] = back[i] }
        return Pos(b, true, 15, -1)
    }

    private val knightD = arrayOf(intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, -2), intArrayOf(-1, 2), intArrayOf(1, -2), intArrayOf(1, 2), intArrayOf(2, -1), intArrayOf(2, 1))
    private val kingD = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1), intArrayOf(0, -1), intArrayOf(0, 1), intArrayOf(1, -1), intArrayOf(1, 0), intArrayOf(1, 1))
    private val diag = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1))
    private val orth = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))

    private fun on(r: Int, c: Int) = r in 0..7 && c in 0..7

    // Is this square attacked by the given side?
    fun attacked(b: IntArray, sq: Int, byWhite: Boolean): Boolean {
        val r = sq / 8; val c = sq % 8
        val s = if (byWhite) 1 else -1
        val pr = r + (if (byWhite) 1 else -1)   // a white pawn attacks upward, so it stands one row below
        for (dc in intArrayOf(-1, 1)) if (on(pr, c + dc) && b[pr * 8 + c + dc] == s) return true
        for (d in knightD) if (on(r + d[0], c + d[1]) && b[(r + d[0]) * 8 + c + d[1]] == 2 * s) return true
        for (d in kingD) if (on(r + d[0], c + d[1]) && b[(r + d[0]) * 8 + c + d[1]] == 6 * s) return true
        for (d in diag) { var rr = r + d[0]; var cc = c + d[1]
            while (on(rr, cc)) { val p = b[rr * 8 + cc]; if (p != 0) { if (p == 3 * s || p == 5 * s) return true; break }; rr += d[0]; cc += d[1] } }
        for (d in orth) { var rr = r + d[0]; var cc = c + d[1]
            while (on(rr, cc)) { val p = b[rr * 8 + cc]; if (p != 0) { if (p == 4 * s || p == 5 * s) return true; break }; rr += d[0]; cc += d[1] } }
        return false
    }

    fun inCheck(p: Pos, white: Boolean): Boolean { val k = p.kingSq(white); return k >= 0 && attacked(p.b, k, !white) }

    fun pseudo(p: Pos): ArrayList<Move> {
        val out = ArrayList<Move>(48)
        val w = p.white; val s = if (w) 1 else -1; val b = p.b
        for (sq in 0..63) {
            val pc = b[sq]
            if (pc == 0 || (pc > 0) != w) continue
            val r = sq / 8; val c = sq % 8
            when (abs(pc)) {
                1 -> {
                    val dir = -s; val nr = r + dir
                    val last = nr == (if (w) 0 else 7)
                    fun add(to: Int) { if (last) for (pr in 5 downTo 2) out.add(Move(sq, to, pr)) else out.add(Move(sq, to)) }
                    if (on(nr, c) && b[nr * 8 + c] == 0) {
                        add(nr * 8 + c)
                        if (r == (if (w) 6 else 1) && b[(nr + dir) * 8 + c] == 0) out.add(Move(sq, (nr + dir) * 8 + c))
                    }
                    for (dc in intArrayOf(-1, 1)) if (on(nr, c + dc)) {
                        val t = nr * 8 + c + dc
                        if ((b[t] != 0 && (b[t] > 0) != w) || t == p.ep) add(t)
                    }
                }
                2, 6 -> {
                    for (d in if (abs(pc) == 2) knightD else kingD) if (on(r + d[0], c + d[1])) {
                        val t = (r + d[0]) * 8 + c + d[1]
                        if (b[t] == 0 || (b[t] > 0) != w) out.add(Move(sq, t))
                    }
                    if (abs(pc) == 6) {
                        val home = if (w) 60 else 4
                        if (sq == home && !attacked(b, sq, !w)) {
                            val ks = if (w) 1 else 4; val qs = if (w) 2 else 8
                            if (p.castle and ks != 0 && b[home + 1] == 0 && b[home + 2] == 0 && b[home + 3] == 4 * s &&
                                !attacked(b, home + 1, !w) && !attacked(b, home + 2, !w)) out.add(Move(sq, home + 2))
                            if (p.castle and qs != 0 && b[home - 1] == 0 && b[home - 2] == 0 && b[home - 3] == 0 && b[home - 4] == 4 * s &&
                                !attacked(b, home - 1, !w) && !attacked(b, home - 2, !w)) out.add(Move(sq, home - 2))
                        }
                    }
                }
                else -> {
                    val dirs = when (abs(pc)) { 3 -> diag; 4 -> orth; else -> diag + orth }
                    for (d in dirs) { var rr = r + d[0]; var cc = c + d[1]
                        while (on(rr, cc)) {
                            val t = rr * 8 + cc
                            if (b[t] == 0) out.add(Move(sq, t)) else { if ((b[t] > 0) != w) out.add(Move(sq, t)); break }
                            rr += d[0]; cc += d[1]
                        } }
                }
            }
        }
        return out
    }

    fun make(p: Pos, m: Move): Pos {
        val b = p.b.copyOf()
        val pc = b[m.from]
        val w = pc > 0
        var ep = -1
        var castle = p.castle
        val captured = b[m.to] != 0 || (abs(pc) == 1 && m.to == p.ep && p.ep >= 0 && m.from % 8 != m.to % 8)
        if (abs(pc) == 1 && m.to == p.ep && p.ep >= 0 && m.from % 8 != m.to % 8) b[m.to + (if (w) 8 else -8)] = 0
        if (abs(pc) == 1 && abs(m.to - m.from) == 16) ep = (m.from + m.to) / 2
        if (abs(pc) == 6 && abs(m.to - m.from) == 2) {
            if (m.to > m.from) { b[m.from + 1] = b[m.from + 3]; b[m.from + 3] = 0 } else { b[m.from - 1] = b[m.from - 4]; b[m.from - 4] = 0 }
        }
        b[m.to] = if (m.promo != 0) m.promo * (if (w) 1 else -1) else pc
        b[m.from] = 0
        for (sq in intArrayOf(m.from, m.to)) castle = castle and when (sq) { 60 -> 3.inv(); 63 -> 1.inv(); 56 -> 2.inv(); 4 -> 12.inv(); 7 -> 4.inv(); 0 -> 8.inv(); else -> -1 }
        return Pos(b, !p.white, castle, ep, if (abs(pc) == 1 || captured) 0 else p.half + 1)
    }

    fun legal(p: Pos): List<Move> = pseudo(p).filter { !inCheck(make(p, it), p.white) }

    fun legalFrom(p: Pos, sq: Int): List<Move> = legal(p).filter { it.from == sq }

    // Neither side can ever give mate: bare kings, one minor piece, or only bishops that all stand on one colour.
    fun insufficient(b: IntArray): Boolean {
        var minors = 0; var knights = 0; var col = -1; var same = true
        for (i in 0..63) {
            when (abs(b[i])) {
                0, 6 -> {}
                1, 4, 5 -> return false
                2 -> { knights++; minors++ }
                3 -> { minors++; val c = (i / 8 + i % 8) % 2; if (col == -1) col = c else if (col != c) same = false }
            }
        }
        if (minors <= 1) return true
        return knights == 0 && same
    }

    // "playing", "mate" (side to move lost), "stalemate", "draw" (dead position or the 75-move rule)
    fun status(p: Pos): String {
        if (legal(p).isEmpty()) return if (inCheck(p, p.white)) "mate" else "stalemate"
        if (insufficient(p.b) || p.half >= 150) return "draw"
        return "playing"
    }

    // The full verdict, with the draw rules that need the game so far. `keys` = Engine.key of every position of the game, the current one last.
    // "playing", "mate", "stalemate", "insufficient", "fifty" (50 moves without a pawn move or capture), "repetition" (same position three times).
    fun result(p: Pos, keys: List<Long>): String {
        val s = status(p)
        if (s == "mate" || s == "stalemate") return s
        if (insufficient(p.b)) return "insufficient"
        if (keys.isNotEmpty()) { val k = keys.last(); if (keys.count { it == k } >= 3) return "repetition" }
        if (p.half >= 100) return "fifty"
        return "playing"
    }

    // ---- the search ----

    val value = intArrayOf(0, 100, 320, 330, 500, 900, 0)

    fun eval(p: Pos): Int {
        var s = 0
        for (i in 0..63) {
            val pc = p.b[i]
            if (pc == 0) continue
            val t = abs(pc)
            val r = i / 8; val c = i % 8
            val rank = if (pc > 0) 7 - r else r                 // how far it has advanced
            val centre = 3 - minOf(maxOf(abs(2 * c - 7), abs(2 * r - 7)) / 2, 3)   // 3 in the middle, 0 on the rim
            var v = value[t]
            when (t) {
                1 -> v += rank * 6 + centre * 4
                2, 3 -> v += centre * 10
                6 -> v += if (rank == 0) 0 else -rank * 8      // keep the king home
                5 -> v += centre * 2
            }
            s += if (pc > 0) v else -v
        }
        return if (p.white) s else -s
    }

    private var nodes = 0
    private var deadline = 0L

    private fun order(p: Pos, ms: List<Move>): List<Move> = ms.sortedByDescending {
        val victim = abs(p.b[it.to]); val me = abs(p.b[it.from])
        (if (victim != 0) 10 * value[victim] - value[me] else 0) + (if (it.promo != 0) 800 else 0)
    }

    private fun quiesce(p: Pos, alpha0: Int, beta: Int, depth: Int): Int {
        var alpha = alpha0
        val stand = eval(p)
        if (depth == 0 || stand >= beta) return stand
        if (stand > alpha) alpha = stand
        for (m in order(p, pseudo(p).filter { p.b[it.to] != 0 || it.promo != 0 })) {
            val n = make(p, m)
            if (inCheck(n, p.white)) continue
            val sc = -quiesce(n, -beta, -alpha, depth - 1)
            if (sc >= beta) return sc
            if (sc > alpha) alpha = sc
        }
        return alpha
    }

    private fun search(p: Pos, depth: Int, alpha0: Int, beta: Int, ply: Int): Int {
        var alpha = alpha0
        if (depth == 0) return quiesce(p, alpha, beta, 4)
        nodes++
        if (nodes and 1023 == 0 && System.currentTimeMillis() > deadline) return eval(p)
        val ms = legal(p)
        if (ms.isEmpty()) return if (inCheck(p, p.white)) -100000 + ply else 0
        for (m in order(p, ms)) {
            val sc = -search(make(p, m), depth - 1, -beta, -alpha, ply + 1)
            if (sc >= beta) return sc
            if (sc > alpha) alpha = sc
        }
        return alpha
    }

    // level 1 easy, 2 medium, 3 hard. Easy plays a decent-looking but shallow game with some mistakes.
    fun best(p: Pos, level: Int): Move? {
        val ms = legal(p)
        if (ms.isEmpty()) return null
        if (level <= 1 && Math.random() < 0.35) return ms.random()
        val depth = when (level) { 1 -> 1; 2 -> 2; else -> 3 }
        deadline = System.currentTimeMillis() + (if (level >= 3) 6000L else 2500L)
        nodes = 0
        var bestMove = ms[0]
        var bestScore = Int.MIN_VALUE
        for (m in order(p, ms)) {
            val sc = -search(make(p, m), depth - 1, -1_000_000, 1_000_000, 1) + (if (level <= 2) (Math.random() * 12).toInt() else 0)
            if (sc > bestScore) { bestScore = sc; bestMove = m }
        }
        return bestMove
    }
}
