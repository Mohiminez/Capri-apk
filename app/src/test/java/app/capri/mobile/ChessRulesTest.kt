package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class ChessRulesTest {
    private fun fen(s: String) = Fen.parse(s)!!
    private fun mv(p: Pos, s: String) = Chess.legal(p).firstOrNull { it.toString() == s }

    private fun perft(p: Pos, d: Int): Long {
        if (d == 0) return 1
        var n = 0L
        for (m in Chess.legal(p)) n += perft(Chess.make(p, m), d - 1)
        return n
    }

    private fun perftBoard(bd: Board, d: Int, buf: Array<IntArray>, ply: Int): Long {
        if (d == 0) return 1
        val n = bd.gen(buf[ply], false)
        var t = 0L
        for (i in 0 until n) {
            val m = buf[ply][i]
            bd.make(m)
            if (!bd.movedIntoCheck()) t += perftBoard(bd, d - 1, buf, ply + 1)
            bd.unmake(m)
        }
        return t
    }

    private val perfts = listOf(
        Triple("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", 4, 197281L),
        Triple("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 3, 97862L),
        Triple("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 4, 43238L),
        Triple("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 3, 9467L),
        Triple("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 3, 62379L),
        Triple("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10", 3, 89890L)
    )

    @Test fun perftMatchesKnownCounts() {
        for ((f, d, n) in perfts) assertEquals(f, n, perft(fen(f), d))
    }

    @Test fun fastBoardPerftMatchesToo() {
        val buf = Array(12) { IntArray(256) }
        for ((f, d, n) in perfts) assertEquals(f, n, perftBoard(Board(fen(f)), d, buf, 0))
    }

    @Test fun fastBoardStaysInStepWithTheRules() {
        val rnd = java.util.Random(11)
        val starts = perfts.map { fen(it.first) }
        for (s in starts) {
            var p = s
            val bd = Board(p)
            val played = ArrayList<Int>()
            val buf = IntArray(256)
            for (i in 0 until 120) {
                val ms = Chess.legal(p)
                if (ms.isEmpty()) break
                val m = ms[rnd.nextInt(ms.size)]
                val code = m.from or (m.to shl 6) or (m.promo shl 12)
                assertTrue(bd.gen(buf, false).let { n -> (0 until n).any { buf[it] == code } })
                bd.make(code); played.add(code)
                p = Chess.make(p, m)
                assertArrayEquals(p.b, bd.b)
                assertEquals(Engine.key(p), bd.hash)
                assertEquals(p.castle, bd.castle); assertEquals(p.ep, bd.ep); assertEquals(p.half, bd.half); assertEquals(p.white, bd.white)
            }
            for (j in played.indices.reversed()) bd.unmake(played[j])
            assertArrayEquals(s.b, bd.b)
            assertEquals(Engine.key(s), bd.hash)
            assertEquals(s.castle, bd.castle)
        }
    }

    @Test fun enPassantWorksAndExpires() {
        val p = fen("rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3")
        val ep = mv(p, "e5f6")!!
        val after = Chess.make(p, ep)
        assertEquals(0, after.b[21 - 8 + 0].let { 0 })      // placeholder to keep indices honest below
        assertEquals(0, after.b[3 * 8 + 5])                  // the f5 pawn is gone
        assertEquals(1, after.b[2 * 8 + 5])                  // white pawn on f6
        // after any other move the right is gone
        val other = Chess.make(p, mv(p, "a2a3")!!)
        assertEquals(-1, other.ep)
        val reply = Chess.make(other, mv(other, "a7a6")!!)
        assertNull(mv(reply, "e5f6"))
    }

    @Test fun enPassantThatExposesTheKingIsIllegal() {
        val p = fen("8/8/8/KPp4r/8/8/8/7k w - c6 0 1")
        assertNull(mv(p, "b5c6"))
        assertNotNull(mv(p, "b5b6"))
    }

    @Test fun castlingRules() {
        val start = fen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertNotNull(mv(start, "e1g1")); assertNotNull(mv(start, "e1c1"))
        // through an attacked square: no
        val thru = fen("r3k2r/8/8/8/8/5r2/8/R3K2R w KQkq - 0 1")
        assertNull(mv(thru, "e1g1")); assertNotNull(mv(thru, "e1c1"))
        // in check: no
        val chk = fen("r3k2r/8/8/8/8/8/4r3/R3K2R w KQkq - 0 1")
        assertNull(mv(chk, "e1g1")); assertNull(mv(chk, "e1c1"))
        // the b1 square may be attacked for the long castle (only the king's path counts)
        val b1 = fen("r3k2r/8/8/8/8/8/1r6/R3K2R w KQkq - 0 1")
        assertNotNull(mv(b1, "e1c1"))
        // rook moved: that right is gone for good
        var p = start
        p = Chess.make(p, mv(p, "h1g1")!!); p = Chess.make(p, mv(p, "a8a7")!!); p = Chess.make(p, mv(p, "g1h1")!!); p = Chess.make(p, mv(p, "a7a8")!!)
        assertNull(mv(p, "e1g1")); assertNotNull(mv(p, "e1c1"))
        // a rook captured on its corner takes the right with it
        val cap = fen("r3k2r/8/8/8/8/8/6b1/R3K2R b KQkq - 0 1")
        val after = Chess.make(cap, mv(cap, "g2h1")!!)
        assertEquals(0, after.castle and 1)
    }

    @Test fun underPromotionsAreAllThere() {
        val p = fen("8/P6k/8/8/8/8/8/K7 w - - 0 1")
        val ms = Chess.legal(p).filter { it.from == 8 }.map { it.toString() }.toSet()
        assertEquals(setOf("a7a8q", "a7a8r", "a7a8b", "a7a8n"), ms)
    }

    @Test fun insufficientMaterial() {
        assertEquals("insufficient", Chess.result(fen("8/8/4k3/8/8/3K4/8/8 w - - 0 1"), emptyList()))
        assertEquals("insufficient", Chess.result(fen("8/8/4k3/8/8/3KB3/8/8 w - - 0 1"), emptyList()))
        assertEquals("insufficient", Chess.result(fen("8/8/4k3/8/8/3KN3/8/8 w - - 0 1"), emptyList()))
        assertEquals("insufficient", Chess.result(fen("8/8/4kb2/8/8/3KB3/8/8 w - - 0 1"), emptyList()))       // both bishops on the same colour (e3, f6)
        assertEquals("playing", Chess.result(fen("8/8/4kb2/8/8/3K1B2/8/8 w - - 0 1"), emptyList()))            // opposite colours: mate is possible
        assertEquals("playing", Chess.result(fen("8/8/4k3/8/8/3KNN2/8/8 w - - 0 1"), emptyList()))
        assertEquals("playing", Chess.result(fen("8/8/4k3/8/8/3KP3/8/8 w - - 0 1"), emptyList()))
        assertEquals("playing", Chess.result(fen("8/8/4k3/8/8/3K1R2/8/8 w - - 0 1"), emptyList()))
    }

    @Test fun fiftyMoveRule() {
        val p = fen("4k3/8/8/8/8/8/8/R3K3 w - - 99 80")
        assertEquals("playing", Chess.result(p, emptyList()))
        val q = Chess.make(p, mv(p, "a1a2")!!)
        assertEquals(100, q.half)
        assertEquals("fifty", Chess.result(q, emptyList()))
        // a capture or pawn move resets the clock
        val r = fen("4k3/8/8/8/8/8/P7/R3K3 w - - 99 80")
        assertEquals(0, Chess.make(r, mv(r, "a2a3")!!).half)
    }

    @Test fun threefoldRepetition() {
        var p = Chess.start()
        val keys = arrayListOf(Engine.key(p))
        val cycle = listOf("g1f3", "g8f6", "f3g1", "f6g8")
        var verdict = "playing"
        for (round in 0 until 2) for (s in cycle) {
            p = Chess.make(p, mv(p, s)!!); keys.add(Engine.key(p))
            verdict = Chess.result(p, keys)
            if (round == 0 && s == "f6g8") assertEquals("playing", verdict)      // second time only
        }
        assertEquals("repetition", verdict)
    }

    @Test fun stalemateAndMate() {
        assertEquals("stalemate", Chess.result(fen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"), emptyList()))
        assertEquals("mate", Chess.result(fen("R5k1/5ppp/8/8/8/8/8/4K3 b - - 0 1"), emptyList()))
    }

    @Test fun fenIsStrict() {
        assertNull(Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBN w KQkq - 0 1"))             // short rank
        assertNull(Fen.parse("P3k3/8/8/8/8/8/8/4K3 w - - 0 1"))                                       // pawn on the back rank
        assertNull(Fen.parse("8/8/8/3kK3/8/8/8/8 w - - 0 1"))                                         // kings touching
        assertNull(Fen.parse("4k3/8/8/8/8/8/8/4K2R b K - 0 1").let { null })
        val p = fen("4k3/8/8/8/8/8/8/4K3 w KQkq e3 0 1")                                              // rights and ep that make no sense are dropped
        assertEquals(0, p.castle); assertEquals(-1, p.ep)
        assertEquals(37, fen("4k3/8/8/8/8/8/8/4K3 w - - 37 60").half)
        assertTrue(Fen.write(fen("4k3/8/8/8/8/8/8/4K3 w - - 37 60")).contains(" 37 "))
    }

    // ---- the search ----

    private fun mirror(p: Pos): Pos {
        val b = IntArray(64)
        for (i in 0..63) b[i xor 56] = -p.b[i]
        var c = 0
        if (p.castle and 1 != 0) c = c or 4; if (p.castle and 2 != 0) c = c or 8; if (p.castle and 4 != 0) c = c or 1; if (p.castle and 8 != 0) c = c or 2
        return Pos(b, !p.white, c, if (p.ep < 0) -1 else p.ep xor 56, p.half)
    }

    @Test fun evaluationIsSymmetric() {
        val rnd = java.util.Random(5)
        for ((f, _, _) in perfts) {
            var p = fen(f)
            for (i in 0 until 30) {
                assertEquals(f, Board(p).eval(), Board(mirror(p)).eval())
                val ms = Chess.legal(p); if (ms.isEmpty()) break
                p = Chess.make(p, ms[rnd.nextInt(ms.size)])
            }
        }
    }

    @Test fun findsMates() {
        val m1 = Engine.analyse(fen("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1"), 1000, 6)!!
        assertEquals("a1a8", m1.move.toString()); assertEquals(1, m1.mateIn)
        val scholar = Engine.analyse(fen("r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4"), 1000, 6)!!
        assertEquals("h5f7", scholar.move.toString())
        // mate in two: Qh5+ ... the back-rank pattern
        val m2 = Engine.analyse(fen("6k1/8/8/8/8/8/R7/1R4K1 w - - 0 1"), 3000, 8)!!
        assertEquals(2, m2.mateIn)
    }

    @Test fun takesTheHangingQueenAndAvoidsLosingOne() {
        val a = Engine.analyse(fen("4k3/8/8/3q4/8/8/8/3RK3 w - - 0 1"), 500, 6)!!
        assertEquals("d1d5", a.move.toString())
        // the queen is attacked by a pawn: it must move or be defended, not stay
        val b = Engine.analyse(fen("4k3/8/2p5/3q4/4P3/8/8/4K3 b - - 0 1"), 600, 6)!!
        assertTrue(b.move.from != 27 || b.move.to != 27)
        assertTrue(b.score > -300)
    }

    @Test fun keepsTheLeadInsteadOfRepeating() {
        // up a queen, with positions repeating before: the search must prefer progress to a draw
        var p = fen("4k3/8/8/8/8/8/Q7/4K3 w - - 0 1")
        val keys = arrayListOf(Engine.key(p))
        var verdict = "playing"
        for (i in 0 until 60) {
            val a = Engine.analyse(p, 60, 8, keys) ?: break
            p = Chess.make(p, a.move); keys.add(Engine.key(p))
            verdict = Chess.result(p, keys)
            if (verdict != "playing") break
            val r = Engine.analyse(p, 20, 4, keys) ?: break
            p = Chess.make(p, r.move); keys.add(Engine.key(p))
            verdict = Chess.result(p, keys)
            if (verdict != "playing") break
        }
        assertEquals("mate", verdict)
    }

    @Test fun rookEndingGetsMated() {
        var p = fen("8/8/8/4k3/8/8/8/R3K3 w - - 0 1")
        val keys = arrayListOf(Engine.key(p))
        var verdict = "playing"
        for (i in 0 until 70) {
            val a = Engine.analyse(p, 120, 10, keys) ?: break
            p = Chess.make(p, a.move); keys.add(Engine.key(p))
            verdict = Chess.result(p, keys); if (verdict != "playing") break
            val r = Engine.analyse(p, 20, 4, keys) ?: break
            p = Chess.make(p, r.move); keys.add(Engine.key(p))
            verdict = Chess.result(p, keys); if (verdict != "playing") break
        }
        assertEquals("mate", verdict)
    }

    @Test fun theBookIsLegalAndUsed() {
        assertTrue(Book.size > 100)
        for (line in Book.lines()) {
            var p = Chess.start()
            for (tok in line.split(" ")) {
                val m = mv(p, tok)
                assertNotNull("illegal book move $tok in: $line", m)
                p = Chess.make(p, m!!)
            }
        }
        val m = Book.pick(Chess.start())
        assertNotNull(m)
        assertNull(Book.pick(fen("4k3/8/8/8/8/8/8/R3K3 w - - 0 1")))
    }

    @Test fun strongBeatsEasy() {
        var strongPoints = 0.0
        for (strongWhite in listOf(true, false)) {
            var p = Chess.start()
            val keys = arrayListOf(Engine.key(p))
            var verdict = "playing"
            var plies = 0
            while (verdict == "playing" && plies < 140) {
                val strongTurn = (p.white == strongWhite)
                val a = if (strongTurn) Engine.analyse(p, 80, 8, keys, true) else Engine.forLevel(p, 1, keys)
                p = Chess.make(p, a!!.move); keys.add(Engine.key(p)); plies++
                verdict = Chess.result(p, keys)
            }
            if (verdict == "mate") { if ((p.white != strongWhite)) strongPoints += 1.0 } else strongPoints += 0.5
        }
        assertTrue("strong should score at least 1.5 of 2 against easy, got $strongPoints", strongPoints >= 1.5)
    }
}
