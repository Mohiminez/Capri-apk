package app.capri.mobile

// Reads a real phone screenshot (a raw dump, path in CAPRI_SHOT) and checks that the pilot can find and read the board. Skipped when the file is not there.
class ShotProbeTest {
    @org.junit.Test fun readsARealScreenshot() {
        val f = java.io.File(System.getenv("CAPRI_SHOT") ?: return)
        if (!f.exists()) return
        val b = java.nio.ByteBuffer.wrap(f.readBytes())
        val w = b.int; val h = b.int
        val px = IntArray(w * h) { b.int }
        val g = BoardVision.detect(px, w, h)
        println("PROBE geo=" + (g?.let { "x=${it.x} y=${it.y} cell=${it.cell}" } ?: "NONE"))
        org.junit.Assert.assertNotNull(g)
        val obs = BoardVision.read(px, w, h, g!!)
        println("PROBE pieces=" + obs.count { it != 0 } + " orientation=" + ChessTracker.orientationOfStart(obs))
        org.junit.Assert.assertNotNull(ChessTracker.orientationOfStart(obs))
    }
}

class ShotPilotTest {
    @org.junit.Test fun pilotPlaysFirstMoveOnARealScreenshot() {
        val f = java.io.File(System.getenv("CAPRI_SHOT") ?: return)
        if (!f.exists()) return
        val b = java.nio.ByteBuffer.wrap(f.readBytes())
        val w = b.int; val h = b.int
        val px = IntArray(w * h) { b.int }
        System.getProperty("capri.sf")?.let { sf -> Stockfish.loader = { System.load(sf) } }
        val taps = ArrayList<String>(); val said = ArrayList<String>(); val stat = ArrayList<String>()
        val scr = object : PilotScreen {
            override fun shot() = PilotShot(px, w, h, 1185f / w)
            override fun tap(x: Int, y: Int) { taps.add("tap $x,$y") }
            override fun point(x: Int, y: Int) {}
        }
        var naps = 0
        lateinit var pilot: ChessPilot
        pilot = ChessPilot({ scr }, PilotSettings(level = 3), { said.add(it) }, { }, { null }, { }, { naps++; if (naps > 60 || taps.size >= 2) pilot.stop() }, status = { stat.add(it) })
        pilot.auto = true
        pilot.runNow()
        println("PROBE taps=$taps\nPROBE said=$said\nPROBE status=${stat.distinct()} naps=$naps")
        org.junit.Assert.assertTrue(taps.size >= 2)
    }

    // The phone refuses a screenshot that comes right after another one. The pilot must still move, not think forever.
    @org.junit.Test fun stillMovesWhenEverySecondScreenshotFails() {
        val f = java.io.File(System.getenv("CAPRI_SHOT") ?: return)
        if (!f.exists()) return
        val b = java.nio.ByteBuffer.wrap(f.readBytes())
        val w = b.int; val h = b.int
        val px = IntArray(w * h) { b.int }
        val taps = ArrayList<String>(); var n = 0
        val scr = object : PilotScreen {
            override fun shot(): PilotShot? = if (n++ % 2 == 1) null else PilotShot(px, w, h, 1185f / w)
            override fun tap(x: Int, y: Int) { taps.add("tap $x,$y") }
            override fun point(x: Int, y: Int) {}
        }
        var naps = 0
        lateinit var pilot: ChessPilot
        pilot = ChessPilot({ scr }, PilotSettings(level = 3), { }, { }, { null }, { }, { naps++; if (naps > 80 || taps.size >= 2) pilot.stop() })
        pilot.auto = true
        pilot.runNow()
        org.junit.Assert.assertTrue("taps=$taps naps=$naps", taps.size >= 2)
    }
}
