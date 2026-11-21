package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class ModsTest {
    @Test fun parsesAFullMod() {
        val m = Mods.parse("""{"name":"Pink Storm","author":"me","accent":"#ff7ac6","colors":{"hair":"#FF7AC6","eyes":"#B14CFF"},"personality":"dramatic","nickname":"boss","tone":3,
            "voice":{"speed":120,"glitch":10}}""")!!
        assertEquals("pink-storm", m.id)
        assertEquals("#FF7AC6", m.accent)
        assertEquals(3, m.tone)
        val c = Mods.changes(m)
        assertEquals("#FF7AC6", c["col_hair"]); assertEquals("", c["col_accent"])       // unnamed colours are reset, not left behind
        assertEquals("dramatic", c["modPersona"]); assertEquals("boss", c["nick"])
        assertEquals(120, c["ttsSpeed"]); assertEquals(10, c["voiceGlitch"])
    }

    @Test fun clampsAndFiltersUnsafeStuff() {
        val m = Mods.parse("""{"name":"X","accent":"red","voice":{"speed":999,"robot":-5},"settings":{"size":3,"quiet":true,"k0":"my-api-key","provider":5,"tool_files":true},"tone":9}""")!!
        assertEquals("", m.accent)
        assertEquals(160, m.voice["speed"]); assertEquals(0, m.voice["robot"])
        assertEquals(3, m.tone)
        val c = Mods.changes(m)
        assertTrue(c.containsKey("size")); assertTrue(c.containsKey("quiet"))
        for (bad in listOf("k0", "provider", "tool_files", "voiceKey", "hfToken")) assertFalse(bad, c.containsKey(bad))
    }

    @Test fun rejectsNonMods() {
        assertNull(Mods.parse("hello"))
        assertNull(Mods.parse("""{"accent":"#FFFFFF"}"""))
        assertNull(Mods.parse("[1,2]"))
    }

    @Test fun oldStyleModsStillLoad() {
        val m = Mods.parse("""{"name":"Old","accent":"#00D5FF","personality":"extra","nickname":"n","settings":{"theme":2}}""")!!
        val c = Mods.changes(m)
        assertEquals("#00D5FF", c["modAccent"]); assertEquals(2, c["theme"])
    }

    @Test fun builtInsAreValid() {
        assertTrue(Mods.builtins.size >= 8)
        for (m in Mods.builtins) {
            assertTrue(m.name, Mods.changes(m).isNotEmpty())
            assertEquals(m.id, Mods.slug(m.name))
            for (k in Mods.changes(m).keys) assertFalse(k.startsWith("k") && k.length < 4 && k[1].isDigit())
        }
        assertEquals(Mods.builtins.map { it.id }.size, Mods.builtins.map { it.id }.toSet().size)
    }

    @Test fun templateParses() { assertNotNull(Mods.parse(Mods.template())) }
}
