package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class ChimesTest {
    @Test fun everySetAndChimeRendersSoftAndShort() {
        for (set in 0 until 6) for (c in 0 until 3) {
            val s = Chimes.render(set, c, 100)
            assertTrue("set $set chime $c", s.size in 10_000..25_000)
            assertTrue(s.any { it.toInt() != 0 })
            assertEquals(0, s.last().toInt())
            assertTrue(Math.abs(s.maxOf { Math.abs(it.toInt()) }) <= 32767)
        }
    }
    @Test fun volumeIsACurve() {
        val a = Chimes.render(0, 1, 100).maxOf { Math.abs(it.toInt()) }; val b = Chimes.render(0, 1, 40).maxOf { Math.abs(it.toInt()) }
        assertTrue(b < a * 0.3 && b > a * 0.2)
        assertEquals(0, Chimes.render(0, 1, 0).maxOf { Math.abs(it.toInt()) })
    }
}
