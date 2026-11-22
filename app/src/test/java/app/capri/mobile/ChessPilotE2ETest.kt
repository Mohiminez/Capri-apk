package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

// A pretend chess app on a pretend phone: it draws the board like a screenshot, accepts taps like a real app
// (select a piece, tap the square; promotion waits for one more tap), and has an engine opponent that answers.
class FakeChessApp(var pos: Pos, val iPlayWhite: Boolean, val flipped: Boolean = !iPlayWhite, val opponentDepth: Int = 2,
                   val w: Int = 540, val h: Int = 1100, val gx: Float = 12f, val gy: Float = 230f, val s: Float = 64.7f, val tapsWork: Boolean = true) : PilotScreen {
    var drags = 0
    var selected = -1
    var promo: Move? = null
    var tint = listOf<Int>()
    val taps = ArrayList<Pair<Int, Int>>()
    val points = ArrayList<Pair<Int, Int>>()
    var badTaps = 0
    var myMoves = ArrayList<String>()
    var shots = 0
    var hidden = false
    private var opponentWait = 2
    private val rnd = java.util.Random(3)

    private fun sqAt(x: Int, y: Int): Int {
        val c = ((x - gx) / s).toInt(); val r = ((y - gy) / s).toInt()
        if (c !in 0..7 || r !in 0..7) return -1
        return if (flipped) 63 - (r * 8 + c) else r * 8 + c
    }

    private fun opponentMove() {
        if (pos.white == iPlayWhite || Chess.status(pos) != "playing") return
        val ms = Chess.legal(pos)
        val m = if (rnd.nextInt(4) == 0) ms[rnd.nextInt(ms.size)] else (Engine.analyse(pos, 60, opponentDepth)?.move ?: ms[0])
        val q = if (m.promo != 0 && m.promo != 5) ms.firstOrNull { it.from == m.from && it.to == m.to && it.promo == 5 } ?: m else m
        tint = listOf(q.from, q.to)
        pos = Chess.make(pos, q)
    }

    override fun tap(x: Int, y: Int) {
        taps.add(Pair(x, y))
        if (!tapsWork) return
        val pm = promo
        if (pm != null) { promo = null; tint = listOf(pm.from, pm.to); pos = Chess.make(pos, pm); myMoves.add(pm.toString()); selected = -1; return }
        if (pos.white != iPlayWhite || Chess.status(pos) != "playing") { badTaps++; return }
        val sq = sqAt(x, y)
        if (sq < 0) { badTaps++; return }
        if (selected >= 0) {
            val cands = Chess.legalFrom(pos, selected).filter { it.to == sq }
            if (cands.isNotEmpty()) {
                val m = cands.firstOrNull { it.promo == 5 } ?: cands[0]
                if (m.promo != 0) { promo = m; return }
                tint = listOf(m.from, m.to); pos = Chess.make(pos, m); myMoves.add(m.toString()); selected = -1; opponentWait = 2; return
            }
        }
        val pc = pos.b[sq]
        if (pc != 0 && (pc > 0) == iPlayWhite) selected = sq else { selected = -1; badTaps++ }
    }

    override fun point(x: Int, y: Int) { points.add(Pair(x, y)) }

    override fun drag(x1: Int, y1: Int, x2: Int, y2: Int) {
        drags++
        if (pos.white != iPlayWhite || Chess.status(pos) != "playing") { badTaps++; return }
        val f = sqAt(x1, y1); val t = sqAt(x2, y2)
        val c = Chess.legalFrom(pos, f).filter { it.to == t }
        val m = c.firstOrNull { it.promo == 5 } ?: c.firstOrNull()
        if (f < 0 || m == null) { badTaps++; return }
        tint = listOf(m.from, m.to); pos = Chess.make(pos, m); myMoves.add(m.toString()); selected = -1; opponentWait = 2
    }

    override fun shot(): PilotShot? {
        shots++
        if (pos.white != iPlayWhite && opponentWait-- <= 0) { opponentMove(); opponentWait = 2 }
        if (hidden) return PilotShot(IntArray(w * h) { (255 shl 24) or 0x333333 }, w, h, 1f)
        val dots = if (selected >= 0) Chess.legalFrom(pos, selected).map { it.to } else emptyList()
        return PilotShot(render(), w, h, 1f)
    }

    private fun render(): IntArray {
        val px = IntArray(w * h) { (255 shl 24) or 0x312E2B }
        for (y in 0 until 80) for (x in 0 until w) px[y * w + x] = (255 shl 24) or 0x262421
        val dots = if (selected >= 0) Chess.legalFrom(pos, selected).map { it.to } else emptyList()
        for (y in gy.toInt() until (gy + 8 * s).toInt()) for (x in gx.toInt() until (gx + 8 * s).toInt()) {
            val c = ((x - gx) / s).toInt().coerceIn(0, 7); val r = ((y - gy) / s).toInt().coerceIn(0, 7)
            val sq = if (flipped) 63 - (r * 8 + c) else r * 8 + c
            var col = if ((r + c) % 2 == 0) 0xF0D9B5 else 0xB58863
            if (sq in tint || sq == selected) col = 0xCDD26A
            val cx = gx + (c + 0.5f) * s; val cy = gy + (r + 0.5f) * s
            val d = Math.hypot(((x - cx) / s).toDouble(), ((y - cy) / s).toDouble()).toFloat()
            val pc = pos.b[sq]
            var out = (255 shl 24) or col
            if (pc == 0 && sq in dots && d < 0.17f) out = (255 shl 24) or 0x8A8A6A
            if (pc != 0) {
                val rad = if (Math.abs(pc) == 1) 0.25f else 0.35f
                if (d < rad) out = (255 shl 24) or (if (pc > 0) 0xF8F8F8 else 0x2B2B2B)
                else if (d < rad + 0.04f) out = (255 shl 24) or (if (pc > 0) 0x303030 else 0xE0E0E0)
            }
            px[y * w + x] = out
        }
        return px
    }
}

class ChessPilotE2ETest {
    private class Run(val app: FakeChessApp, val auto: Boolean = true, val seed: Pos? = null, val maxMoves: Int = 6) {
        val said = ArrayList<String>()
        var naps = 0
        lateinit var pilot: ChessPilot
        fun go(): ChessPilot {
            pilot = ChessPilot({ app }, PilotSettings(level = 1), { said.add(it) }, { }, { seed }, { }, { naps++; if (naps > 350) pilot.stop() })
            pilot.auto = auto
            // stop once she has played enough, or the game ended
            val watcher = Thread {
                while (pilot.running) { if (pilot.movesPlayed >= maxMoves) pilot.stop(); Thread.sleep(20) }
            }
            watcher.start()
            pilot.auto = auto
            pilot.runNow()
            return pilot
        }
    }

    @Test fun playsWhiteFromTheStartInsideAnotherApp() {
        val app = FakeChessApp(Chess.start(), true)
        val r = Run(app)
        val p = r.go()
        assertEquals(r.said.toString(), 0, app.badTaps)
        assertTrue("moves=${app.myMoves}", app.myMoves.size >= 4)
        assertNull(p.lastError)
        // every move she made was legal in the app (the fake only applies legal ones) and the game moved on
        assertTrue(app.pos.b.count { it != 0 } > 20)
    }

    @Test fun playsBlackOnAFlippedBoard() {
        val app = FakeChessApp(Chess.start(), false)
        val r = Run(app)
        r.go()
        assertEquals(r.said.toString(), 0, app.badTaps)
        assertTrue("moves=${app.myMoves}", app.myMoves.size >= 4)
    }

    @Test fun fallsBackToDraggingWhenTapsDoNothing() {
        val app = FakeChessApp(Chess.start(), true, tapsWork = false)
        val said = ArrayList<String>()
        var naps = 0
        lateinit var pil: ChessPilot
        pil = ChessPilot({ app }, PilotSettings(level = 1), { said.add(it) }, { }, { null }, { }, { naps++; if (naps > 500) pil.stop() }, 30L)
        val watcher = Thread { while (pil.running) { if (app.myMoves.size >= 3) pil.stop(); Thread.sleep(20) } }
        watcher.start()
        pil.runNow()
        assertTrue("drags=${app.drags} moves=${app.myMoves} said=$said", app.myMoves.size >= 3)
        assertTrue(app.drags >= 3)
    }

    @Test fun coachModeOnlyPoints() {
        val app = FakeChessApp(Chess.start(), true)
        val r = Run(app, auto = false)
        val pil = ChessPilot({ app }, PilotSettings(level = 1), { r.said.add(it) }, { }, { null }, { }, { r.naps++; if (r.naps > 400) r.pilot.stop() })
        r.pilot = pil; pil.auto = false
        pil.runNow()
        assertEquals(0, app.taps.size)
        assertTrue(app.points.size >= 2)
        assertTrue(r.said.any { it.startsWith("Best move") })
    }

    @Test fun gameAlreadyUnderWayWithoutAReadingStopsPolitely() {
        var p = Chess.start()
        for (s in listOf("e2e4", "e7e5", "g1f3")) p = Chess.make(p, Chess.legal(p).first { it.toString() == s })
        val app = FakeChessApp(p, false)
        val r = Run(app, seed = null)
        r.go()
        assertEquals(0, app.taps.size)
        assertTrue(r.said.any { it.contains("already under way") })
    }

    @Test fun midGameWithASeedFollowsAndPlays() {
        var p = Chess.start()
        for (s in listOf("e2e4", "e7e5", "g1f3", "b8c6")) p = Chess.make(p, Chess.legal(p).first { it.toString() == s })
        val app = FakeChessApp(p, true)                      // it's White's move; the seed says so
        val r = Run(app, seed = p, maxMoves = 3)
        r.go()
        assertEquals(r.said.toString(), 0, app.badTaps)
        assertTrue("moves=${app.myMoves}", app.myMoves.size >= 2)
    }

    @Test fun promotionTakesTheExtraTap() {
        val start = Fen.parse("8/P6k/8/8/8/8/8/K7 b - - 0 1")!!
        val app = FakeChessApp(start, true, flipped = false, opponentDepth = 1)
        val r = Run(app, seed = start, maxMoves = 1)
        r.go()
        assertEquals(r.said.toString(), 0, app.badTaps)
        assertTrue("moves=${app.myMoves}", app.myMoves.any { it.endsWith("a8q") })
    }

    @Test fun boardDisappearsAndSheStops() {
        val app = FakeChessApp(Chess.start(), true)
        app.hidden = true
        val r = Run(app)
        r.go()
        assertEquals(0, app.taps.size)
        assertTrue(r.said.any { it.contains("board") })
    }
}
