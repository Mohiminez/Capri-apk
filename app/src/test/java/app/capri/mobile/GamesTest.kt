package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class GamesTest {
    @Test fun tttTakesWinAndBlocks() {
        // Capri (2) can win at index 2
        val b = intArrayOf(2, 2, 0, 1, 1, 0, 0, 0, 0)
        assertEquals(2, TicTacToe.aiMove(b, 3))
        // must block you (1) at index 5
        val c = intArrayOf(1, 1, 0, 0, 2, 0, 0, 0, 0)
        assertEquals(2, TicTacToe.aiMove(c, 3))
    }

    @Test fun tttHardNeverLoses() {
        val rnd = java.util.Random(7)
        repeat(300) {
            val b = IntArray(9)
            var turn = 1
            while (TicTacToe.winner(b) == 0 && !TicTacToe.full(b)) {
                if (turn == 1) { val free = (0..8).filter { b[it] == 0 }; b[free[rnd.nextInt(free.size)]] = 1 }
                else b[TicTacToe.aiMove(b.clone(), 3, rnd)] = 2
                turn = 3 - turn
            }
            assertNotEquals(1, TicTacToe.winner(b))
        }
    }

    @Test fun connectFourDetectsWins() {
        val b = IntArray(42)
        for (c in 0..3) b[5 * 7 + c] = 1
        assertEquals(1, ConnectFour.winner(b))
        assertEquals(4, ConnectFour.winningCells(b).size)
        val d = IntArray(42)
        for (k in 0..3) d[(5 - k) * 7 + k] = 2            // diagonal
        assertEquals(2, ConnectFour.winner(d))
        assertEquals(0, ConnectFour.winner(IntArray(42)))
    }

    @Test fun connectFourDropsAndFills() {
        val b = IntArray(42)
        assertEquals(5, ConnectFour.dropRow(b, 3))
        for (r in 0..5) b[r * 7 + 3] = 1
        assertEquals(-1, ConnectFour.dropRow(b, 3))
        assertEquals(-1, ConnectFour.dropRow(b, 9))
    }

    @Test fun connectFourAiWinsAndBlocks() {
        // Capri has three in the bottom row: columns 0,1,2 -> must play 3
        val w = IntArray(42); for (c in 0..2) w[5 * 7 + c] = 2; w[5 * 7 + 6] = 1; w[4 * 7 + 6] = 1
        assertEquals(3, ConnectFour.aiMove(w, 2))
        // you have three in the bottom row at 0,1,2 -> she must block at 3
        val b = IntArray(42); for (c in 0..2) b[5 * 7 + c] = 1; b[5 * 7 + 6] = 2
        assertEquals(3, ConnectFour.aiMove(b, 2))
    }

    @Test fun rpsRules() {
        assertEquals(1, Rps.beats(0, 2)); assertEquals(-1, Rps.beats(0, 1)); assertEquals(0, Rps.beats(2, 2))
        assertEquals(1, Rps.beats(1, 0)); assertEquals(1, Rps.beats(2, 1))
    }
}

class LocalAskTest {
    @Test fun phoneFacts() {
        assertEquals("[[info:time]]", LocalAsks.match("كم الساعة"))
        assertEquals("[[info:time]]", LocalAsks.match("what time is it?"))
        assertEquals("[[info:battery]]", LocalAsks.match("كم البطارية"))
        assertEquals("[[info:battery]]", LocalAsks.match("how much battery do I have"))
        assertEquals("[[info:date]]", LocalAsks.match("اي يوم اليوم"))
    }

    @Test fun flashAndVolume() {
        assertEquals("[[flash:on]]", LocalAsks.match("شغل الكشاف"))
        assertEquals("[[flash:off]]", LocalAsks.match("اطفي الكشاف"))
        assertEquals("[[flash:on]]", LocalAsks.match("turn on the flashlight"))
        assertEquals("[[volume:up]]", LocalAsks.match("ارفع الصوت"))
        assertEquals("[[volume:down]]", LocalAsks.match("وطي الصوت"))
        assertEquals("[[volume:40]]", LocalAsks.match("set volume to 40"))
    }

    @Test fun gamesAndClock() {
        assertTrue(LocalAsks.match("play connect four")!!.contains("[[game:c4]]"))
        assertTrue(LocalAsks.match("نلعب اكس او")!!.contains("[[game:ttt]]"))
        assertTrue(LocalAsks.match("العب حجر ورقة مقص")!!.contains("[[game:rps]]"))
        assertTrue(LocalAsks.match("مؤقت 5 دقايق")!!.contains("[[timer:300|Capri]]"))
        assertTrue(LocalAsks.match("صحيني الساعة 6")!!.contains("[[alarm:6:00|Capri]]"))
        assertTrue(LocalAsks.match("صحيني الساعة 7:30 مساء")!!.contains("[[alarm:19:30|Capri]]"))
    }

    @Test fun throughQuickAsks() {
        assertTrue(QuickAsks.match("كم الساعة")!!.contains("[[info:time]]"))
        assertTrue(QuickAsks.match("ذكرني بعد 5 دقايق اشرب")!!.contains("[[remind:+300|"))
        assertNull(LocalAsks.match("tell me a joke"))
        assertNull(LocalAsks.match("what is the meaning of life"))
    }
}
