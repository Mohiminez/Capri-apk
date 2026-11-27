package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class AsidesTest {
    @Test fun stops() {
        for (s in listOf("stop", "Stop!", "ok stop it", "cancel", "never mind", "that's enough", "وقف", "وقفي كل شي", "ستوب", "خلاص", "كافي", "الغي المهمه", "لا تكمل", "وقف اللعبه"))
            assertEquals("stop: $s", Asides.Kind.STOP, Asides.classify(s))
    }
    @Test fun progress() {
        for (s in listOf("what are you doing", "how's it going", "are you done", "status", "how long", "وش تسوين", "وين وصلتي", "خلصتي؟", "كم باقي", "شلون ماشي الشغل"))
            assertEquals("progress: $s", Asides.Kind.PROGRESS, Asides.classify(s))
    }
    @Test fun otherThingsAreLeftToTheBrain() {
        for (s in listOf("open youtube", "play some music", "you are cool", "stop being annoying and write me a poem about the sea in three verses please", "", "وش الجو اليوم"))
            assertNull(Asides.classify(s))
    }
    @Test fun statusLines() {
        assertTrue(Asides.status("", 10_000, false, 0).contains("getting going"))
        assertTrue(Asides.status("[GAME] roaming the map", 5 * 60_000L, false, 0).contains("roaming the map"))
        assertFalse(Asides.status("[GAME] x", 5 * 60_000L, false, 1).contains("[GAME]"))
        assertTrue(Asides.status("فتحت يوتيوب", 3 * 60_000L, true, 0).contains("دقايق"))
    }
}
