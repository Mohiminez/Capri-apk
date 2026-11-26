package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class AutoModesTest {
    private fun inp(h: Int = 14, b: Int = 80, c: Boolean = false, app: String = "") = AutoModes.Input(h, b, c, app)

    @Test fun nightWindowWrapsMidnight() {
        assertTrue(AutoModes.inWindow(23, 23, 6)); assertTrue(AutoModes.inWindow(2, 23, 6)); assertFalse(AutoModes.inWindow(6, 23, 6)); assertFalse(AutoModes.inWindow(14, 23, 6))
        assertFalse(AutoModes.inWindow(5, 5, 5))
    }

    @Test fun priorities() {
        assertEquals("drive", AutoModes.wanted(inp(app = "com.google.android.apps.maps")))
        assertEquals("meeting", AutoModes.wanted(inp(h = 2, b = 5, app = "us.zoom.videomeetings")))
        assertEquals("battery", AutoModes.wanted(inp(h = 2, b = 10)))
        assertEquals("night", AutoModes.wanted(inp(h = 2, b = 10, c = true)))
        assertNull(AutoModes.wanted(inp()))
        assertNull(AutoModes.wanted(inp(b = -1, h = 14)))
    }

    @Test fun switchesAndRestores() {
        val a = AutoModes.step("normal", "", "drive"); assertEquals("drive", a.apply)
        assertNull(AutoModes.step("drive", "drive", "drive").apply)
        val back = AutoModes.step("drive", "drive", null); assertTrue(back.restore)
        assertEquals("night", AutoModes.step("drive", "drive", "night").apply)
    }

    @Test fun neverFightsYourOwnChoice() {
        // you chose Party by hand while she had switched to Night: she lets go
        assertTrue(AutoModes.step("party", "night", "night").forget)
        // you are in Game or Meeting yourself: night and battery do not replace it
        assertNull(AutoModes.step("game", "", "night").apply)
        assertNull(AutoModes.step("meeting", "", "battery").apply)
        // already in the wanted mode: nothing to do
        assertNull(AutoModes.step("night", "", "night").apply)
    }

    @Test fun newModesExistAndParse() {
        assertNotNull(Modes.find("drive")); assertNotNull(Modes.find("meeting"))
        assertEquals("drive", Modes.fromPhrase("driving mode"))
        assertEquals("meeting", Modes.fromPhrase("وضع الاجتماع"))
        assertEquals(true, Modes.find("meeting")!!.set["quiet"])
        assertEquals(Modes.all.map { it.id }.size, Modes.all.map { it.id }.toSet().size)
    }
}

class IntroPlanTest {
    private val H = 3600_000L
    @Test fun offAndGlitch() {
        assertNull(IntroPlan.pick("off", 0, 100 * H, 0))
        assertEquals("glitch", IntroPlan.pick("glitch", 99 * H, 100 * H, 0))
    }
    @Test fun cinematicsHaveACooldown() {
        assertEquals("peek", IntroPlan.pick("peek", 0, 100 * H, 5))
        assertEquals("glitch", IntroPlan.pick("peek", 98 * H, 100 * H, 5))
        assertEquals("close", IntroPlan.pick("close", 90 * H, 100 * H, 5))
        assertEquals("peek", IntroPlan.pick("peek", 200 * H, 100 * H, 5)) // clock went backwards: allowed
    }
    @Test fun randomPicksAllThree() {
        val got = (0..5).map { IntroPlan.pick("random", 0, 100 * H, it) }.toSet()
        assertEquals(setOf("peek", "close", "glitch"), got)
        assertEquals("glitch", IntroPlan.pick("random", 99 * H, 100 * H, 0))
        assertEquals("peek", IntroPlan.pick("random", 0, 100 * H, -3))
    }
}
