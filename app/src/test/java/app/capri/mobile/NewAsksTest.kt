package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class NewAsksTest {
    @Test fun wolMacAndPacket() {
        assertTrue(byteArrayOf(0x0A, 0x1B, 0x2C, 0x3D, 0x4E, 0x5F).contentEquals(WakeOnLan.mac("0a:1B-2c.3D 4e:5F")!!))
        assertTrue(byteArrayOf(1, 2, 3, 4, 5, 6).contentEquals(WakeOnLan.mac("010203040506")!!))
        assertNull(WakeOnLan.mac("01:02:03")); assertNull(WakeOnLan.mac("zz:02:03:04:05:06"))
        val p = WakeOnLan.packet(WakeOnLan.mac("01:02:03:04:05:06")!!)
        assertEquals(102, p.size)
        for (i in 0 until 6) assertEquals(0xFF.toByte(), p[i])
        assertEquals(6.toByte(), p[101]); assertEquals(1.toByte(), p[6])
        assertEquals("AA:BB:CC:DD:EE:FF", WakeOnLan.pretty("aa-bb-cc-dd-ee-ff"))
    }

    @Test fun wolAsks() {
        assertTrue(WakeOnLan.isAsk("wake my pc")); assertTrue(WakeOnLan.isAsk("Wake up the computer please")); assertTrue(WakeOnLan.isAsk("شغل الكمبيوتر"))
        assertTrue(WakeOnLan.isAsk("فيقي الكمبيوتر حقي")); assertTrue(WakeOnLan.isAsk("wol"))
        assertFalse(WakeOnLan.isAsk("my pc is slow")); assertFalse(WakeOnLan.isAsk("how do I wake up early"))
    }

    private fun st(ram: Long = 3000, stor: Long = 50000, bat: Int = 80, t: Float = 30f, save: Boolean = false, th: Int = 0) =
        PhoneHealth.Stats(ram, 6000, stor, 128000, bat, false, t, save, th)

    @Test fun healthFindings() {
        assertTrue(PhoneHealth.findings(st(), false).isEmpty())
        assertEquals(1, PhoneHealth.findings(st(t = 45f), false).size)
        assertTrue(PhoneHealth.findings(st(ram = 300), false)[0].contains("Memory"))
        assertTrue(PhoneHealth.findings(st(stor = 1000), true)[0].contains("التخزين"))
        assertTrue(PhoneHealth.findings(st(save = true, bat = 10), false).size == 2)
        assertTrue(PhoneHealth.say(st(t = 46f, ram = 200), false).contains("hot"))
    }

    @Test fun healthAsks() {
        assertTrue(PhoneHealth.isAsk("why is my phone slow")); assertTrue(PhoneHealth.isAsk("ليش جوالي بطيء")); assertTrue(PhoneHealth.isAsk("جوالي يهنگ"))
        assertFalse(PhoneHealth.isAsk("play slow songs")); assertFalse(PhoneHealth.isAsk("my friend is slow"))
    }

    @Test fun recallKeepsPinnedAndRelevantWithinBudget() {
        val notes = (1..30).joinToString("\n") { "- fact number $it about topic$it " + "x".repeat(60) } + "\n* always call him Sam"
        val r = Memory.recall(notes, "tell me about topic7", 600).map { it.text }
        assertTrue(r.any { it.contains("Sam") }); assertTrue(r.any { it.contains("topic7 ") })
        assertTrue(r.sumOf { it.length + 4 } <= 600)
        // small memory: everything comes back unchanged
        assertEquals(2, Memory.recall("- a\n* b", "anything").size)
    }
}
