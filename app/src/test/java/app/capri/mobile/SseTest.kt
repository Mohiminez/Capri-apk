package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class SseTest {
    @Test fun readsContentPieces() {
        assertEquals("Hel", Sse.delta("data: {\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}"))
        assertEquals("", Sse.delta("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}"))
        assertEquals("", Sse.delta("data: {\"choices\":[{\"delta\":{\"content\":null},\"finish_reason\":\"stop\"}]}"))
        assertEquals("", Sse.delta("data: {\"choices\":[]}"))
        assertEquals("", Sse.delta("data: {\"usage\":{}}"))
    }

    @Test fun unescapesJson() {
        assertEquals("a \"b\"\nc\\d é", Sse.delta("data: {\"choices\":[{\"delta\":{\"content\":\"a \\\"b\\\"\\nc\\\\d \\u00e9\"}}]}"))
        assertEquals("مرحبا", Sse.delta("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"مرحبا\"}}]}"))
        // a reasoning piece is not the answer
        assertEquals("", Sse.delta("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"thinking\"}}]}"))
    }

    @Test fun ignoresOtherLines() {
        assertNull(Sse.delta("")); assertNull(Sse.delta(": keep-alive")); assertNull(Sse.delta("event: x"))
        assertNull(Sse.delta("data: [DONE]")); assertNull(Sse.delta("data: not json"))
        assertTrue(Sse.done("data: [DONE]")); assertFalse(Sse.done("data: {}"))
    }

    @Test fun previewHidesTagsAndThinking() {
        assertEquals("Opening it now.", Sse.preview("Opening it now. [[open:spotify]]"))
        assertEquals("Sure thing", Sse.preview("Sure thing [[ope"))
        assertEquals("Sure thing", Sse.preview("Sure thing [["))
        assertEquals("Sure thing", Sse.preview("Sure thing ["))
        assertEquals("Hi", Sse.preview("<think>hmm</think>Hi"))
        assertEquals("", Sse.preview("<think>still thinking"))
        assertEquals("a b", Sse.preview("a\n\n  b"))
    }
}
