package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class NewCareTest {
    @Test fun crisisRealVsPlayed() {
        for (s in listOf("i want to kill myself", "I don’t want to be here anymore".replace("be here anymore", "live anymore"), "im suicidal", "i've been cutting myself", "everyone would be better off without me", "ابي اموت", "ودي أنتحر", "ما عاد ابي اعيش", "أبغى أقتل نفسي", "راح انتحر"))
            assertTrue("missed: $s", Crisis.means(s))
        for (s in listOf("this boss makes me want to die", "how do i kill myself in minecraft", "lol i died again", "i killed it today", "i'm dying of laughter", "ابي اموت من الضحك", "ابي اموت من الجوع", "مت في اللعبة", "ما ابي اروح", "ما ابي اعيش في هالبيت".replace("ما ابي اعيش في هالبيت", "وين الاكل"), "", "i want to diet"))
            assertFalse("took for real: $s", Crisis.means(s))
    }
    @Test fun crisisLine() {
        assertTrue(Crisis.line("US", false).contains("988")); assertTrue(Crisis.line("IQ", true).contains("findahelpline.com"))
        assertFalse(Crisis.line("IQ", false).contains("988"))
        assertTrue(Crisis.due(1000, 0)); assertFalse(Crisis.due(1000 + 5000, 1000)); assertTrue(Crisis.due(1000 + 600_000, 1000))
    }
    private val M = 60_000L
    private fun cfg(vararg off: CareKind) = Care.Cfg({ it !in off }, { Care.defaultMins(it) })
    @Test fun waterAfterNinetyMinutesOfUse() {
        val c = Care(); val f = Care.Cfg({ it == CareKind.WATER }, { Care.defaultMins(it) })
        assertNull(c.check(0, 9, 0, 100, 0, 0, null, f, true))
        assertNull(c.check(89 * M, 10, 29, 100, 0, 0, null, f, true))
        assertEquals(CareKind.WATER, c.check(90 * M, 10, 30, 100, 0, 0, null, f, true))
        assertNull(c.check(91 * M, 10, 31, 100, 0, 0, null, f, true))
    }
    @Test fun awayResetsTimersAndNothingIsSaid() {
        val c = Care(); val f = cfg(CareKind.LATE, CareKind.MEAL, CareKind.BORED)
        c.check(0, 9, 0, 100, 0, 0, null, f, true)
        assertNull(c.check(500 * M, 9, 0, 100, 11 * M, 0, null, f, true))
        assertNull(c.check(501 * M, 9, 0, 100, 0, 0, null, f, true)) // starts over, nothing due yet
        assertNull(c.check(502 * M, 9, 0, 100, 0, 0, null, f, true))
    }
    @Test fun bedtimeOncePerNight() {
        val c = Care(); val f = cfg(CareKind.WATER, CareKind.STRETCH, CareKind.EYES, CareKind.MEAL, CareKind.BORED)
        c.check(0, 1, 5, 100, 0, 0, null, f, true)
        assertEquals(CareKind.LATE, c.check(M, 1, 6, 100, 0, 0, null, f, true))
        assertNull(c.check(2 * M, 1, 7, 100, 0, 0, null, f, true))
        assertNull(c.check(3 * M, 3, 7, 100, 0, 0, null, f, true))
        // next evening (day 100 at 23:00 is evening 100; 101 at 1:00 is the same night) with bedtime 23 
        val g = Care.Cfg({ it == CareKind.LATE }, { if (it == CareKind.LATE) 23 else 999 })
        val d = Care(); d.check(0, 22, 0, 100, 0, 0, null, g, true)
        assertEquals(CareKind.LATE, d.check(M, 23, 0, 100, 0, 0, null, g, true))
        assertNull(d.check(2 * M, 0, 30, 101, 0, 0, null, g, true))
    }
    @Test fun mealOncePerWindowNotLateNotAtPickup() {
        val c = Care(); val f = Care.Cfg({ it == CareKind.MEAL }, { if (it == CareKind.MEAL) 240 else 999 })
        c.check(0, 12, 0, 100, 0, 0, null, f, true)
        assertNull(c.check(10 * M, 12, 10, 100, 0, 0, null, f, true))      // just picked up
        assertEquals(CareKind.MEAL, c.check(40 * M, 12, 40, 100, 0, 0, null, f, true))
        assertNull(c.check(41 * M, 12, 41, 100, 0, 0, null, f, true))
        assertNull(c.check(100 * M, 14, 0, 100, 0, 0, null, f, true))      // between windows
        assertEquals(CareKind.MEAL, c.check(200 * M, 16, 10, 100, 0, 0, null, f, true)) // next window at 16:00
        val l = Care(); l.check(0, 21, 0, 100, 0, 0, null, f, true)
        assertNull(l.check(60 * M, 21, 40, 100, 0, 0, null, f, true))      // too late in the evening
    }
    @Test fun gameBreakOnlyWhilePlayingAndBoredNeedsSilence() {
        val f = Care.Cfg({ it == CareKind.GAME || it == CareKind.BORED }, { if (it == CareKind.GAME) 60 else 45 })
        val c = Care(); c.check(0, 15, 0, 100, 0, 0, null, f, true)
        assertNull(c.check(70 * M, 16, 10, 100, 0, 0, null, f, true))
        val g = Care(); g.check(0, 15, 0, 100, 0, 0, "Fortnite", f, true)
        assertEquals(CareKind.GAME, g.check(61 * M, 16, 1, 100, 0, 0, "Fortnite", f, true))
        val b = Care(); b.check(0, 15, 0, 100, 0, 0, null, f, true)
        assertNull(b.check(50 * M, 15, 50, 100, 0, 10 * M, null, f, true))
        assertEquals(CareKind.BORED, b.check(50 * M, 15, 50, 100, 0, 46 * M, null, f, true))
    }
    @Test fun busyKeepsItDueThenSpeaksOnce() {
        val f = Care.Cfg({ it == CareKind.WATER }, { 10 })
        val c = Care(); c.check(0, 9, 0, 100, 0, 0, null, f, true)
        assertNull(c.check(11 * M, 9, 11, 100, 0, 0, null, f, false))
        assertEquals(CareKind.WATER, c.check(12 * M, 9, 12, 100, 0, 0, null, f, true))
        assertNull(c.check(13 * M, 9, 13, 100, 0, 0, null, f, true))
    }
    @Test fun careLinesExist() { for (k in CareKind.values()) { assertTrue(CareLines.pick(k, true, 3).isNotEmpty()); assertTrue(CareLines.pick(k, false, -2).isNotEmpty()) } }
    @Test fun stopwatch() {
        assertEquals("start", StopwatchTalk.request("start the stopwatch", false)); assertEquals("stop", StopwatchTalk.request("stop my stopwatch", true))
        assertEquals("start", StopwatchTalk.request("شغل ساعة التوقيت", false)); assertEquals("stop", StopwatchTalk.request("وقف الستوب واتش", true))
        assertEquals("check", StopwatchTalk.request("how long has it been", true)); assertNull(StopwatchTalk.request("how long has it been", false))
        assertNull(StopwatchTalk.request("what time is it", false))
        assertNull(StopwatchTalk.request("stop watching", false)); assertNull(StopwatchTalk.request("stop watching", true))
        assertEquals("4 minutes 12 seconds", StopwatchTalk.words(252_000, false)); assertEquals("0 seconds", StopwatchTalk.words(0, false)); assertEquals("2 hours", StopwatchTalk.words(7_200_000, false))
        val o = StopwatchTalk.run("start", 0, 5000, false); assertEquals(5000L, o.startedAt)
        assertEquals(0L, StopwatchTalk.run("stop", 5000, 65000, false).startedAt); assertTrue(StopwatchTalk.run("check", 5000, 65000, false).say.contains("1 minute"))
    }
    @Test fun spend() {
        var r = Spend.add("", 100, 500, 200); r = Spend.add(r, 100, 50, 10); r = Spend.add(r, 99, 1000, 0)
        assertEquals(2, Spend.today(r, 100).asks); assertEquals(760L, Spend.today(r, 100).tokens); assertEquals(3, Spend.week(r, 100).asks)
        r = Spend.add(r, 130, 1, 1); assertEquals(1, Spend.parse(r).size)
        assertEquals(emptyList<Spend.Row>(), Spend.parse("garbage")); assertEquals("1.5k", Spend.tokens(1500)); assertEquals("25k", Spend.tokens(25_000)); assertEquals("2.0M", Spend.tokens(2_000_000))
        assertTrue(Spend.line(r, 130, true).contains("اليوم"))
    }
}
