package app.capri.mobile

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test

class PerchClipsTest {
    // Gradle runs unit tests from the module folder (app/), a plain shell run from the project root
    private fun assets() = File("src/main/assets").takeIf { it.exists() } ?: File("app/src/main/assets")

    @Test fun desktopPerchSetShipsWithSheets() {
        val dir = assets()
        val m = File(dir, "sprites.json").readText()
        for (n in listOf("perch_arrive", "perch_leave", "perch_glitch", "perch_blink_glitch", "perch_music", "idle_blink_glitch",
            "laydown", "laydown_blink", "laydown_glitch", "laydown_leave")) {
            assertTrue("$n in manifest", m.contains("\"$n\": {"))
            assertTrue("$n sheet", File(dir, "sprites/$n.webp").length() > 1000)
        }
    }
    @Test fun ledgeModeExists() {
        val md = Modes.find("chill")!!
        assertTrue(md.set["perchBias"] == 1)
        assertTrue(Modes.find("normal")!!.set["perchBias"] == 0)
    }
    @Test fun chairEmoteShips() {
        val d = File(assets(), "emotes/chair")
        val n = File(d, "info.txt").readText().trim().split(" ")[0].toInt()
        assertTrue(n > 100)
        for (i in 0 until n) assertTrue("frame $i", File(d, "%03d.webp".format(i)).length() > 0)
    }
    @Test fun newModesAreFoundByName() {
        assertTrue(Modes.fromPhrase("وضع الجلسة") == "chill")
        assertTrue(Modes.fromPhrase("night mode") == "night")
        assertTrue(Modes.find("party")!!.set["wanderMix"] == 2)
    }
    @Test fun gameAsks() {
        assertTrue(QuickAsks.match("العبلي فورت نايت")!!.contains("[[playgame:فورت نايت]]"))
        assertTrue(QuickAsks.match("العب لي ببجي")!!.contains("[[playgame:ببجي]]"))
        assertTrue(QuickAsks.match("play fortnite for me")!!.contains("[[playgame:fortnite]]"))
        assertTrue(QuickAsks.match("play subway surfers")!!.contains("[[playgame:subway surfers]]"))
        assertTrue(QuickAsks.match("play despacito")!!.contains("[[music:despacito]]"))
        assertTrue(QuickAsks.match("العب مكاني")!!.contains("[[chess:auto]]"))
    }
    @Test fun huskParsesClaudeCodeHooks() {
        val e = Husk.parse("{\"hook_event_name\":\"PreToolUse\",\"tool_name\":\"Edit\",\"tool_input\":{\"file_path\":\"/src/app/Main.kt\"}}")!!
        assertTrue(e.tool == "Edit" && Husk.describe(e, false) == "Editing Main.kt")
        assertTrue(Husk.describe(Husk.parse("{\"hook_event_name\":\"Stop\"}")!!, false).contains("finished"))
        assertTrue(Husk.parse("not json") == null)
        assertTrue(Husk.summary(emptyList(), false).contains("Nothing"))
        assertTrue(QuickAsks.match("what is claude doing")!!.contains("huskstatus"))
        assertTrue(QuickAsks.match("clip that")!!.contains("[[set:clip|save]]"))
    }
    @Test fun huskServerAcceptsOnlyTheToken() {
        val got = java.util.concurrent.atomic.AtomicInteger()
        val hk = Husk("tok123", 18765, false) { got.incrementAndGet() }
        assertTrue(hk.start())
        fun post(tok: String): String {
            val c = java.net.Socket("127.0.0.1", 18765); val body = "{\"hook_event_name\":\"Stop\"}"
            c.getOutputStream().write(("POST /hook HTTP/1.1\r\nX-Capri-Token: $tok\r\nContent-Length: ${body.length}\r\n\r\n$body").toByteArray())
            val r = c.getInputStream().bufferedReader().readLine(); c.close(); return r
        }
        assertTrue(post("tok123").contains("200"))
        assertTrue(post("wrong").contains("403"))
        Thread.sleep(200); hk.stop()
        assertTrue(got.get() == 1)
    }
    @Test fun researchAndChainedAsks() {
        assertTrue(QuickAsks.match("search for best pizza in baghdad")!!.contains("[[web:best pizza in baghdad]]"))
        assertTrue(QuickAsks.match("ابحثلي عن سعر الدولار")!!.contains("[[web:سعر الدولار]]"))
        assertTrue(QuickAsks.match("open spotify and search for drake and play it")!!.contains("[[do:Open the app \"spotify\", search for \"drake\" in it, then play the first result.]]"))
        assertTrue(QuickAsks.match("افتح يوتيوب وابحث عن اغاني وشغل اول نتيجة")!!.contains("[[do:Open the app \"يوتيوب\""))
        assertTrue(QuickAsks.match("open the browser")!!.contains("[[browse:"))
        assertTrue(QuickAsks.match("go to example.com")!!.contains("[[browse:example.com]]"))
        assertTrue(QuickAsks.match("play despacito")!!.contains("[[music:despacito]]"))
        assertTrue(Tools.readable("<html><script>x()</script><p>Hello &amp; <b>world</b></p></html>") == "Hello & world")
    }
    @Test fun gameSkillPacksAndDocs() {
        val dir = File(assets(), "gameskills")
        val skills = dir.listFiles()!!.mapNotNull { GameSkills.parse(it.readText()) }
        assertTrue(skills.size >= 6)
        assertTrue(GameSkills.forGame(skills, "Subway Surfers").contains("swipe"))
        assertTrue(GameSkills.forGame(skills, "2048").contains("corner"))
        assertTrue(GameSkills.forGame(skills, "some unknown game").isEmpty())
        assertTrue(GameSkills.parse("---\ncapri-skill: 1\nid: x\nname: X\ngames: Foo, Bar\n---\nPlay it well.")!!.games == listOf("foo", "bar"))
        assertTrue(GameSkills.parse("just text") == null)
        assertTrue(DocText.docx("<w:p><w:r><w:t>Hello &amp; hi</w:t></w:r></w:p><w:p><w:r><w:t>Two</w:t></w:r></w:p>") == "Hello & hi\n\nTwo")
        assertTrue(QuickAsks.match("ملخص اليوم")!!.contains("[[brief:today]]"))
    }
    @Test fun arabicGameNamesMatchEnglishAppLabels() {
        val pairs = listOf("فورت نايت" to "Fortnite", "ببجي" to "PUBG MOBILE", "ماين كرافت" to "Minecraft", "روبلوكس" to "Roblox", "فري فاير" to "Free Fire", "كلاش رويال" to "Clash Royale")
        for ((ar, en) in pairs) {
            val a = Actions.skeletonOf(ar); val e = Actions.skeletonOf(en)
            assertTrue("$ar=$a $en=$e", a == e || e.startsWith(a) || a.startsWith(e))
        }
    }
    private fun frame(w: Int, h: Int, bg: Int, vararg boxes: IntArray): Reflex.Frame {
        val px = IntArray(w * h) { bg }
        for (b in boxes) for (y in b[1] until b[3]) for (x in b[0] until b[2]) px[y * w + x] = b[4]
        return Reflex.Frame(px, w, h)
    }
    private val BLACK = 0xFF000000.toInt(); private val WHITE = 0xFFFFFFFF.toInt()
    @Test fun reflexTapsLowestDarkTile() {
        val f = frame(100, 200, WHITE, intArrayOf(10, 20, 30, 50, BLACK), intArrayOf(60, 120, 80, 150, BLACK))
        val r = Reflex(listOf(Reflex.Rule(Reflex.Cond("blob", "dark", pick = "lowest"), listOf(Reflex.Do("tapblob")), 0)), 10_000, 8)
        val a = r.step(f, null, 0)
        assertEquals(1, a.size)
        assertTrue(Math.abs(a[0].x1 - 0.70f) < 0.06f); assertTrue(Math.abs(a[0].y1 - 0.675f) < 0.06f)
    }
    @Test fun reflexColorsAndCooldownAndChange() {
        assertTrue(Reflex.matches(0xFFE02020.toInt(), "red")); assertTrue(Reflex.matches(0xFF20C040.toInt(), "green")); assertFalse(Reflex.matches(WHITE, "red"))
        val f = frame(60, 60, WHITE)
        val r = Reflex(listOf(Reflex.Rule(Reflex.Cond("none"), listOf(Reflex.Do("tap", doubleArrayOf(0.5, 0.9))), 300)), 10_000, 8)
        assertEquals(1, r.step(f, null, 1000).size); assertEquals(0, r.step(f, null, 1100).size); assertEquals(1, r.step(f, null, 1400).size)
        val g = frame(60, 60, WHITE, intArrayOf(0, 0, 60, 60, BLACK))
        assertTrue(Reflex.changed(g, f, Reflex.Cond("changed"))); assertFalse(Reflex.changed(f, f, Reflex.Cond("changed")))
    }
    @Test fun reflexNoBlobNoAction() {
        val f = frame(60, 60, WHITE)
        val r = Reflex(listOf(Reflex.Rule(Reflex.Cond("blob", "red"), listOf(Reflex.Do("tapblob")), 0)), 10_000, 8)
        assertEquals(0, r.step(f, null, 0).size)
    }
    @Test fun gridCellParse() {
        assertEquals(Pair(2, 4), GamePilot.parseCell("C5", 8, 14)); assertEquals(Pair(0, 0), GamePilot.parseCell(" a1 ", 8, 14))
        assertTrue(GamePilot.parseCell("Z9", 8, 14) == null); assertTrue(GamePilot.parseCell("A15", 8, 14) == null); assertTrue(GamePilot.parseCell("", 8, 14) == null)
    }
}
