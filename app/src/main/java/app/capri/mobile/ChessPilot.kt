package app.capri.mobile

import android.os.Handler
import android.os.Looper

class PilotShot(val px: IntArray, val w: Int, val h: Int, val k: Float)   // k = real screen pixels per pixel here

// What the pilot needs from the phone: a screenshot, a tap, and a way to show where she is about to tap.
interface PilotScreen {
    fun shot(): PilotShot?
    fun tap(x: Int, y: Int)
    fun point(x: Int, y: Int)
    fun drag(x1: Int, y1: Int, x2: Int, y2: Int) { }      // some apps want the piece dragged, not tapped
}

class PilotSettings(val level: Int = 2, val talk: Set<String> = ChessEvents.all.toSet(), val ar: Boolean = false)

// Plays chess in another app for you: she watches the screen, follows the game by herself (no AI, nothing leaves the phone),
// finds the best move with her engine and taps it. In coach mode she only points at the move and tells you.
class ChessPilot(
    private val screen: () -> PilotScreen?,
    private val settings: PilotSettings,
    private val say: (String) -> Unit,
    private val log: (String) -> Unit,
    private val seed: () -> Pos?,          // the position from a vision model, for a game that is already under way (may block)
    private val finished: () -> Unit,
    private val sleeper: (Long) -> Unit = { try { Thread.sleep(it) } catch (e: InterruptedException) { } },
    private val staleMs: Long = 5000L
) {
    @Volatile var running = false
    @Volatile var auto = true
    @Volatile var lastError: String? = null
    var movesPlayed = 0
    private var thread: Thread? = null
    private val ui: Handler? = try { Handler(Looper.getMainLooper()) } catch (e: Throwable) { null }

    fun start(autoPlay: Boolean) {
        if (running) return
        running = true
        auto = autoPlay
        thread = Thread({ runNow() }, "chess-pilot").also { it.start() }
    }

    // Runs the whole job on the calling thread (the tests call this directly).
    fun runNow() {
        running = true
        try { loop() } catch (e: Throwable) { Diag.log("chesspilot", e.toString()); lastError = e.toString() } finally { running = false; post { finished() } }
    }

    fun stop() { running = false; thread?.interrupt() }

    private fun post(f: () -> Unit) { val h = ui; if (h != null) h.post(f) else f() }
    private fun tell(s: String) = post { say(s) }
    private fun nap(ms: Long) = sleeper(ms)
    private fun t(en: String, ar: String) = if (settings.ar) ar else en

    private fun sameMove(a: Move, b: Move) = a.from == b.from && a.to == b.to

    private fun loop() {
        var geo: Geo? = null
        var tracker: ChessTracker? = null
        var prevObs: IntArray? = null
        var lostFrames = 0; var noShot = 0; var lostGame = 0
        var sawOpponent = false
        var resynced = false
        var actedKey = 0L; var actedAt = 0L; var tries = 0
        var lastChange = System.currentTimeMillis()
        val talk = ChessTalk(settings.ar)
        talk.enabled = settings.talk
        val millis = when (settings.level) { 1 -> 500L; 2 -> 1200L; 3 -> 2500L; else -> 5000L }
        val keys = ArrayList<Long>()
        var unstable = 0
        var waitSaid = 0L
        tell(if (auto) t("Okay, I'm on the board. Hands off, I'll play.", "تمام، أنا على الرقعة. لا تلمس، أنا ألعب.") else t("I'm watching your game. I'll point at the best move.", "أراقب لعبتك. بأشر لك على أفضل نقلة."))

        while (running) {
            val scr = screen()
            if (scr == null) { tell(t("I lost my accessibility access.", "فقدت صلاحية الوصول.")); return }
            val sh = scr.shot()
            if (sh == null) {
                if (++noShot > 6) { tell(t("I can't take screenshots (needs Android 11+ and my accessibility access).", "ما أقدر آخذ لقطات (يحتاج أندرويد 11+ وصلاحية الوصول).")); return }
                nap(900); continue
            }
            noShot = 0
            if (geo == null || !BoardVision.stillThere(sh.px, sh.w, sh.h, geo)) {
                geo = BoardVision.detect(sh.px, sh.w, sh.h)
                if (geo == null) {
                    prevObs = null
                    if (++lostFrames == 12) tell(t("I can't see a chess board. Open your game and keep the board on screen.", "ما أشوف رقعة شطرنج. افتح اللعبة وخلي الرقعة على الشاشة."))
                    if (lostFrames > 60) { tell(t("No board for a while, so I'm stopping.", "ما فيه رقعة من فترة، فبوقف.")); return }
                    nap(900); continue
                }
                lostFrames = 0
                prevObs = null
            }
            val obs = BoardVision.read(sh.px, sh.w, sh.h, geo)
            // wait until the picture holds still (pieces slide when they move)
            val before = prevObs
            prevObs = obs
            if (before == null) { nap(350); continue }
            if (ChessTracker.mismatches(before, obs) > 0 && unstable++ < 8) { nap(350); continue }   // still sliding: look again (but not forever)
            unstable = 0

            if (tracker == null) {
                val o = ChessTracker.orientationOfStart(obs)
                if (o != null) {
                    tracker = ChessTracker(Chess.start(), o)
                    keys.clear(); keys.add(Engine.key(Chess.start()))
                    sawOpponent = !o       // playing white from the very start: it's my move; as black I wait for white
                } else {
                    tell(t("The game is already under way. Reading the position...", "اللعبة شغالة. أقرأ الوضع..."))
                    val p = seed()
                    if (p == null) { tell(t("I couldn't read this position. Start a new game and I'll follow from move one, or pick an online brain that can see pictures.", "ما قدرت أقرأ هذا الوضع. ابدأ لعبة جديدة وأتابع من أول نقلة، أو اختر عقل أونلاين يشوف الصور.")); return }
                    val o2 = ChessTracker.orientationFor(p, obs)
                    if (o2 == null) { tell(t("What I read from the picture doesn't match the board. Try again in a moment.", "اللي قرأته من الصورة ما يطابق الرقعة. جرب بعد شوي.")); return }
                    tracker = ChessTracker(p, o2)
                    keys.clear(); keys.add(Engine.key(p))
                    // the reading says whose move it is; if it's mine she plays now (a wrong guess shows up as "my move isn't going through")
                    sawOpponent = p.white == !o2
                    tell(t("The game was already going. I'll play from here.", "اللعبة كانت شغالة. بكمل منها."))
                }
                tell(t("I see the board. I'm playing ", "شفت الرقعة. ألعب ") + (if (tracker.flipped) t("black", "الأسود") else t("white", "الأبيض")) + t(" for you.", " عنك."))
                nap(300); continue
            }
            val tr = tracker
            val mineWhite = !tr.flipped

            val base = tr.pos
            val r = tr.update(obs)
            if (r.unsure) { nap(450); continue }
            if (r.lost) {
                lostGame++
                if (lostGame == 3 && !resynced) {      // one try to find our feet again from a fresh reading
                    resynced = true
                    val p = seed()
                    val o2 = p?.let { ChessTracker.orientationFor(it, obs) }
                    if (p != null && o2 != null) { tracker = ChessTracker(p, o2); keys.clear(); keys.add(Engine.key(p)); sawOpponent = false; lostGame = 0; tell(t("I got confused, but I read the board again.", "اختلطت علي، بس قرأت الرقعة من جديد.")); continue }
                }
                if (lostGame >= 6) { tell(t("I lost track of the game. Say it again when you start a new one.", "ضيّعت متابعة اللعبة. قولها لي مرة ثانية لما تبدأ وحدة جديدة.")); return }
                nap(600); continue
            }
            lostGame = 0
            if (r.moves.isNotEmpty()) {
                lastChange = System.currentTimeMillis()
                var p = if (Chess.legal(base).any { sameMove(it, r.moves[0]) }) base else ChessTracker.swapSide(base)
                for (m in r.moves) {
                    val after = Chess.make(p, m)
                    val byHer = p.white == mineWhite
                    if (!byHer) sawOpponent = true
                    talk.react(p, m, after, byHer)?.let { tell(it) }
                    p = after
                    keys.add(Engine.key(after))
                }
                actedKey = 0L; tries = 0
            }
            val st = Chess.result(tr.pos, keys)
            if (st != "playing") {
                tell(when (st) {
                    "mate" -> if (tr.pos.white != mineWhite) t("Checkmate. That's the game, I won.", "كش مات. انتهت اللعبة، فزت.") else t("Checkmate... they got me.", "كش مات... غلبوني.")
                    "stalemate" -> t("Stalemate. A draw.", "جمود. تعادل.")
                    "repetition" -> t("Same position three times. That's a draw.", "نفس الوضع ثلاث مرات. تعادل.")
                    "fifty" -> t("Fifty moves with no capture or pawn move. That's a draw.", "خمسين نقلة بدون أكل أو حركة بيدق. تعادل.")
                    else -> t("Not enough pieces left to mate. A draw.", "ما باقي قطع تكفي للمات. تعادل.")
                })
                return
            }
            if (System.currentTimeMillis() - lastChange > 10 * 60_000L) { tell(t("Nothing has moved for ten minutes, so I'm stopping.", "ما تحرك شي من عشر دقايق، فبوقف.")); return }

            if (tr.pos.white == mineWhite && sawOpponent) {
                val key = Engine.key(tr.pos)
                val stale = key == actedKey && System.currentTimeMillis() - actedAt > staleMs
                if (key != actedKey || stale) {
                    if (stale && ++tries >= 4) { tell(t("My move isn't going through. Maybe a pop-up is in the way, or the app doesn't take taps from me. Stopping.", "نقلتي ما تمر. يمكن نافذة تغطي الرقعة أو التطبيق ما يقبل لمساتي. بوقف.")); return }
                    val best = Engine.analyse(tr.pos, millis, 30, keys, true) ?: return
                    if (!running) return
                    // thinking took a while: look again, and only touch the screen if the board is exactly where and how it was
                    val fresh = scr.shot()
                    val g = geo
                    if (fresh == null || !BoardVision.stillThere(fresh.px, fresh.w, fresh.h, g) ||
                        ChessTracker.mismatches(tr.boardView(BoardVision.read(fresh.px, fresh.w, fresh.h, g)), ChessTracker.occupancy(tr.pos)) > 1) { prevObs = null; nap(400); continue }
                    val (fx, fy) = tr.center(g, best.move.from); val (tx, ty) = tr.center(g, best.move.to)
                    val k = fresh.k
                    val x1 = (fx * k).toInt(); val y1 = (fy * k).toInt(); val x2 = (tx * k).toInt(); val y2 = (ty * k).toInt()
                    actedKey = key; actedAt = System.currentTimeMillis()
                    val san = San.of(tr.pos, best.move)
                    if (!auto) {
                        tell(t("Best move: $san", "أفضل نقلة: $san") + (best.mateIn?.takeIf { it > 0 }?.let { t(" (mate in $it)", " (مات في $it)") } ?: ""))
                        scr.point(x1, y1)
                        nap(700)
                        scr.point(x2, y2)
                    } else {
                        // first try: tap the piece, tap the square. If that didn't go through, drag it; then tap again, slower.
                        when (tries) {
                            1, 3 -> scr.drag(x1, y1, x2, y2)
                            else -> {
                                scr.tap(x1, y1)
                                nap(if (tries == 2) 600L else 260L)
                                if (!running) return
                                scr.tap(x2, y2)
                            }
                        }
                        if (best.move.promo != 0) { nap(500); scr.tap(x2, y2) }   // the queen shows on the destination square
                        if (tries == 0) { movesPlayed++; log("played $san in a chess app") }
                    }
                    prevObs = null
                    nap(900); continue
                }
            }
            // nothing to do: it's their move. Say so once in a while, so a quiet pilot doesn't look dead.
            if (tr.pos.white != mineWhite && System.currentTimeMillis() - lastChange > 40_000L && System.currentTimeMillis() - waitSaid > 60_000L) {
                waitSaid = System.currentTimeMillis()
                tell(t("Waiting for the other side to move.", "أنتظر الخصم يلعب."))
            }
            nap(400)
        }
    }
}
