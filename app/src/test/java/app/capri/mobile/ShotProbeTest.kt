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
