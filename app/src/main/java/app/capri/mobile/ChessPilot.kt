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
    fun arrow(x1: Int, y1: Int, x2: Int, y2: Int, cell: Int) { }     // a drawn arrow over the board (coach mode)
    fun frame(x: Int, y: Int, size: Int) { }                           // an outline around the board
    fun clearArrow() { }
    fun boardSeen(x: Int, y: Int, size: Int) { }                       // where the board is on the real screen, so her body can stay put when it is not in the way
    fun clearOverlay() { }
}

class PilotSettings(val level: Int = 2, val talk: Set<String> = ChessEvents.all.toSet(), val ar: Boolean = false, val store: PieceStore? = null, val frame: Boolean = false)

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
    private val staleMs: Long = 5000L,
    private val status: (String) -> Unit = { }     // a short live line for the on-screen chip: what she is doing right now
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
        try { loop() } catch (e: Throwable) { Diag.log("chesspilot", e.toString()); lastError = e.toString() } finally { running = false; try { screen()?.clearOverlay() } catch (e: Throwable) { }; post { status(""); finished() } }
    }

    // At the opening position every piece is known, so that's where she learns the look of this app's pieces (once per theme).
    private fun learnPieces(sh: PilotShot, g: Geo, flipped: Boolean) {
        val st = settings.store ?: return
        try {
            val key = PieceVision.paletteKey(sh.px, sh.w, sh.h, g)
            if (st.load(key)?.complete() == true) return
            val set = PieceVision.learn(sh.px, sh.w, sh.h, g, flipped) ?: return
            st.save(set)
            log("learned the pieces of this chess theme")
        } catch (e: Throwable) { }
    }

    private fun readOwn(sh: PilotShot, g: Geo, obs: IntArray): Reading? {
        val st = settings.store ?: return null
        return try {
            val set = st.load(PieceVision.paletteKey(sh.px, sh.w, sh.h, g)) ?: return null
            val rd = PieceVision.readPosition(sh.px, sh.w, sh.h, g, obs, set) ?: return null
            // it must agree with what the screen shows, square for square
            val want = ChessTracker.occupancy(rd.pos)
            val shown = if (rd.flipped) IntArray(64) { obs[63 - it] } else obs
            if ((0..63).any { want[it] != shown[it] }) null else rd
        } catch (e: Throwable) { null }
    }

    fun stop() { running = false; thread?.interrupt() }

    private fun post(f: () -> Unit) { val h = ui; if (h != null) h.post(f) else f() }
    private fun tell(s: String) = post { say(s) }
    private fun st(s: String) = post { status(s) }
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
        var freshFails = 0
        var stuckRounds = 0
        var waitSaid = 0L
        tell(if (auto) t("Okay, I'm on the board. Hands off, I'll play.", "تمام، أنا على الرقعة. لا تلمس، أنا ألعب.") else t("I'm watching your game. I'll point at the best move.", "أراقب لعبتك. بأشر لك على أفضل نقلة."))

        st(t("\uD83D\uDD0D Looking for the board...", "\uD83D\uDD0D أدوّر على الرقعة..."))
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
                    st(t("\uD83D\uDD0D No board on screen. Open your chess game", "\uD83D\uDD0D ما أشوف رقعة. افتح لعبة الشطرنج"))
                    if (++lostFrames == 4) tell(t("I'm ready but I can't see a board yet. Open your chess game (chess.com, Lichess...) and I'll start.", "جاهزة بس ما أشوف رقعة للحين. افتح لعبة الشطرنج (chess.com أو Lichess...) وأبدأ."))
                    if (lostFrames == 12) tell(t("I can't see a chess board. Open your game and keep the board on screen.", "ما أشوف رقعة شطرنج. افتح اللعبة وخلي الرقعة على الشاشة."))
                    if (lostFrames > 60) { tell(t("No board for a while, so I'm stopping.", "ما فيه رقعة من فترة، فبوقف.")); return }
                    nap(900); continue
                }
                lostFrames = 0
                prevObs = null
                st(t("\u2705 Board found", "\u2705 لقيت الرقعة"))
                geo?.let { scr.boardSeen((it.x * sh.k).toInt(), (it.y * sh.k).toInt(), (it.cell * 8 * sh.k).toInt()) }
                if (settings.frame) geo?.let { scr.frame((it.x * sh.k).toInt(), (it.y * sh.k).toInt(), (it.cell * 8 * sh.k).toInt()) }
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
                    learnPieces(sh, geo, o)
                } else {
                    // a game under way: first try with the piece shapes she learned from this app (no brain, no network)
                    val mine = readOwn(sh, geo, obs)
                    if (mine != null) {
                        tracker = ChessTracker(mine.pos, mine.flipped)
                        keys.clear(); keys.add(Engine.key(mine.pos))
                        sawOpponent = mine.pos.white == !mine.flipped
                        tell(t("The game is already going. I read the pieces myself and I'll play from here.", "اللعبة شغالة. قرأت القطع بنفسي وبكمل منها."))
                        tell(t("I see the board. I'm playing ", "شفت الرقعة. ألعب ") + (if (mine.flipped) t("black", "الأسود") else t("white", "الأبيض")) + t(" for you.", " عنك."))
                        nap(300); continue
                    }
                    tell(t("The game is already under way. Reading the position...", "اللعبة شغالة. أقرأ الوضع..."))
                    val p = seed()
                    if (p == null) { tell(t("I couldn't read this position. Start a new game and I'll follow from move one, or pick an online brain that can see pictures.", "ما قدرت أقرأ هذا الوضع. ابدأ لعبة جديدة وأتابع من أول نقلة وأتعلم شكل قطعك، أو اختر عقل أونلاين يشوف الصور.")); return }
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
                st(t("\uD83D\uDD04 Lost the thread, re-reading the board...", "\uD83D\uDD04 ضاع علي الخيط، أعيد قراءة الرقعة..."))
                if (lostGame >= 2) {
                    // first her own eyes (the pieces she learned), then a vision model if there is one, and she keeps trying instead of stopping
                    val mine = readOwn(sh, geo, obs)
                    if (mine != null) { tracker = ChessTracker(mine.pos, mine.flipped); keys.clear(); keys.add(Engine.key(mine.pos)); sawOpponent = true; lostGame = 0; actedKey = 0L; tries = 0; log("re-read the board"); continue }
                }
                if (lostGame == 4) {
                    val p = seed()
                    val o2 = p?.let { ChessTracker.orientationFor(it, obs) }
                    if (p != null && o2 != null) { tracker = ChessTracker(p, o2); keys.clear(); keys.add(Engine.key(p)); sawOpponent = true; lostGame = 0; actedKey = 0L; tries = 0; continue }
                }
                if (lostGame == 8) tell(t("I'm having trouble following the board. I'll keep trying, and you can make your move yourself if you like.", "أواجه صعوبة بمتابعة الرقعة. بكمل المحاولة، وإذا تبي العب نقلتك بنفسك."))
                if (lostGame >= 60) { tell(t("I couldn't find my way back into this game, so I'm stopping. Say \"play for me\" to start again.", "ما قدرت أرجع للعبة، فبوقف. قل «العبي مكاني» أبدأ من جديد.")); return }
                nap(700); continue
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
                try { scr.clearArrow() } catch (e: Throwable) { }
                actedKey = 0L; tries = 0; stuckRounds = 0
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
                    if (stale && ++tries >= 4) {
                        tell(t("My move isn't going through. Maybe a pop-up is in the way. I'll keep trying.", "نقلتي ما تمر. يمكن نافذة تغطي الرقعة. بكمل أحاول."))
                        st(t("\u26A0 My move isn't going through, retrying...", "\u26A0 نقلتي ما تمر، أعيد..."))
                        nap(4000); tries = 1; actedKey = 0L; if (++stuckRounds >= 6) { tell(t("I can't make the move go through, so I'm stopping.", "ما أقدر أمرر النقلة، فبوقف.")); return }
                    }
                    st(t("\uD83E\uDDE0 Thinking (${Engine.name})...", "\uD83E\uDDE0 أفكر (${Engine.name})..."))
                    val best = Engine.analyse(tr.pos, millis, 30, keys, true) ?: return
                    if (!running) return
                    // thinking took a while: look again, and only touch the screen if the board is exactly where and how it was
                    // (a phone allows only one screenshot every second or so, so a failed second look is retried, and never allowed to loop forever)
                    var fresh = scr.shot()
                    if (fresh == null) { nap(600); fresh = scr.shot() }
                    val g = geo
                    val same = fresh != null && BoardVision.stillThere(fresh.px, fresh.w, fresh.h, g) &&
                        ChessTracker.mismatches(tr.boardView(BoardVision.read(fresh.px, fresh.w, fresh.h, g)), ChessTracker.occupancy(tr.pos)) <= 1
                    if (!same && ++freshFails < 4) { st(t("\uD83D\uDC40 Checking the board again...", "\uD83D\uDC40 أتأكد من الرقعة مرة ثانية...")); prevObs = null; nap(400); continue }
                    if (!same) log("acting on the first look (second look kept failing)")
                    freshFails = 0
                    val usedShot = fresh ?: sh
                    val (fx, fy) = tr.center(g, best.move.from); val (tx, ty) = tr.center(g, best.move.to)
                    val k = usedShot.k
                    val x1 = (fx * k).toInt(); val y1 = (fy * k).toInt(); val x2 = (tx * k).toInt(); val y2 = (ty * k).toInt()
                    actedKey = key; actedAt = System.currentTimeMillis()
                    val san = San.of(tr.pos, best.move)
                    st(if (auto) t("\u265F Playing $san", "\u265F ألعب $san") else t("\u2B50 Best move: $san", "\u2B50 أفضل نقلة: $san"))
                    if (!auto) {
                        tell(t("Best move: $san", "أفضل نقلة: $san") + (best.mateIn?.takeIf { it > 0 }?.let { t(" (mate in $it)", " (مات في $it)") } ?: ""))
                        scr.arrow(x1, y1, x2, y2, (g.cell * k).toInt())
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
            st(if (tr.pos.white != mineWhite) t("\u23F3 Waiting for their move", "\u23F3 أنتظر نقلة الخصم") else t("\uD83D\uDC40 Watching the board", "\uD83D\uDC40 أراقب الرقعة"))
            if (tr.pos.white != mineWhite && System.currentTimeMillis() - lastChange > 40_000L && System.currentTimeMillis() - waitSaid > 60_000L) {
                waitSaid = System.currentTimeMillis()
                tell(t("Waiting for the other side to move.", "أنتظر الخصم يلعب."))
            }
            nap(400)
        }
    }
}

// "Look at my board": what she can see and read right now, in words. Also learns the pieces when the opening position is on screen.
object ChessScan {
    fun run(scr: PilotScreen?, store: PieceStore?, ar: Boolean): String {
        fun t(en: String, arb: String) = if (ar) arb else en
        val sh = scr?.shot() ?: return t("I can't take a screenshot (needs Android 11+ and my accessibility access).", "ما أقدر آخذ لقطة (يحتاج أندرويد 11+ وصلاحية الوصول).")
        val g = BoardVision.detect(sh.px, sh.w, sh.h) ?: return t("I can't see a chess board on the screen. Open your game and keep the board fully visible.", "ما أشوف رقعة شطرنج على الشاشة. افتح اللعبة وخلي الرقعة كاملة ظاهرة.")
        val obs = BoardVision.read(sh.px, sh.w, sh.h, g)
        val n = obs.count { it != 0 }
        val head = t("I see a chess board with $n pieces. ", "أشوف رقعة شطرنج فيها $n قطعة. ")
        val key = PieceVision.paletteKey(sh.px, sh.w, sh.h, g)
        val o = ChessTracker.orientationOfStart(obs)
        if (o != null) {
            if (store == null) return head + t("It's the opening position, so I can follow the whole game.", "هذي بداية لعبة، أقدر أتابعها كلها.")
            val have = store.load(key)?.complete() == true
            if (have) return head + t("It's a new game and I already know how these pieces look.", "لعبة جديدة وأنا أعرف شكل هذي القطع من قبل.")
            val set = PieceVision.learn(sh.px, sh.w, sh.h, g, o)
            return if (set != null) { store.save(set); head + t("It's a new game: I learned how these pieces look. From now on I can read a game that's already running in this theme without any brain.", "لعبة جديدة: تعلمت شكل هذي القطع. من الحين أقدر أقرأ أي لعبة شغالة بهذا الثيم بدون أي عقل.") }
            else head + t("It's the opening position but I couldn't learn the pieces from it (something may be covering the board).", "هذي بداية لعبة بس ما قدرت أتعلم القطع منها (يمكن شي يغطي الرقعة).")
        }
        val set = store?.load(key) ?: return head + t("It's a game under way and I don't know this app's pieces yet. Start a new game once with me watching (say \"learn my pieces\") and I'll learn them.", "لعبة شغالة وما أعرف قطع هذا التطبيق بعد. ابدأ لعبة جديدة مرة وحدة وأنا أشوف (قول \"تعلمي قطعي\") وأتعلمها.")
        val rd = PieceVision.readPosition(sh.px, sh.w, sh.h, g, obs, set) ?: return head + t("I know this theme but I couldn't read every piece (a pop-up, a highlight or an animation may be in the way). Try again in a moment.", "أعرف هذا الثيم بس ما قدرت أقرأ كل القطع (يمكن نافذة أو تظليل أو حركة). جرب بعد شوي.")
        return head + t("I can read it. ", "أقدر أقرأها. ") + (if (rd.flipped) t("You're black. ", "انت الأسود. ") else t("You're white. ", "انت الأبيض. ")) + (if (rd.pos.white == !rd.flipped) t("It's your move.", "دورك.") else t("It's their move.", "دور الخصم."))
    }
}
