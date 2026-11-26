package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class ApiErrorsTest {
    @Test fun retryable() {
        for (c in intArrayOf(408, 429, 500, 502, 503, 504, 529)) assertTrue("" + c, ApiErrors.retryable(c))
        for (c in intArrayOf(400, 401, 402, 403, 404)) assertFalse("" + c, ApiErrors.retryable(c))
        assertTrue(ApiErrors.backoffMs(0) < ApiErrors.backoffMs(1) && ApiErrors.backoffMs(1) < ApiErrors.backoffMs(2))
    }

    @Test fun friendlyNeverShowsJson() {
        for (c in intArrayOf(401, 402, 404, 408, 429, 500, 503, 418)) for (ar in listOf(true, false)) {
            val m = ApiErrors.friendly(c, ar)
            assertFalse(m, m.contains("{") || m.contains("\""))
            assertTrue(m.length < 100)
        }
        assertTrue(ApiErrors.friendly(503, false).contains("busy"))
    }

    @Test fun cleanTurnsAnyExceptionIntoOneShortLine() {
        assertEquals(ApiErrors.friendly(503, true), ApiErrors.clean(HttpErr(503, "x"), true))
        assertTrue(ApiErrors.clean(java.net.UnknownHostException("generativelanguage.googleapis.com"), false).contains("internet"))
        assertTrue(ApiErrors.clean(java.net.SocketTimeoutException("Read timed out"), false).contains("too long"))
        val raw = ApiErrors.clean(Exception("[{ \"error\": { \"code\": 503, \"message\": \"" + "busy ".repeat(60) + "\" } }]"), false)
        assertTrue(raw, raw.length <= 110); assertFalse(raw.contains("{") || raw.contains("\""))
        assertEquals("Something went wrong.", ApiErrors.clean(Exception(""), false))
    }

    @Test fun lighterGeminiModel() {
        val g = "https://generativelanguage.googleapis.com/v1beta/openai"
        assertEquals("gemini-2.5-flash", ApiErrors.fallbackModel(g, "gemini-2.5-pro"))
        assertEquals("gemini-2.5-flash-lite", ApiErrors.fallbackModel(g, "gemini-2.5-flash"))
        assertEquals("gemini-2.0-flash-lite", ApiErrors.fallbackModel(g, "models/gemini-2.0-flash-001"))
        assertNull(ApiErrors.fallbackModel(g, "gemini-2.5-flash-lite"))
        assertNull(ApiErrors.fallbackModel("https://api.openai.com/v1", "gpt-4o"))
    }
}
