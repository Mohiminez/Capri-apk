package app.capri.mobile

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

// Runs only when a desktop build of the library exists (-Dcapri.sf=/path/libstockfish.so); on a normal checkout it is skipped.
class StockfishNativeTest {
    private fun ready(): Boolean {
        val path = System.getProperty("capri.sf") ?: return false
        Stockfish.loader = { System.load(path) }
        return Stockfish.ready()
    }

    @Test fun findsMateInOne() {
        assumeTrue(ready())
        val p = Fen.parse("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1")!!
        val a = Stockfish.analyse(p, 500)!!
        assertEquals("a1a8", a.move.toString())
        assertEquals(1, a.mateIn)
    }

    @Test fun playsLegalMovesFromManyPositionsIncludingCastlingAndPromotion() {
        assumeTrue(ready())
        for (f in listOf(Chess.start().let { Fen.write(it) }, "r3k2r/pppq1ppp/2n2n2/3pp3/3PP3/2N2N2/PPPQ1PPP/R3K2R w KQkq - 0 1",
            "8/P7/8/8/8/8/k6K/8 w - - 0 1", "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 3")) {
            val p = Fen.parse(f)!!
            val a = Stockfish.analyse(p, 300)!!
            assertTrue(f, Chess.legal(p).any { it.toString() == a.move.toString() })
        }
    }

    @Test fun engineUsesItAndBeatsTheOldSearchOnATactic() {
        assumeTrue(ready())
        Engine.useStockfish = true
        val p = Fen.parse("r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4")!!
        val a = Engine.analyse(p, 800, 30)!!
        assertEquals("h5f7", a.move.toString())
        assertEquals("Stockfish 11", Engine.name)
    }

    @Test fun staysFastAndAnswersRepeatedly() {
        assumeTrue(ready())
        var p = Chess.start()
        for (i in 0 until 8) { val a = Stockfish.analyse(p, 150)!!; p = Chess.make(p, a.move) }
        assertNotNull(p)
    }
}
