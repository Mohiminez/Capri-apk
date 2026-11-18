package app.capri.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QuickAsksTest {
    @Test fun opensAnAppAndTypes() {
        assertEquals("On it. [[hands:openType|WhatsApp|hello there]]", QuickAsks.match("open WhatsApp and type hello there"))
    }

    @Test fun arabicOpenAndType() {
        assertNotNull(QuickAsks.match("افتح واتساب واكتب مرحبا كيف حالك"))
    }

    @Test fun chatIsNotACommand() {
        assertNull(QuickAsks.match("what is your name"))
        assertNull(QuickAsks.match("write me a poem"))
    }

    @Test fun simpleControls() {
        assertEquals("[[hands:back]]", QuickAsks.match("go back"))
        assertEquals("[[hands:scroll|down]]", QuickAsks.match("scroll down"))
    }

    @Test fun networkGuard() {
        assert(Net.isPrivate("192.168.1.20"))
        assert(Net.isPrivate("127.0.0.1"))
        assert(!Net.isPrivate("8.8.8.8"))
    }
}

class MoreAsksTest {
    @Test fun danceAndScroll() {
        assertEquals("[[emote:dance]]", QuickAsks.match("dance"))
        assertEquals("[[scrollforme:on]]", QuickAsks.match("scroll for me"))
        assertEquals("[[emote:stop]]", QuickAsks.match("stop dancing"))
    }

    @Test fun ownSettings() {
        assertEquals("[[set:size|bigger]]", QuickAsks.match("make you bigger"))
        assertEquals("[[set:color|hair=cyan]]", QuickAsks.match("change your hair color to cyan"))
    }
}
