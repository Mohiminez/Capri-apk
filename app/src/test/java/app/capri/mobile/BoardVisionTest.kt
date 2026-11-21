package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class BoardVisionTest {
    // A fake phone screenshot: app background, a title bar, the board somewhere, and a bottom bar.
    private class Shot(val px: IntArray, val w: Int, val h: Int, val gx: Int, val gy: Int, val s: Int)

    private fun shot(pos: Pos, flipped: Boolean, w: Int = 480, h: Int = 900, gx: Int = 0, gy: Int = 170, s: Int = 60,
                     light: Int = 0xF0D9B5, dark: Int = 0xB58863, dots: List<Int> = emptyList(), tint: List<Int> = emptyList()): Shot {
        val px = IntArray(w * h) { 0x262421 }
        for (y in 0 until 60) for (x in 0 until w) px[y * w + x] = 0x3A3733          // title bar
        for (y in h - 120 until h) for (x in 0 until w) px[y * w + x] = if ((x / 40) % 2 == 0) 0x555555 else 0x333333   // controls
        for (r in 0..7) for (c in 0..7) {
            var col = if ((r + c) % 2 == 0) light else dark
            val sq = if (flipped) 63 - (r * 8 + c) else r * 8 + c
            if (sq in tint) col = 0xCDD26A
            for (y in 0 until s) for (x in 0 until s) px[(gy + r * s + y) * w + gx + c * s + x] = col
            val pc = pos.b[sq]
            val cx = gx + c * s + s / 2; val cy = gy + r * s + s / 2
            if (pc != 0) {
                val rad = (s * 0.36).toInt()
                for (y in -rad..rad) for (x in -rad..rad) if (x * x + y * y <= rad * rad) px[(cy + y) * w + cx + x] = if (pc > 0) 0xFAFAFA else 0x1A1A1A
            } else if (sq in dots) {
                val rad = (s * 0.12).toInt()
                for (y in -rad..rad) for (x in -rad..rad) if (x * x + y * y <= rad * rad) px[(cy + y) * w + cx + x] = 0x808060
            }
        }
        return Shot(px.map { it or (255 shl 24) }.toIntArray(), w, h, gx, gy, s)
    }

    @Test fun findsTheBoardAndReadsTheStartingPosition() {
        val sh = shot(Chess.start(), false)
        val g = BoardVision.detect(sh.px, sh.w, sh.h)
        assertNotNull(g)
        assertEquals(sh.gx.toFloat(), g!!.x, 3f); assertEquals(sh.gy.toFloat(), g.y, 3f); assertEquals(sh.s.toFloat(), g.cell, 1.5f)
        val obs = BoardVision.read(sh.px, sh.w, sh.h, g)
        assertEquals(0, ChessTracker.mismatches(obs, ChessTracker.occupancy(Chess.start())))
        assertEquals(false, ChessTracker.orientationOfStart(obs))
    }

    @Test fun flippedBoardAndOtherColours() {
        val sh = shot(Chess.start(), true, gx = 20, gy = 200, s = 55, light = 0xEEEED2, dark = 0x769656)
        val g = BoardVision.detect(sh.px, sh.w, sh.h)!!
        val obs = BoardVision.read(sh.px, sh.w, sh.h, g)
        assertEquals(true, ChessTracker.orientationOfStart(obs))
    }

    @Test fun noBoardNoDetection() {
        val px = IntArray(400 * 700) { (255 shl 24) or 0x333333 }
        assertNull(BoardVision.detect(px, 400, 700))
    }

    @Test fun legalMoveDotsAndHighlightsDoNotConfuseIt() {
        val sh = shot(Chess.start(), false, dots = listOf(Chess.legal(Chess.start()).first { it.toString() == "e2e4" }.to, 44), tint = listOf(52, 36))
        val g = BoardVision.detect(sh.px, sh.w, sh.h)!!
        val obs = BoardVision.read(sh.px, sh.w, sh.h, g)
        assertEquals(0, ChessTracker.mismatches(obs, ChessTracker.occupancy(Chess.start())))
    }

    private fun mv(p: Pos, s: String) = Chess.legal(p).first { it.toString() == s }

    @Test fun followsAGameMoveByMove() {
        for (flipped in listOf(false, true)) {
            var p = Chess.start()
            val tr = ChessTracker(p, flipped)
            var geo: Geo? = null
            for (s in listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "e1g1")) {
                p = Chess.make(p, mv(p, s))
                val sh = shot(p, flipped)
                geo = geo ?: BoardVision.detect(sh.px, sh.w, sh.h)
                val r = tr.update(BoardVision.read(sh.px, sh.w, sh.h, geo!!))
                assertFalse(s, r.lost); assertEquals(s, 1, r.moves.size); assertEquals(s, r.moves[0].toString())
            }
            assertEquals(Fen.write(p), Fen.write(tr.pos))
        }
    }

    @Test fun opponentRepliedWhileWeWerentLooking() {
        var p = Chess.start()
        val tr = ChessTracker(p, false)
        p = Chess.make(p, mv(p, "d2d4")); p = Chess.make(p, mv(p, "d7d5"))
        val sh = shot(p, false)
        val g = BoardVision.detect(sh.px, sh.w, sh.h)!!
        val r = tr.update(BoardVision.read(sh.px, sh.w, sh.h, g))
        assertEquals(listOf("d2d4", "d7d5"), r.moves.map { it.toString() })
    }

    @Test fun captureEnPassantAndPromotion() {
        var p = Fen.parse("4k3/P7/8/3pP3/8/8/8/4K3 w - d6 0 1")!!
        val tr = ChessTracker(p, false)
        p = Chess.make(p, mv(p, "e5d6"))          // en passant
        var sh = shot(p, false); var g = BoardVision.detect(sh.px, sh.w, sh.h)!!
        var r = tr.update(BoardVision.read(sh.px, sh.w, sh.h, g))
        assertEquals("e5d6", r.moves.single().toString())
        p = Chess.make(p, mv(p, "e8f8"))
        sh = shot(p, false); r = tr.update(BoardVision.read(sh.px, sh.w, sh.h, g))
        p = Chess.make(p, mv(p, "a7a8q"))
        sh = shot(p, false); r = tr.update(BoardVision.read(sh.px, sh.w, sh.h, g))
        assertEquals("a7a8q", r.moves.single().toString())
    }

    @Test fun lostWhenSomethingImpossibleAppears() {
        val tr = ChessTracker(Chess.start(), false)
        val weird = IntArray(64) { if (it % 3 == 0) 1 else 0 }
        assertTrue(tr.update(weird).lost)
    }

    @Test fun squareCentres() {
        val tr = ChessTracker(Chess.start(), false)
        val g = Geo(10f, 100f, 50f, 1f)
        val (x, y) = tr.center(g, 52)         // e2
        assertEquals(10f + 4.5f * 50f, x, 0.1f); assertEquals(100f + 6.5f * 50f, y, 0.1f)
        val fl = ChessTracker(Chess.start(), true)
        val (x2, y2) = fl.center(g, 52)
        assertEquals(10f + 3.5f * 50f, x2, 0.1f); assertEquals(100f + 1.5f * 50f, y2, 0.1f)
    }
}

class ChessPilotAskTest {
    @Test fun phrases() {
        assertTrue(QuickAsks.match("play chess for me")!!.contains("[[chess:auto]]"))
        assertTrue(QuickAsks.match("play this game for me")!!.contains("[[chess:auto]]"))
        assertTrue(QuickAsks.match("العبي عني")!!.contains("[[chess:auto]]"))
        assertTrue(QuickAsks.match("coach me")!!.contains("[[chess:coach]]"))
        assertTrue(QuickAsks.match("stop chess")!!.contains("[[chess:stop]]"))
        assertTrue(QuickAsks.match("play chess")!!.contains("[[chess:open]]"))
        assertTrue(QuickAsks.match("play game of thrones theme")!!.contains("[[music:"))
    }
}
