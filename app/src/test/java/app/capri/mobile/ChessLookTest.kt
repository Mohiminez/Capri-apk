package app.capri.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessLookTest {
    @Test fun everyBoardIsReadable() {
        for (t in BoardThemes.all) {
            assertTrue(t.name + " squares", BoardThemes.contrast(t.light, t.dark) >= 1.3)
            for (sq in listOf(t.light, t.dark)) {
                assertTrue(t.name + " white piece", BoardThemes.contrast(t.white, sq) >= 1.5 || BoardThemes.contrast(t.outWhite, sq) >= 3.0)
                assertTrue(t.name + " black piece", BoardThemes.contrast(t.black, sq) >= 1.5 || BoardThemes.contrast(t.outBlack, sq) >= 3.0)
                assertTrue(t.name + " marks", BoardThemes.contrast(t.mark, sq) >= 1.5)
            }
        }
    }

    @Test fun namesAreUniqueAndLookupIsSafe() {
        assertEquals(BoardThemes.all.size, BoardThemes.all.map { it.name }.toSet().size)
        assertEquals(BoardThemes.all[0], BoardThemes.at(-4))
        assertEquals(BoardThemes.all[0], BoardThemes.at(99))
        assertEquals(0, BoardThemes.saved(77))
        assertEquals(0, BoardThemes.next(BoardThemes.all.size - 1))
        assertEquals(1, BoardThemes.next(0))
    }

    @Test fun contrastMath() {
        assertEquals(21.0, BoardThemes.contrast(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.01)
        assertEquals(1.0, BoardThemes.contrast(0xFF123456.toInt(), 0xFF123456.toInt()), 0.001)
    }

    @Test fun capturedAndLeadFromTheStart() {
        val p = Chess.start()
        assertTrue(Captured.lost(p.b, true).isEmpty())
        assertTrue(Captured.lost(p.b, false).isEmpty())
        assertEquals(0, Captured.lead(p.b))
    }

    @Test fun capturedAfterATrade() {
        // White has lost its queen and one pawn; Black has only a rook and pawns left
        val p = Fen.parse("r3k3/pppppppp/8/8/8/8/PPPPPPP1/RNB1KBNR w - - 0 1")!!
        assertEquals(listOf(5, 1), Captured.lost(p.b, true))
        assertEquals(listOf(5, 4, 3, 3, 2, 2), Captured.lost(p.b, false))
        assertTrue(Captured.lead(p.b) > 0)
    }
}
