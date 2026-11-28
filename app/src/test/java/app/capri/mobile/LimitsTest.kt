package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class LimitsTest {
    @Test fun hold() {
        assertEquals(7000L, Limits.holdMs(10, 1)); assertEquals(22000L, Limits.holdMs(900, 1))
        assertTrue(Limits.holdMs(100, 0) < Limits.holdMs(100, 1)); assertTrue(Limits.holdMs(100, 2) > Limits.holdMs(100, 1))
        assertEquals(4200L, Limits.holdMs(0, 0)); assertEquals(7000L, Limits.holdMs(-5, 9))
    }
    @Test fun memory() { assertEquals(8, Limits.memory(0, false)); assertEquals(20, Limits.memory(1, false)); assertEquals(32, Limits.memory(2, false)); assertEquals(6, Limits.memory(2, true)); assertEquals(20, Limits.memory(7, false)) }
    @Test fun replies() {
        assertEquals("", Limits.replyHint(1)); assertTrue(Limits.replyHint(0).contains("single short")); assertTrue(Limits.replyHint(3).contains("no length limit"))
    }
    @Test fun popIns() {
        assertEquals(Pair(20 * 60000L, 40 * 60000L), Limits.popRangeMs(1, 0, 0, 30)); assertEquals(Pair(8 * 60000L, 20 * 60000L), Limits.popRangeMs(2, 0, 0, 30))
        assertEquals(Pair(3 * 60000L, 8 * 60000L), Limits.popRangeMs(3, 0, 0, 30)); assertEquals(Pair(1200000L, 2700000L), Limits.popRangeMs(0, 0, 0, 30))
        assertEquals(Pair(12 * 60000L, 25 * 60000L), Limits.popRangeMs(3, 25, 12, 30)); assertEquals(Pair(12 * 60000L, 12 * 60000L), Limits.popRangeMs(0, 12, 12, 30))
    }
}
