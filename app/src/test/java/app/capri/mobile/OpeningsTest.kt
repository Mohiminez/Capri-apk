package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class OpeningsTest {
    private fun n(s: String) = Openings.name(s.split(' '))
    @Test fun namesTheLongestLine() {
        assertEquals("Ruy Lopez: Morphy Defense", n("e4 e5 Nf3 Nc6 Bb5 a6 Ba4 Nf6"))
        assertEquals("Sicilian Defense: Najdorf Variation", n("e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Be3"))
        assertEquals("Queen's Gambit Declined", n("d4 d5 c4 e6 Nc3"))
        assertEquals("King's Pawn Game", n("e4 e5 Qh5"))
        assertEquals("Italian Game: Giuoco Piano", n("e4+ e5 Nf3 Nc6 Bc4 Bc5"))
    }
    @Test fun noNameWhenNothingMatches() { assertNull(n("a3 a6")); assertNull(Openings.name(emptyList())) }
    @Test fun aLineNeedsAllItsMoves() { assertEquals("Sicilian Defense", n("e4 c5 Nf3")) }
}
