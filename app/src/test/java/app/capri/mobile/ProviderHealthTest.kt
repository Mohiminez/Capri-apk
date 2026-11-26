package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class ProviderHealthTest {
    @Test fun failedProviderGoesLastThenComesBack() {
        val h = ProviderHealth()
        assertEquals(listOf(0, 1, 2), h.order(listOf(0, 1, 2), 1000))
        h.fail(0, 503, 1000)
        assertEquals(listOf(1, 2, 0), h.order(listOf(0, 1, 2), 2000))
        assertTrue(h.cooling(0, 2000))
        assertEquals(listOf(0, 1, 2), h.order(listOf(0, 1, 2), 40_000))   // 30 s later it is tried first again
        h.fail(0, 503, 1000); h.ok(0)
        assertFalse(h.cooling(0, 2000))
    }

    @Test fun cooldownGrowsAndKeyErrorsWaitLonger() {
        val h = ProviderHealth()
        h.fail(1, 503, 0); assertTrue(h.cooling(1, 29_000)); assertFalse(h.cooling(1, 31_000))
        h.fail(1, 503, 0); assertTrue(h.cooling(1, 100_000))
        h.fail(1, 0, 0); assertTrue(h.cooling(1, 290_000)); assertFalse(h.cooling(1, 310_000))
        h.fail(2, 401, 0); assertTrue(h.cooling(2, 590_000))
    }

    @Test fun nothingIsEverDropped() {
        val h = ProviderHealth()
        h.fail(0, 503, 0); h.fail(1, 503, 0)
        assertEquals(listOf(0, 1), h.order(listOf(0, 1), 1000))
        assertEquals(listOf(2, 0, 1), h.order(listOf(0, 1, 2), 1000))
    }
}
