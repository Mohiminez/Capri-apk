package app.capri.mobile

// The real Stockfish 11 (C++, GPL-3), compiled into the app as libstockfish.so and driven through its normal UCI text protocol.
// If the library is missing or breaks for any reason, ready() is false and Engine falls back to Capri's own search: nothing else changes.
internal object StockfishNative {
    @JvmStatic external fun start(hashMb: Int, threads: Int): Boolean
    @JvmStatic external fun send(cmd: String)
    @JvmStatic external fun read(timeoutMs: Int): String?
    @JvmStatic external fun drain()
}

object Stockfish {
    private var state = 0 // 0 not tried, 1 running, -1 unavailable
    private var skill = 20
    /** Tests swap this to point at a library built on the desktop. */
    internal var loader: () -> Unit = { System.loadLibrary("stockfish") }
    @Volatile var lastError: String? = null
    @Volatile var enabled = true

    val name: String get() = if (state == 1) "Stockfish 11" else "Capri"

    @Synchronized fun ready(): Boolean {
        if (!enabled) return false
        if (state == 1) return true
        if (state == -1) return false
        try {
            loader()
            val cores = Runtime.getRuntime().availableProcessors()
            val threads = (cores / 2).coerceIn(1, 4)
            if (!StockfishNative.start(64, threads)) throw IllegalStateException("engine did not start")
            // wait for "readyok" (the answer to the first isready)
            val t0 = System.currentTimeMillis()
            var ok = false
            while (System.currentTimeMillis() - t0 < 8000) {
                val l = StockfishNative.read(200) ?: continue
                if (l.startsWith("readyok")) { ok = true; break }
            }
            if (!ok) throw IllegalStateException("engine silent")
            state = 1
        } catch (t: Throwable) {
            lastError = t.toString()
            state = -1
        }
        return state == 1
    }

    private fun setSkill(level: Int) {
        if (level == skill) return
        StockfishNative.send("setoption name Skill Level value $level")
        skill = level
    }

    /** Best move by Stockfish for [p], thinking up to [millis]. Null if the engine is unavailable or answered something illegal. */
    @Synchronized fun analyse(p: Pos, millis: Long, maxDepth: Int = 30, level: Int = 20): Analysis? {
        if (!ready()) return null
        val legal = Chess.legal(p)
        if (legal.isEmpty()) return null
        try {
            StockfishNative.drain()
            setSkill(level.coerceIn(0, 20))
            StockfishNative.send("position fen ${Fen.write(p)}")
            StockfishNative.send("go movetime ${millis.coerceAtLeast(30)} depth ${maxDepth.coerceIn(1, 60)}")
            val deadline = System.currentTimeMillis() + millis + 4000
            var depth = 0; var score = 0; var pvText = emptyList<String>(); var best: String? = null
            while (System.currentTimeMillis() < deadline) {
                val l = StockfishNative.read(100) ?: continue
                if (l.startsWith("info ") && " score " in l && " pv " in l) {
                    val tk = l.split(' ')
                    val d = tk.indexOf("depth").let { if (it >= 0) tk.getOrNull(it + 1)?.toIntOrNull() else null } ?: continue
                    val si = tk.indexOf("score")
                    val kind = tk.getOrNull(si + 1); val v = tk.getOrNull(si + 2)?.toIntOrNull() ?: continue
                    if (tk.getOrNull(si + 3) == "lowerbound" || tk.getOrNull(si + 3) == "upperbound") continue
                    depth = d
                    score = if (kind == "mate") (if (v > 0) 100000 - (2 * v - 1) else -(100000 - (2 * -v))) else v
                    pvText = tk.drop(tk.indexOf("pv") + 1)
                } else if (l.startsWith("bestmove")) { best = l.split(' ').getOrNull(1); break }
            }
            if (best == null) { StockfishNative.send("stop"); val t1 = System.currentTimeMillis()
                while (System.currentTimeMillis() - t1 < 1500) { val l = StockfishNative.read(100) ?: continue; if (l.startsWith("bestmove")) { best = l.split(' ').getOrNull(1); break } } }
            val mv = legal.firstOrNull { it.toString() == best } ?: return null
            // the principal variation, kept only while every move is legal
            val pv = ArrayList<Move>(); var q = p
            for (s in pvText) { val m = Chess.legal(q).firstOrNull { it.toString() == s } ?: break; pv.add(m); q = Chess.make(q, m) }
            if (pv.isEmpty() || pv[0].toString() != mv.toString()) { pv.clear(); pv.add(mv) }
            return Analysis(mv, score, depth, pv)
        } catch (t: Throwable) {
            lastError = t.toString()
            return null
        }
    }
}
