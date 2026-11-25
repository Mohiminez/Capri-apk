package app.capri.mobile

// The real Stockfish 11 (C++, GPL-3), compiled into the app as libstockfish.so and driven through its normal UCI text protocol.
// If the library is missing or breaks for any reason, ready() is false and Engine falls back to Capri's own search: nothing else changes.
internal object StockfishNative {
    @JvmStatic external fun start(hashMb: Int, threads: Int): Boolean
    @JvmStatic external fun send(cmd: String)
    @JvmStatic external fun read(timeoutMs: Int): String?
    @JvmStatic external fun drain()
}

// Stockfish 16 with its neural network (NNUE): about 300 Elo stronger than version 11. Same protocol, its own library; the network is an asset copied to the app's files.
internal object Stockfish16Native {
    @JvmStatic external fun start(hashMb: Int, threads: Int): Boolean
    @JvmStatic external fun send(cmd: String)
    @JvmStatic external fun read(timeoutMs: Int): String?
    @JvmStatic external fun drain()
    @JvmStatic external fun netLoaded(): Boolean
}

object Stockfish {
    const val NET_NAME = "nn-5af11540bbfe.nnue"
    const val NET_SIZE = 40119326L

    private var state = 0 // 0 not tried, 1 running, -1 unavailable
    private var version = 11
    internal var loader16: () -> Unit = { System.loadLibrary("stockfish16") }
    /** Where the network file lives and how to read it from the app; set once by [prepare]. Tests point [netPath] at a file directly. */
    @Volatile internal var netPath: java.io.File? = null
    @Volatile internal var netSource: (() -> java.io.InputStream)? = null

    fun prepare(ctx: android.content.Context) {
        if (netSource != null) return
        val app = ctx.applicationContext
        netPath = java.io.File(java.io.File(app.filesDir, "sf").also { it.mkdirs() }, NET_NAME)
        // Not bundled (keeps the APK ~40 MB smaller): a copy in assets wins if someone builds with one, otherwise it is downloaded once.
        netSource = {
            try { app.assets.open(NET_NAME) } catch (e: java.io.FileNotFoundException) {
                val c = java.net.URL(NET_URL).openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = true
                c.inputStream
            }
        }
        // fetch quietly in the background so the strong engine is ready by the first game; Stockfish 11 plays until then
        if (!netReady() && !hasAsset(app)) startFetch()
    }

    fun netReady() = netPath?.let { it.exists() && it.length() == NET_SIZE } == true
    @Volatile var downloaded = 0L
        private set
    @Volatile var fetchError = ""
        private set

    /** One line for the screen: what the strong engine's file is doing. */
    fun netStatus(ctx: android.content.Context): String {
        if (netReady() || hasAsset(ctx)) return "ready"
        if (fetching) return "downloading " + (downloaded * 100 / NET_SIZE).coerceIn(0, 99) + "%"
        return if (fetchError.isNotEmpty()) "failed: $fetchError" else "missing"
    }

    /** Downloads the Stockfish 16 network (40 MB) in the background. Safe to call again after a failure. */
    fun startFetch() {
        if (fetching || netReady()) return
        val f = netPath ?: return
        fetching = true; downloaded = 0; fetchError = ""
        Thread {
            try {
                val tmp = java.io.File(f.path + ".part")
                netSource!!().use { i -> tmp.outputStream().use { o ->
                    val buf = ByteArray(1 shl 16)
                    while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); downloaded += n }
                } }
                if (tmp.length() == NET_SIZE) tmp.renameTo(f) else { fetchError = "incomplete (" + tmp.length() / 1024 + " KB)"; tmp.delete() }
            } catch (t: Throwable) { fetchError = (t.message ?: t.javaClass.simpleName).take(60); lastError = "net download: $t" } finally { fetching = false }
        }.also { it.isDaemon = true }.start()
    }
    @Volatile private var fetching = false
    const val NET_URL = "https://github.com/official-stockfish/networks/raw/master/nn-5af11540bbfe.nnue"
    private fun hasAsset(app: android.content.Context) = try { app.assets.open(NET_NAME).close(); true } catch (e: Exception) { false }


    private fun send(c: String) = if (version == 16) Stockfish16Native.send(c) else StockfishNative.send(c)
    private fun read(ms: Int): String? = if (version == 16) Stockfish16Native.read(ms) else StockfishNative.read(ms)
    private fun drain() = if (version == 16) Stockfish16Native.drain() else StockfishNative.drain()

    // The NNUE engine, only if its network is really there and loads. Anything wrong returns false and the old engine takes over.
    private fun tryNew(): Boolean {
        try {
            val f = netPath ?: return false
            if (fetching && !f.exists()) return false
            if (!f.exists() || f.length() != NET_SIZE) {
                val src = netSource ?: return false
                val tmp = java.io.File(f.path + ".part")
                src().use { i -> tmp.outputStream().use { o -> i.copyTo(o, 1 shl 16) } }
                if (tmp.length() != NET_SIZE || !tmp.renameTo(f)) { tmp.delete(); throw IllegalStateException("network file is incomplete") }
            }
            loader16()
            val cores = Runtime.getRuntime().availableProcessors()
            if (!Stockfish16Native.start(96, (cores / 2).coerceIn(1, 4))) throw IllegalStateException("engine 16 did not start")
            version = 16
            Stockfish16Native.send("setoption name EvalFile value ${f.path}")
            Stockfish16Native.send("isready")
            val t0 = System.currentTimeMillis(); var ok = false
            while (System.currentTimeMillis() - t0 < 20000) { val l = Stockfish16Native.read(200) ?: continue; if (l.startsWith("readyok")) { ok = true; break } }
            if (!ok) throw IllegalStateException("engine 16 silent")
            if (!Stockfish16Native.netLoaded()) throw IllegalStateException("network did not load")
            return true
        } catch (t: Throwable) {
            lastError = "sf16: $t"
            version = 11
            return false
        }
    }

    private var skill = 20
    /** Tests swap this to point at a library built on the desktop. */
    internal var loader: () -> Unit = { System.loadLibrary("stockfish") }
    @Volatile var lastError: String? = null
    @Volatile var enabled = true

    val name: String get() = if (state == 1) (if (version == 16) "Stockfish 16 NNUE" else "Stockfish 11") else "Capri"

    @Synchronized fun ready(): Boolean {
        if (!enabled) return false
        if (state == 1) return true
        if (state == -1) return false
        if (tryNew()) { state = 1; return true }
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
        send("setoption name Skill Level value $level")
        skill = level
    }

    /** Best move by Stockfish for [p], thinking up to [millis]. Null if the engine is unavailable or answered something illegal. */
    @Synchronized fun analyse(p: Pos, millis: Long, maxDepth: Int = 30, level: Int = 20): Analysis? {
        if (!ready()) return null
        val legal = Chess.legal(p)
        if (legal.isEmpty()) return null
        try {
            drain()
            setSkill(level.coerceIn(0, 20))
            send("position fen ${Fen.write(p)}")
            send("go movetime ${millis.coerceAtLeast(30)} depth ${maxDepth.coerceIn(1, 60)}")
            val deadline = System.currentTimeMillis() + millis + 4000
            var depth = 0; var score = 0; var pvText = emptyList<String>(); var best: String? = null
            while (System.currentTimeMillis() < deadline) {
                val l = read(100) ?: continue
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
            if (best == null) { send("stop"); val t1 = System.currentTimeMillis()
                while (System.currentTimeMillis() - t1 < 1500) { val l = read(100) ?: continue; if (l.startsWith("bestmove")) { best = l.split(' ').getOrNull(1); break } } }
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
