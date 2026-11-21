package app.capri.mobile

import kotlin.math.abs

class Analysis(val move: Move, val score: Int, val depth: Int, val pv: List<Move>) {
    // score is for the side to move, in centipawns; mate scores are +-(100000 - plies)
    val mateIn: Int? get() = if (abs(score) > 90000) (100000 - abs(score) + 1) / 2 * (if (score > 0) 1 else -1) else null
}

// A stronger search than the quick one in Chess: iterative deepening against a clock, transposition table, killer moves,
// check extensions, quiescence and a better evaluation (pawn structure, bishop pair, open files).
object Engine {
    private class Entry(val key: Long, val depth: Int, val score: Int, val flag: Int, val move: Move?)

    private val rnd = java.util.Random(0x5EED)
    private val z = Array(13) { LongArray(64) { rnd.nextLong() } }
    private val zSide = rnd.nextLong()
    private val zCastle = LongArray(16) { rnd.nextLong() }
    private val zEp = LongArray(65) { rnd.nextLong() }

    fun key(p: Pos): Long {
        var k = if (p.white) 0L else zSide
        for (i in 0..63) { val pc = p.b[i]; if (pc != 0) k = k xor z[pc + 6][i] }
        return k xor zCastle[p.castle and 15] xor zEp[if (p.ep < 0) 64 else p.ep]
    }

    private var tt = HashMap<Long, Entry>()
    private var nodes = 0L
    private var deadline = 0L
    private var stop = false
    private val killers = Array(64) { arrayOfNulls<Move>(2) }
    private const val MATE = 100000

    private fun same(a: Move?, b: Move) = a != null && a.from == b.from && a.to == b.to && a.promo == b.promo

    // From the side to move's view: Chess.eval plus structure.
    private fun evalPlus(p: Pos): Int {
        var s = Chess.eval(p)
        var extra = 0
        val pawnsW = IntArray(8); val pawnsB = IntArray(8)
        var bishopsW = 0; var bishopsB = 0
        for (i in 0..63) {
            val pc = p.b[i]; if (pc == 0) continue
            val c = i % 8
            if (pc == 1) pawnsW[c]++ else if (pc == -1) pawnsB[c]++ else if (pc == 3) bishopsW++ else if (pc == -3) bishopsB++
        }
        if (bishopsW >= 2) extra += 30
        if (bishopsB >= 2) extra -= 30
        for (c in 0..7) {
            if (pawnsW[c] > 1) extra -= 12 * (pawnsW[c] - 1)
            if (pawnsB[c] > 1) extra += 12 * (pawnsB[c] - 1)
            val nbW = (if (c > 0) pawnsW[c - 1] else 0) + (if (c < 7) pawnsW[c + 1] else 0)
            val nbB = (if (c > 0) pawnsB[c - 1] else 0) + (if (c < 7) pawnsB[c + 1] else 0)
            if (pawnsW[c] > 0 && nbW == 0) extra -= 10
            if (pawnsB[c] > 0 && nbB == 0) extra += 10
        }
        for (i in 0..63) {
            val pc = p.b[i]; val c = i % 8; val r = i / 8
            if (pc == 1) {                       // passed pawn: no enemy pawn ahead on its file or neighbours
                var passed = true
                for (rr in 0 until r) for (cc in maxOf(0, c - 1)..minOf(7, c + 1)) if (p.b[rr * 8 + cc] == -1) passed = false
                if (passed) extra += 12 + (6 - r) * 10
            } else if (pc == -1) {
                var passed = true
                for (rr in r + 1..7) for (cc in maxOf(0, c - 1)..minOf(7, c + 1)) if (p.b[rr * 8 + cc] == 1) passed = false
                if (passed) extra -= 12 + (r - 1) * 10
            } else if (pc == 4 && pawnsW[c] == 0) extra += if (pawnsB[c] == 0) 18 else 8
            else if (pc == -4 && pawnsB[c] == 0) extra -= if (pawnsW[c] == 0) 18 else 8
        }
        s += if (p.white) extra else -extra
        return s
    }

    private fun order(p: Pos, ms: List<Move>, ttMove: Move?, ply: Int): List<Move> {
        fun score(m: Move): Int {
            if (same(ttMove, m)) return 1_000_000
            val victim = abs(p.b[m.to]); val me = abs(p.b[m.from])
            var s = if (victim != 0) 10_000 + 10 * Chess.value[victim] - Chess.value[me] else 0
            if (m.promo != 0) s += 8_000
            if (victim == 0 && ply < 64) { if (same(killers[ply][0], m)) s += 5_000 else if (same(killers[ply][1], m)) s += 4_000 }
            return s
        }
        return ms.sortedByDescending { score(it) }
    }

    private fun quiesce(p: Pos, alpha0: Int, beta: Int, depth: Int): Int {
        var alpha = alpha0
        val stand = evalPlus(p)
        if (depth == 0 || stand >= beta) return stand
        if (stand > alpha) alpha = stand
        val caps = Chess.pseudo(p).filter { p.b[it.to] != 0 || it.promo != 0 }
        for (m in caps.sortedByDescending { 10 * Chess.value[abs(p.b[it.to])] - Chess.value[abs(p.b[it.from])] }) {
            val n = Chess.make(p, m)
            if (Chess.inCheck(n, p.white)) continue
            val sc = -quiesce(n, -beta, -alpha, depth - 1)
            if (sc >= beta) return sc
            if (sc > alpha) alpha = sc
        }
        return alpha
    }

    private fun search(p: Pos, depth0: Int, alpha0: Int, beta: Int, ply: Int): Int {
        var alpha = alpha0
        var depth = depth0
        nodes++
        if (nodes and 2047L == 0L && System.currentTimeMillis() > deadline) stop = true
        if (stop) return 0
        val inChk = Chess.inCheck(p, p.white)
        if (inChk && ply < 30) depth++                       // never leave a king in check at the horizon
        if (depth <= 0) return quiesce(p, alpha, beta, 5)
        val k = key(p)
        val e = tt[k]
        var ttMove: Move? = null
        if (e != null && e.key == k) {
            ttMove = e.move
            if (e.depth >= depth && ply > 0) {
                if (e.flag == 0) return e.score
                if (e.flag == 1 && e.score >= beta) return e.score
                if (e.flag == 2 && e.score <= alpha) return e.score
            }
        }
        val ms = Chess.legal(p)
        if (ms.isEmpty()) return if (inChk) -MATE + ply else 0
        var bestMove: Move? = null
        var flag = 2
        for (m in order(p, ms, ttMove, ply)) {
            val sc = -search(Chess.make(p, m), depth - 1, -beta, -alpha, ply + 1)
            if (stop) return 0
            if (sc >= beta) {
                if (p.b[m.to] == 0 && ply < 64 && !same(killers[ply][0], m)) { killers[ply][1] = killers[ply][0]; killers[ply][0] = m }
                tt[k] = Entry(k, depth, sc, 1, m)
                return sc
            }
            if (sc > alpha) { alpha = sc; bestMove = m; flag = 0 }
        }
        tt[k] = Entry(k, depth, alpha, flag, bestMove)
        return alpha
    }

    // 1 easy (quick, sloppy), 2 medium, 3 hard, 4 master (thinks as long as it can in six seconds).
    fun forLevel(p: Pos, level: Int): Analysis? {
        val ms = Chess.legal(p)
        if (ms.isEmpty()) return null
        if (level <= 1 && Math.random() < 0.35) { val m = ms.random(); return Analysis(m, 0, 1, listOf(m)) }
        return when (level) { 1 -> analyse(p, 400, 2); 2 -> analyse(p, 900, 3); 3 -> analyse(p, 2500, 6); else -> analyse(p, 6000, 14) }
    }

    private fun pv(p0: Pos, first: Move, n: Int): List<Move> {
        val out = arrayListOf(first)
        var p = Chess.make(p0, first)
        val seen = HashSet<Long>()
        while (out.size < n) {
            val k = key(p)
            if (!seen.add(k)) break
            val m = tt[k]?.move ?: break
            if (Chess.legal(p).none { same(m, it) }) break
            out.add(m); p = Chess.make(p, m)
        }
        return out
    }

    // Best move by thinking up to `millis`. `maxDepth` limits how deep it goes (weaker play for lower levels).
    @Synchronized fun analyse(p: Pos, millis: Long, maxDepth: Int = 10): Analysis? {
        val root = Chess.legal(p)
        if (root.isEmpty()) return null
        if (root.size == 1) return Analysis(root[0], 0, 1, listOf(root[0]))
        if (tt.size > 400_000) tt = HashMap()
        for (a in killers) { a[0] = null; a[1] = null }
        nodes = 0; stop = false
        deadline = System.currentTimeMillis() + millis
        var best: Analysis? = null
        var depth = 1
        while (depth <= maxDepth) {
            var alpha = -MATE - 1
            var bm: Move? = null
            var bs = -MATE - 1
            val prev = best?.move ?: tt[key(p)]?.move
            for (m in order(p, root, prev, 0)) {
                val sc = -search(Chess.make(p, m), depth - 1, -MATE - 1, -alpha, 1)
                if (stop) break
                if (sc > bs) { bs = sc; bm = m; if (sc > alpha) alpha = sc }
            }
            if (stop || bm == null) break
            tt[key(p)] = Entry(key(p), depth, bs, 0, bm)
            best = Analysis(bm, bs, depth, pv(p, bm, depth))
            if (abs(bs) > MATE - 200) break            // a forced mate was found
            depth++
        }
        return best ?: Analysis(root[0], 0, 0, listOf(root[0]))
    }
}

// Standard algebraic notation ("Nf3", "exd5", "O-O", "e8=Q+") for showing and speaking moves.
object San {
    fun of(p: Pos, m: Move): String {
        val pc = abs(p.b[m.from])
        var s: String
        if (pc == 6 && abs(m.to - m.from) == 2) s = if (m.to > m.from) "O-O" else "O-O-O"
        else {
            val cap = p.b[m.to] != 0 || (pc == 1 && m.to == p.ep)
            s = ""
            if (pc == 1) { if (cap) s += ('a' + m.from % 8) }
            else {
                s += "?PNBRQK"[pc]
                val others = Chess.legal(p).filter { it.to == m.to && abs(p.b[it.from]) == pc && it.from != m.from }
                if (others.isNotEmpty()) {
                    if (others.none { it.from % 8 == m.from % 8 }) s += ('a' + m.from % 8)
                    else if (others.none { it.from / 8 == m.from / 8 }) s += (8 - m.from / 8)
                    else s += Chess.name(m.from)
                }
            }
            if (cap) s += "x"
            s += Chess.name(m.to)
            if (m.promo != 0) s += "=" + "??NBRQ"[m.promo]
        }
        val after = Chess.make(p, m)
        val st = Chess.status(after)
        if (st == "mate") s += "#" else if (Chess.inCheck(after, after.white)) s += "+"
        return s
    }
}

// FEN text <-> position, so a game from chess.com or Lichess can be pasted in and a position copied out.
object Fen {
    const val START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

    fun parse(text: String): Pos? {
        val f = text.trim().split(Regex("\\s+"))
        if (f.size < 2) return null
        val b = IntArray(64)
        var sq = 0
        for (ch in f[0]) {
            when {
                ch == '/' -> { if (sq % 8 != 0) return null }
                ch in '1'..'8' -> sq += ch - '0'
                else -> {
                    val i = "pnbrqk".indexOf(ch.lowercaseChar())
                    if (i < 0 || sq > 63) return null
                    b[sq++] = (i + 1) * (if (ch.isUpperCase()) 1 else -1)
                }
            }
        }
        if (sq != 64) return null
        val white = when (f[1]) { "w" -> true; "b" -> false; else -> return null }
        var castle = 0
        val c = f.getOrElse(2) { "-" }
        if ('K' in c) castle = castle or 1; if ('Q' in c) castle = castle or 2; if ('k' in c) castle = castle or 4; if ('q' in c) castle = castle or 8
        var ep = -1
        val e = f.getOrElse(3) { "-" }
        if (e.length == 2 && e[0] in 'a'..'h' && e[1] in '1'..'8') ep = (8 - (e[1] - '0')) * 8 + (e[0] - 'a')
        val p = Pos(b, white, castle, ep)
        // sanity: one king each, and the side NOT to move must not be in check
        if (b.count { it == 6 } != 1 || b.count { it == -6 } != 1) return null
        if (Chess.inCheck(p, !white)) return null
        return p
    }

    fun write(p: Pos): String {
        val sb = StringBuilder()
        for (r in 0..7) {
            var empty = 0
            for (c in 0..7) {
                val pc = p.b[r * 8 + c]
                if (pc == 0) { empty++; continue }
                if (empty > 0) { sb.append(empty); empty = 0 }
                val ch = "pnbrqk"[abs(pc) - 1]
                sb.append(if (pc > 0) ch.uppercaseChar() else ch)
            }
            if (empty > 0) sb.append(empty)
            if (r < 7) sb.append('/')
        }
        sb.append(if (p.white) " w " else " b ")
        var c = ""
        if (p.castle and 1 != 0) c += "K"; if (p.castle and 2 != 0) c += "Q"; if (p.castle and 4 != 0) c += "k"; if (p.castle and 8 != 0) c += "q"
        sb.append(if (c.isEmpty()) "-" else c).append(' ')
        sb.append(if (p.ep >= 0) Chess.name(p.ep) else "-").append(" 0 1")
        return sb.toString()
    }
}
