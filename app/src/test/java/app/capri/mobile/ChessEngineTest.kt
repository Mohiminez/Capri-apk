package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class ChessEngineTest {
    @Test fun fenRoundTrip() {
        assertEquals(Fen.START, Fen.write(Fen.parse(Fen.START)!!))
        val p = Fen.parse("r3k2r/pppq1ppp/2n5/3pP3/8/5N2/PPP2PPP/R2QK2R w KQkq d6 0 1")!!
        assertEquals("r3k2r/pppq1ppp/2n5/3pP3/8/5N2/PPP2PPP/R2QK2R w KQkq d6 0 1", Fen.write(p))
        assertNull(Fen.parse("nonsense"))
        assertNull(Fen.parse("8/8/8/8/8/8/8/8 w - - 0 1"))          // no kings
    }
    @Test fun findsMateInOne() {
        val p = Fen.parse("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")!!
        val a = Engine.analyse(p, 1500)!!
        assertEquals("a1a8", a.move.toString())
        assertEquals(1, a.mateIn)
    }
    @Test fun winsAHangingQueen() {
        val p = Fen.parse("rnb1kbnr/pppp1ppp/8/4p3/4P2q/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1")!!
        val a = Engine.analyse(p, 1500)!!
        assertEquals("f3h4", a.move.toString())
    }
    @Test fun rejectsAnIllegalPosition() {
        assertNull(Fen.parse("4k3/8/8/3p4/4Q3/8/8/4K3 w - - 0 1"))   // the side not to move is in check
    }
    @Test fun playsALegalMoveFromTheStart() {
        val a = Engine.analyse(Chess.start(), 600)!!
        assertTrue(Chess.legal(Chess.start()).any { it.toString() == a.move.toString() })
    }
    @Test fun sanNames() {
        val s = Chess.start()
        assertEquals("e4", San.of(s, Chess.legal(s).first { it.toString() == "e2e4" }))
        assertEquals("Nf3", San.of(s, Chess.legal(s).first { it.toString() == "g1f3" }))
        val c = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")!!
        assertEquals("O-O", San.of(c, Chess.legal(c).first { it.toString() == "e1g1" }))
        assertEquals("O-O-O", San.of(c, Chess.legal(c).first { it.toString() == "e1c1" }))
        val pr = Fen.parse("8/P6k/8/8/8/8/8/K7 w - - 0 1")!!
        assertEquals("a8=Q", San.of(pr, Chess.legal(pr).first { it.toString() == "a7a8q" }))
        val dis = Fen.parse("4k3/8/8/8/8/8/4K3/R6R w - - 0 1")!!
        assertEquals("Rad1", San.of(dis, Chess.legal(dis).first { it.toString() == "a1d1" }))
        val mate = Fen.parse("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")!!
        assertEquals("Ra8#", San.of(mate, Chess.legal(mate).first { it.toString() == "a1a8" }))
    }
    @Test fun strongerThanTheQuickSearchOnATactic() {
        // knight fork wins the queen: Nc7+ forks king and rook
        val p = Fen.parse("r3k3/8/8/3N4/8/8/8/4K3 w - - 0 1")!!
        val a = Engine.analyse(p, 1500)!!
        assertEquals("d5c7", a.move.toString())
    }
}

class ChessScreenAskTest {
    @Test fun phrases() {
        assertTrue(QuickAsks.match("what's the best move")!!.contains("[[chess:screen]]") || QuickAsks.match("what is the best move")!!.contains("[[chess:screen]]"))
        assertTrue(QuickAsks.match("help me with this chess game")!!.contains("[[chess:screen]]"))
        assertTrue(QuickAsks.match("play chess")!!.contains("[[chess:open]]"))
    }
}
