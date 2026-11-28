package app.capri.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
    var theme: BoardTheme = BoardThemes.at(0)
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
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val clip = Path()

    override fun onMeasure(w: Int, h: Int) {
        val s = MeasureSpec.getSize(w)
        setMeasuredDimension(s, s)
    }

    private fun sqOf(r: Int, c: Int) = if (flipped) (7 - r) * 8 + (7 - c) else r * 8 + c

    override fun onDraw(canvas: Canvas) {
        val rad = resources.displayMetrics.density * 10f
        clip.rewind(); clip.addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), rad, rad, Path.Direction.CW)
        canvas.save(); canvas.clipPath(clip)
        drawBoard(canvas)
        drawHint(canvas)
        canvas.restore()
    }

    private fun drawHint(canvas: Canvas) {
        val h = hint ?: return
        val cell = width / 8f
        fun cx(sq: Int): Float { var c = sq % 8; if (flipped) c = 7 - c; return (c + 0.5f) * cell }
        fun cy(sq: Int): Float { var r = sq / 8; if (flipped) r = 7 - r; return (r + 0.5f) * cell }
        val x1 = cx(h.from); val y1 = cy(h.from); val x2 = cx(h.to); val y2 = cy(h.to)
        p.style = Paint.Style.STROKE; p.strokeWidth = cell * 0.16f; p.strokeCap = Paint.Cap.ROUND
        p.color = alphaOf(theme.mark, 210)
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
            val isLight = (r + c) % 2 == 0
            p.color = if (isLight) theme.light else theme.dark
            canvas.drawRect(rect, p)
            val lm = last
            if (lm != null && (sq == lm.from || sq == lm.to)) { p.color = alphaOf(theme.last, 105); canvas.drawRect(rect, p) }
            if (sq == checkSq) {
                p.shader = android.graphics.RadialGradient(rect.centerX(), rect.centerY(), cell * 0.62f, intArrayOf(Color.argb(235, 255, 40, 70), Color.argb(150, 232, 36, 64), Color.argb(0, 232, 36, 64)), floatArrayOf(0f, 0.45f, 1f), android.graphics.Shader.TileMode.CLAMP)
                canvas.drawRect(rect, p); p.shader = null
            }
            if (sq == selected) { p.color = alphaOf(theme.mark, 105); canvas.drawRect(rect, p) }
            // the board's own coordinates, small, in the colour of the other square so they never fight the pieces
            label.color = if (isLight) theme.dark else theme.light
            if (c == 0) canvas.drawText("" + (8 - sq / 8), rect.left + cell * 0.06f, rect.top + cell * 0.23f, label)
            if (r == 7) canvas.drawText("" + ('a' + sq % 8), rect.right - cell * 0.2f, rect.bottom - cell * 0.07f, label)
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
            p.color = alphaOf(theme.mark, 190)
            canvas.drawCircle((c + 0.5f) * cell, (r + 0.5f) * cell, if (pos.b[t] != 0) cell * 0.42f else cell * 0.12f, p)
        }
    }

    private fun drawPiece(canvas: Canvas, pc: Int, cx: Float, cy0: Float, cell: Float, alpha: Int) {
        glyph.textSize = cell * 0.82f
        val ch = "" + ('\u265A' + when (abs(pc)) { 6 -> 0; 5 -> 1; 4 -> 2; 3 -> 3; 2 -> 4; else -> 5 }) + "\uFE0E"
        val cy = cy0 + cell * 0.28f
        glyph.style = Paint.Style.FILL; glyph.color = Color.argb(alpha / 3, 0, 0, 0)
        canvas.drawText(ch, cx + cell * 0.03f, cy + cell * 0.04f, glyph)     // soft shadow
        glyph.style = Paint.Style.STROKE; glyph.strokeWidth = cell * 0.06f; glyph.strokeJoin = Paint.Join.ROUND
        glyph.color = alphaOf(if (pc > 0) theme.outWhite else theme.outBlack, alpha)
        canvas.drawText(ch, cx, cy, glyph)
        glyph.style = Paint.Style.FILL
        glyph.color = alphaOf(if (pc > 0) theme.white else theme.black, alpha)
        canvas.drawText(ch, cx, cy, glyph)
    }

    private fun alphaOf(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun sqAt(x: Float, y: Float): Int {
        val cell = width / 8f
        return sqOf((y / cell).toInt().clampTo(0, 7), (x / cell).toInt().clampTo(0, 7))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { downSq = sqAt(e.x, e.y); downX = e.x; downY = e.y; parent?.requestDisallowInterceptTouchEvent(true); return true }
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
    private lateinit var capMe: TextView
    private lateinit var capHer: TextView
    private lateinit var herLevel: TextView
    private lateinit var opening: TextView
    private lateinit var strip: TextView
    private lateinit var stripScroll: android.widget.HorizontalScrollView
    private var themeNo = 0
    private var thinking = false
    private var over = false
    private lateinit var talk: ChessTalk
    private var spoken = false
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun t(en: String, ar: String) = L.t(en, ar)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Stockfish.prepare(this)
        prefs = Prefs(this)
        L.init(prefs)
        level = Engine.migrateLevel(prefs.getI("chessLevel", 3), prefs.getB("chessLv5", false)).also { prefs.putI("chessLevel", it); prefs.putB("chessLv5", true) }
        talk = ChessTalk(L.ar)
        talk.enabled = ChessEvents.all.filter { prefs.getB("chessTalk_$it", true) }.toSet()
        spoken = prefs.getB("chessVoice", false)
        val accent = Themes.accent(prefs)

        themeNo = BoardThemes.saved(prefs.getI("chessBoard", 0))
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Night.ground)
        root.setPadding(dp(12), dp(28), dp(12), dp(16))

        // top line: back, and the two things you change most (her level, your colour)
        val head = LinearLayout(this); head.orientation = LinearLayout.HORIZONTAL; head.gravity = Gravity.CENTER_VERTICAL
        val bar = TextView(this)
        bar.text = "\u2039  " + t("CHESS", "الشطرنج")
        bar.typeface = Typeface.DEFAULT_BOLD
        bar.textSize = 16f
        bar.setTextColor(Night.amber)
        bar.setPadding(dp(4), dp(6), dp(10), dp(6))
        bar.setOnClickListener { finish() }
        head.addView(bar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val lvl = chip("") { level = level % 5 + 1; prefs.putI("chessLevel", level); levelLabel(it as TextView) }
        levelLabel(lvl)
        head.addView(lvl, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        val side = chip(if (playerWhite) t("YOU: WHITE", "أنت: الأبيض") else t("YOU: BLACK", "أنت: الأسود")) {
            playerWhite = !playerWhite
            (it as TextView).text = if (playerWhite) t("YOU: WHITE", "أنت: الأبيض") else t("YOU: BLACK", "أنت: الأسود")
            newGame()
        }
        head.addView(side)
        root.addView(head)

        // her: a card with her face, her name, what she has taken, and what she says
        sprite = SpriteView(this)
        sprite.glowColor = accent
        sprite.unit = dp(92) / 384f
        says = TextView(this)
        says.setTextColor(Night.cyan)
        says.textSize = 14f
        says.typeface = Typeface.MONOSPACE
        says.setPadding(dp(12), dp(8), dp(12), dp(8))
        says.background = NeonPanel(resources.displayMetrics.density)
        capHer = TextView(this); capHer.setTextColor(Night.faint); capHer.textSize = 15f; capHer.setPadding(dp(8), 0, 0, 0)
        val herName = TextView(this); herName.text = "CAPRI"; herName.setTextColor(Night.white); herName.typeface = Typeface.DEFAULT_BOLD; herName.textSize = 14f
        herLevel = TextView(this); herLevel.setTextColor(Night.faint); herLevel.textSize = 11f; herLevel.typeface = Typeface.MONOSPACE; herLevel.setPadding(dp(8), 0, 0, 0)
        val herLine = LinearLayout(this); herLine.orientation = LinearLayout.HORIZONTAL; herLine.gravity = Gravity.CENTER_VERTICAL
        herLine.addView(herName); herLine.addView(herLevel); herLine.addView(capHer)
        val herCol = LinearLayout(this); herCol.orientation = LinearLayout.VERTICAL
        herCol.addView(herLine)
        herCol.addView(says, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        val card = LinearLayout(this); card.orientation = LinearLayout.HORIZONTAL; card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(dp(8), dp(8), dp(10), dp(8))
        card.background = GradientDrawable().apply { setColor(Night.alpha(Night.panel, 255)); setStroke(dp(1), Night.deepEdge); cornerRadius = dp(16).toFloat() }
        card.addView(sprite, LinearLayout.LayoutParams(dp(62), dp(92)))
        card.addView(herCol, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        levelLabel(lvl)

        evalBar = EvalBar(this)
        root.addView(evalBar, LinearLayout.LayoutParams(-1, dp(12)).apply { topMargin = dp(10); bottomMargin = dp(8) })

        board = BoardView(this)
        board.accent = accent
        board.theme = BoardThemes.at(themeNo)
        board.onTap = { tap(it) }
        board.canDrag = { sq -> !over && !thinking && pos.white == playerWhite && pos.b[sq] != 0 && (pos.b[sq] > 0) == playerWhite }
        root.addView(board, LinearLayout.LayoutParams(-1, -2))

        // you: under the board
        val meName = TextView(this); meName.text = t("YOU", "أنت"); meName.setTextColor(Night.white); meName.typeface = Typeface.DEFAULT_BOLD; meName.textSize = 14f
        capMe = TextView(this); capMe.setTextColor(Night.faint); capMe.textSize = 15f; capMe.setPadding(dp(8), 0, 0, 0)
        val meLine = LinearLayout(this); meLine.orientation = LinearLayout.HORIZONTAL; meLine.gravity = Gravity.CENTER_VERTICAL
        meLine.addView(meName); meLine.addView(capMe)
        root.addView(meLine, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); leftMargin = dp(4) })

        status = TextView(this)
        status.setTextColor(Night.white)
        status.textSize = 14f
        status.typeface = Typeface.DEFAULT_BOLD
        status.gravity = Gravity.CENTER
        status.setPadding(dp(14), dp(8), dp(14), dp(8))
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // the game so far, scrolling sideways, with the opening's name when she knows it
        opening = TextView(this); opening.setTextColor(Night.amber); opening.textSize = 12f; opening.typeface = Typeface.MONOSPACE
        opening.setPadding(dp(4), dp(8), 0, 0)
        root.addView(opening)
        strip = TextView(this); strip.setTextColor(Night.faint); strip.textSize = 12f; strip.typeface = Typeface.MONOSPACE; strip.setSingleLine(true)
        strip.setPadding(dp(4), dp(2), dp(4), dp(2))
        stripScroll = android.widget.HorizontalScrollView(this)
        stripScroll.isHorizontalScrollBarEnabled = false
        stripScroll.addView(strip)
        root.addView(stripScroll, LinearLayout.LayoutParams(-1, -2))

        // the five things you use all game, big and in one row
        val tools = LinearLayout(this); tools.orientation = LinearLayout.HORIZONTAL
        tools.addView(tool("\u271A", t("NEW", "جديدة")) { newGame() }, weight())
        tools.addView(tool("\u21B6", t("UNDO", "تراجع")) { undo() }, weight())
        tools.addView(tool("\u21B7", t("REDO", "إعادة")) { redoMove() }, weight())
        tools.addView(tool("\u2726", t("HINT", "تلميح")) { showHint() }, weight())
        tools.addView(tool("\u25B6", t("PLAY", "العب عني")) { playForMe() }, weight())
        root.addView(tools, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val more = LinearLayout(this); more.orientation = LinearLayout.HORIZONTAL
        more.addView(button(t("REVIEW", "راجع")) { review() }, weight())
        more.addView(button(t("EXPLAIN", "اشرح")) { explain() }, weight())
        more.addView(button(t("MORE", "المزيد") + " \u22EF") { moreMenu() }, weight())
        root.addView(more, LinearLayout.LayoutParams(-1, -2))

        val scroll = android.widget.ScrollView(this)
        scroll.isFillViewport = true
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.addView(root)
        setContentView(neonWrap(this, scroll))

        sprite.play("idle")
        newGame()
    }

    private fun weight() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(6), dp(3), 0) }

    private fun levelLabel(v: TextView) {
        v.text = t("LEVEL: ", "المستوى: ") + when (level) { 1 -> t("EASY", "سهل"); 2 -> t("MEDIUM", "متوسط"); 3 -> t("CLUB", "نادي"); 4 -> t("HARD", "صعب"); else -> t("MASTER", "خبير") }
        if (this::herLevel.isInitialized) herLevel.text = "\u2022 " + v.text.substring(t("LEVEL: ", "المستوى: ").length)
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

    // a small pill for the top line
    private fun chip(s: String, click: (View) -> Unit): TextView {
        val b = TextView(this)
        b.text = s
        b.setTextColor(Night.white)
        b.textSize = 11f
        b.typeface = Typeface.MONOSPACE
        b.gravity = Gravity.CENTER
        b.setPadding(dp(10), dp(7), dp(10), dp(7))
        b.background = GradientDrawable().apply { setColor(Night.field); setStroke(dp(1), Night.deepEdge); cornerRadius = dp(16).toFloat() }
        b.foreground = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), null, null)
        b.setOnClickListener(click)
        return b
    }

    // a big round-ish button: a symbol over a short word
    private fun tool(glyph: String, label: String, click: (View) -> Unit): TextView {
        val b = TextView(this)
        val sp = android.text.SpannableString(glyph + "\n" + label)
        sp.setSpan(android.text.style.RelativeSizeSpan(1.7f), 0, glyph.length, 0)
        b.text = sp
        b.setTextColor(Night.white)
        b.textSize = 10f
        b.typeface = Typeface.MONOSPACE
        b.gravity = Gravity.CENTER
        b.setPadding(dp(2), dp(8), dp(2), dp(8))
        b.background = GradientDrawable().apply { setColor(Night.field); setStroke(dp(1), Night.deepEdge); cornerRadius = dp(18).toFloat() }
        b.foreground = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), null, null)
        b.setOnClickListener(click)
        return b
    }

    // everything else, in one list so the screen stays clean
    private fun moreMenu() {
        val names = listOf(voiceLabel(), t("COMMENTS", "التعليقات"), autoLabel(), boardLabel(), t("FLIP THE BOARD", "اقلب الرقعة"), t("COPY THE GAME (PGN)", "انسخ اللعبة (PGN)"), "FEN", t("RESIGN", "استسلام"), t("OFFER A DRAW", "اعرض تعادل"))
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(t("More", "المزيد"))
            .setItems(names.toTypedArray()) { _, i ->
                when (i) {
                    0 -> { spoken = !spoken; prefs.putB("chessVoice", spoken) }
                    1 -> talkDialog()
                    2 -> { auto = !auto; if (auto) playForMe() }
                    3 -> { themeNo = BoardThemes.next(themeNo); prefs.putI("chessBoard", themeNo); board.theme = BoardThemes.at(themeNo); board.invalidate() }
                    4 -> { board.flipped = !board.flipped; board.invalidate() }
                    5 -> copyPgn()
                    6 -> fenDialog()
                    7 -> resign()
                    else -> offerDraw()
                }
            }.show()
    }

    // the game as text, so it can go to a chess site or a friend
    private fun copyPgn() {
        val sb = StringBuilder()
        val res = when { !over -> "*"; endReason == "mate" -> if (pos.white) "0-1" else "1-0"; endReason == "resigned" -> if (playerWhite) "0-1" else "1-0"; else -> "1/2-1/2" }
        sb.append("[Event \"Capri\"]\n[White \"").append(if (playerWhite) "Me" else "Capri").append("\"]\n[Black \"").append(if (playerWhite) "Capri" else "Me").append("\"]\n[Result \"").append(res).append("\"]\n\n")
        for (i in moves.indices) { val m = moves[i] ?: continue; val b = history[i]; if (b.white) sb.append(i / 2 + 1).append(". "); sb.append(San.of(b, m)).append(' ') }
        sb.append(res)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("PGN", sb.toString()))
        say("Copied the game.", "نسخت اللعبة.")
    }

    private fun boardLabel() = t("BOARD: ", "الرقعة: ") + BoardThemes.at(themeNo).let { if (L.ar) it.nameAr else it.name }

    private fun say(en: String, ar: String) { says.text = t(en, ar) }

    private fun voiceLabel() = t("VOICE: ", "الصوت: ") + if (spoken) t("ON", "شغال") else t("OFF", "مطفي")

    // Shows her remark and, if you switched her voice on, has the pet speak it too.
    private fun remark(line: String?) {
        if (line.isNullOrBlank()) return
        says.text = line
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
        val ms = if (level >= 5) 4000L else 2200L
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
        if (have != null) { nudgeOrHint(have); return }
        say("Let me look...", "خلني أشوف...")
        val snap = pos
        val keys = keysNow()
        bg("chess", this) {
            val a = Engine.analyse(snap, 3000, 30, keys)
            ui.post { if (!isFinishing && pos === snap && a != null) { suggest = a; suggestKey = Engine.key(snap); setEval(a, snap.white); nudgeOrHint(a) } }
        }
    }

    // The first press only says which piece to look at; the second shows the move (a coach, not an answer sheet).
    private var hintStageKey = 0L
    private fun nudgeOrHint(a: Analysis) {
        val k = Engine.key(pos)
        if (hintStageKey != k) { hintStageKey = k; remark(Coach.nudge(pos.b[a.move.from], a.move.from, L.ar)); return }
        announceHint(a)
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
            val a = Engine.analyse(snap, if (level >= 5) 6000L else 3500L, 30, keys, true)
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

    private fun newGame() {
        puzzleAt = -1; puzzles = emptyList(); puzzleWait = false
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
        if (says.text.isEmpty()) says.text = "..."
        if (!playerWhite) aiMove() else think()
    }

    private fun clearSel() { board.selected = -1; board.targets = emptyList() }

    private fun tap(sq: Int) {
        if (over || thinking || puzzleWait || pos.white != playerWhite) return
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
        if (training) { trainMove(m); return }
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
        if ("mate" !in talk.enabled && st == "mate" || "draw" !in talk.enabled && st != "mate") says.text = (if (st == "mate") t("Checkmate.", "كش مات.") else t("A draw.", "تعادل."))
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
                if (ex == null) { says.text = plain }
                else ex(prompt) { r -> ui.post { if (!isFinishing) { if (r.isNullOrBlank() || r.trim() == "NOTHING") says.text = plain else remark(r) } } }
            }
        }
    }

    // Steps forward again after UNDO (one move at a time), so an imported game can be walked through.
    private fun redoMove() {
        if (thinking || training || redo.isEmpty()) return
        val (after, m) = redo.removeAt(redo.size - 1)
        history.add(pos); moves.add(m); pos = after
        board.last = m; board.hint = null; suggest = null; suggestKey = 0L; pendingJudge = null
        over = false; endReason = "playing"
        clearSel(); refresh()
        finished(false)
    }

    private fun undo() {
        if (thinking || training || history.isEmpty()) return
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

    private fun glyphs(codes: List<Int>): String = codes.joinToString("") { "" + ('\u265A' + when (it) { 5 -> 1; 4 -> 2; 3 -> 3; 2 -> 4; else -> 5 }) + "\uFE0E" }

    // "1. e4 e5 2. Nf3 ..." for the whole game
    private fun movesLine(): String {
        val sb = StringBuilder()
        for (i in moves.indices) {
            val m = moves[i] ?: continue
            val before = history[i]
            if (before.white) sb.append(i / 2 + 1).append(". ") else if (i == 0) sb.append("1\u2026 ")
            sb.append(San.of(before, m)).append("  ")
        }
        return sb.toString().trim()
    }

    private fun refresh() {
        board.pos = pos
        board.invalidate()
        val check = !over && Chess.inCheck(pos, pos.white)
        val mine = pos.white == playerWhite
        status.text = when {
            over -> when (endReason) {
                "mate" -> t("Checkmate", "كش مات"); "stalemate" -> t("Draw (stalemate)", "تعادل (جمود)")
                "repetition" -> t("Draw (same position 3 times)", "تعادل (تكرار 3 مرات)"); "fifty" -> t("Draw (50 moves, no capture or pawn move)", "تعادل (50 نقلة بدون أكل أو بيدق)")
                "insufficient" -> t("Draw (not enough pieces)", "تعادل (قطع غير كافية)"); "resigned" -> t("You resigned", "استسلمت"); "agreed" -> t("Draw (agreed)", "تعادل (باتفاق)"); else -> t("Draw", "تعادل") }
            thinking -> t("She is thinking...", "تفكر...")
            check -> t("Check! ", "كش! ") + (if (mine) t("Your move", "دورك") else t("Her move", "دورها"))
            mine -> t("Your move", "دورك")
            else -> t("Her move", "دورها")
        }
        // the pill takes the colour of the moment: red for check or the end, her colour while she thinks, yours when it is you
        val edge = when { over || check -> Night.sRed; thinking || !mine -> Night.hot; else -> Night.sCyan }
        status.background = GradientDrawable().apply { setColor(Night.alpha(edge, 38)); setStroke(dp(1), edge); cornerRadius = dp(18).toFloat() }
        // what each side has taken, and who is ahead
        val lead = Captured.lead(pos.b) * (if (playerWhite) 1 else -1)
        val takenByMe = glyphs(Captured.lost(pos.b, !playerWhite))
        val takenByHer = glyphs(Captured.lost(pos.b, playerWhite))
        capMe.text = takenByMe + (if (lead > 0) "  +$lead" else "")
        capHer.text = takenByHer + (if (lead < 0) "  +${-lead}" else "")
        openingLine()
        opening.text = openingName?.let { "\u265E " + it } ?: ""
        opening.visibility = if (openingName == null) View.GONE else View.VISIBLE
        strip.text = movesLine().ifEmpty { t("No moves yet", "ما فيه نقلات للحين") }
        stripScroll.post { stripScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun resign() {
        if (over || thinking || training) return
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(t("Resign?", "تستسلم؟"))
            .setPositiveButton(t("Resign", "استسلام")) { _, _ ->
                over = true; endReason = "resigned"; refresh()
                remark(t("Good game. Want a review? Press REVIEW THE GAME.", "لعبة حلوة. تبي مراجعة؟ اضغط راجع اللعبة."))
            }.setNegativeButton(t("Keep playing", "أكمل"), null).show()
    }

    // She takes the draw only when she is not better (and the game is not just starting).
    private fun offerDraw() {
        if (over || thinking || training || pos.white != playerWhite || moves.isEmpty()) return
        say("Let me think about it...", "خلني أفكر...")
        val snap = pos
        bg("chess", this) {
            val a = Engine.analyse(snap, 800, 14)
            val her = -(a?.score ?: 0)          // the score is for the side to move, which is you
            ui.post {
                if (isFinishing || pos !== snap || over) return@post
                val yes = Coach.acceptsDraw(her, moves.size)
                remark(Coach.drawReply(yes, L.ar))
                if (yes) { over = true; endReason = "agreed"; refresh() }
            }
        }
    }

    // ---- training on your own mistakes: the positions where the review found you losing the most, one by one ----
    class Puzzle(val pos: Pos, val best: Move, val bestSan: String, val playedSan: String)
    private var puzzles: List<Puzzle> = emptyList()
    private var puzzleAt = -1
    private var puzzleTries = 0
    private var puzzleSolved = 0
    private var puzzleWait = false
    private val training get() = puzzleAt >= 0

    private var trainSide = true
    private fun startTraining(list: List<Puzzle>) {
        trainSide = playerWhite
        puzzles = list; puzzleAt = -1; puzzleSolved = 0
        nextPuzzle()
    }

    private fun nextPuzzle() {
        puzzleAt++; puzzleWait = false
        if (puzzleAt >= puzzles.size) {
            val n = puzzles.size; val ok = puzzleSolved
            puzzles = emptyList(); puzzleAt = -1
            playerWhite = trainSide
            newGame()
            remark(Coach.puzzleDone(ok, n, L.ar))
            return
        }
        val z = puzzles[puzzleAt]
        puzzleTries = 0
        pos = z.pos; history.clear(); moves.clear(); redo.clear()
        over = false; endReason = "playing"; thinking = false
        playerWhite = z.pos.white; board.flipped = !playerWhite; board.last = null; board.hint = null
        suggest = null; suggestKey = 0L; pendingJudge = null; hintStageKey = 0L
        clearSel(); refresh()
        remark(Coach.puzzleIntro(puzzleAt + 1, puzzles.size, z.playedSan, L.ar))
    }

    private fun trainMove(m: Move) {
        val z = puzzles[puzzleAt]
        clearSel()
        if (m.from == z.best.from && m.to == z.best.to && m.promo == z.best.promo) {
            puzzleSolved++; puzzleWait = true
            pos = Chess.make(z.pos, m); board.last = m; board.animate(m, z.pos.b[m.to]); refresh()
            remark(Coach.puzzleRight(L.ar))
            ui.postDelayed({ if (!isFinishing && training) nextPuzzle() }, 1500)
        } else {
            puzzleTries++
            if (puzzleTries >= 2) {
                puzzleWait = true; board.hint = z.best; board.invalidate()
                remark(Coach.puzzleShow(z.bestSan, L.ar))
                ui.postDelayed({ if (!isFinishing && training) nextPuzzle() }, 2600)
            } else remark(Coach.puzzleWrong(L.ar))
        }
    }

    // The name of the opening, once the moves match one.
    private var openingFor = -1
    private var openingName: String? = null
    private fun openingLine(): String {
        if (openingFor != moves.size) {
            openingFor = moves.size
            openingName = try {
                if (moves.isEmpty() || moves.any { it == null }) null
                else Openings.name(moves.indices.map { San.of(history[it], moves[it]!!) })
            } catch (e: Exception) { null }
        }
        return openingName?.let { "\n" + t("Opening: ", "الافتتاحية: ") + it } ?: ""
    }

    // A look back at the game: the engine grades every move, then she shows accuracy and the moves that cost the most.
    private var reviewing = false
    private fun review() {
        if (reviewing || thinking) return
        if (moves.size < 2 || moves.any { it == null }) { say("Play a few moves first.", "العب كم نقلة أول."); return }
        reviewing = true
        val positions = ArrayList<Pos>(history); positions.add(pos)
        val played = ArrayList<Move>(moves.map { it!! })
        val whiteIsYou = playerWhite
        val opening = openingLine().trim()
        var cancelled = false
        val dlg = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(t("Reviewing the game", "أراجع اللعبة"))
            .setMessage("0 / ${positions.size}").setNegativeButton(t("Cancel", "إلغاء")) { _, _ -> cancelled = true }.setCancelable(false).create()
        dlg.show()
        bg("chess", this) {
            val evals = IntArray(positions.size); val bests = arrayOfNulls<Move>(positions.size)
            for (i in positions.indices) {
                if (cancelled || isFinishing) break
                val p = positions[i]
                if (Chess.legal(p).isEmpty()) evals[i] = if (Chess.inCheck(p, p.white)) -100000 else 0
                else { val a = Engine.analyse(p, 220, 14); if (a != null) { evals[i] = a.score; bests[i] = a.move } }
                ui.post { if (!isFinishing && dlg.isShowing) dlg.setMessage("${i + 1} / ${positions.size}") }
            }
            val rows = ArrayList<Review.Row>()
            if (!cancelled && !isFinishing) for (i in played.indices) {
                val p = positions[i]
                val san = San.of(p, played[i])
                val best = bests[i]?.let { San.of(p, it) }
                rows.add(Review.row(i, p.white, san, best, evals[i], -evals[i + 1], Chess.legal(p).size == 1))
            }
            val whiteCp = Coach.whiteView(evals, BooleanArray(positions.size) { positions[it].white })
            val list = rows.filter { it.white == whiteIsYou && (it.grade == Review.Grade.MISTAKE || it.grade == Review.Grade.BLUNDER) && bests[it.ply] != null }
                .sortedByDescending { it.loss }.take(5).sortedBy { it.ply }
                .map { Puzzle(positions[it.ply], bests[it.ply]!!, it.best ?: it.san, it.san) }
            ui.post {
                reviewing = false
                try { dlg.dismiss() } catch (e: Exception) { }
                if (cancelled || isFinishing) return@post
                showReview(rows, whiteIsYou, opening, whiteCp, list)
            }
        }
    }

    private fun showReview(rows: List<Review.Row>, youWhite: Boolean, opening: String, graph: IntArray, list: List<Puzzle>) {
        val sb = StringBuilder()
        if (opening.isNotEmpty()) sb.append(opening).append("\n\n")
        fun side(white: Boolean, who: String) {
            val x = Review.side(rows, white)
            sb.append(who).append(": ").append(t("accuracy ", "الدقة ")).append(Math.round(x.accuracy)).append("%\n")
            sb.append("   ").append(t("Best ", "ممتازة ")).append(x.best).append(" \u00B7 ").append(t("Good ", "كويسة ")).append(x.good).append(" \u00B7 ")
                .append("?! ").append(x.inaccuracies).append(" \u00B7 ? ").append(x.mistakes).append(" \u00B7 ?? ").append(x.blunders).append("\n")
        }
        side(youWhite, t("You", "أنت")); side(!youWhite, "Capri")
        val key = Review.keyMoments(rows)
        sb.append("\n")
        if (key.isEmpty()) sb.append(t("No big mistakes. Clean game.", "ما فيه أخطاء كبيرة. لعبة نظيفة."))
        else {
            sb.append(t("Moves that cost the most:", "النقلات اللي كلّفت أكثر:")).append("\n")
            for (r in key) {
                sb.append("\u2022 ").append(Review.moveText(r)).append(Review.mark(r.grade)).append("  ").append(Review.label(r.grade, L.ar))
                if (r.best != null) sb.append(" \u2014 ").append(t("better: ", "الأفضل: ")).append(r.best)
                sb.append("\n")
            }
        }
        val tv = TextView(this); tv.text = sb.toString().trimEnd(); tv.textSize = 14f; tv.setTextColor(Night.white); tv.typeface = Typeface.MONOSPACE
        tv.setPadding(dp(20), dp(10), dp(20), dp(10))
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL
        val bad = rows.filter { it.grade == Review.Grade.MISTAKE || it.grade == Review.Grade.BLUNDER }.map { it.ply + 1 }.toSet()
        if (graph.size >= 3) col.addView(EvalGraph(this, graph, bad), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(96)).apply { setMargins(dp(20), dp(8), dp(20), 0) })
        col.addView(tv)
        val sv = android.widget.ScrollView(this); sv.addView(col)
        val b = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(t("Game review", "مراجعة اللعبة")).setView(sv)
            .setPositiveButton(t("OK", "تمام"), null)
            .setNeutralButton(t("Copy", "نسخ")) { _, _ ->
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Capri", sb.toString()))
            }
        if (list.isNotEmpty()) b.setNegativeButton(t("Train my mistakes", "تدرّب على أخطائي")) { _, _ -> startTraining(list) }
        b.show()
    }

    override fun onDestroy() { ui.removeCallbacksAndMessages(null); super.onDestroy() }
}


// A thin bar above the board: the lighter part is White's share, with the number from her search.
class EvalBar(ctx: Context) : View(ctx) {
    var cp = 0               // centipawns, White's view
    var mate: Int? = null    // mate in N for White (positive) or Black (negative)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textSize = 24f }
    private val clip = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val share = if (mate != null) (if (mate!! > 0) 1f else 0f) else (0.5f + Math.atan(cp / 400.0).toFloat() / Math.PI.toFloat()).coerceIn(0.03f, 0.97f)
        clip.rewind(); clip.addRoundRect(RectF(0f, 0f, w, h), h / 2, h / 2, Path.Direction.CW)
        canvas.save(); canvas.clipPath(clip)
        p.color = Color.parseColor("#15101C"); canvas.drawRect(0f, 0f, w, h, p)
        p.color = Color.parseColor("#E8E2EC"); canvas.drawRect(0f, 0f, w * share, h, p)
        p.color = Color.argb(90, 128, 128, 128); canvas.drawRect(w / 2 - 1f, 0f, w / 2 + 1f, h, p)   // the even line
        canvas.restore()
        txt.textSize = h * 0.78f
        val label = if (mate != null) "M" + kotlin.math.abs(mate!!) else (if (cp >= 0) "+" else "") + String.format(java.util.Locale.US, "%.1f", cp / 100f)
        txt.color = if (share > 0.5f) Color.parseColor("#15101C") else Color.parseColor("#E8E2EC")
        if (share > 0.5f) canvas.drawText(label, h * 0.6f, h * 0.8f, txt) else canvas.drawText(label, w - txt.measureText(label) - h * 0.6f, h * 0.8f, txt)
    }
}


// The game at a glance: the engine's score at each position, from White's side (above the line is better for White). Red dots mark mistakes.
class EvalGraph(ctx: Context, private val cp: IntArray, private val bad: Set<Int>) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val d = resources.displayMetrics.density
        p.style = Paint.Style.FILL; p.color = Night.alpha(Night.field, 255); c.drawRoundRect(0f, 0f, w, h, 8 * d, 8 * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = d; p.color = Night.alpha(Night.rule, 160); c.drawLine(0f, h / 2, w, h / 2, p)
        val n = cp.size; if (n < 2) return
        fun x(i: Int) = 6 * d + (w - 12 * d) * i / (n - 1)
        fun y(i: Int) = h / 2 - (cp[i].coerceIn(-600, 600) / 600f) * (h / 2 - 6 * d)
        val path = Path(); path.moveTo(x(0), y(0)); for (i in 1 until n) path.lineTo(x(i), y(i))
        p.strokeWidth = 2 * d; p.color = Night.cyan; c.drawPath(path, p)
        p.style = Paint.Style.FILL; p.color = Night.sRed
        for (i in bad) if (i in 0 until n) c.drawCircle(x(i), y(i), 3.5f * d, p)
    }
}
