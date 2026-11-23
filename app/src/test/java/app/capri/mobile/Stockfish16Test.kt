package app.capri.mobile

// Runs the real Stockfish 16 + network on the host when -Dcapri.sf16=<lib> and -Dcapri.net=<nnue> are given; skipped otherwise.
class Stockfish16Test {
    @org.junit.Test fun loadsTheNetworkAndFindsSensibleMoves() {
        val lib = System.getProperty("capri.sf16") ?: return
        val net = java.io.File(System.getProperty("capri.net") ?: return)
        Stockfish.loader16 = { System.load(lib) }
        Stockfish.netPath = net
        Stockfish.netSource = { net.inputStream() }
        org.junit.Assert.assertTrue("ready: " + Stockfish.lastError, Stockfish.ready())
        org.junit.Assert.assertEquals("Stockfish 16 NNUE", Stockfish.name)
        // mate in one for white: Qh5#-style position (back rank)
        val p = Fen.parse("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1")!!
        val a = Stockfish.analyse(p, 1500, 12, 20)
        println("SF16 move=" + a?.move?.let { Chess.name(it.from) + Chess.name(it.to) } + " depth=" + a?.depth + " score=" + a?.score)
        org.junit.Assert.assertNotNull(a)
        org.junit.Assert.assertEquals("a1a8", Chess.name(a!!.move.from) + Chess.name(a.move.to))
    }
}
