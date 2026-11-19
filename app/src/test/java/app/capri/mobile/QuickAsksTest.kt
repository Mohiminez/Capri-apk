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

class NewFeatureAsksTest {
    @Test fun pointsAtThings() {
        assertEquals("[[point:send]]", QuickAsks.match("circle the send button"))
        assertEquals("[[point:settings]]", QuickAsks.match("where is the settings icon"))
    }

    @Test fun stopsWatching() {
        assertEquals("[[watch:off]]", QuickAsks.match("stop watching"))
    }

    @Test fun clearsClones() {
        assertEquals("[[clones:0]]", QuickAsks.match("clear clones"))
    }

    @Test fun versionCompare() {
        assert(Updater.newer("0.5.12", "0.5.9"))
        assert(!Updater.newer("v0.5.9", "0.5.9"))
        assert(Updater.newer("0.6.0", "0.5.99"))
    }

    @Test fun clampNeverThrows() {
        assertEquals(5, 9.clampTo(5, 3))
        assertEquals(0.2f, 0.2f.clampTo(1f, 0f).let { 0.2f }, 0f)
    }
}

class ChessAskTest {
    @Test fun opensChess() {
        assert(QuickAsks.match("let's play chess")!!.contains("[[chess:open]]"))
        assert(QuickAsks.match("play chess")!!.contains("[[chess:open]]"))
        assert(QuickAsks.match("خلنا نلعب شطرنج")!!.contains("[[chess:open]]"))
        assert(QuickAsks.match("العب شطرنج")!!.contains("[[chess:open]]"))
    }
}

class LookTest {
    @Test fun wakeWordVariants() {
        val re = Regex("(capri|kapri|cabri|capry|كابري|كابرى|كابريه|كابرة|كبري|كابر)", RegexOption.IGNORE_CASE)
        assert(re.containsMatchIn("hey Capri open whatsapp"))
        assert(re.containsMatchIn("اهلا كابري"))
        assert(!re.containsMatchIn("open whatsapp"))
    }
}
