package app.capri.mobile

import android.app.Activity
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

// Draws Connect four and tic-tac-toe and reports which column / cell was tapped.
class GameBoard(ctx: Context, val kind: String) : View(ctx) {
    var cells = IntArray(if (kind == "c4") 42 else 9)
    var glow: List<Int> = emptyList()
    var accent = Color.parseColor("#FF2A6D")
    var onTap: (Int) -> Unit = {}
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cols get() = if (kind == "c4") 7 else 3
    private val rows get() = if (kind == "c4") 6 else 3

    override fun onMeasure(w: Int, h: Int) {
        val s = MeasureSpec.getSize(w)
        setMeasuredDimension(s, (s * rows / cols.toFloat()).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val cell = width / cols.toFloat()
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#15101C")
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), cell * 0.12f, cell * 0.12f, p)
        for (r in 0 until rows) for (c in 0 until cols) {
            val i = r * cols + c
            val cx = c * cell + cell / 2; val cy = r * cell + cell / 2
            if (kind == "c4") {
                p.style = Paint.Style.FILL
                p.color = when (cells[i]) { 1 -> Color.parseColor("#5EF0F6"); 2 -> accent; else -> Color.parseColor("#2B2434") }
                canvas.drawCircle(cx, cy, cell * 0.4f, p)
                if (i in glow) { p.style = Paint.Style.STROKE; p.strokeWidth = cell * 0.08f; p.color = Color.WHITE; canvas.drawCircle(cx, cy, cell * 0.4f, p) }
            } else {
                p.style = Paint.Style.STROKE; p.strokeWidth = cell * 0.02f; p.color = Color.parseColor("#3A2F46")
                canvas.drawRect(c * cell, r * cell, (c + 1) * cell, (r + 1) * cell, p)
                p.strokeWidth = cell * 0.09f
                p.color = if (i in glow) Color.WHITE else if (cells[i] == 1) Color.parseColor("#5EF0F6") else accent
                val m = cell * 0.26f
                if (cells[i] == 1) { canvas.drawLine(c * cell + m, r * cell + m, (c + 1) * cell - m, (r + 1) * cell - m, p); canvas.drawLine((c + 1) * cell - m, r * cell + m, c * cell + m, (r + 1) * cell - m, p) }
                else if (cells[i] == 2) canvas.drawCircle(cx, cy, cell * 0.24f, p)
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) {
            val c = (e.x / (width / cols.toFloat())).toInt().coerceIn(0, cols - 1)
            val r = (e.y / (height / rows.toFloat())).toInt().coerceIn(0, rows - 1)
            onTap(if (kind == "c4") c else r * cols + c)
        }
        return true
    }
}

class GamesActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var kind: String
    private lateinit var status: TextView
    private lateinit var says: TextView
    private lateinit var sprite: SpriteView
    private lateinit var holder: LinearLayout
    private var board: GameBoard? = null
    private var cells = IntArray(0)
    private var level = 2
    private var over = false
    private var busy = false
    private var youStart = true
    private var wins = 0; private var losses = 0; private var draws = 0
    private var lastRps = -1
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun t(en: String, ar: String) = L.t(en, ar)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        L.init(prefs)
        kind = intent.getStringExtra("game").let { if (it == "ttt" || it == "rps") it else "c4" }
        level = prefs.getI("gameLevel", 2).clampTo(1, 3)
        val accent = Themes.accent(prefs)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Night.ground)
        root.setPadding(dp(12), dp(28), dp(12), dp(12))

        val bar = TextView(this)
        bar.text = "\u2039  " + when (kind) { "ttt" -> "TIC-TAC-TOE"; "rps" -> "ROCK PAPER SCISSORS"; else -> "CONNECT FOUR" } + " >"
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
        says.setTextColor(Night.cyan); says.textSize = 15f; says.typeface = Typeface.MONOSPACE
        says.setPadding(dp(14), dp(10), dp(14), dp(10))
        says.background = NeonPanel(resources.displayMetrics.density)
        top.addView(says, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8) })
        root.addView(top)

        status = TextView(this)
        status.setTextColor(Night.white); status.textSize = 14f; status.gravity = Gravity.CENTER
        status.setPadding(0, dp(8), 0, dp(8))
        root.addView(status)

        holder = LinearLayout(this)
        holder.orientation = LinearLayout.VERTICAL
        root.addView(holder, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        row.addView(button(t("NEW GAME", "لعبة جديدة")) { newGame() }, weight())
        if (kind != "rps") {
            val lvl = button("") { level = level % 3 + 1; prefs.putI("gameLevel", level); (it as TextView).text = levelText() }
            lvl.text = levelText()
            row.addView(lvl, weight())
        }
        row.addView(button(t("OTHER GAME", "لعبة ثانية")) {
            val next = when (kind) { "c4" -> "ttt"; "ttt" -> "rps"; else -> "c4" }
            startActivity(android.content.Intent(this, GamesActivity::class.java).putExtra("game", next)); finish()
        }, weight())
        root.addView(row)
        setContentView(neonWrap(this, root))
        sprite.play("idle")
        newGame()
    }

    private fun levelText() = t("LEVEL: ", "المستوى: ") + when (level) { 1 -> t("EASY", "سهل"); 2 -> t("MEDIUM", "متوسط"); else -> t("HARD", "صعب") }
    private fun weight() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(6), dp(3), 0) }

    private fun button(s: String, click: (View) -> Unit): TextView {
        val b = TextView(this)
        b.text = s; b.setTextColor(Night.white); b.textSize = 13f; b.typeface = Typeface.DEFAULT_BOLD; b.gravity = Gravity.CENTER
        b.setPadding(dp(8), dp(12), dp(8), dp(12))
        b.typeface = Typeface.MONOSPACE
        b.background = GradientDrawable().apply { setColor(Night.field); setStroke(dp(1), Night.deepEdge); cornerRadius = dp(22).toFloat() }
        b.foreground = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), null, null)
        b.setOnClickListener(click)
        return b
    }

    private fun say(en: String, ar: String) { says.text = "CAPRI: " + t(en, ar) }
    private fun score() { status.text = t("You $wins   Draw $draws   Capri $losses", "أنت $wins   تعادل $draws   كابري $losses") }

    private fun newGame() {
        holder.removeAllViews()
        over = false; busy = false
        score()
        if (kind == "rps") { buildRps(); say("Rock, paper, scissors. No crying when you lose.", "حجر ورقة مقص. بدون بكي لما تخسر."); return }
        val b = GameBoard(this, kind)
        b.accent = Themes.accent(prefs)
        b.onTap = { tap(it) }
        board = b
        cells = b.cells
        holder.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        youStart = !youStart
        if (!youStart) { say("I'll go first.", "أنا أبدأ."); busy = true; ui.postDelayed({ aiTurn() }, 500) }
        else say(if (kind == "c4") "Drop a disc. Tap a column." else "You're X. Tap a square.", if (kind == "c4") "نزّل قطعة. اضغط على عمود." else "أنت X. اضغط على مربع.")
    }

    private fun tap(i: Int) {
        if (over || busy) return
        if (kind == "c4") {
            val r = ConnectFour.dropRow(cells, i)
            if (r < 0) return
            cells[r * 7 + i] = 1
        } else {
            if (cells[i] != 0) return
            cells[i] = 1
        }
        board?.invalidate()
        if (checkEnd(true)) return
        busy = true
        ui.postDelayed({ aiTurn() }, 450)
    }

    private fun aiTurn() {
        if (isFinishing) return
        Thread {
            val m = if (kind == "c4") ConnectFour.aiMove(cells.clone(), level) else TicTacToe.aiMove(cells.clone(), level)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (kind == "c4") { val r = ConnectFour.dropRow(cells, m); if (r >= 0) cells[r * 7 + m] = 2 }
                else if (m >= 0) cells[m] = 2
                board?.invalidate()
                busy = false
                checkEnd(false)
            }
        }.start()
    }

    private fun checkEnd(playerJustMoved: Boolean): Boolean {
        val w = if (kind == "c4") ConnectFour.winner(cells) else TicTacToe.winner(cells)
        val full = if (kind == "c4") ConnectFour.full(cells) else TicTacToe.full(cells)
        if (w == 0 && !full) return false
        over = true
        if (w != 0) board?.glow = if (kind == "c4") ConnectFour.winningCells(cells) else tttLine()
        board?.invalidate()
        when (w) {
            1 -> { wins++; say("You got me. Rematch?", "غلبتني. نعيدها؟"); sprite.play("glitch", false) { sprite.play("idle") } }
            2 -> { losses++; say("Gotcha. Better luck next time, choom.", "فزت. حظ أوفر المرة الجاية."); sprite.play("grin", false) { sprite.play("idle") } }
            else -> { draws++; say("A draw. Again?", "تعادل. مرة ثانية؟") }
        }
        score()
        return true
    }

    private fun tttLine(): List<Int> {
        val lines = listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), listOf(0, 4, 8), listOf(2, 4, 6))
        return lines.firstOrNull { cells[it[0]] != 0 && cells[it[0]] == cells[it[1]] && cells[it[1]] == cells[it[2]] } ?: emptyList()
    }

    private fun buildRps() {
        val names = listOf(t("ROCK", "حجر"), t("PAPER", "ورقة"), t("SCISSORS", "مقص"))
        val result = TextView(this)
        result.setTextColor(Night.white); result.textSize = 18f; result.gravity = Gravity.CENTER; result.setPadding(0, dp(24), 0, dp(24))
        result.text = "?"
        holder.addView(result)
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        for (k in 0..2) row.addView(button(names[k]) {
            val ai = Rps.aiPick(lastRps)
            lastRps = k
            val r = Rps.beats(k, ai)
            result.text = names[k] + "  vs  " + names[ai] + "\n" + when (r) { 1 -> t("You win", "فزت"); -1 -> t("I win", "فزت أنا"); else -> t("Draw", "تعادل") }
            when (r) {
                1 -> { wins++; say("Lucky. Again.", "حظ. مرة ثانية.") }
                -1 -> { losses++; say("Too easy.", "سهلة."); sprite.play("grin", false) { sprite.play("idle") } }
                else -> { draws++; say("Same thing. Again.", "نفس الشي. مرة ثانية.") }
            }
            score()
        }, weight())
        holder.addView(row)
    }

    override fun onDestroy() { ui.removeCallbacksAndMessages(null); super.onDestroy() }
}
