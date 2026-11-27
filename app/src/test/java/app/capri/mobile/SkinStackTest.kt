package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class SkinStackTest {
    @Test fun readsSkinJson() {
        val m = SkinStack.meta("{ \"format\": 1, \"id\": \"cat-ears\", \"name\": \"Cat Ears\", \"author\": \"You\", \"accessory\": true }")!!
        assertEquals("cat-ears", m.id); assertEquals("Cat Ears", m.name); assertTrue(m.accessory)
        val w = SkinStack.meta("{\"name\":\"Red Hoodie\"}")!!
        assertEquals("red-hoodie", w.id); assertFalse(w.accessory)
        assertNull(SkinStack.meta("{}")); assertNull(SkinStack.meta("not json"))
        assertFalse(SkinStack.meta("{\"id\":\"x\",\"accessory\":false}")!!.accessory)
    }

    @Test fun onlySafeStripNamesAreRead() {
        assertEquals("idle", SkinStack.stripName("idle.png")); assertEquals("perch_talk", SkinStack.stripName("perch_talk.png"))
        for (bad in listOf("../idle.png", "sprites/idle.png", "/idle.png", "IDLE.png", "idle.gif", "idle.png.exe", ".png", "a b.png", "idle.PNG")) assertNull(SkinStack.stripName(bad))
    }

    @Test fun stackOrder() {
        assertEquals(listOf("a", "b"), SkinStack.toggle(listOf("a"), "b"))
        assertEquals(listOf("b"), SkinStack.toggle(listOf("a", "b"), "a"))
        // off and on again moves it to the top
        assertEquals(listOf("b", "a"), SkinStack.toggle(SkinStack.toggle(listOf("a", "b"), "a"), "a"))
        assertEquals(listOf("a", "c"), SkinStack.usable(listOf("a", "gone", "c", "a"), setOf("a", "c")))
    }

    @Test fun stripMustFitHerFrame() {
        assertEquals(60, SkinStack.frames(6960, 384, 116, 384))
        assertEquals(90, SkinStack.frames(10440, 384, 116, 384))
        assertEquals(0, SkinStack.frames(6960, 380, 116, 384))   // wrong height
        assertEquals(0, SkinStack.frames(7000, 384, 116, 384))   // not a whole number of frames
        assertEquals(0, SkinStack.frames(116 * 300, 384, 116, 384)) // more than 240 frames
        assertEquals(0, SkinStack.frames(100, 384, 116, 384))
    }
}
