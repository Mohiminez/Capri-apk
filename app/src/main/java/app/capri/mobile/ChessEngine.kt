package app.capri.mobile

import kotlin.math.abs

class Analysis(val move: Move, val score: Int, val depth: Int, val pv: List<Move>) {
    // score is for the side to move, in centipawns; mate scores are +-(100000 - plies)
    val mateIn: Int? get() = if (abs(score) > 90000) (100000 - abs(score) + 1) / 2 * (if (score > 0) 1 else -1) else null
}

// The search she plays with: iterative deepening against a clock, principal-variation search, a transposition table, null-move pruning,
// late-move reductions, killer and history moves, check extensions, quiescence, repetition and 50-move awareness, a small opening book.
object Engine {
    /** Real Stockfish when its library loads (it does on the phone); Capri's own search otherwise, and in the desktop unit tests. */
    @Volatile var useStockfish = true
    val name: String get() = if (useStockfish) Stockfish.name else "Capri"
    internal val rnd = java.util.Random(0x5EED)
    internal val z = Array(13) { LongArray(64) { rnd.nextLong() } }
    internal val zSide = rnd.nextLong()
    internal val zCastle = LongArray(16) { rnd.nextLong() }
    internal val zEp = LongArray(65) { rnd.nextLong() }

    // The en passant square only matters when a pawn could actually take there (that is what makes two positions the same).
    fun epEffective(b: IntArray, white: Boolean, ep: Int): Int {
        if (ep < 0) return 64
        val row = if (white) ep / 8 + 1 else ep / 8 - 1          // where the pawn that just moved two squares stands
        val me = if (white) 1 else -1
        val c = ep % 8
        if (row !in 0..7) return 64
        if (c > 0 && b[row * 8 + c - 1] == me) return ep
        if (c < 7 && b[row * 8 + c + 1] == me) return ep
        return 64
    }

    fun key(p: Pos): Long {
        var k = if (p.white) 0L else zSide
        for (i in 0..63) { val pc = p.b[i]; if (pc != 0) k = k xor z[pc + 6][i] }
        return k xor zCastle[p.castle and 15] xor zEp[epEffective(p.b, p.white, p.ep)]
    }

    private const val MATE = 100000
    private const val INF = 1_000_000
    private const val TT_BITS = 19
    private val tKey = LongArray(1 shl TT_BITS)
    private val tScore = IntArray(1 shl TT_BITS)
    private val tMeta = IntArray(1 shl TT_BITS)       // depth << 2 | flag (0 exact, 1 lower bound, 2 upper bound)
    private val tMove = IntArray(1 shl TT_BITS)
    private val killers = Array(100) { IntArray(2) }
    private val hist = IntArray(2 * 4096)
    private val mv = Array(100) { IntArray(256) }
    private val sc = Array(100) { IntArray(256) }
    private var nodes = 0L
    private var deadline = 0L
    private var stop = false
    private val victimVal = intArrayOf(0, 100, 320, 330, 500, 900, 2000)

    private fun toTT(s: Int, ply: Int) = if (s > MATE - 300) s + ply else if (s < -MATE + 300) s - ply else s
    private fun fromTT(s: Int, ply: Int) = if (s > MATE - 300) s - ply else if (s < -MATE + 300) s + ply else s

    private fun store(k: Long, depth: Int, score: Int, flag: Int, move: Int, ply: Int) {
        val i = (k ushr (64 - TT_BITS)).toInt()
        tKey[i] = k; tScore[i] = toTT(score, ply); tMeta[i] = (depth shl 2) or flag; tMove[i] = move
    }

    private fun orderScores(bd: Board, n: Int, ply: Int, ttMove: Int, caps: Boolean) {
        val m = mv[ply]; val s = sc[ply]
        val side = if (bd.white) 0 else 1
        for (i in 0 until n) {
            val x = m[i]
            val from = x and 63; val to = (x shr 6) and 63; val promo = (x shr 12) and 7
            val att = Math.abs(bd.b[from])
            var vic = Math.abs(bd.b[to])
            if (vic == 0 && att == 1 && (from and 7) != (to and 7)) vic = 1
            var v: Int
            if (x == ttMove) v = 2_000_000
            else if (vic != 0) v = 1_000_000 + victimVal[vic] * 16 - att
            else if (promo != 0) v = 900_000 + promo
            else if (!caps && killers[ply][0] == x) v = 800_000
            else if (!caps && killers[ply][1] == x) v = 700_000
            else v = hist[side * 4096 + from * 64 + to]
            s[i] = v
        }
    }

    private fun pick(n: Int, from: Int, ply: Int): Int {
        val m = mv[ply]; val s = sc[ply]
        var best = from
        for (j in from + 1 until n) if (s[j] > s[best]) best = j
        if (best != from) { val tm = m[from]; m[from] = m[best]; m[best] = tm; val ts = s[from]; s[from] = s[best]; s[best] = ts }
        return m[from]
    }

    private fun qsearch(bd: Board, alpha0: Int, beta: Int, ply: Int): Int {
        nodes++
        if ((nodes and 2047L) == 0L && System.currentTimeMillis() > deadline) stop = true
        if (stop) return 0
        if (ply >= 90) return bd.eval()
        var alpha = alpha0
        val inChk = bd.inCheck()
        var stand = -INF
        if (!inChk) {
            stand = bd.eval()
            if (stand >= beta) return stand
            if (stand > alpha) alpha = stand
        }
        val n = bd.gen(mv[ply], !inChk)
        orderScores(bd, n, ply, 0, true)
        var legal = 0
        for (i in 0 until n) {
            val m = pick(n, i, ply)
            if (!inChk) {
                val vic = Math.abs(bd.b[(m shr 6) and 63])
                if (stand + victimVal[vic] + 200 < alpha && ((m shr 12) and 7) == 0) continue
            }
            bd.make(m)
            if (bd.movedIntoCheck()) { bd.unmake(m); continue }
            legal++
            val s = -qsearch(bd, -beta, -alpha, ply + 1)
            bd.unmake(m)
            if (stop) return 0
            if (s >= beta) return s
            if (s > alpha) alpha = s
        }
        if (inChk && legal == 0) return -MATE + ply
        return alpha
    }

    private fun search(bd: Board, depth0: Int, alpha0: Int, beta: Int, ply: Int, nullOk: Boolean): Int {
        var alpha = alpha0
        var depth = depth0
        nodes++
        if ((nodes and 2047L) == 0L && System.currentTimeMillis() > deadline) stop = true
        if (stop) return 0
        if (ply > 0 && (bd.half >= 100 || bd.repeated())) return 0
        if (ply >= 90) return bd.eval()
        val inChk = bd.inCheck()
        if (inChk && ply < 60) depth++
        if (depth <= 0) return qsearch(bd, alpha, beta, ply)
        // a mate already found closer to the root can't be beaten here
        val lowBound = -MATE + ply; val highBound = MATE - ply - 1
        if (alpha < lowBound) alpha = lowBound
        var betaW = beta
        if (betaW > highBound) betaW = highBound
        if (alpha >= betaW) return alpha
        val pv = betaW - alpha > 1
        val k = bd.hash
        val ti = (k ushr (64 - TT_BITS)).toInt()
        var ttMove = 0
        if (tKey[ti] == k) {
            ttMove = tMove[ti]
            val td = tMeta[ti] shr 2; val tf = tMeta[ti] and 3
            if (td >= depth && !pv) {
                val ts = fromTT(tScore[ti], ply)
                if (tf == 0) return ts
                if (tf == 1 && ts >= betaW) return ts
                if (tf == 2 && ts <= alpha) return ts
            }
        }
        var staticEval = 0
        if (!inChk && depth <= 3) staticEval = bd.eval()
        if (!pv && !inChk) {
            if (depth <= 3 && staticEval - 130 * depth >= betaW && Math.abs(betaW) < MATE - 300) return staticEval
            if (nullOk && depth >= 3 && bd.hasPieces(bd.white)) {
                val ev = if (depth <= 3) staticEval else bd.eval()
                if (ev >= betaW) {
                    val r = 2 + depth / 4
                    bd.makeNull()
                    val s = -search(bd, depth - 1 - r, -betaW, -betaW + 1, ply + 1, false)
                    bd.unmakeNull()
                    if (stop) return 0
                    if (s >= betaW) return if (s > MATE - 300) betaW else s
                }
            }
        }
        val n = bd.gen(mv[ply], false)
        orderScores(bd, n, ply, ttMove, false)
        var legal = 0
        var best = -INF
        var bestMove = 0
        var flag = 2
        val side = if (bd.white) 0 else 1
        for (i in 0 until n) {
            val m = pick(n, i, ply)
            val from = m and 63; val to = (m shr 6) and 63
            val quiet = bd.b[to] == 0 && ((m shr 12) and 7) == 0 && !(Math.abs(bd.b[from]) == 1 && (from and 7) != (to and 7))
            bd.make(m)
            if (bd.movedIntoCheck()) { bd.unmake(m); continue }
            legal++
            val gives = bd.inCheck()
            if (!pv && quiet && !inChk && !gives && depth <= 2 && legal > 1 && staticEval + 150 * depth <= alpha) { bd.unmake(m); continue }
            var s: Int
            if (legal == 1) s = -search(bd, depth - 1, -betaW, -alpha, ply + 1, true)
            else {
                var red = 0
                if (depth >= 3 && legal > 3 && quiet && !inChk && !gives) red = if (legal > 8 && depth >= 5) 2 else 1
                s = -search(bd, depth - 1 - red, -alpha - 1, -alpha, ply + 1, true)
                if (s > alpha && red > 0) s = -search(bd, depth - 1, -alpha - 1, -alpha, ply + 1, true)
                if (s > alpha && s < betaW) s = -search(bd, depth - 1, -betaW, -alpha, ply + 1, true)
            }
            bd.unmake(m)
            if (stop) return 0
            if (s > best) { best = s; bestMove = m }
            if (s > alpha) {
                alpha = s; flag = 0
                if (alpha >= betaW) {
                    if (quiet) {
                        if (killers[ply][0] != m) { killers[ply][1] = killers[ply][0]; killers[ply][0] = m }
                        val hi = side * 4096 + from * 64 + to
                        hist[hi] += depth * depth
                        if (hist[hi] > 400_000) for (j in hist.indices) hist[j] = hist[j] / 2
                    }
                    store(k, depth, s, 1, m, ply)
                    return s
                }
            }
        }
        if (legal == 0) return if (inChk) -MATE + ply else 0
        store(k, depth, alpha, flag, bestMove, ply)
        return alpha
    }

    // 1 easy (shallow and careless), 2 medium, 3 club (Stockfish at skill 8, roughly a club player), 4 hard (skill 15, about 2000),
    // 5 master (full strength, thinks as long as it can in a few seconds).
    fun forLevel(p: Pos, level: Int, history: List<Long> = emptyList()): Analysis? {
        val ms = Chess.legal(p)
        if (ms.isEmpty()) return null
        return when (level) {
            1 -> weak(p, 2, 140, history)
            2 -> weak(p, 3, 45, history)
            3 -> analyse(p, 900, 12, history, true, 8)
            4 -> analyse(p, 1800, 14, history, true, 15)
            else -> analyse(p, 5000, 30, history, true)
        }
    }

    /** Levels used to be four (3 = hard at full strength, 4 = master). Five now, so an old saved 3 or 4 moves up one and keeps meaning what it meant. */
    fun migrateLevel(saved: Int, already: Boolean): Int = if (already) saved.coerceIn(1, 5) else (if (saved >= 3) saved + 1 else saved).coerceIn(1, 5)

    // Easy levels: score every move at a fixed shallow depth, then let some noise pick - so she plays sensible but human mistakes.
    @Synchronized private fun weak(p: Pos, depth: Int, noise: Int, history: List<Long>): Analysis? {
        val root = Chess.legal(p)
        if (root.size == 1) return Analysis(root[0], 0, 1, listOf(root[0]))
        Book.pick(p)?.let { return Analysis(it, 0, 0, listOf(it)) }
        val bd = Board(p, history)
        stop = false; nodes = 0; deadline = System.currentTimeMillis() + 3000
        var bestMove = root[0]; var bestVal = -INF; var bestScore = 0
        for (m in root) {
            val code = m.from or (m.to shl 6) or (m.promo shl 12)
            bd.make(code)
            val s = -search(bd, depth - 1, -INF, INF, 1, true)
            bd.unmake(code)
            val noisy = s + rnd.nextInt(noise + 1)
            if (noisy > bestVal) { bestVal = noisy; bestMove = m; bestScore = s }
        }
        return Analysis(bestMove, bestScore, depth, listOf(bestMove))
    }

    private fun pvLine(p0: Pos, first: Move, n: Int): List<Move> {
        val out = arrayListOf(first)
        var p = Chess.make(p0, first)
        val seen = HashSet<Long>()
        while (out.size < n) {
            val k = key(p)
            if (!seen.add(k)) break
            val i = (k ushr (64 - TT_BITS)).toInt()
            if (tKey[i] != k || tMove[i] == 0) break
            val x = tMove[i]
            val m = Chess.legal(p).firstOrNull { it.from == (x and 63) && it.to == ((x shr 6) and 63) && it.promo == ((x shr 12) and 7) } ?: break
            out.add(m); p = Chess.make(p, m)
        }
        return out
    }

    // Best move by thinking up to `millis`, no deeper than `maxDepth`. `history` = Engine.key of the positions played so far (for repetition).
    @Synchronized fun analyse(p: Pos, millis: Long, maxDepth: Int = 10, history: List<Long> = emptyList(), book: Boolean = false, skill: Int = 20): Analysis? {
        val legalMoves = Chess.legal(p)
        if (legalMoves.isEmpty()) return null
        if (legalMoves.size == 1) return Analysis(legalMoves[0], 0, 1, listOf(legalMoves[0]))
        if (book) Book.pick(p)?.let { return Analysis(it, 0, 0, listOf(it)) }
        if (useStockfish && millis >= 300) Stockfish.analyse(p, millis, maxOf(maxDepth, 24), skill)?.let { return it }
        val bd = Board(p, history)
        val maxDepth = if (skill >= 20) maxDepth else minOf(maxDepth, 2 + skill / 3)   // her own search, when Stockfish is not there: a lower skill looks less far ahead
        for (a in killers) { a[0] = 0; a[1] = 0 }
        for (i in hist.indices) hist[i] = hist[i] / 4
        nodes = 0; stop = false
        val t0 = System.currentTimeMillis()
        deadline = t0 + millis
        // root moves, best first
        var root = IntArray(256); var rootN = 0
        val tmp = IntArray(256); val cnt = bd.gen(tmp, false)
        for (i in 0 until cnt) { bd.make(tmp[i]); if (!bd.movedIntoCheck()) root[rootN++] = tmp[i]; bd.unmake(tmp[i]) }
        val rootScore = IntArray(256)
        var best: Analysis? = null
        var depth = 1
        var prevBest = 0
        while (depth <= maxDepth) {
            var alpha = -INF
            var bm = 0; var bs = -INF
            // previous best move first, then by last iteration's scores
            for (i in 0 until rootN) rootScore[i] = if (root[i] == prevBest) INF else rootScore[i]
            val order = (0 until rootN).sortedByDescending { rootScore[it] }
            val newRoot = IntArray(256); val newScore = IntArray(256)
            var done = 0
            for ((idx, oi) in order.withIndex()) {
                val m = root[oi]
                bd.make(m)
                var s: Int
                if (idx == 0) s = -search(bd, depth - 1, -INF, -alpha, 1, true)
                else {
                    s = -search(bd, depth - 1, -alpha - 1, -alpha, 1, true)
                    if (s > alpha && !stop) s = -search(bd, depth - 1, -INF, -alpha, 1, true)
                }
                bd.unmake(m)
                if (stop) { if (idx == 0) { bm = 0 }; break }
                newRoot[idx] = m; newScore[idx] = s; done++
                if (s > bs) { bs = s; bm = m }
                if (s > alpha) alpha = s
            }
            if (stop) {
                // an unfinished iteration: its best move still counts if it beat the previous best fully
                if (bm != 0 && done > 0 && (best == null || (bs > best.score && bm != prevBest))) {
                    val mm = Chess.legal(p).first { it.from == (bm and 63) && it.to == ((bm shr 6) and 63) && it.promo == ((bm shr 12) and 7) }
                    best = Analysis(mm, bs, depth, pvLine(p, mm, depth))
                }
                break
            }
            if (bm == 0) break
            for (i in 0 until rootN) { root[i] = newRoot[i]; rootScore[i] = newScore[i] }
            prevBest = bm
            store(bd.hash, depth, bs, 0, bm, 0)
            val mm = Chess.legal(p).first { it.from == (bm and 63) && it.to == ((bm shr 6) and 63) && it.promo == ((bm shr 12) and 7) }
            best = Analysis(mm, bs, depth, pvLine(p, mm, depth))
            if (Math.abs(bs) > MATE - 300 && depth >= 2) break           // a forced mate was found
            if (System.currentTimeMillis() - t0 > millis * 6 / 10 && depth >= 4) break   // the next iteration will not finish
            depth++
        }
        return best ?: Analysis(legalMoves[0], 0, 0, listOf(legalMoves[0]))
    }
}

// Standard algebraic notation ("Nf3", "exd5", "O-O", "e8=Q+") for showing and speaking moves.
// A whole game from text: "1. e4 e5 2. Nf3 ..." with tags, comments {like this}, variations (like this) and results ignored.
class PgnGame(val start: Pos, val moves: List<Move>, val badAt: Int)

object Pgn {
    fun parse(text: String): PgnGame? {
        var start = Chess.start()
        Regex("\\[FEN\\s+\"([^\"]+)\"\\]").find(text)?.let { m -> Fen.parse(m.groupValues[1])?.let { start = it } }
        var t = text.replace(Regex("\\[[^\\]]*\\]"), " ").replace(Regex("\\{[^}]*\\}"), " ").replace(Regex(";[^\\n]*"), " ")
        var prev: String
        do { prev = t; t = t.replace(Regex("\\([^()]*\\)"), " ") } while (t != prev)
        t = t.replace(Regex("\\$\\d+"), " ").replace(Regex("\\d+\\.(\\.\\.)?"), " ")
        val toks = t.split(Regex("\\s+")).filter { it.isNotBlank() && it !in setOf("1-0", "0-1", "1/2-1/2", "*") }
        if (toks.isEmpty()) return null
        var p = start
        val out = ArrayList<Move>()
        for ((i, tok) in toks.withIndex()) {
            val m = San.parse(p, tok)
            if (m == null) return if (out.isEmpty()) null else PgnGame(start, out, i)
            out.add(m); p = Chess.make(p, m)
        }
        return PgnGame(start, out, -1)
    }
}

object San {
    private fun norm(s: String) = s.replace("0-0-0", "O-O-O").replace("0-0", "O-O").replace(Regex("[+#!?x=]"), "").replace("e.p.", "").trim()

    // The legal move that this text means, or null.
    fun parse(p: Pos, text: String): Move? {
        val want = norm(text)
        if (want.isEmpty()) return null
        return Chess.legal(p).firstOrNull { norm(of(p, it)) == want }
            ?: Chess.legal(p).firstOrNull { it.toString() == text.trim().lowercase() }
    }

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
        // sanity: one king each, no pawns on the back ranks, kings apart, and the side NOT to move must not be in check
        if (b.count { it == 6 } != 1 || b.count { it == -6 } != 1) return null
        for (i in 0..7) if (abs(b[i]) == 1 || abs(b[56 + i]) == 1) return null
        val wk = b.indexOf(6); val bk = b.indexOf(-6)
        if (abs(wk / 8 - bk / 8) <= 1 && abs(wk % 8 - bk % 8) <= 1) return null
        // castling rights only count when the king and the rook are still at home
        if (b[60] != 6) castle = castle and 3.inv()
        if (b[4] != -6) castle = castle and 12.inv()
        if (b[63] != 4) castle = castle and 1.inv()
        if (b[56] != 4) castle = castle and 2.inv()
        if (b[7] != -4) castle = castle and 4.inv()
        if (b[0] != -4) castle = castle and 8.inv()
        // an en passant square is only real with the pawn that just moved standing in front of it
        if (ep >= 0) {
            val ok = if (white) ep / 8 == 2 && ep + 8 < 64 && b[ep + 8] == -1 && b[ep] == 0 else ep / 8 == 5 && ep - 8 >= 0 && b[ep - 8] == 1 && b[ep] == 0
            if (!ok) ep = -1
        }
        val half = f.getOrNull(4)?.toIntOrNull()?.coerceIn(0, 200) ?: 0
        val p = Pos(b, white, castle, ep, half)
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
        sb.append(if (p.ep >= 0) Chess.name(p.ep) else "-").append(" ${p.half} 1")
        return sb.toString()
    }
}
