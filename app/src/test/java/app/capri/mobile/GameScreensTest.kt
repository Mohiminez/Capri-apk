package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class GameScreensTest {
    private fun ws(vararg t: String) = t.mapIndexed { i, s -> Word(s, 0.1f + i * 0.05f, 0.5f, 0.1f, 0.04f) }

    @Test fun classifies() {
        assertEquals(GameScreen.VICTORY, GameScreens.classify(ws("VICTORY", "ROYALE", "Continue")))
        assertEquals(GameScreen.ELIMINATED, GameScreens.classify(ws("You", "were", "eliminated", "Spectate", "Leave")))
        assertEquals(GameScreen.QUEUE, GameScreens.classify(ws("Searching", "for", "match", "Cancel")))
        assertEquals(GameScreen.BUS, GameScreens.classify(ws("Jump", "from", "the", "Battle", "Bus")))
        assertEquals(GameScreen.MATCH, GameScreens.classify(ws("Players", "left", "12", "Eliminations", "0")))
        assertEquals(GameScreen.LOBBY, GameScreens.classify(ws("PLAY", "Battle", "Pass", "Locker", "Quests")))
        assertEquals(GameScreen.UNKNOWN, GameScreens.classify(ws("hello", "world")))
        assertEquals(GameScreen.UNKNOWN, GameScreens.classify(emptyList()))
    }

    @Test fun leaveButtonNeverSpectates() {
        val b = GameScreens.leaveButton(listOf(Word("Spectate", 0.1f, 0.8f, 0.2f, 0.06f), Word("Return", 0.4f, 0.9f, 0.15f, 0.06f), Word("to", 0.56f, 0.9f, 0.05f, 0.06f), Word("Lobby", 0.62f, 0.9f, 0.15f, 0.06f)))!!
        assertEquals("return to lobby", b.text)
        assertNull(GameScreens.leaveButton(listOf(Word("Spectate", 0.1f, 0.8f, 0.2f, 0.06f))))
        // a price on the line blocks it
        assertNull(GameScreens.leaveButton(listOf(Word("Leave", 0.3f, 0.8f, 0.1f, 0.05f), Word("$4.99", 0.45f, 0.8f, 0.1f, 0.05f))))
    }
}
