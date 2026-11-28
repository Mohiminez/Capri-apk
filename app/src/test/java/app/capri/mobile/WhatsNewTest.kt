package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class WhatsNewTest {
    @Test fun showsOnceAfterAnUpdateButNotOnAFreshInstall() {
        assertTrue(WhatsNew.due(0, false)); assertTrue(WhatsNew.due(WhatsNew.VERSION - 1, false))
        assertFalse(WhatsNew.due(WhatsNew.VERSION, false)); assertFalse(WhatsNew.due(0, true))
    }
    @Test fun everyItemHasBothLanguages() {
        for (i in WhatsNew.items) { assertTrue(i.en.length > 20); assertTrue(i.ar.length > 20); assertTrue(i.ar.any { it in '؀'..'ۿ' }) }
        assertTrue(WhatsNew.text(true).contains("•")); assertEquals(WhatsNew.items.size, WhatsNew.text(false).split("\n\n").size)
    }
}
