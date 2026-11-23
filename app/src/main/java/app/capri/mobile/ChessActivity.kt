package app.capri.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

// The board. Draws squares, pieces and hints, and reports which square was tapped.
class BoardView(ctx: Context) : View(ctx) {
    var pos: Pos = Chess.start()
    var flipped = false
    var selected = -1
    var targets: List<Int> = emptyList()
    var last: Move? = null
    var accent = Color.parseColor("#FF2A6D")
    var onTap: (Int) -> Unit = {}
    var hint: Move? = null
    var canDrag: (Int) -> Boolean = { false }

    // a piece sliding to its new square, a captured piece fading out, a piece following your finger
    private var animTo = -1; private var animFrom = -1; private var animStart = 0L
    private var ghost = 0
    private var dragSq = -1; private var dragX = 0f; private var dragY = 0f; private var downSq = -1; private var downX = 0f; private var downY = 0f
    private val ANIM_MS = 230L

    /** Call right after [pos] has been set to the position after [m]; [captured] is the piece that stood on the target (0 if none). */
    fun animate(m: Move, captured: Int) {
        animFrom = m.from; animTo = m.to; ghost = captured; animStart = android.os.SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    private fun progress(): Float {
        if (animTo < 0) return 1f
        val f = ((android.os.SystemClock.uptimeMillis() - animStart).toFloat() / ANIM_MS).coerceIn(0f, 1f)
        if (f >= 1f) { animTo = -1; ghost = 0 }
        return 1f - (1f - f) * (1f - f) * (1f - f)
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; color = Color.parseColor("#92828C") }
    private val light = Color.parseColor("#3A3050")
    private val dark = Color.parseColor("#1B1428")

    override fun onMeasure(w: Int, h: Int) {
        val s = MeasureSpec.getSize(w)
        setMeasuredDimension(s, s)
    }

    private fun sqOf(r: Int, c: Int) = if (flipped) (7 - r) * 8 + (7 - c) else r * 8 + c

    override fun onDraw(canvas: Canvas) {
        drawBoard(canvas)
        val h = hint ?: return
        val cell = width / 8f
        fun cx(sq: Int): Float { var c = sq % 8; if (flipped) c = 7 - c; return (c + 0.5f) * cell }
        fun cy(sq: Int): Float { var r = sq / 8; if (flipped) r = 7 - r; return (r + 0.5f) * cell }
        val x1 = cx(h.from); val y1 = cy(h.from); val x2 = cx(h.to); val y2 = cy(h.to)
        p.style = Paint.Style.STROKE; p.strokeWidth = cell * 0.16f; p.strokeCap = Paint.Cap.ROUND
        p.color = Color.argb(200, 94, 240, 246)
        val dx = x2 - x1; val dy = y2 - y1; val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
        val ux = dx / len; val uy = dy / len
        canvas.drawLine(x1, y1, x2 - ux * cell * 0.25f, y2 - uy * cell * 0.25f, p)
        p.style = Paint.Style.FILL
        val path = android.graphics.Path()
        path.moveTo(x2, y2)
        path.lineTo(x2 - ux * cell * 0.38f - uy * cell * 0.2f, y2 - uy * cell * 0.38f + ux * cell * 0.2f)
        path.lineTo(x2 - ux * cell * 0.38f + uy * cell * 0.2f, y2 - uy * cell * 0.38f - ux * cell * 0.2f)
        path.close()
        canvas.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun drawBoard(canvas: Canvas) {
        val cell = width / 8f
        val checkSq = if (Chess.inCheck(pos, pos.white)) pos.kingSq(pos.white) else -1
        glyph.textSize = cell * 0.82f
        label.textSize = cell * 0.2f
        for (r in 0..7) for (c in 0..7) {
            val sq = sqOf(r, c)
            val rect = RectF(c * cell, r * cell, (c + 1) * cell, (r + 1) * cell)
            p.style = Paint.Style.FILL
            p.color = if ((r + c) % 2 == 0) light else dark
            canvas.drawRect(rect, p)
            val lm = last
            if (lm != null && (sq == lm.from || sq == lm.to)) { p.color = Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent)); canvas.drawRect(rect, p) }
            if (sq == checkSq) { p.color = Color.argb(150, 232, 36, 64); canvas.drawRect(rect, p) }
            if (sq == selected) { p.color = Color.argb(110, 94, 240, 246); canvas.drawRect(rect, p) }
            if (c == 0) canvas.drawText("" + (8 - sq / 8), rect.left + cell * 0.05f, rect.top + cell * 0.22f, label)
            if (r == 7) canvas.drawText("" + ('a' + sq % 8), rect.right - cell * 0.2f, rect.bottom - cell * 0.06f, label)
        }
        // pieces: both sides use the solid glyphs, tinted, with a dark edge so they read on any square
        val prog = progress()
        for (r in 0..7) for (c in 0..7) {
            val sq = sqOf(r, c)
            val pc = pos.b[sq]
            if (pc == 0 || (sq == animTo && prog < 1f) || sq == dragSq) continue
            drawPiece(canvas, pc, (c + 0.5f) * cell, (r + 0.5f) * cell, cell, 255)
        }
        if (animTo >= 0) {
            val pc = pos.b[animTo]
            fun cx(sq: Int): Float { var c = sq % 8; if (flipped) c = 7 - c; return (c + 0.5f) * cell }
            fun cy(sq: Int): Float { var r = sq / 8; if (flipped) r = 7 - r; return (r + 0.5f) * cell }
            if (ghost != 0) drawPiece(canvas, ghost, cx(animTo), cy(animTo), cell, ((1f - prog) * 255).toInt())
            if (pc != 0) drawPiece(canvas, pc, cx(animFrom) + (cx(animTo) - cx(animFrom)) * prog, cy(animFrom) + (cy(animTo) - cy(animFrom)) * prog - kotlin.math.sin(prog * Math.PI).toFloat() * cell * 0.12f, cell, 255)
            postInvalidateOnAnimation()
        }
        if (dragSq >= 0) { val pc = pos.b[dragSq]; if (pc != 0) drawPiece(canvas, pc, dragX, dragY - cell * 0.35f, cell * 1.12f, 255) }
        for (t in targets) {
            val r = if (flipped) 7 - t / 8 else t / 8
            val c = if (flipped) 7 - t % 8 else t % 8
            p.style = if (pos.b[t] != 0) Paint.Style.STROKE else Paint.Style.FILL
            p.strokeWidth = cell * 0.07f
            p.color = Color.argb(200, 94, 240, 246)
            canvas.drawCircle((c + 0.5f) * cell, (r + 0.5f) * cell, if (pos.b[t] != 0) cell * 0.42f else cell * 0.12f, p)
        }
    }

    private fun drawPiece(canvas: Canvas, pc: Int, cx: Float, cy0: Float, cell: Float, alpha: Int) {
        glyph.textSize = cell * 0.82f
        val ch = "" + ('\u265A' + when (abs(pc)) { 6 -> 0; 5 -> 1; 4 -> 2; 3 -> 3; 2 -> 4; else -> 5 }) + "\uFE0E"
        val cy = cy0 + cell * 0.28f
        glyph.style = Paint.Style.FILL; glyph.color = Color.argb(alpha / 3, 0, 0, 0)
        canvas.drawText(ch, cx + cell * 0.03f, cy + cell * 0.04f, glyph)     // soft shadow
        glyph.style = Paint.Style.STROKE; glyph.strokeWidth = cell * 0.06f
        glyph.color = if (pc > 0) Color.argb(alpha, 10, 10, 16) else Color.argb(alpha, 242, 242, 242)
        canvas.drawText(ch, cx, cy, glyph)
        glyph.style = Paint.Style.FILL
        glyph.color = if (pc > 0) Color.argb(alpha, 244, 240, 242) else Color.argb(alpha, 232, 66, 70)
        canvas.drawText(ch, cx, cy, glyph)
    }

    private fun sqAt(x: Float, y: Float): Int {
        val cell = width / 8f
        return sqOf((y / cell).toInt().clampTo(0, 7), (x / cell).toInt().clampTo(0, 7))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { downSq = sqAt(e.x, e.y); downX = e.x; downY = e.y; return true }
            MotionEvent.ACTION_MOVE -> {
                if (dragSq < 0 && downSq >= 0 && Math.hypot((e.x - downX).toDouble(), (e.y - downY).toDouble()) > width / 40.0 && canDrag(downSq)) {
                    onTap(downSq)            // picks it up: shows where it can go
                    dragSq = downSq; performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                }
                if (dragSq >= 0) { dragX = e.x; dragY = e.y; invalidate() }
                return true
            }
            MotionEvent.ACTION_CANCEL -> { dragSq = -1; invalidate(); return true }
        }
        if (dragSq >= 0 && e.action == MotionEvent.ACTION_UP) {
            val target = sqAt(e.x, e.y); dragSq = -1
            if (target != downSq) onTap(target)
            invalidate(); return true
        }
        if (e.action == MotionEvent.ACTION_UP) {
            val cell = width / 8f
            val c = (e.x / cell).toInt().clampTo(0, 7)
            val r = (e.y / cell).toInt().clampTo(0, 7)
            onTap(sqOf(r, c))
        }
        return true
    }
}

// Play chess with her. She sits above the board, thinks (glitches) while she searches, and comments.
class ChessActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var board: BoardView
    private lateinit var status: TextView
    private lateinit var says: TextView
    private lateinit var sprite: SpriteView
    private var pos = Chess.start()
    private val history = ArrayList<Pos>()
    private val moves = ArrayList<Move?>()
    private val redo = ArrayList<Pair<Pos, Move?>>()
    private var playerWhite = true
    private var level = 3
    private var endReason = "playing"
    private fun keysNow(): List<Long> = history.map { Engine.key(it) } + Engine.key(pos)
    private lateinit var evalBar: EvalBar
    private var suggest: Analysis? = null
    private var suggestKey = 0L
    private var auto = false
    private var pendingJudge: Triple<Analysis, Pos, Move>? = null
    private var hintLabel: TextView? = null
    private var thinking = false
    private var over = false
    private lateinit var talk: ChessTalk
    private var spoken = false
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun t(en: String, ar: String) = L.t(en, ar)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        L.init(prefs)
        level = prefs.getI("chessLevel", 3).clampTo(1, 4)
        talk = ChessTalk(L.ar)
        talk.enabled = ChessEvents.all.filter { prefs.getB("chessTalk_$it", true) }.toSet()
        spoken = prefs.getB("chessVoice", false)
        val accent = Themes.accent(prefs)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Night.ground)
        root.setPadding(dp(12), dp(28), dp(12), dp(12))

        val bar = TextView(this)
        bar.text = "\u2039  CHESS >"
        bar.typeface = Typeface.DEFAULT_BOLD
        bar.textSize = 15f
        bar.setTextColor(Night.amber)
        bar.setPadding(dp(6), 0, 0, dp(8))
        bar.setOnClickListener { finish() }
        root.addView(bar)
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        sprite = SpriteView(this)
        sprite.glowColor = accent
        sprite.unit = dp(104) / 384f
        top.addView(sprite, LinearLayout.LayoutParams(dp(70), dp(104)))
        says = TextView(this)
        says.setTextColor(Night.cyan)
        says.textSize = 15f
        says.typeface = Typeface.MONOSPACE
        says.setPadding(dp(14), dp(10), dp(14), dp(10))
        says.background = NeonPanel(resources.displayMetrics.density)
        top.addView(says, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8) })
        root.addView(top)

        status = TextView(this)
        status.setTextColor(Night.white)
        status.textSize = 14f
        status.gravity = Gravity.CENTER
        status.setPadding(0, dp(8), 0, dp(8))
        root.addView(status)

        evalBar = EvalBar(this)
        root.addView(evalBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(18)).apply { bottomMargin = dp(6) })

        board = BoardView(this)
        board.accent = accent
        board.onTap = { tap(it) }
        board.canDrag = { sq -> !over && !thinking && pos.white == playerWhite && pos.b[sq] != 0 && (pos.b[sq] > 0) == playerWhite }
        root.addView(board, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val row1 = LinearLayout(this); row1.orientation = LinearLayout.HORIZONTAL
        val row2 = LinearLayout(this); row2.orientation = LinearLayout.HORIZONTAL
        row1.addView(button(t("NEW GAME", "لعبة جديدة")) { newGame() }, weight())
        row1.addView(button(t("UNDO", "تراجع")) { undo() }, weight())
        row1.addView(button(t("REDO", "إعادة")) { redoMove() }, weight())
        val lvl = button("") { level = level % 4 + 1; prefs.putI("chessLevel", level); levelLabel(it as TextView) }
        levelLabel(lvl)
        row2.addView(lvl, weight())
        val side = button("") {
            playerWhite = !playerWhite
            (it as TextView).text = if (playerWhite) t("YOU: WHITE", "أنت: الأبيض") else t("YOU: BLACK", "أنت: الأسود")
            newGame()
        }
        side.text = t("YOU: WHITE", "أنت: الأبيض")
        row2.addView(side, weight())
        val row3 = LinearLayout(this); row3.orientation = LinearLayout.HORIZONTAL
        val vb = button("") { spoken = !spoken; prefs.putB("chessVoice", spoken); (it as TextView).text = voiceLabel() }
        vb.text = voiceLabel()
        row3.addView(vb, weight())
        row3.addView(button(t("COMMENTS", "التعليقات")) { talkDialog() }, weight())
        row3.addView(button(t("EXPLAIN", "اشرح")) { explain() }, weight())
        val row4 = LinearLayout(this); row4.orientation = LinearLayout.HORIZONTAL
        row4.addView(button(t("HINT", "تلميح")) { showHint() }, weight())
        row4.addView(button(t("PLAY FOR ME", "العب عني")) { playForMe() }, weight())
        val ab = button("") { auto = !auto; (it as TextView).text = autoLabel(); if (auto) playForMe() }
        ab.text = autoLabel(); row4.addView(ab, weight())
        row4.addView(button("FEN") { fenDialog() }, weight())
        root.addView(row1)
        root.addView(row2)
        root.addView(row3)
        root.addView(row4)
        setContentView(neonWrap(this, root))

        sprite.play("idle")
        newGame()
    }

    private fun weight() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(6), dp(3), 0) }

    private fun levelLabel(v: TextView) {
        v.text = t("LEVEL: ", "المستوى: ") + when (level) { 1 -> t("EASY", "سهل"); 2 -> t("MEDIUM", "متوسط"); 3 -> t("HARD", "صعب"); else -> t("MASTER", "خبير") }
    }

    private fun button(s: String, click: (View) -> Unit): TextView {
        val b = TextView(this)
        b.text = s
        b.setTextColor(Night.white)
        b.textSize = 13f
        b.typeface = Typeface.DEFAULT_BOLD
        b.gravity = Gravity.CENTER
        b.setPadding(dp(8), dp(12), dp(8), dp(12))
        b.typeface = Typeface.MONOSPACE
        b.background = GradientDrawable().apply { setColor(Night.field); setStroke(dp(1), Night.deepEdge); cornerRadius = dp(22).toFloat() }
        b.foreground = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), null, null)
        b.setOnClickListener(click)
        return b
    }

    private fun say(en: String, ar: String) { says.text = "CAPRI: " + t(en, ar) }

    private fun voiceLabel() = t("VOICE: ", "الصوت: ") + if (spoken) t("ON", "شغال") else t("OFF", "مطفي")

    // Shows her remark and, if you switched her voice on, has the pet speak it too.
    private fun remark(line: String?) {
        if (line.isNullOrBlank()) return
        says.text = "CAPRI: $line"
        if (spoken) ChessBridge.speaker?.invoke(line)
    }

    private fun talkDialog() {
        val names = mapOf("capture" to t("Captures", "الأكل"), "check" to t("Check", "كش"), "castle" to t("Castling", "التبييت"),
            "promotion" to t("Promotion", "الترقية"), "mate" to t("Checkmate", "كش مات"), "draw" to t("Draw", "التعادل"),
            "start" to t("Game start", "بداية اللعبة"), "judge" to t("Judging your moves", "تقييم نقلاتك"), "aside" to t("Small remarks", "ملاحظات عامة"))
        val keys = ChessEvents.all
        val on = BooleanArray(keys.size) { talk.enabled.contains(keys[it]) }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(t("She comments on", "تعلّق على"))
            .setMultiChoiceItems(keys.map { names[it] ?: it }.toTypedArray(), on) { _, i, c -> on[i] = c }
            .setPositiveButton(t("OK", "تمام")) { _, _ ->
                keys.forEachIndexed { i, k -> prefs.putB("chessTalk_$k", on[i]) }
                talk.enabled = keys.filterIndexed { i, _ -> on[i] }.toSet()
            }.show()
    }

    private fun autoLabel() = t("AUTO: ", "تلقائي: ") + if (auto) t("ON", "شغال") else t("OFF", "مطفي")

    private fun setEval(a: Analysis, whiteToMove: Boolean) {
        evalBar.cp = if (whiteToMove) a.score else -a.score
        evalBar.mate = a.mateIn?.let { if (whiteToMove) it else -it }
        evalBar.invalidate()
    }

    // Think about the position while you think, so a hint is ready the moment you ask.
    private fun think() {
        if (over || pos.white != playerWhite) return
        val snap = pos
        val k = Engine.key(snap)
        if (suggestKey == k) return
        val ms = if (level >= 4) 4000L else 2200L
        val keys = keysNow()
        bg("chess", this) {
            val a = Engine.analyse(snap, ms, 30, keys)
            ui.post {
                if (isFinishing || pos !== snap || a == null) return@post
                suggest = a; suggestKey = k
                setEval(a, snap.white)
            }
        }
    }

    private fun suggestionFor(snap: Pos): Analysis? = if (suggestKey == Engine.key(snap)) suggest else null

    private fun showHint() {
        if (over || thinking || pos.white != playerWhite) return
        val have = suggestionFor(pos)
        if (have != null) { announceHint(have); return }
        say("Let me look...", "خلني أشوف...")
        val snap = pos
        val keys = keysNow()
        bg("chess", this) {
            val a = Engine.analyse(snap, 3000, 30, keys)
            ui.post { if (!isFinishing && pos === snap && a != null) { suggest = a; suggestKey = Engine.key(snap); setEval(a, snap.white); announceHint(a) } }
        }
    }

    private fun announceHint(a: Analysis) {
        board.hint = a.move
        board.invalidate()
        val san = San.of(pos, a.move)
        val line = a.pv.take(3).let { pv -> var q = pos; pv.joinToString(" ") { m -> val x = San.of(q, m); q = Chess.make(q, m); x } }
        remark(t("I'd play $san.", "أنا بلعب $san.") + (a.mateIn?.takeIf { it > 0 }?.let { t(" Mate in $it!", " مات في $it!") } ?: "") + "  ($line)")
    }

    // She plays your move for you (the best she can find).
    private fun playForMe() {
        if (over || thinking || pos.white != playerWhite) return
        val have = suggestionFor(pos)
        if (have != null) { play(have.move); return }
        val snap = pos
        val keys = keysNow()
        say("On it.", "على طول.")
        bg("chess", this) {
            val a = Engine.analyse(snap, if (level >= 4) 6000L else 3500L, 30, keys, true)
            ui.post { if (!isFinishing && pos === snap && a != null && !over) play(a.move) }
        }
    }

    private fun fenDialog() {
        val box = android.widget.EditText(this)
        box.setText(Fen.write(pos)); box.setTextColor(Night.white); box.textSize = 12f; box.typeface = Typeface.MONOSPACE
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("FEN / PGN")
            .setMessage(t("Copy this to take the position elsewhere. Or paste a FEN, or a whole game (PGN) from chess.com or Lichess: you can step through it with UNDO and REDO, or play on from it.", "انسخه عشان تاخذ الوضع لمكان ثاني. أو الصق FEN، أو لعبة كاملة (PGN) من chess.com أو Lichess: تمشي فيها بتراجع وإعادة، أو تكمل لعب منها."))
            .setView(box)
            .setPositiveButton(t("LOAD", "حمّل")) { _, _ ->
                val txt = box.text.toString()
                val p = Fen.parse(txt)
                if (p == null) {
                    val game = Pgn.parse(txt)
                    if (game == null) { say("That doesn't look like a real position or game.", "هذا مو وضع ولا لعبة صحيحة."); return@setPositiveButton }
                    history.clear(); moves.clear(); redo.clear()
                    var cur = game.start
                    for (m in game.moves) { history.add(cur); moves.add(m); cur = Chess.make(cur, m) }
                    pos = cur; playerWhite = cur.white
                    over = false; endReason = "playing"; thinking = false; auto = false
                    board.flipped = !playerWhite; board.last = moves.lastOrNull(); board.hint = null; clearSel()
                    suggest = null; suggestKey = 0L; pendingJudge = null
                    refresh()
                    say("Loaded ${game.moves.size} moves" + (if (game.badAt >= 0) " (stopped at a move I couldn't read)" else "") + ". UNDO walks back through it, or play on.",
                        "حمّلت ${game.moves.size} نقلة" + (if (game.badAt >= 0) " (وقفت عند نقلة ما قدرت أقرأها)" else "") + ". تراجع يمشي بيك لورا، أو كمّل لعب.")
                    if (!finished(false)) think()
                    return@setPositiveButton
                }
                pos = p; playerWhite = p.white
                history.clear(); moves.clear(); redo.clear(); over = false; endReason = "playing"; thinking = false; auto = false
                board.flipped = !playerWhite; board.last = null; board.hint = null; clearSel()
                suggest = null; suggestKey = 0L
                refresh(); say("Loaded. Your move.", "تم. دورك."); think()
            }
            .setNeutralButton(t("COPY", "انسخ")) { _, _ ->
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("FEN", box.text.toString()))
            }.show()
    }

    // "1. e4 e5 2. Nf3 ..." for the last few moves
    private fun moveText(): String {
        if (moves.isEmpty()) return ""
        val sb = StringBuilder()
        val from = maxOf(0, moves.size - 8)
        for (i in from until moves.size) {
            val m = moves[i] ?: continue
            val before = history[i]
            if (before.white) sb.append(i / 2 + 1).append(". ") else if (i == from) sb.append("… ")
            sb.append(San.of(before, m)).append(' ')
        }
        return "\n" + sb.toString().trim()
    }

    private fun newGame() {
        pos = Chess.start()
        history.clear(); moves.clear(); redo.clear()
        over = false; endReason = "playing"
        thinking = false
        board.flipped = !playerWhite
        board.last = null
        board.hint = null
        suggest = null; suggestKey = 0L; pendingJudge = null
        evalBar.cp = 0; evalBar.mate = null; evalBar.invalidate()
        clearSel()
        refresh()
        talk.reset()
        remark(talk.start(playerWhite) ?: "")
        if (says.text.isEmpty() || says.text == "CAPRI: ") says.text = "CAPRI: ..."
        if (!playerWhite) aiMove() else think()
    }

    private fun clearSel() { board.selected = -1; board.targets = emptyList() }

    private fun tap(sq: Int) {
        if (over || thinking || pos.white != playerWhite) return
        val pc = pos.b[sq]
        if (board.selected >= 0) {
            val cands = Chess.legalFrom(pos, board.selected).filter { it.to == sq }
            if (cands.isNotEmpty()) {
                if (cands[0].promo != 0) {
                    val names = listOf(t("Queen", "وزير"), t("Rook", "قلعة"), t("Bishop", "فيل"), t("Knight", "حصان"))
                    val codes = listOf(5, 4, 3, 2)
                    AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(t("Promote to", "رقّي إلى")).setItems(names.toTypedArray()) { _, i ->
                        play(cands.first { it.promo == codes[i] })
                    }.setCancelable(false).show()
                } else play(cands[0])
                return
            }
        }
        if (pc != 0 && (pc > 0) == playerWhite) {
            board.selected = sq
            board.targets = Chess.legalFrom(pos, sq).map { it.to }
        } else clearSel()
        board.invalidate()
    }

    private fun play(m: Move) {
        val before = pos
        board.hint = null
        pendingJudge = suggestionFor(before)?.let { Triple(it, before, m) }
        history.add(pos); moves.add(m); redo.clear()
        pos = Chess.make(pos, m)
        board.last = m
        board.animate(m, before.b[m.to])
        board.performHapticFeedback(if (before.b[m.to] != 0) android.view.HapticFeedbackConstants.LONG_PRESS else android.view.HapticFeedbackConstants.CLOCK_TICK)
        if (before.b[m.to] != 0) { sprite.emote("angry"); sprite.squash(1f) }
        clearSel()
        refresh()
        if (finished(true)) { remark(talk.react(before, m, pos, false)); return }
        remark(talk.react(before, m, pos, false))
        aiMove()
    }

    private fun aiMove() {
        thinking = true
        sprite.glitchFx = true
        refresh()
        val snapshot = pos
        val lv = level
        val judged = pendingJudge
        pendingJudge = null
        val keys = keysNow()
        bg("chess", this) {
            val a = Engine.forLevel(snapshot, lv, keys)
            val m = a?.move
            ui.postDelayed({
                sprite.glitchFx = false
                thinking = false
                if (isFinishing || m == null || pos !== snapshot) return@postDelayed
                history.add(pos); moves.add(m)
                pos = Chess.make(pos, m)
                board.last = m
                board.animate(m, snapshot.b[m.to])
                board.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                if (snapshot.b[m.to] != 0) { sprite.emote("star"); sprite.hop() }
                if (a.depth >= 4) setEval(a, snapshot.white)
                refresh()
                val line = talk.react(snapshot, m, pos, true)
                // judge your last move only when she searched properly, and only against her own best line
                var verdict: String? = null
                if (judged != null && a.depth >= 4 && lv >= 3) {
                    val (sug, sugPos, played) = judged
                    val yourScore = -a.score
                    val loss = sug.score - yourScore
                    if (played.toString() != sug.move.toString())
                        verdict = talk.judge(loss, San.of(sugPos, sug.move), a.mateIn?.takeIf { it > 0 })
                }
                val both = listOfNotNull(verdict, line).joinToString("  ")
                if (finished(false)) { remark(line); return@postDelayed }
                remark(both)
                think()
                if (auto) ui.postDelayed({ if (auto && !isFinishing) playForMe() }, 900)
            }, 350)
        }
    }

    private fun finished(playerJustMoved: Boolean): Boolean {
        val st = Chess.result(pos, keysNow())
        if (st == "playing") return false
        over = true
        endReason = st
        if (st == "mate") sprite.play(if (playerJustMoved) "glitch" else "grin", false) { sprite.play("idle") }
        if ("mate" !in talk.enabled && st == "mate" || "draw" !in talk.enabled && st != "mate") says.text = "CAPRI: " + (if (st == "mate") t("Checkmate.", "كش مات.") else t("A draw.", "تعادل."))
        refresh()
        return true
    }

    // "Why is that the best move?": the engine finds the line, her brain puts it in words (without a brain she gives the line itself).
    private fun explain() {
        if (thinking || over) return
        val snap = pos
        val keys = keysNow()
        say("Let me think it through...", "خليني أفكر...")
        bg("chess", this) {
            val a = Engine.analyse(snap, 2500, 30, keys)
            if (a == null) return@bg
            val sb = StringBuilder()
            var p = snap
            for (m in a.pv.take(6)) { sb.append(San.of(p, m)).append(' '); p = Chess.make(p, m) }
            val line = sb.toString().trim()
            val first = San.of(snap, a.move)
            val ev = a.mateIn?.takeIf { it > 0 }?.let { "mate in $it" } ?: "%+.1f".format(a.score / 100.0)
            val plain = "$first ($ev). $line"
            val prompt = "(The user asked why this chess move is best. Position FEN: ${Fen.write(snap)}. The engine's best line: $line, evaluation $ev for the side to move. In two short sentences, in your own voice, say what the first move does and what it avoids or wins. Stick to the engine's line; no lists, no invented variations.)"
            ui.post {
                if (isFinishing) return@post
                val ex = ChessBridge.explainer
                if (ex == null) { says.text = "CAPRI: $plain" }
                else ex(prompt) { r -> ui.post { if (!isFinishing) { if (r.isNullOrBlank() || r.trim() == "NOTHING") says.text = "CAPRI: $plain" else remark(r) } } }
            }
        }
    }

    // Steps forward again after UNDO (one move at a time), so an imported game can be walked through.
    private fun redoMove() {
        if (thinking || redo.isEmpty()) return
        val (after, m) = redo.removeAt(redo.size - 1)
        history.add(pos); moves.add(m); pos = after
        board.last = m; board.hint = null; suggest = null; suggestKey = 0L; pendingJudge = null
        over = false; endReason = "playing"
        clearSel(); refresh()
        finished(false)
    }

    private fun undo() {
        if (thinking || history.isEmpty()) return
        // take back her move and yours, so it is your turn again
        var n = if (pos.white == playerWhite) 2 else 1
        while (n-- > 0 && history.isNotEmpty()) { val m = moves.removeAt(moves.size - 1); redo.add(Pair(pos, m)); pos = history.removeAt(history.size - 1) }
        over = false; endReason = "playing"
        board.hint = null; suggest = null; suggestKey = 0L; pendingJudge = null
        board.last = moves.lastOrNull()
        clearSel()
        refresh()
        think()
        say("Okay, take it back.", "ماشي، رجّعها.")
    }

    // Pieces each side has lost, as small glyphs, plus the material lead (pawn = 1).
    private fun capturedLine(): String {
        val start = mapOf(1 to 8, 2 to 2, 3 to 2, 4 to 2, 5 to 1)
        val worth = mapOf(1 to 1, 2 to 3, 3 to 3, 4 to 5, 5 to 9)
        fun lost(white: Boolean): String {
            val sb = StringBuilder()
            for (code in intArrayOf(5, 4, 3, 2, 1)) {
                val have = pos.b.count { it == (if (white) code else -code) }
                repeat(maxOf(0, start.getValue(code) - have)) { sb.append('\u265A' + when (code) { 5 -> 1; 4 -> 2; 3 -> 3; 2 -> 4; else -> 5 }) }
            }
            return sb.toString()
        }
        var lead = 0
        for (v in pos.b) if (v != 0 && kotlin.math.abs(v) != 6) lead += (if (v > 0) 1 else -1) * worth.getValue(kotlin.math.abs(v))
        if (!playerWhite) lead = -lead
        val mine = lost(!playerWhite); val hers = lost(playerWhite)
        if (mine.isEmpty() && hers.isEmpty()) return ""
        return "\n$mine  |  $hers  " + (if (lead > 0) "+$lead" else if (lead < 0) "$lead" else "=")
    }

    private fun refresh() {
        board.pos = pos
        board.invalidate()
        status.text = when {
            over -> when (endReason) {
                "mate" -> t("Checkmate", "كش مات"); "stalemate" -> t("Draw (stalemate)", "تعادل (جمود)")
                "repetition" -> t("Draw (same position 3 times)", "تعادل (تكرار 3 مرات)"); "fifty" -> t("Draw (50 moves, no capture or pawn move)", "تعادل (50 نقلة بدون أكل أو بيدق)")
                "insufficient" -> t("Draw (not enough pieces)", "تعادل (قطع غير كافية)"); else -> t("Draw", "تعادل") }
            thinking -> t("She is thinking...", "تفكر...")
            Chess.inCheck(pos, pos.white) -> t("Check! ", "كش! ") + t("Your move", "دورك")
            pos.white == playerWhite -> t("Your move", "دورك")
            else -> t("Her move", "دورها")
        } + capturedLine() + moveText()
    }

    override fun onDestroy() { ui.removeCallbacksAndMessages(null); super.onDestroy() }
}


// A thin bar above the board: the lighter part is White's share, with the number from her search.
class EvalBar(ctx: Context) : View(ctx) {
    var cp = 0               // centipawns, White's view
    var mate: Int? = null    // mate in N for White (positive) or Black (negative)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textSize = 24f }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val share = if (mate != null) (if (mate!! > 0) 1f else 0f) else (0.5f + Math.atan(cp / 400.0).toFloat() / Math.PI.toFloat()).coerceIn(0.03f, 0.97f)
        p.color = Color.parseColor("#15101C"); canvas.drawRect(0f, 0f, w, h, p)
        p.color = Color.parseColor("#E8E2EC"); canvas.drawRect(0f, 0f, w * share, h, p)
        txt.textSize = h * 0.8f
        val label = if (mate != null) "M" + kotlin.math.abs(mate!!) else (if (cp >= 0) "+" else "") + String.format(java.util.Locale.US, "%.1f", cp / 100f)
        txt.color = if (share > 0.5f) Color.parseColor("#15101C") else Color.parseColor("#E8E2EC")
        if (share > 0.5f) canvas.drawText(label, 6f, h * 0.82f, txt) else canvas.drawText(label, w - txt.measureText(label) - 6f, h * 0.82f, txt)
    }
}
