package app.capri.mobile

// The board the search plays on: mutable (make / unmake), packed integer moves, a running hash, no allocation while it searches.
// Same squares and piece codes as Pos (a8 = 0, h1 = 63, white positive). A move is  from | to << 6 | promo << 12.
internal object Tabs {
    val knight = Array(64) { IntArray(0) }
    val king = Array(64) { IntArray(0) }
    val rays = Array(8) { Array(64) { IntArray(0) } }       // 0..3 straight, 4..7 diagonal
    val cmask = IntArray(64) { -1 }
    val VAL = intArrayOf(0, 100, 320, 330, 500, 900, 0)
    val MG = Array(7) { IntArray(64) }
    val EG = Array(7) { IntArray(64) }

    init {
        val kd = arrayOf(intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, -2), intArrayOf(-1, 2), intArrayOf(1, -2), intArrayOf(1, 2), intArrayOf(2, -1), intArrayOf(2, 1))
        val gd = arrayOf(intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1), intArrayOf(0, -1), intArrayOf(0, 1), intArrayOf(1, -1), intArrayOf(1, 0), intArrayOf(1, 1))
        val dirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1), intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1))
        for (sq in 0..63) {
            val r = sq / 8; val c = sq % 8
            knight[sq] = kd.filter { r + it[0] in 0..7 && c + it[1] in 0..7 }.map { (r + it[0]) * 8 + c + it[1] }.toIntArray()
            king[sq] = gd.filter { r + it[0] in 0..7 && c + it[1] in 0..7 }.map { (r + it[0]) * 8 + c + it[1] }.toIntArray()
            for (d in 0..7) {
                val l = ArrayList<Int>()
                var rr = r + dirs[d][0]; var cc = c + dirs[d][1]
                while (rr in 0..7 && cc in 0..7) { l.add(rr * 8 + cc); rr += dirs[d][0]; cc += dirs[d][1] }
                rays[d][sq] = l.toIntArray()
            }
        }
        cmask[60] = 3.inv(); cmask[63] = 1.inv(); cmask[56] = 2.inv(); cmask[4] = 12.inv(); cmask[7] = 4.inv(); cmask[0] = 8.inv()

        // Piece-square tables, drawn the way White sees the board (top row = rank 8).
        val pawn = intArrayOf(0,0,0,0,0,0,0,0, 50,50,50,50,50,50,50,50, 10,10,20,30,30,20,10,10, 5,5,10,25,25,10,5,5, 0,0,0,20,20,0,0,0, 5,-5,-10,0,0,-10,-5,5, 5,10,10,-20,-20,10,10,5, 0,0,0,0,0,0,0,0)
        val knightT = intArrayOf(-50,-40,-30,-30,-30,-30,-40,-50, -40,-20,0,0,0,0,-20,-40, -30,0,10,15,15,10,0,-30, -30,5,15,20,20,15,5,-30, -30,0,15,20,20,15,0,-30, -30,5,10,15,15,10,5,-30, -40,-20,0,5,5,0,-20,-40, -50,-40,-30,-30,-30,-30,-40,-50)
        val bishop = intArrayOf(-20,-10,-10,-10,-10,-10,-10,-20, -10,0,0,0,0,0,0,-10, -10,0,5,10,10,5,0,-10, -10,5,5,10,10,5,5,-10, -10,0,10,10,10,10,0,-10, -10,10,10,10,10,10,10,-10, -10,5,0,0,0,0,5,-10, -20,-10,-10,-10,-10,-10,-10,-20)
        val rook = intArrayOf(0,0,0,0,0,0,0,0, 5,10,10,10,10,10,10,5, -5,0,0,0,0,0,0,-5, -5,0,0,0,0,0,0,-5, -5,0,0,0,0,0,0,-5, -5,0,0,0,0,0,0,-5, -5,0,0,0,0,0,0,-5, 0,0,0,5,5,0,0,0)
        val queen = intArrayOf(-20,-10,-10,-5,-5,-10,-10,-20, -10,0,0,0,0,0,0,-10, -10,0,5,5,5,5,0,-10, -5,0,5,5,5,5,0,-5, 0,0,5,5,5,5,0,-5, -10,5,5,5,5,5,0,-10, -10,0,5,0,0,0,0,-10, -20,-10,-10,-5,-5,-10,-10,-20)
        val kingMg = intArrayOf(-30,-40,-40,-50,-50,-40,-40,-30, -30,-40,-40,-50,-50,-40,-40,-30, -30,-40,-40,-50,-50,-40,-40,-30, -30,-40,-40,-50,-50,-40,-40,-30, -20,-30,-30,-40,-40,-30,-30,-20, -10,-20,-20,-20,-20,-20,-20,-10, 20,20,0,0,0,0,20,20, 20,30,10,0,0,10,30,20)
        val kingEg = intArrayOf(-50,-40,-30,-20,-20,-30,-40,-50, -30,-20,-10,0,0,-10,-20,-30, -30,-10,20,30,30,20,-10,-30, -30,-10,30,40,40,30,-10,-30, -30,-10,30,40,40,30,-10,-30, -30,-10,20,30,30,20,-10,-30, -30,-30,0,0,0,0,-30,-30, -50,-30,-30,-30,-30,-30,-30,-50)
        val all = arrayOf(pawn, knightT, bishop, rook, queen)
        for (i in 0..4) { for (s in 0..63) { MG[i + 1][s] = all[i][s]; EG[i + 1][s] = all[i][s] } }
        for (s in 0..63) { EG[1][s] = pawn[s] / 2 + (7 - s / 8) * 4; MG[6][s] = kingMg[s]; EG[6][s] = kingEg[s] }
    }
}

internal class Board(p: Pos, history: List<Long> = emptyList()) {
    val b = p.b.copyOf()
    var white = p.white
    var castle = p.castle
    var ep = p.ep
    var half = p.half
    var hash = Engine.key(p)
    private var epK = Engine.zEp[Engine.epEffective(b, white, ep)]
    val ksq = IntArray(2)
    private val uCap = IntArray(300); private val uCastle = IntArray(300); private val uEp = IntArray(300); private val uHalf = IntArray(300)
    private val uHash = LongArray(300); private val uEpK = LongArray(300); private val uEpCap = BooleanArray(300)
    private var sp = 0
    private val keys = LongArray(1200)
    private var nKeys = 0
    private val pwF = IntArray(8); private val pbF = IntArray(8)

    init {
        for (i in 0..63) { if (b[i] == 6) ksq[0] = i else if (b[i] == -6) ksq[1] = i }
        for (k in history.takeLast(600)) keys[nKeys++] = k
        if (nKeys == 0 || keys[nKeys - 1] != hash) keys[nKeys++] = hash
    }

    fun attacked(sq: Int, byWhite: Boolean): Boolean {
        val s = if (byWhite) 1 else -1
        val r = sq shr 3; val c = sq and 7
        val pr = if (byWhite) r + 1 else r - 1
        if (pr in 0..7) {
            if (c > 0 && b[pr * 8 + c - 1] == s) return true
            if (c < 7 && b[pr * 8 + c + 1] == s) return true
        }
        for (t in Tabs.knight[sq]) if (b[t] == 2 * s) return true
        for (t in Tabs.king[sq]) if (b[t] == 6 * s) return true
        for (d in 0..3) for (t in Tabs.rays[d][sq]) { val p = b[t]; if (p != 0) { if (p == 4 * s || p == 5 * s) return true; break } }
        for (d in 4..7) for (t in Tabs.rays[d][sq]) { val p = b[t]; if (p != 0) { if (p == 3 * s || p == 5 * s) return true; break } }
        return false
    }

    fun inCheck(): Boolean = attacked(ksq[if (white) 0 else 1], !white)
    // after make(): did the side that just moved leave its own king attacked?
    fun movedIntoCheck(): Boolean = attacked(ksq[if (white) 1 else 0], white)

    fun repeated(): Boolean {
        var i = nKeys - 3
        val lim = maxOf(0, nKeys - 1 - half)
        while (i >= lim) { if (keys[i] == hash) return true; i -= 2 }
        return false
    }

    // Pseudo-legal moves into `out`; with caps = true only captures and queen promotions. Returns how many.
    fun gen(out: IntArray, caps: Boolean): Int {
        var n = 0
        val w = white; val s = if (w) 1 else -1
        for (sq in 0..63) {
            val pc = b[sq]
            if (pc == 0 || (pc > 0) != w) continue
            val a = if (pc > 0) pc else -pc
            val r = sq shr 3; val c = sq and 7
            when (a) {
                1 -> {
                    val dir = -s; val nr = r + dir
                    if (nr !in 0..7) continue
                    val last = nr == (if (w) 0 else 7)
                    val f = nr * 8 + c
                    if (b[f] == 0) {
                        if (last) { out[n++] = sq or (f shl 6) or (5 shl 12); if (!caps) { out[n++] = sq or (f shl 6) or (4 shl 12); out[n++] = sq or (f shl 6) or (3 shl 12); out[n++] = sq or (f shl 6) or (2 shl 12) } }
                        else if (!caps) {
                            out[n++] = sq or (f shl 6)
                            if (r == (if (w) 6 else 1) && b[f + dir * 8] == 0) out[n++] = sq or ((f + dir * 8) shl 6)
                        }
                    }
                    var dc = -1
                    while (dc <= 1) {
                        val cc = c + dc
                        if (cc in 0..7) {
                            val t = nr * 8 + cc
                            if ((b[t] != 0 && (b[t] > 0) != w) || (t == ep && b[t] == 0)) {
                                if (last) { out[n++] = sq or (t shl 6) or (5 shl 12); if (!caps) { out[n++] = sq or (t shl 6) or (4 shl 12); out[n++] = sq or (t shl 6) or (3 shl 12); out[n++] = sq or (t shl 6) or (2 shl 12) } }
                                else out[n++] = sq or (t shl 6)
                            }
                        }
                        dc += 2
                    }
                }
                2 -> for (t in Tabs.knight[sq]) { val x = b[t]; if (x == 0) { if (!caps) out[n++] = sq or (t shl 6) } else if ((x > 0) != w) out[n++] = sq or (t shl 6) }
                6 -> {
                    for (t in Tabs.king[sq]) { val x = b[t]; if (x == 0) { if (!caps) out[n++] = sq or (t shl 6) } else if ((x > 0) != w) out[n++] = sq or (t shl 6) }
                    if (!caps) {
                        val home = if (w) 60 else 4
                        if (sq == home && !attacked(sq, !w)) {
                            val ks = if (w) 1 else 4; val qs = if (w) 2 else 8
                            if (castle and ks != 0 && b[home + 1] == 0 && b[home + 2] == 0 && b[home + 3] == 4 * s && !attacked(home + 1, !w) && !attacked(home + 2, !w)) out[n++] = sq or ((home + 2) shl 6)
                            if (castle and qs != 0 && b[home - 1] == 0 && b[home - 2] == 0 && b[home - 3] == 0 && b[home - 4] == 4 * s && !attacked(home - 1, !w) && !attacked(home - 2, !w)) out[n++] = sq or ((home - 2) shl 6)
                        }
                    }
                }
                else -> {
                    val from = if (a == 4) 0 else if (a == 3) 4 else 0
                    val to = if (a == 4) 3 else if (a == 3) 7 else 7
                    for (d in from..to) for (t in Tabs.rays[d][sq]) {
                        val x = b[t]
                        if (x == 0) { if (!caps) out[n++] = sq or (t shl 6) } else { if ((x > 0) != w) out[n++] = sq or (t shl 6); break }
                    }
                }
            }
        }
        return n
    }

    fun make(m: Int) {
        val from = m and 63; val to = (m shr 6) and 63; val promo = (m shr 12) and 7
        val pc = b[from]; val w = pc > 0; val a = if (pc > 0) pc else -pc
        uCastle[sp] = castle; uEp[sp] = ep; uHalf[sp] = half; uHash[sp] = hash; uEpK[sp] = epK
        var h = hash xor epK
        var cap = b[to]
        var epCap = false
        if (a == 1 && (from and 7) != (to and 7) && cap == 0) {
            val cs = if (w) to + 8 else to - 8
            cap = b[cs]; b[cs] = 0; h = h xor Engine.z[cap + 6][cs]; epCap = true
        } else if (cap != 0) h = h xor Engine.z[cap + 6][to]
        uCap[sp] = cap; uEpCap[sp] = epCap
        half = if (a == 1 || cap != 0) 0 else half + 1
        h = h xor Engine.z[pc + 6][from]
        b[from] = 0
        val placed = if (promo != 0) (if (w) promo else -promo) else pc
        b[to] = placed
        h = h xor Engine.z[placed + 6][to]
        if (a == 6) {
            ksq[if (w) 0 else 1] = to
            if (to - from == 2 || from - to == 2) {
                val rf: Int; val rt: Int
                if (to > from) { rf = from + 3; rt = from + 1 } else { rf = from - 4; rt = from - 1 }
                val r = b[rf]; b[rf] = 0; b[rt] = r
                h = h xor Engine.z[r + 6][rf] xor Engine.z[r + 6][rt]
            }
        }
        val oc = castle
        castle = castle and Tabs.cmask[from] and Tabs.cmask[to]
        h = h xor Engine.zCastle[oc and 15] xor Engine.zCastle[castle and 15]
        ep = if (a == 1 && (to - from == 16 || from - to == 16)) (from + to) / 2 else -1
        white = !white
        h = h xor Engine.zSide
        epK = Engine.zEp[Engine.epEffective(b, white, ep)]
        h = h xor epK
        hash = h
        keys[nKeys++] = h
        sp++
    }

    fun unmake(m: Int) {
        sp--; nKeys--
        white = !white
        val from = m and 63; val to = (m shr 6) and 63; val promo = (m shr 12) and 7
        val w = white
        val placed = b[to]
        val pc = if (promo != 0) (if (w) 1 else -1) else placed
        b[from] = pc
        val cap = uCap[sp]
        if (uEpCap[sp]) { b[to] = 0; b[if (w) to + 8 else to - 8] = cap } else b[to] = cap
        if ((if (pc > 0) pc else -pc) == 6) {
            ksq[if (w) 0 else 1] = from
            if (to - from == 2) { b[from + 3] = b[from + 1]; b[from + 1] = 0 }
            else if (from - to == 2) { b[from - 4] = b[from - 1]; b[from - 1] = 0 }
        }
        castle = uCastle[sp]; ep = uEp[sp]; half = uHalf[sp]; hash = uHash[sp]; epK = uEpK[sp]
    }

    fun makeNull() {
        uEp[sp] = ep; uHash[sp] = hash; uEpK[sp] = epK; uHalf[sp] = half
        var h = hash xor epK
        ep = -1; white = !white; half++
        h = h xor Engine.zSide
        epK = Engine.zEp[64]; h = h xor epK
        hash = h
        keys[nKeys++] = h
        sp++
    }

    fun unmakeNull() { sp--; nKeys--; white = !white; ep = uEp[sp]; hash = uHash[sp]; epK = uEpK[sp]; half = uHalf[sp] }

    fun hasPieces(w: Boolean): Boolean {
        for (i in 0..63) { val p = b[i]; if (p != 0 && (p > 0) == w) { val a = if (p > 0) p else -p; if (a in 2..5) return true } }
        return false
    }

    // ---- evaluation, in centipawns from the side to move's point of view ----

    private fun centerDist(sq: Int): Int { val r = sq shr 3; val c = sq and 7; return (Math.abs(2 * r - 7) + Math.abs(2 * c - 7)) / 2 }

    fun eval(): Int {
        var mg = 0; var eg = 0; var phase = 0
        var npW = 0; var npB = 0; var pawnsW = 0; var pawnsB = 0
        var bishW = 0; var bishB = 0; var rq = 0; var pieces = 0
        java.util.Arrays.fill(pwF, 0); java.util.Arrays.fill(pbF, 0)
        for (i in 0..63) {
            val pc = b[i]
            if (pc == 0) continue
            val w = pc > 0; val a = if (w) pc else -pc
            val idx = if (w) i else i xor 56
            var vm = Tabs.VAL[a] + Tabs.MG[a][idx]
            var ve = Tabs.VAL[a] + Tabs.EG[a][idx]
            val r = i shr 3; val c = i and 7
            pieces++
            when (a) {
                1 -> { if (w) { pawnsW++; pwF[c]++ } else { pawnsB++; pbF[c]++ } }
                2 -> { phase += 1; if (w) npW += 320 else npB += 320
                    var m = 0; for (t in Tabs.knight[i]) { val x = b[t]; if (x == 0 || (x > 0) != w) m++ }
                    vm += (m - 4) * 4; ve += (m - 4) * 4 }
                3, 4, 5 -> {
                    if (a == 3) { phase += 1; if (w) { npW += 330; bishW++ } else { npB += 330; bishB++ } }
                    else if (a == 4) { phase += 2; rq++; if (w) npW += 500 else npB += 500 }
                    else { phase += 4; rq++; if (w) npW += 900 else npB += 900 }
                    var m = 0
                    val from = if (a == 4) 0 else if (a == 3) 4 else 0
                    val to = if (a == 4) 3 else if (a == 3) 7 else 7
                    for (d in from..to) for (t in Tabs.rays[d][i]) { val x = b[t]; if (x == 0) m++ else { if ((x > 0) != w) m++; break } }
                    val wgt = if (a == 3) 3 else if (a == 4) 2 else 1
                    vm += (m - (if (a == 3) 6 else if (a == 4) 7 else 13)) * wgt
                    ve += (m - (if (a == 3) 6 else if (a == 4) 7 else 13)) * (if (a == 4) 4 else wgt)
                }
                6 -> {
                    // pawns in front of the king
                    val dir = if (w) -1 else 1
                    val mine = if (w) 1 else -1
                    var sh = 0
                    for (dc in -1..1) { val cc = c + dc; if (cc !in 0..7) continue
                        val r1 = r + dir; val r2 = r + 2 * dir
                        if (r1 in 0..7 && b[r1 * 8 + cc] == mine) sh += 12 else if (r2 in 0..7 && b[r2 * 8 + cc] == mine) sh += 6 }
                    vm += sh
                }
            }
            if (w) { mg += vm; eg += ve } else { mg -= vm; eg -= ve }
        }
        if (phase > 24) phase = 24
        // pawn structure and rooks
        for (i in 0..63) {
            val pc = b[i]
            if (pc != 1 && pc != -1 && pc != 4 && pc != -4) continue
            val r = i shr 3; val c = i and 7
            if (pc == 1 || pc == -1) {
                val w = pc == 1
                val own = if (w) pwF else pbF
                val ownNb = (if (c > 0) own[c - 1] else 0) + (if (c < 7) own[c + 1] else 0)
                var sm = 0; var se = 0
                if (own[c] > 1) { sm -= 6; se -= 10 }
                if (ownNb == 0) { sm -= 12; se -= 16 }
                // passed: no enemy pawn ahead on this or a neighbouring file
                var passed = true
                val enemy = if (w) -1 else 1
                var rr = if (w) r - 1 else r + 1
                while (rr in 0..7 && passed) {
                    for (cc in maxOf(0, c - 1)..minOf(7, c + 1)) if (b[rr * 8 + cc] == enemy) { passed = false; break }
                    rr += if (w) -1 else 1
                }
                if (passed) {
                    val rel = if (w) 7 - r else r
                    val bonus = PASS[rel]
                    sm += bonus; se += bonus * 2
                }
                if (w) { mg += sm; eg += se } else { mg -= sm; eg -= se }
            } else {
                val w = pc == 4
                val ownP = if (w) pwF[c] else pbF[c]
                val enP = if (w) pbF[c] else pwF[c]
                var bonus = 0
                if (ownP == 0) bonus = if (enP == 0) 20 else 10
                if (w) { mg += bonus; eg += bonus / 2 } else { mg -= bonus; eg -= bonus / 2 }
            }
        }
        if (bishW >= 2) { mg += 30; eg += 50 }
        if (bishB >= 2) { mg -= 30; eg -= 50 }
        if (pawnsW == 0 && pawnsB == 0 && rq == 0 && Chess.insufficient(b)) return 0
        // a lone king against a big lead: drive it to the edge and bring the king over
        val matW = npW + pawnsW * 100; val matB = npB + pawnsB * 100
        if (matW - matB >= 400 && npB <= 330 && pawnsB == 0) eg += mopUp(ksq[1], ksq[0])
        else if (matB - matW >= 400 && npW <= 330 && pawnsW == 0) eg -= mopUp(ksq[0], ksq[1])
        val s = (mg * phase + eg * (24 - phase)) / 24
        return (if (white) s else -s) + 10
    }

    private fun mopUp(loserKing: Int, winnerKing: Int): Int {
        val d = Math.abs((loserKing shr 3) - (winnerKing shr 3)) + Math.abs((loserKing and 7) - (winnerKing and 7))
        return centerDist(loserKing) * 12 + (14 - d) * 5
    }

    companion object { private val PASS = intArrayOf(0, 5, 10, 20, 35, 60, 100, 0) }
}
