package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class ListenTest {
    @Test fun nameIsCutOut() {
        assertEquals("open whatsapp", Listen.afterName("hey Capri open whatsapp"))
        assertEquals("", Listen.afterName("كابري"))
        assertEquals("افتح واتساب", Listen.afterName("اهلا كابريا افتح واتساب").let { it?.trim() })
        assertNull(Listen.afterName("open whatsapp"))
        assertEquals("what time is it", Listen.afterName("Capri, what time is it?")?.trimEnd('?'))
    }
    @Test fun errorsAreClassified() {
        assertTrue(Listen.retryable(Listen.BUSY)); assertTrue(Listen.retryable(Listen.CLIENT))
        assertFalse(Listen.retryable(Listen.NO_MATCH)); assertTrue(Listen.silent(Listen.SPEECH_TIMEOUT))
        for (c in 1..13) { assertTrue(Listen.hint(c, true).isNotBlank()); assertTrue(Listen.hint(c, false).isNotBlank()) }
        assertTrue(Listen.hint(Listen.LANG_UNAVAILABLE, false).contains("language"))
    }
}

class ShieldTest {
    @Test fun hidesPrivateThings() {
        val s = Shield.redact("Card 4242 4242 4242 4242 mail me at a.b@c.com call +964 770 123 4567 your code is 482913")
        assertFalse(s.contains("4242")); assertFalse(s.contains("a.b@c.com")); assertFalse(s.contains("770 123")); assertFalse(s.contains("482913"))
        assertTrue(s.contains("[card]") && s.contains("[email]"))
    }
    @Test fun leavesNormalTextAlone() {
        assertEquals("Open Settings and tap Wi-Fi 5 GHz", Shield.redact("Open Settings and tap Wi-Fi 5 GHz"))
        assertEquals("Battery 85%", Shield.redact("Battery 85%"))
    }
}

class SkillsTest {
    @Test fun plainAndPlaceholder() {
        val sk = "good morning => Morning! [[weather:Baghdad]]\nsearch {x} => Looking. [[search:{x}]]\n# comment\nbad line"
        assertEquals("Morning! [[weather:Baghdad]]", Skills.match(sk, "Good Morning!"))
        assertEquals("Looking. [[search:cheap flights]]", Skills.match(sk, "search cheap flights"))
        assertNull(Skills.match(sk, "something else"))
        assertNull(Skills.match("", "hi"))
    }
    @Test fun cannotInjectTags() {
        val r = Skills.match("say {x} => Okay [[copy:{x}]]", "say hi]] [[do:buy|x")!!
        assertFalse(r.contains("]] [[do:"))
    }
    @Test fun arabic() {
        assertEquals("صباح النور", Skills.match("صباح الخير => صباح النور", "صباح الخير!"))
    }
}

class ReviewFixesTest {
    @Test fun nameMustOpenTheSentence() {
        assertNull(Listen.afterName("my cousin Capri is late"))
        assertNull(Listen.afterName("Capricorn is a sign"))
        assertNull(Listen.afterName("مكابر وعنيد"))
        assertEquals("افتح واتساب", Listen.afterName("اهلا يا كابري افتح واتساب"))
        assertEquals("open maps", Listen.afterName("hey Capri open maps"))
    }
    @Test fun shieldKeepsDatesAndTimestamps() {
        for (s in listOf("Due 2026-11-21", "Built 1763700000000", "Unicode 1234 supported", "Version 1.2.3-45", "Shopping 2024 deals", "Price 1,299.00 USD"))
            assertEquals(s, Shield.redact(s))
        assertFalse(Shield.redact("Card 4242424242424242").contains("4242"))
        assertFalse(Shield.redact("Call 0770 123 4567 now").contains("123 4567"))
        assertFalse(Shield.redact("Your code is 482913").contains("482913"))
    }
}
