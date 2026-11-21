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
