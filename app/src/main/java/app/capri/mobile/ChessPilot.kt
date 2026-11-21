package app.capri.mobile

import android.os.Handler
import android.os.Looper

// Plays chess in another app for you: she watches the screen, follows the game by itself (no AI, nothing leaves the phone),
// finds the best move with her engine and taps it. In coach mode she only points at the move and tells you.
class ChessPilot(
    private val acc: () -> CapriAccess?,
    private val prefs: Prefs,
    private val say: (String) -> Unit,
    private val seed: () -> Pos?,          // the position from a vision model, for a game that is already under way (may block)
    private val finished: () -> Unit
) {
    @Volatile var running = false
    @Volatile var auto = true
    private var thread: Thread? = null
    private val ui = Handler(Looper.getMainLooper())

    fun start(autoPlay: Boolean) {
        if (running) return
        running = true
        auto = autoPlay
        thread = Thread({ try { loop() } catch (e: Throwable) { Diag.log("chesspilot", e.toString()) } finally { running = false; ui.post { finished() } } }, "chess-pilot").also { it.start() }
    }

    fun stop() { running = false; thread?.interrupt() }

    private fun tell(s: String) = ui.post { say(s) }
    private fun nap(ms: Long) { try { Thread.sleep(ms) } catch (e: InterruptedException) { } }

    private fun loop() {
        var geo: Geo? = null
        var tracker: ChessTracker? = null
        var prevObs: IntArray? = null
        var lostFrames = 0; var noShot = 0; var lostGame = 0
        var sawOpponent = false
        var actedKey = 0L; var actedAt = 0L; var tries = 0
        val talk = ChessTalk(L.ar)
        talk.enabled = ChessEvents.all.filter { prefs.getB("chessTalk_$it", true) }.toSet()
        val millis = when (prefs.getI("chessLevel", 2)) { 1 -> 800L; 2 -> 1500L; 3 -> 2500L; else -> 4000L }
        tell(if (auto) L.t("Okay, I'm on the board. Hands off, I'll play.", "تمام، أنا على الرقعة. لا تلمس، أنا ألعب.") else L.t("I'm watching your game. I'll point at the best move.", "أراقب لعبتك. بأشر لك على أفضل نقلة."))

        while (running) {
            val a = acc()
            if (a == null) { tell(L.t("I lost my accessibility access.", "فقدت صلاحية الوصول.")); return }
            val sh = a.shotPixels()
            if (sh == null) { if (++noShot > 6) { tell(L.t("I can't take screenshots (needs Android 11+ and my accessibility access).", "ما أقدر آخذ لقطات (يحتاج أندرويد 11+ وصلاحية الوصول).")); return }; nap(900); continue }
            noShot = 0
            if (geo == null || !BoardVision.stillThere(sh.px, sh.w, sh.h, geo)) {
                geo = BoardVision.detect(sh.px, sh.w, sh.h)
                if (geo == null) {
                    if (++lostFrames == 12) tell(L.t("I can't see a chess board. Open your game and keep the board on screen.", "ما أشوف رقعة شطرنج. افتح اللعبة وخلي الرقعة على الشاشة."))
                    if (lostFrames > 60) { tell(L.t("No board for a while, so I'm stopping.", "ما فيه رقعة من فترة، فبوقف.")); return }
                    nap(900); continue
                }
                lostFrames = 0
            }
            val obs = BoardVision.read(sh.px, sh.w, sh.h, geo)
            // wait until the picture holds still (pieces slide when they move)
            val before = prevObs
            prevObs = obs
            if (before == null || ChessTracker.mismatches(before, obs) > 0) { nap(450); continue }

            if (tracker == null) {
                val o = ChessTracker.orientationOfStart(obs)
                if (o != null) tracker = ChessTracker(Chess.start(), o)
                else {
                    val p = seed()
                    if (p == null) { tell(L.t("This game is already under way and I couldn't read its position. Start a new game (or an online vision brain), and I'll follow from move one.", "اللعبة شغالة ما قدرت أقرأ وضعها. ابدأ لعبة جديدة (أو عقل أونلاين يشوف الصور) وأتابع من أول نقلة.")); return }
                    val o2 = ChessTracker.orientationFor(p, obs)
                    if (o2 == null) { tell(L.t("What I read from the picture doesn't match the board. Try again in a moment.", "اللي قرأته من الصورة ما يطابق الرقعة. جرب بعد شوي.")); return }
                    tracker = ChessTracker(p, o2)
                }
                val side = if (tracker.flipped) L.t("black", "الأسود") else L.t("white", "الأبيض")
                tell(L.t("I see the board. I'm playing $side for you.", "شفت الرقعة. ألعب $side عنك."))
                sawOpponent = tracker.pos.white == !tracker.flipped && Fen.write(tracker.pos).startsWith(Fen.START.substringBefore(' '))
                nap(300); continue
            }
            val tr = tracker
            val mineWhite = !tr.flipped

            val base = tr.pos
            val r = tr.update(obs)
            if (r.unsure) { nap(450); continue }
            if (r.lost) {
                if (++lostGame >= 6) { tell(L.t("I lost track of the game. Say it again when you start a new one.", "ضيّعت متابعة اللعبة. قولها لي مرة ثانية لما تبدأ وحدة جديدة.")); return }
                nap(600); continue
            }
            lostGame = 0
            if (r.moves.isNotEmpty()) {
                var p = base.let { b -> if (Chess.legal(b).any { it.from == r.moves[0].from && it.to == r.moves[0].to }) b else ChessTracker.swapSide(b) }
                for (m in r.moves) {
                    val after = Chess.make(p, m)
                    val byHer = p.white == mineWhite
                    if (!byHer) sawOpponent = true
                    talk.react(p, m, after, byHer)?.let { line -> if (!byHer || !auto) tell(line) else tell(line) }
                    p = after
                }
                actedKey = 0L; tries = 0
            }
            val st = Chess.status(tr.pos)
            if (st != "playing") {
                tell(if (st == "mate") (if (tr.pos.white != mineWhite) L.t("Checkmate. That's the game, I won.", "كش مات. انتهت اللعبة، فزت.") else L.t("Checkmate... they got me.", "كش مات... غلبوني.")) else L.t("Draw. Fine.", "تعادل. ماشي."))
                return
            }
            if (tr.pos.white == mineWhite && sawOpponent || (tr.pos.white == mineWhite && Fen.write(tr.pos).startsWith(Fen.START.substringBefore(' ')) && mineWhite)) {
                val key = Engine.key(tr.pos)
                val stale = key == actedKey && System.currentTimeMillis() - actedAt > 4500
                if (key != actedKey || stale) {
                    if (stale && ++tries > 2) { tell(L.t("My move isn't going through. Maybe a pop-up is in the way. Stopping.", "نقلتي ما تمر. يمكن نافذة تغطي الرقعة. بوقف.")); return }
                    val best = Engine.analyse(tr.pos, millis, 12) ?: return
                    val g = geo
                    val (fx, fy) = tr.center(g, best.move.from); val (tx, ty) = tr.center(g, best.move.to)
                    val k = sh.k
                    actedKey = key; actedAt = System.currentTimeMillis()
                    val san = San.of(tr.pos, best.move)
                    if (!auto) {
                        tell(L.t("Best move: $san", "أفضل نقلة: $san") + (best.mateIn?.takeIf { it > 0 }?.let { L.t(" (mate in $it)", " (مات في $it)") } ?: ""))
                        a.point((fx * k).toInt(), (fy * k).toInt())
                        nap(700)
                        a.point((tx * k).toInt(), (ty * k).toInt())
                    } else {
                        a.point((fx * k).toInt(), (fy * k).toInt())
                        if (!running) return
                        a.tap((fx * k).toInt(), (fy * k).toInt())
                        nap(280)
                        a.point((tx * k).toInt(), (ty * k).toInt())
                        a.tap((tx * k).toInt(), (ty * k).toInt())
                        if (best.move.promo != 0) { nap(500); a.tap((tx * k).toInt(), (ty * k).toInt()) }   // the queen shows on the destination square
                        ActionLog.add(prefs, "chess", "played $san in a chess app")
                    }
                    prevObs = null
                    nap(900); continue
                }
            }
            nap(600)
        }
    }
}
