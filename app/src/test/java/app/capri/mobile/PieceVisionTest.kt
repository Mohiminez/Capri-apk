package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class PieceVisionTest {
    private class Shot(val px: IntArray, val w: Int, val h: Int)

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(sh: Int) = (((a shr sh) and 255) * (1 - t) + ((b shr sh) and 255) * t).toInt().coerceIn(0, 255)
        return (255 shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun render(pos: Pos, flipped: Boolean, light: Int = 0xF0D9B5, dark: Int = 0xB58863, tint: List<Int> = emptyList(),
                       gx: Float = 12f, gy: Float = 230f, s: Float = 60.4f, w: Int = 520, h: Int = 1000, flat: Boolean = false, seed: Long = 5): Shot {
        val rnd = java.util.Random(seed)
        val px = IntArray(w * h) { (255 shl 24) or 0x312E2B }
        for (y in gy.toInt() until (gy + 8 * s).toInt()) for (x in gx.toInt() until (gx + 8 * s).toInt()) {
            val c = ((x - gx) / s).toInt().coerceIn(0, 7); val r = ((y - gy) / s).toInt().coerceIn(0, 7)
            val sq = if (flipped) 63 - (r * 8 + c) else r * 8 + c
            var col = if ((r + c) % 2 == 0) light else dark
            if (sq in tint) col = mix(col, 0xF5F682, 0.5f)
            var out = (255 shl 24) or col
            val pc = pos.b[sq]
            if (pc != 0) {
                val dx = (x - (gx + (c + 0.5f) * s)) / s; val dy = (y - (gy + (r + 0.5f) * s)) / s
                val p = if (flat) { if (Math.hypot(dx.toDouble(), dy.toDouble()) < 0.3) (255 shl 24) or (if (pc > 0) 0xF8F8F8 else 0x2B2B2B) else null } else PieceArt.pixel(pc, dx, dy)
                if (p != null) out = p
            }
            px[y * w + x] = out
        }
        // light blur and noise
        val res = px.copyOf()
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            var rr = 0; var gg = 0; var bb = 0
            for (j in -1..1) for (i in -1..1) { val p = px[(y + j) * w + x + i]; rr += (p shr 16) and 255; gg += (p shr 8) and 255; bb += p and 255 }
            val n = rnd.nextInt(9) - 4
            res[y * w + x] = (255 shl 24) or ((rr / 9 + n).coerceIn(0, 255) shl 16) or ((gg / 9 + n).coerceIn(0, 255) shl 8) or (bb / 9 + n).coerceIn(0, 255)
        }
        return Shot(res, w, h)
    }

    private fun rd(sh: Shot, g: Geo, set: PieceSet, truth: Pos? = null, flip: Boolean = false): Reading? {
        val sb = StringBuilder(); PieceVision.dbg = sb
        val ob = BoardVision.read(sh.px, sh.w, sh.h, g)
        if (truth != null) { val want = ChessTracker.occupancy(truth); val got = if (flip) IntArray(64) { ob[63 - it] } else ob
            val bad = (0..63).filter { want[it] != got[it] }; if (bad.isNotEmpty()) sb.append("OBS WRONG at $bad fen=${Fen.write(truth)}\n") }
        val r = PieceVision.readPosition(sh.px, sh.w, sh.h, g, ob, set)
        PieceVision.dbg = null
        if (r == null) System.err.println("READ FAILED geo=${g.x},${g.y},${g.cell}\n" + sb.toString().takeLast(900))
        return r
    }

    private fun mv(p: Pos, s: String) = Chess.legal(p).first { it.toString() == s }

    private fun game(n: Int, seed: Long): Pair<Pos, Move?> {
        val rnd = java.util.Random(seed)
        var p = Chess.start(); var last: Move? = null
        repeat(n) { val ms = Chess.legal(p); if (ms.isNotEmpty()) { last = ms[rnd.nextInt(ms.size)]; p = Chess.make(p, last!!) } }
        return Pair(p, last)
    }

    private fun learned(flip: Boolean, light: Int = 0xF0D9B5, dark: Int = 0xB58863): PieceSet {
        val sh = render(Chess.start(), flip, light, dark)
        val g0 = BoardVision.detect(sh.px, sh.w, sh.h)!!
        val g = if (System.getProperty("truegeo") != null) Geo(12f, 230f, 60.4f, 1f) else g0
        val sb = StringBuilder(); PieceVision.dbg = sb
        val r = PieceVision.learn(sh.px, sh.w, sh.h, g, flip)
        PieceVision.dbg = null
        if (r == null) throw AssertionError("learning failed (flip=$flip) geo=${g.x},${g.y},${g.cell}\n" + sb.toString().takeLast(1500))
        return r
    }

    @Test fun learnsFromTheStartAndReadsTheStartBack() {
        for (flip in listOf(false, true)) {
            val set = learned(flip)
            assertTrue(set.complete())
        }
    }

    @Test fun readsMidGamePositionsOnEitherSideOfTheBoard() {
        for (flip in listOf(false, true)) {
            val set = learned(flip)
            var ok = 0
            for (seed in 1L..12L) {
                val (p, last) = game(10 + (seed.toInt() * 5) % 50, seed)
                if (Chess.legal(p).isEmpty()) continue
                val sh = render(p, flip, tint = listOfNotNull(last?.from, last?.to))
                val g = BoardVision.detect(sh.px, sh.w, sh.h) ?: continue
                val obs = BoardVision.read(sh.px, sh.w, sh.h, g)
                val rd = rd(sh, g, set, p, flip)
                assertNotNull("seed=$seed flip=$flip", rd)
                assertArrayEquals("seed=$seed flip=$flip ${Fen.write(p)}", p.b, rd!!.pos.b)
                assertEquals(flip, rd.flipped)
                assertEquals("side to move from the last-move tint, seed=$seed", p.white, rd.pos.white)
                ok++
            }
            assertTrue("read at least 8 of 12, got $ok", ok >= 8)
        }
    }

    @Test fun anotherThemeNeedsItsOwnLearning() {
        val a = learned(false)
        val sh = render(Chess.start(), false, 0xEEEED2, 0x769656)
        val g = BoardVision.detect(sh.px, sh.w, sh.h)!!
        val keyB = PieceVision.paletteKey(sh.px, sh.w, sh.h, g)
        assertNotEquals(a.key, keyB)
        val b = learned(false, 0xEEEED2, 0x769656)
        assertEquals(keyB, b.key)
        val (p, last) = game(24, 3)
        val s2 = render(p, false, 0xEEEED2, 0x769656, tint = listOfNotNull(last?.from, last?.to))
        val g2 = BoardVision.detect(s2.px, s2.w, s2.h)!!
        val rd = rd(s2, g2, b)
        assertNotNull(rd); assertArrayEquals(p.b, rd!!.pos.b)
    }

    @Test fun theKeyIsTheSameWhicheverWayUpTheBoardIs() {
        val k = listOf(false, true).map { f -> val sh = render(Chess.start(), f); PieceVision.paletteKey(sh.px, sh.w, sh.h, BoardVision.detect(sh.px, sh.w, sh.h)!!) }
        assertEquals(k[0], k[1])
    }

    @Test fun savedSetsReadTheSameAsFreshOnes() {
        val set = learned(false)
        val back = PieceSet.fromBytes(set.key, set.toBytes())!!
        val (p, last) = game(30, 9)
        val sh = render(p, false, tint = listOfNotNull(last?.from, last?.to))
        val g = BoardVision.detect(sh.px, sh.w, sh.h)!!
        val rd = rd(sh, g, back)
        assertNotNull(rd); assertArrayEquals(p.b, rd!!.pos.b)
    }

    @Test fun refusesToGuessWhenThePiecesLookDifferent() {
        val set = learned(false)
        val (p, _) = game(20, 4)
        val sh = render(p, false, flat = true)            // plain discs: nothing like what she learned
        val g = BoardVision.detect(sh.px, sh.w, sh.h)!!
        assertNull(PieceVision.readPosition(sh.px, sh.w, sh.h, g, BoardVision.read(sh.px, sh.w, sh.h, g), set))
    }

    @Test fun cannotLearnFromAMidGameScreen() {
        val (p, _) = game(20, 4)
        val sh = render(p, false)
        val g = BoardVision.detect(sh.px, sh.w, sh.h)!!
        assertNull(PieceVision.learn(sh.px, sh.w, sh.h, g, false))
    }
}
