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
}
