package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class ChessReactionsTest {
    private fun mv(p: Pos, s: String): Move = Chess.legal(p).first { it.toString() == s }
    private fun play(p: Pos, vararg ms: String): Pos { var q = p; for (s in ms) q = Chess.make(q, mv(q, s)); return q }

    @Test fun capture_and_check_detected() {
        val p = play(Chess.start(), "e2e4", "d7d5")
        assertTrue("capture" in ChessEvents.detect(p, mv(p, "e4d5"), Chess.make(p, mv(p, "e4d5"))))
        val q = play(Chess.start(), "e2e4", "f7f6", "d1h5")
        assertTrue("check" in ChessEvents.detect(play(Chess.start(), "e2e4", "f7f6"), mv(play(Chess.start(), "e2e4", "f7f6"), "d1h5"), q))
    }

    @Test fun mate_has_top_priority() {
        val p = play(Chess.start(), "f2f3", "e7e5", "g2g4")
        val m = mv(p, "d8h4")
        val after = Chess.make(p, m)
        assertEquals("mate", ChessEvents.detect(p, m, after).first())
        val t = ChessTalk(false, java.util.Random(1))
        assertTrue(t.react(p, m, after, true)!!.contains("heckmate"))
    }

    @Test fun castle_detected() {
        val p = play(Chess.start(), "e2e4", "e7e5", "g1f3", "g8f6", "f1c4", "f8c5")
        val m = mv(p, "e1g1")
        assertTrue("castle" in ChessEvents.detect(p, m, Chess.make(p, m)))
    }

    @Test fun no_repeat_until_pool_used() {
        val t = ChessTalk(false, java.util.Random(3))
        val a = t.start(true)!!
        t.reset()
        val seen = HashSet<String>()
        repeat(3) { seen.add(t.start(true)!!) }
        assertEquals(3, seen.size)
        assertNotNull(a)
    }

    @Test fun disabled_event_is_silent_and_arabic_works() {
        val p = play(Chess.start(), "e2e4", "d7d5")
        val m = mv(p, "e4d5")
        val t = ChessTalk(true, java.util.Random(2), setOf("check"))
        assertNull(t.react(p, m, Chess.make(p, m), true))
        t.enabled = setOf("capture")
        assertTrue(t.react(p, m, Chess.make(p, m), true)!!.any { it in '؀'..'ۿ' })
    }
}
