package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

// Harder fake screenshots: fractional cell size, outlined pieces of different sizes, noise, blur, a frame, rank/file letters,
// move dots as big as real apps draw them, last-move tint, and a check glow.
class BoardVisionRealisticTest {
    private class Shot(val px: IntArray, val w: Int, val h: Int, val gx: Float, val gy: Float, val s: Float)

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(sh: Int) = (((a shr sh) and 255) * (1 - t) + ((b shr sh) and 255) * t).toInt().coerceIn(0, 255)
        return (255 shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun render(pos: Pos, flipped: Boolean, w: Int = 540, h: Int = 1100, gx: Float = 12f, gy: Float = 230f, s: Float = 64.7f,
                       light: Int = 0xEEEED2, dark: Int = 0x769656, dotR: Float = 0.17f, dots: List<Int> = emptyList(),
                       tint: List<Int> = emptyList(), noise: Int = 5, blur: Boolean = true, frame: Boolean = true, seed: Long = 7): Shot {
        val rnd = java.util.Random(seed)
        val px = IntArray(w * h) { (255 shl 24) or 0x312E2B }
        for (y in 0 until 80) for (x in 0 until w) px[y * w + x] = (255 shl 24) or 0x262421
        for (y in h - 160 until h) for (x in 0 until w) px[y * w + x] = (255 shl 24) or (if ((x / 55) % 2 == 0) 0x6B6A68 else 0x3C3A38)
        if (frame) for (y in (gy - 6).toInt() until (gy + 8 * s + 6).toInt()) for (x in (gx - 6).toInt() until (gx + 8 * s + 6).toInt()) px[y * w + x] = (255 shl 24) or 0x1E1C1A
        for (y in gy.toInt() until (gy + 8 * s).toInt()) for (x in gx.toInt() until (gx + 8 * s).toInt()) {
            val c = ((x - gx) / s).toInt().coerceIn(0, 7); val r = ((y - gy) / s).toInt().coerceIn(0, 7)
            val sq = if (flipped) 63 - (r * 8 + c) else r * 8 + c
            var col = if ((r + c) % 2 == 0) light else dark
            if (sq in tint) col = mix(col, 0xF5F682, 0.55f)
            val cx = gx + (c + 0.5f) * s; val cy = gy + (r + 0.5f) * s
            val dx = (x - cx) / s; val dy = (y - cy) / s
            val d = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val pc = pos.b[sq]
            var out = (255 shl 24) or col
            if (pc == 0 && sq in dots && d < dotR) out = mix(out, 0x000000, 0.2f)
            if (pc != 0) {
                val rad = if (Math.abs(pc) == 1) 0.25f else 0.35f
                if (d < rad) out = (255 shl 24) or (if (pc > 0) 0xF8F8F8 else 0x2B2B2B)
                else if (d < rad + 0.04f) out = (255 shl 24) or (if (pc > 0) 0x303030 else 0xE0E0E0)
                if (Math.abs(pc) == 6 && sq == pos.kingSq(pos.white) && Chess.inCheck(pos, pos.white) && d >= rad + 0.04f && d < 0.5f) out = mix(out, 0xFF0000, 0.35f)
            }
            // file/rank letters in a corner
            if (dx < -0.32f && dy < -0.32f && (x + y) % 3 == 0) out = (255 shl 24) or (if ((r + c) % 2 == 0) dark else light)
            px[y * w + x] = out
        }
        var res = px
        if (blur) {
            res = px.copyOf()
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                var rr = 0; var gg = 0; var bb = 0
                for (j in -1..1) for (i in -1..1) { val p = px[(y + j) * w + x + i]; rr += (p shr 16) and 255; gg += (p shr 8) and 255; bb += p and 255 }
                res[y * w + x] = (255 shl 24) or ((rr / 9) shl 16) or ((gg / 9) shl 8) or (bb / 9)
            }
        }
        if (noise > 0) for (i in res.indices) {
            val n = rnd.nextInt(noise * 2 + 1) - noise
            val p = res[i]
            res[i] = (255 shl 24) or ((((p shr 16) and 255) + n).coerceIn(0, 255) shl 16) or ((((p shr 8) and 255) + n).coerceIn(0, 255) shl 8) or ((p and 255) + n).coerceIn(0, 255)
        }
        return Shot(res, w, h, gx, gy, s)
    }

    private fun mv(p: Pos, s: String) = Chess.legal(p).first { it.toString() == s }

    @Test fun realisticStartingBoard() {
        for (flip in listOf(false, true)) {
            val sh = render(Chess.start(), flip)
            val g = BoardVision.detect(sh.px, sh.w, sh.h)
            assertNotNull("detect flip=$flip", g)
            assertEquals(sh.gx, g!!.x, 4f); assertEquals(sh.gy, g.y, 4f); assertEquals(sh.s, g.cell, 1.5f)
            val obs = BoardVision.read(sh.px, sh.w, sh.h, g)
            assertEquals("flip=$flip", 0, ChessTracker.mismatches(if (flip) IntArray(64) { obs[63 - it] } else obs, ChessTracker.occupancy(Chess.start())))
        }
    }

    @Test fun bigMoveDotsAreNotPieces() {
        val p = Chess.start()
        val targets = Chess.legalFrom(p, 62).map { it.to } + listOf(40, 42, 44, 45)       // knight dots and more
        val sh = render(p, false, dots = targets + (16..31).toList(), dotR = 0.19f)
        val g = BoardVision.detect(sh.px, sh.w, sh.h)!!
        assertEquals(0, ChessTracker.mismatches(BoardVision.read(sh.px, sh.w, sh.h, g), ChessTracker.occupancy(p)))
    }

    @Test fun tintAndCheckGlow() {
        var p = Chess.start()
        for (s in listOf("e2e4", "f7f6", "d1h5", "g7g6", "h5g6")) p = Chess.make(p, mv(p, s))   // check on the king
        val sh = render(p, false, tint = listOf(Chess.legal(Chess.start()).first { it.toString() == "e2e4" }.to, 52))
        val g = BoardVision.detect(sh.px, sh.w, sh.h)!!
        assertEquals(0, ChessTracker.mismatches(BoardVision.read(sh.px, sh.w, sh.h, g), ChessTracker.occupancy(p)))
    }

    @Test fun otherBoardThemes() {
        val themes = listOf(Pair(0xF0D9B5, 0xB58863), Pair(0xDEE3E6, 0x8CA2AD), Pair(0xE8E8E8, 0x6D8FB5), Pair(0xEEEED2, 0x769656))
        for ((l, d) in themes) {
            val sh = render(Chess.start(), false, light = l, dark = d)
            val g = BoardVision.detect(sh.px, sh.w, sh.h)
            assertNotNull("theme ${l.toString(16)}", g)
            assertEquals(0, ChessTracker.mismatches(BoardVision.read(sh.px, sh.w, sh.h, g!!), ChessTracker.occupancy(Chess.start())))
        }
    }

    @Test fun smallBoardNotFillingTheWidth() {
        val sh = render(Chess.start(), false, w = 600, h = 1000, gx = 90f, gy = 200f, s = 52f)
        val g = BoardVision.detect(sh.px, sh.w, sh.h)!!
        assertEquals(90f, g.x, 4f); assertEquals(52f, g.cell, 1.5f)
    }

    @Test fun followsAFullOpeningWithDotsAndTint() {
        var p = Chess.start()
        val tr = ChessTracker(p, false)
        var geo: Geo? = null
        val line = "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 d2d3 f8c5 c1g5 d7d6 b1c3 h7h6 g5h4 g7g5 h4g3 c8g4".split(" ")
        var last = -1
        for (s in line) {
            val m = mv(p, s); p = Chess.make(p, m)
            val sh = render(p, false, dots = listOf(20, 28, 36), tint = listOf(m.from, m.to), seed = s.hashCode().toLong())
            geo = geo ?: BoardVision.detect(sh.px, sh.w, sh.h)
            val r = tr.update(BoardVision.read(sh.px, sh.w, sh.h, geo!!))
            assertFalse("$s lost", r.lost); assertEquals(s, listOf(s), r.moves.map { it.toString() })
            last = r.moves.size
        }
        assertEquals(1, last)
        assertEquals(Fen.write(p), Fen.write(tr.pos))
    }
}
