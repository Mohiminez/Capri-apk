package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class TextTargetsTest {
    private fun w(t: String, x: Float, y: Float, ww: Float = 0.1f, h: Float = 0.04f) = Word(t, x, y, ww, h)

    @Test fun findsASingleWordAndAPhrase() {
        val ws = listOf(w("PLAY", 0.40f, 0.88f, 0.2f, 0.06f), w("AGAIN", 0.62f, 0.88f, 0.2f, 0.06f), w("Settings", 0.1f, 0.05f))
        assertEquals(0.50f, TextTargets.find(ws, "play")!!.cx, 0.2f)
        val ph = TextTargets.find(ws, "play again")!!
        assertEquals(0.41f + 0.0f, ph.x + 0.01f, 0.02f)
        assertTrue(ph.w > 0.4f)
        assertNull(TextTargets.find(ws, "leave"))
    }

    @Test fun toleratesOneWrongLetter() {
        assertNotNull(TextTargets.find(listOf(w("C0NTINUE", 0.3f, 0.9f)), "continue"))
        assertNotNull(TextTargets.find(listOf(w("CONTINU", 0.3f, 0.9f)), "continue"))
        assertNull(TextTargets.find(listOf(w("COUNTING", 0.3f, 0.9f)), "continue"))
        assertNull(TextTargets.find(listOf(w("OK", 0.3f, 0.9f)), "ox"))   // short words must match exactly
    }

    @Test fun prefersTheBiggerButtonOverBodyText() {
        val ws = listOf(w("play", 0.1f, 0.3f, 0.04f, 0.02f), w("PLAY", 0.4f, 0.9f, 0.2f, 0.07f))
        assertEquals(0.9f, TextTargets.find(ws, "play")!!.y, 0.001f)
    }

    @Test fun autoTapPicksContinueFirstAndSkipsRiskyScreens() {
        val ws = listOf(w("Victory", 0.4f, 0.2f), w("CONTINUE", 0.4f, 0.9f, 0.2f, 0.06f), w("Play", 0.1f, 0.9f))
        assertEquals("continue", TextTargets.autoTap(ws, emptyList())!!.text)
        // a store screen is never auto-tapped
        assertNull(TextTargets.autoTap(listOf(w("Item", 0.1f, 0.1f), w("Shop", 0.2f, 0.1f), w("BUY", 0.5f, 0.5f), w("NOW", 0.62f, 0.5f), w("Continue", 0.4f, 0.9f)), emptyList()))
        // a price on the same line as the button blocks it
        assertNull(TextTargets.autoTap(listOf(w("Claim", 0.3f, 0.8f), w("$4.99", 0.5f, 0.8f)), emptyList()))
        // watch-an-ad buttons are not pressed
        assertNull(TextTargets.autoTap(listOf(w("Watch", 0.2f, 0.8f), w("ad", 0.3f, 0.8f), w("to", 0.36f, 0.8f), w("claim", 0.42f, 0.8f)), emptyList()))
    }

    @Test fun aButtonThatDoesNothingIsNotPressedForever() {
        val ws = listOf(w("PLAY", 0.4f, 0.9f, 0.2f, 0.06f))
        assertNotNull(TextTargets.autoTap(ws, listOf("play", "play")))
        assertNull(TextTargets.autoTap(ws, listOf("play", "play", "play")))
    }

    @Test fun statusBarTextIsNotAButton() {
        assertNull(TextTargets.autoTap(listOf(w("OK", 0.5f, 0.0f, 0.05f, 0.03f)), emptyList()))
    }

    @Test fun describeListsBigWordsWithPixelCentres() {
        val s = TextTargets.describe(listOf(w("READY", 0.4f, 0.9f, 0.2f, 0.06f), w("a", 0.1f, 0.1f)), 720, 1600)
        assertTrue(s, s.contains("\"READY\" @360,1488"))
        assertFalse(s.contains("\"a\""))
        assertEquals("(none read)", TextTargets.describe(emptyList(), 720, 1600))
    }

    private fun frame(color: Int, n: Int = 100) = Reflex.Frame(IntArray(n) { color }, 10, n / 10)

    @Test fun stormPurple() {
        assertTrue(TextTargets.purpleShare(frame(0xFF7A38D8.toInt())) > 0.9f)
        assertTrue(TextTargets.purpleShare(frame(0xFFC030A0.toInt())) > 0.9f)
        assertEquals(0f, TextTargets.purpleShare(frame(0xFF3A8A3A.toInt())), 0f)   // grass
        assertEquals(0f, TextTargets.purpleShare(frame(0xFF808080.toInt())), 0f)   // grey
        assertEquals(0f, TextTargets.purpleShare(frame(0xFF101010.toInt())), 0f)   // dark
        assertEquals(0f, TextTargets.purpleShare(frame(0xFF4A90E0.toInt())), 0f)   // sky blue
    }
}
