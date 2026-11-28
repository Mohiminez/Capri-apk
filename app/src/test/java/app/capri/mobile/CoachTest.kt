package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class CoachTest {
    @Test fun nudgeNamesThePieceAndSquareOnly() {
        val s = Coach.nudge(2, 6, false)       // a knight on g1
        assertTrue(s.contains("knight") && s.contains("g1")); assertFalse(s.contains("f3"))
        assertTrue(Coach.nudge(2, 6, true).contains("g1"))
    }
    @Test fun squareNames() { assertEquals("a1", Coach.squareName(0)); assertEquals("h8", Coach.squareName(63)); assertEquals("e4", Coach.squareName(28)) }
    @Test fun drawOnlyWhenNotBetterAndNotTooEarly() {
        assertTrue(Coach.acceptsDraw(0, 40)); assertTrue(Coach.acceptsDraw(-300, 40))
        assertFalse(Coach.acceptsDraw(120, 40)); assertFalse(Coach.acceptsDraw(0, 8))
    }
    @Test fun whiteViewFlipsBlackToMove() {
        val w = Coach.whiteView(intArrayOf(50, 50, 99999), booleanArrayOf(true, false, false))
        assertArrayEqualsList(listOf(50, -50, -1000), w.toList())
    }
    private fun assertArrayEqualsList(a: List<Int>, b: List<Int>) = assertEquals(a, b)
}
