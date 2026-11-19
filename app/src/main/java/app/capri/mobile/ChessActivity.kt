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

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; color = Color.parseColor("#92828C") }
    private val light = Color.parseColor("#2B2434")
    private val dark = Color.parseColor("#15101C")

    override fun onMeasure(w: Int, h: Int) {
        val s = MeasureSpec.getSize(w)
        setMeasuredDimension(s, s)
    }

    private fun sqOf(r: Int, c: Int) = if (flipped) (7 - r) * 8 + (7 - c) else r * 8 + c

    override fun onDraw(canvas: Canvas) {
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
        for (r in 0..7) for (c in 0..7) {
            val sq = sqOf(r, c)
            val pc = pos.b[sq]
            if (pc == 0) continue
            val ch = "" + ('\u265A' + when (abs(pc)) { 6 -> 0; 5 -> 1; 4 -> 2; 3 -> 3; 2 -> 4; else -> 5 }) + "\uFE0E"
            val cx = (c + 0.5f) * cell
            val cy = (r + 0.5f) * cell + cell * 0.28f
            glyph.style = Paint.Style.STROKE; glyph.strokeWidth = cell * 0.06f
            glyph.color = if (pc > 0) Color.parseColor("#0A0A10") else Color.parseColor("#F2F2F2")
            canvas.drawText(ch, cx, cy, glyph)
            glyph.style = Paint.Style.FILL
            glyph.color = if (pc > 0) Color.parseColor("#F4F0F2") else Color.parseColor("#E84246")
            canvas.drawText(ch, cx, cy, glyph)
        }
        for (t in targets) {
            val r = if (flipped) 7 - t / 8 else t / 8
            val c = if (flipped) 7 - t % 8 else t % 8
            p.style = if (pos.b[t] != 0) Paint.Style.STROKE else Paint.Style.FILL
            p.strokeWidth = cell * 0.07f
            p.color = Color.argb(200, 94, 240, 246)
            canvas.drawCircle((c + 0.5f) * cell, (r + 0.5f) * cell, if (pos.b[t] != 0) cell * 0.42f else cell * 0.12f, p)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
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
    private var playerWhite = true
    private var level = 2
    private var thinking = false
    private var over = false
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun t(en: String, ar: String) = L.t(en, ar)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        L.init(prefs)
        level = prefs.getI("chessLevel", 2).clampTo(1, 3)
        val accent = Themes.accent(prefs)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Night.ground)
        root.setPadding(dp(12), dp(28), dp(12), dp(12))

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

        board = BoardView(this)
        board.accent = accent
        board.onTap = { tap(it) }
        root.addView(board, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val row1 = LinearLayout(this); row1.orientation = LinearLayout.HORIZONTAL
        val row2 = LinearLayout(this); row2.orientation = LinearLayout.HORIZONTAL
        row1.addView(button(t("NEW GAME", "لعبة جديدة")) { newGame() }, weight())
        row1.addView(button(t("UNDO", "تراجع")) { undo() }, weight())
        val lvl = button("") { level = level % 3 + 1; prefs.putI("chessLevel", level); levelLabel(it as TextView) }
        levelLabel(lvl)
        row2.addView(lvl, weight())
        val side = button("") {
            playerWhite = !playerWhite
            (it as TextView).text = if (playerWhite) t("YOU: WHITE", "أنت: الأبيض") else t("YOU: BLACK", "أنت: الأسود")
            newGame()
        }
        side.text = t("YOU: WHITE", "أنت: الأبيض")
        row2.addView(side, weight())
        root.addView(row1)
        root.addView(row2)
        setContentView(root)

        sprite.play("idle")
        newGame()
    }

    private fun weight() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(6), dp(3), 0) }

    private fun levelLabel(v: TextView) {
        v.text = t("LEVEL: ", "المستوى: ") + when (level) { 1 -> t("EASY", "سهل"); 2 -> t("MEDIUM", "متوسط"); else -> t("HARD", "صعب") }
    }

    private fun button(s: String, click: (View) -> Unit): TextView {
        val b = TextView(this)
        b.text = s
        b.setTextColor(Night.white)
        b.textSize = 13f
        b.typeface = Typeface.DEFAULT_BOLD
        b.gravity = Gravity.CENTER
        b.setPadding(dp(8), dp(12), dp(8), dp(12))
        b.background = GradientDrawable().apply { setColor(Night.hover); setStroke(dp(1), Night.edge); cornerRadius = dp(6).toFloat() }
        b.setOnClickListener(click)
        return b
    }

    private fun say(en: String, ar: String) { says.text = "CAPRI: " + t(en, ar) }

    private fun newGame() {
        pos = Chess.start()
        history.clear(); moves.clear()
        over = false
        thinking = false
        board.flipped = !playerWhite
        board.last = null
        clearSel()
        refresh()
        if (Math.random() < 0.5) say("Let's play. Don't go easy on me.", "يلا نلعب. لا تهوّن علي.")
        else say("White moves first. Show me what you've got.", "الأبيض يبدأ. وريني شطارتك.")
        if (!playerWhite) aiMove()
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
                    AlertDialog.Builder(this).setTitle(t("Promote to", "رقّي إلى")).setItems(names.toTypedArray()) { _, i ->
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
        val captured = pos.b[m.to] != 0 || (abs(pos.b[m.from]) == 1 && m.to == pos.ep)
        history.add(pos); moves.add(m)
        pos = Chess.make(pos, m)
        board.last = m
        clearSel()
        refresh()
        if (finished(true)) return
        if (captured && Math.random() < 0.5) say("Ouch. Fine, you got that one.", "آخ. ماشي، هذي لك.")
        else if (Chess.inCheck(pos, pos.white)) say("Check? I saw that coming.", "كش؟ كنت متوقعتها.")
        else if (Math.random() < 0.25) say(listOf("Hmm, interesting.", "Let me think...", "Not bad.").random(), listOf("همم، مثير.", "خلني أفكر...", "مو سيئة.").random())
        aiMove()
    }

    private fun aiMove() {
        thinking = true
        sprite.glitchFx = true
        refresh()
        val snapshot = pos
        val lv = level
        bg("chess", this) {
            val m = Chess.best(snapshot, lv)
            ui.postDelayed({
                sprite.glitchFx = false
                thinking = false
                if (isFinishing || m == null || pos !== snapshot) return@postDelayed
                val cap = snapshot.b[m.to] != 0
                history.add(pos); moves.add(m)
                pos = Chess.make(pos, m)
                board.last = m
                refresh()
                if (finished(false)) return@postDelayed
                if (cap) say("Mine now.", "صارت لي.")
                else if (Chess.inCheck(pos, pos.white)) say("Check. Watch your king.", "كش. دير بالك على الملك.")
            }, 350)
        }
    }

    private fun finished(playerJustMoved: Boolean): Boolean {
        val st = Chess.status(pos)
        if (st == "playing") return false
        over = true
        when (st) {
            "mate" -> if (playerJustMoved) { say("Checkmate. You got me... good game.", "كش مات. غلبتني... لعبة حلوة."); sprite.play("glitch", false) { sprite.play("idle") } }
                      else { say("Checkmate. Better luck next time, choom.", "كش مات. حظ أوفر المرة الجاية."); sprite.play("grin", false) { sprite.play("idle") } }
            "stalemate" -> say("Stalemate. A draw, I'll take it.", "تعادل بالجمود. ماشي.")
            else -> say("Not enough pieces left. A draw.", "ما بقى قطع كافية. تعادل.")
        }
        refresh()
        return true
    }

    private fun undo() {
        if (thinking || history.isEmpty()) return
        // take back her move and yours, so it is your turn again
        var n = if (pos.white == playerWhite) 2 else 1
        while (n-- > 0 && history.isNotEmpty()) { pos = history.removeAt(history.size - 1); moves.removeAt(moves.size - 1) }
        over = false
        board.last = moves.lastOrNull()
        clearSel()
        refresh()
        say("Okay, take it back.", "ماشي، رجّعها.")
    }

    private fun refresh() {
        board.pos = pos
        board.invalidate()
        status.text = when {
            over -> when (Chess.status(pos)) { "mate" -> t("Checkmate", "كش مات"); "stalemate" -> t("Draw (stalemate)", "تعادل (جمود)"); else -> t("Draw", "تعادل") }
            thinking -> t("She is thinking...", "تفكر...")
            Chess.inCheck(pos, pos.white) -> t("Check! ", "كش! ") + t("Your move", "دورك")
            pos.white == playerWhite -> t("Your move", "دورك")
            else -> t("Her move", "دورها")
        }
    }

    override fun onDestroy() { ui.removeCallbacksAndMessages(null); super.onDestroy() }
}
