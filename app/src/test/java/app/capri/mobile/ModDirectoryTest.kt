package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class ModDirectoryTest {
    private val json = """{"format":1,"mods":[
      {"folder":"arasaka","file":"arasaka.capri-skin","name":"Arasaka","kind":"skin","author":"Issei","about":"black and red"},
      {"folder":"colors-royal","file":"colors-royal.capri-mod","name":"Colors: Royal","kind":"skin","author":"Avery","about":"purple"},
      {"folder":"dice","file":"dice.capri-code","name":"Dice","kind":"code","author":"M","about":"3d dice"},
      {"folder":"../evil","file":"x.capri-skin","name":"Evil","kind":"skin"},
      {"folder":"ok","file":"a/b.capri-skin","name":"Evil2","kind":"skin"}]}"""

    @Test fun keepsPhoneKindsAndCountsTheRest() {
        val p = ModDirectory.parse(json)
        assertEquals(listOf("Arasaka", "Colors: Royal"), p.usable.map { it.name })
        assertEquals(1, p.hidden)
    }

    @Test fun urlsAreBuiltFromSafeNames() {
        val l = ModDirectory.parse(json).usable[0]
        assertEquals("https://raw.githubusercontent.com/lachydotmcg/capri-mods/HEAD/mods/arasaka/arasaka.capri-skin", l.url)
    }

    @Test fun filtersBySearchAndKind() {
        val all = ModDirectory.parse(json).usable
        assertEquals(1, ModDirectory.filter(all, "purple", "").size)
        assertEquals(1, ModDirectory.filter(all, "", "skin").size)
        assertEquals(2, ModDirectory.filter(all, "", "").size)
        assertEquals(0, ModDirectory.filter(all, "zzz", "").size)
    }

    @Test fun badJsonIsEmpty() { assertEquals(0, ModDirectory.parse("nope").usable.size) }
    @Test fun extensionDecidesKind() { assertEquals("settings", ModDirectory.parse(json).usable[1].kind) }
}
