package app.capri.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class ChessTest {
    private fun fen(s: String): Pos {
        val f = s.split(" ")
        val b = IntArray(64)
        var i = 0
        for (ch in f[0]) {
            if (ch == '/') continue
            if (ch.isDigit()) { i += ch - '0'; continue }
            val v = "pnbrqk".indexOf(ch.lowercaseChar()) + 1
            b[i++] = if (ch.isUpperCase()) v else -v
        }
        var c = 0
        if ('K' in f[2]) c = c or 1; if ('Q' in f[2]) c = c or 2; if ('k' in f[2]) c = c or 4; if ('q' in f[2]) c = c or 8
        val ep = if (f[3] == "-") -1 else (f[3][0] - 'a') + (8 - (f[3][1] - '0')) * 8
        return Pos(b, f[1] == "w", c, ep)
    }

    private fun perft(p: Pos, d: Int): Long = if (d == 0) 1 else Chess.legal(p).sumOf { perft(Chess.make(p, it), d - 1) }

    @Test fun startPosition() {
        assertEquals(20L, perft(Chess.start(), 1))
        assertEquals(400L, perft(Chess.start(), 2))
        assertEquals(8902L, perft(Chess.start(), 3))
    }

    // the classic "Kiwipete" position exercises castling, en passant, promotion and pins
    @Test fun kiwipete() {
        val p = fen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(48L, perft(p, 1))
        assertEquals(2039L, perft(p, 2))
        assertEquals(97862L, perft(p, 3))
    }

    @Test fun enPassantAndPromotion() {
        val p = fen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(14L, perft(p, 1))
        assertEquals(191L, perft(p, 2))
        assertEquals(2812L, perft(p, 3))
    }

    @Test fun foolsMate() {
        val p = fen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")
        assertEquals("mate", Chess.status(p))
    }

    @Test fun findsMateInOne() {
        val p = fen("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")
        assertEquals("a1a8", Chess.best(p, 2).toString())
    }
}
