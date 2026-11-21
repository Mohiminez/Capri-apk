package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class RouterTest {
    @Test fun shortChatGoesFast() {
        assertEquals(3, Router.pick("hello there", 1, 3, false))
        assertEquals(1, Router.pick("hello there", 1, -1, false))
        assertEquals(1, Router.pick("explain quantum physics", 1, 3, false))
        assertEquals(1, Router.pick("what is this", 1, 3, true))
        assertEquals(1, Router.pick("x".repeat(80), 1, 3, false))
        assertEquals(1, Router.pick("اشرح لي", 1, 3, false))
    }
    @Test fun queue() {
        assertTrue(OfflineQueue.isNetworkError("Unable to resolve host api.groq.com"))
        assertFalse(OfflineQueue.isNetworkError("401 invalid api key"))
        var q = ""
        for (i in 1..8) q = OfflineQueue.push(q, "m$i")
        assertEquals(5, q.split("\n").size)
        val (first, rest) = OfflineQueue.pop(q)!!
        assertEquals("m4", first); assertEquals(4, rest.split("\n").size)
        assertNull(OfflineQueue.pop(""))
        assertEquals("a", OfflineQueue.push(OfflineQueue.push("", "a"), "a"))
    }
}

class KnowledgeTest {
    private val doc = "My wifi password hint is the dog's name.\n\nThe gym opens at six and closes at ten on weekdays.\n\nRent is due on the first of each month."
    @Test fun findsTheRightPassage() {
        val cs = Knowledge.chunk("home", doc, 60)
        assertTrue(cs.size >= 3)
        val hit = Knowledge.best(cs, "when does the gym open")
        assertTrue(hit.first().text.contains("gym"))
        assertTrue(Knowledge.best(cs, "what is the capital of France").isEmpty())
    }
    @Test fun arabicQuery() {
        val cs = Knowledge.chunk("n", "موعد الاجتماع يوم الثلاثاء الساعة عشرة\n\nكلمة السر مكتوبة في الدفتر", 60)
        assertTrue(Knowledge.best(cs, "متى موعد الاجتماع").first().text.contains("الاجتماع"))
    }
    @Test fun longParagraphsAreSplit() {
        assertTrue(Knowledge.chunk("d", "word ".repeat(400), 600).all { it.text.length <= 600 })
    }
}

class AutoRouteTest {
    private val names = listOf("Gemini (free key)", "Groq (free key, very fast)", "Claude", "DeepSeek", "On this phone (offline model)")
    private val local = 4
    @Test fun codeGoesToTheStrongest() {
        assertEquals(2, AutoRoute.pick("fix this python bug", false, 1, names, { it != 4 }, local))
    }
    @Test fun quickChatGoesFast() {
        assertEquals(1, AutoRoute.pick("hey", false, 2, names, { it != 4 }, local))
    }
    @Test fun keepsMainWhenAlreadyGood() {
        assertEquals(2, AutoRoute.pick("fix this python bug", false, 2, names, { true }, local))
    }
    @Test fun skipsProvidersWithoutKeys() {
        assertEquals(3, AutoRoute.pick("fix this python bug", false, 1, names, { it == 3 || it == 1 }, local))
    }
    @Test fun offlineStaysOffline() {
        assertEquals(4, AutoRoute.pick("fix this python bug", false, 4, names, { true }, local))
    }
    @Test fun picturesGoToAVisionBrain() {
        assertEquals(0, AutoRoute.pick("what is this", true, 1, names, { true }, local))
    }
    @Test fun topics() {
        assertEquals(AutoRoute.Topic.WRITING, AutoRoute.topic("translate this to french", false))
        assertEquals(AutoRoute.Topic.LONG, AutoRoute.topic("x".repeat(2000), false))
        assertEquals(AutoRoute.Topic.CODE, AutoRoute.topic("اشرح لي هذا الكود", false))
    }
}
