package app.capri.mobile

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

// Her room: a small scene that grows with how close you are. The window shows the real time of day, and each bond level adds something
// (a rug, a plant, a lamp and shelf, string lights). You can pet her, give her a snack, play, let her dance, sit or sleep.
object RoomRules {
    /** What the room contains at a bond level (0..4). */
    fun decor(level: Int): Set<String> = buildSet {
        if (level >= 1) add("rug")
        if (level >= 2) { add("plant"); add("neon") }
        if (level >= 3) { add("lamp"); add("shelf") }
        if (level >= 4) { add("lights"); add("poster") }
    }

    /** Time of day for the window: 0 night, 1 dawn/dusk, 2 day. */
    fun sky(hour: Int): Int = when (hour) { in 7..17 -> 2; in 5..6, in 18..19 -> 1; else -> 0 }

    const val PET_GAP = 6_000L
    const val SNACK_GAP = 45 * 60_000L
    const val PLAY_GAP = 5 * 60_000L

    /** Seconds still to wait before [last] + [gap] has passed, or 0. */
    fun wait(now: Long, last: Long, gap: Long): Long = maxOf(0L, (last + gap - now + 999) / 1000)
}

class RoomView(ctx: Context) : View(ctx) {
    var level = 0
    var hour = 12
    var lampOn = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val start = System.currentTimeMillis()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val floorY = h * 0.72f
        val sky = RoomRules.sky(hour)
        // wall
        p.shader = LinearGradient(0f, 0f, 0f, floorY, if (sky == 0) 0xFF171428.toInt() else 0xFF2A2440.toInt(), 0xFF1B1730.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, floorY, p); p.shader = null
        // wallpaper: soft vertical stripes and a baseboard
        p.color = 0x0DFFFFFF; var sx0 = 0f; var k = 0
        while (sx0 < w) { if (k % 2 == 0) c.drawRect(sx0, 0f, sx0 + w / 18f, floorY, p); sx0 += w / 18f; k++ }
        p.color = 0xFF2B2346.toInt(); c.drawRect(0f, floorY - h * 0.03f, w, floorY, p)
        p.color = 0x22FFFFFF; c.drawRect(0f, floorY - h * 0.03f, w, floorY - h * 0.03f + 3f, p)
        // floor
        p.shader = LinearGradient(0f, floorY, 0f, h, 0xFF3A2E52.toInt(), 0xFF1E1830.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, floorY, w, h, p); p.shader = null
        p.color = 0x33FFFFFF; p.strokeWidth = 2f
        for (i in 1..6) c.drawLine(0f, floorY + (h - floorY) * i / 7f, w, floorY + (h - floorY) * i / 7f, p)

        // window with the real sky
        val wx = w * 0.12f; val wy = h * 0.10f; val ww = w * 0.34f; val wh = h * 0.34f
        p.shader = LinearGradient(0f, wy, 0f, wy + wh,
            when (sky) { 2 -> intArrayOf(0xFF6EC6FF.toInt(), 0xFFB9E6FF.toInt()); 1 -> intArrayOf(0xFFFF8A65.toInt(), 0xFF5C4B8A.toInt()); else -> intArrayOf(0xFF0B1030.toInt(), 0xFF1D2A5C.toInt()) }[0],
            when (sky) { 2 -> 0xFFB9E6FF.toInt(); 1 -> 0xFF5C4B8A.toInt(); else -> 0xFF1D2A5C.toInt() }, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(wx, wy, wx + ww, wy + wh), 14f, 14f, p); p.shader = null
        val t = (System.currentTimeMillis() - start) / 1000.0
        if (sky == 0) {
            p.color = Color.WHITE
            for (i in 0 until 14) { val sx = wx + ww * ((i * 37 % 100) / 100f); val sy = wy + wh * 0.8f * ((i * 53 % 100) / 100f)
                p.alpha = (140 + 100 * sin(t * 1.5 + i)).toInt().coerceIn(40, 255); c.drawCircle(sx, sy, 2.2f, p) }
            p.alpha = 255; p.color = 0xFFF5F1D0.toInt(); c.drawCircle(wx + ww * 0.75f, wy + wh * 0.28f, ww * 0.09f, p)
        } else {
            p.color = if (sky == 2) 0xFFFFE27A.toInt() else 0xFFFFB07A.toInt()
            c.drawCircle(wx + ww * (if (sky == 2) 0.72f else 0.25f), wy + wh * (if (sky == 2) 0.28f else 0.72f), ww * 0.1f, p)
            p.color = 0x66FFFFFF; val cx = ((t * 6) % (ww + 80)).toFloat() - 40
            c.drawRoundRect(RectF(wx + cx, wy + wh * 0.5f, wx + cx + 70, wy + wh * 0.5f + 22), 12f, 12f, p)
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = 8f; p.color = 0xFF5B4A7E.toInt()
        c.drawRoundRect(RectF(wx, wy, wx + ww, wy + wh), 14f, 14f, p)
        p.strokeWidth = 5f; c.drawLine(wx + ww / 2, wy, wx + ww / 2, wy + wh, p); c.drawLine(wx, wy + wh / 2, wx + ww, wy + wh / 2, p)
        p.style = Paint.Style.FILL

        // curtains
        p.color = 0xFF7B2D5B.toInt(); val sway = (sin(t * 0.8) * 4).toFloat()
        val cw = ww * 0.2f
        c.drawRect(wx - 10f, wy - 10f, wx + cw + sway, wy + wh + 14f, p); c.drawRect(wx + ww - cw - sway, wy - 10f, wx + ww + 10f, wy + wh + 14f, p)
        p.color = 0xFF5B1E41.toInt(); c.drawRect(wx - 14f, wy - 16f, wx + ww + 14f, wy - 6f, p)
        // sunbeam or moonlight spilling onto the floor
        val beam = when (sky) { 2 -> 0x22FFF2B0; 1 -> 0x22FF9A6A; else -> 0x226A8CFF }
        val bp = Path(); bp.moveTo(wx + 8f, wy + wh); bp.lineTo(wx + ww - 8f, wy + wh); bp.lineTo(w * 0.62f, h); bp.lineTo(w * 0.05f, h); bp.close()
        p.color = beam; c.drawPath(bp, p)
        // desk with a screen that flickers
        val dx = w * 0.60f; val dy = floorY - h * 0.10f
        p.color = 0xFF4A3A6E.toInt(); c.drawRect(dx, dy, dx + w * 0.34f, dy + h * 0.025f, p); c.drawRect(dx + 6f, dy, dx + 14f, floorY + h * 0.02f, p); c.drawRect(dx + w * 0.34f - 14f, dy, dx + w * 0.34f - 6f, floorY + h * 0.02f, p)
        p.color = 0xFF0E0B1B.toInt(); c.drawRoundRect(RectF(dx + w * 0.07f, dy - h * 0.12f, dx + w * 0.27f, dy - 4f), 8f, 8f, p)
        p.color = if ((t * 2).toInt() % 7 == 0) 0xFF1B6B73.toInt() else 0xFF2EE6E0.toInt(); p.alpha = 200
        for (i in 0..3) c.drawRect(dx + w * 0.09f, dy - h * 0.105f + i * h * 0.022f, dx + w * (0.09f + 0.05f + 0.03f * ((i * 3 + (t * 1.3).toInt()) % 4)), dy - h * 0.095f + i * h * 0.022f, p)
        p.alpha = 255
        // a pink cushion on the floor
        p.color = 0xFFFF6FA5.toInt(); c.drawOval(RectF(w * 0.07f, floorY + h * 0.07f, w * 0.23f, floorY + h * 0.14f), p)
        p.color = 0xFFFFB3D0.toInt(); c.drawOval(RectF(w * 0.10f, floorY + h * 0.075f, w * 0.20f, floorY + h * 0.11f), p)

        val d = RoomRules.decor(level)
        if ("neon" in d) { val a = (190 + 65 * sin(t * 3)).toInt().coerceIn(120, 255)
            p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.color = 0xFFFF2D6F.toInt(); p.alpha = a
            val hp = Path(); val hx = w * 0.50f; val hy = h * 0.20f
            hp.moveTo(hx, hy + 30f); hp.cubicTo(hx - 50f, hy - 10f, hx - 10f, hy - 30f, hx, hy); hp.cubicTo(hx + 10f, hy - 30f, hx + 50f, hy - 10f, hx, hy + 30f)
            c.drawPath(hp, p); p.strokeWidth = 14f; p.alpha = (a / 4); c.drawPath(hp, p); p.alpha = 255; p.style = Paint.Style.FILL }
        if ("rug" in d) { p.color = 0xFF7B2D5B.toInt(); c.drawOval(RectF(w * 0.18f, floorY + (h - floorY) * 0.25f, w * 0.82f, h - (h - floorY) * 0.12f), p)
            p.color = 0xFFB5487F.toInt(); c.drawOval(RectF(w * 0.26f, floorY + (h - floorY) * 0.36f, w * 0.74f, h - (h - floorY) * 0.22f), p) }
        if ("poster" in d) { p.color = 0xFF0E0B1B.toInt(); c.drawRect(w * 0.62f, h * 0.10f, w * 0.88f, h * 0.36f, p); p.color = 0xFFFF2D6F.toInt(); c.drawCircle(w * 0.75f, h * 0.2f, w * 0.05f, p); p.color = 0xFF2EE6A6.toInt(); c.drawRect(w * 0.66f, h * 0.28f, w * 0.84f, h * 0.30f, p) }
        if ("shelf" in d) { p.color = 0xFF6B5A8E.toInt(); c.drawRect(w * 0.60f, h * 0.43f, w * 0.90f, h * 0.45f, p)
            p.color = 0xFFFF2D6F.toInt(); c.drawRect(w * 0.64f, h * 0.38f, w * 0.68f, h * 0.43f, p); p.color = 0xFF2EE6A6.toInt(); c.drawRect(w * 0.70f, h * 0.36f, w * 0.74f, h * 0.43f, p); p.color = 0xFFFFD84A.toInt(); c.drawRect(w * 0.76f, h * 0.39f, w * 0.80f, h * 0.43f, p) }
        if ("plant" in d) { p.color = 0xFF8A5A3C.toInt(); c.drawRect(w * 0.06f, floorY - h * 0.07f, w * 0.14f, floorY + h * 0.02f, p)
            p.color = 0xFF2EE6A6.toInt(); for (i in -2..2) c.drawOval(RectF(w * 0.10f + i * w * 0.02f - w * 0.02f, floorY - h * (0.17f + 0.02f * (2 - Math.abs(i))), w * 0.10f + i * w * 0.02f + w * 0.02f, floorY - h * 0.06f), p) }
        if ("lamp" in d) { val lx = w * 0.90f
            p.color = 0xFF5B4A7E.toInt(); c.drawRect(lx - 3f, floorY - h * 0.2f, lx + 3f, floorY + h * 0.02f, p)
            p.color = if (lampOn) 0xFFFFD84A.toInt() else 0xFF8B7AAE.toInt(); val path = Path(); path.moveTo(lx - w * 0.04f, floorY - h * 0.14f); path.lineTo(lx + w * 0.04f, floorY - h * 0.14f); path.lineTo(lx + w * 0.025f, floorY - h * 0.22f); path.lineTo(lx - w * 0.025f, floorY - h * 0.22f); path.close(); c.drawPath(path, p)
            if (lampOn) { p.shader = RadialGradient(lx, floorY - h * 0.14f, w * 0.35f, 0x55FFD84A, 0x00FFD84A, Shader.TileMode.CLAMP); c.drawCircle(lx, floorY - h * 0.14f, w * 0.35f, p); p.shader = null } }
        if ("lights" in d) { p.strokeWidth = 3f; p.color = 0xFF5B4A7E.toInt(); p.style = Paint.Style.STROKE
            val pa = Path(); pa.moveTo(0f, h * 0.04f); pa.quadTo(w / 2, h * 0.12f, w, h * 0.04f); c.drawPath(pa, p); p.style = Paint.Style.FILL
            val cols = intArrayOf(0xFFFF2D6F.toInt(), 0xFF2EE6A6.toInt(), 0xFFFFD84A.toInt(), 0xFF6EC6FF.toInt())
            for (i in 1..9) { val x = w * i / 10f; val y = h * (0.04f + 0.08f * 4 * (x / w) * (1 - x / w)) + 8f
                p.color = cols[i % 4]; p.alpha = (170 + 85 * sin(t * 2 + i)).toInt().coerceIn(90, 255); c.drawCircle(x, y, 7f, p); p.alpha = 255 } }
        // floating dust and fireflies, and a soft vignette
        for (i in 0 until 16) {
            val fx = w * (((i * 61) % 100) / 100f) + (sin(t * 0.5 + i) * 14).toFloat()
            val fy = h * (1f - (((t * 0.02 + i * 0.137) % 1.0)).toFloat())
            p.color = if (sky == 0) 0xFFFFF3A8.toInt() else 0xFFFFFFFF.toInt(); p.alpha = (60 + 60 * sin(t * 2 + i)).toInt().coerceIn(20, 140)
            c.drawCircle(fx, fy, 2.4f + (i % 3), p)
        }
        p.alpha = 255
        p.shader = RadialGradient(w / 2, h * 0.55f, maxOf(w, h) * 0.75f, 0x00000000, 0x88000000.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        postInvalidateDelayed(90)
    }
}

class RoomActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var room: RoomView
    private lateinit var sprite: SpriteView
    private lateinit var status: TextView
    private lateinit var says: TextView
    private val h = Handler(Looper.getMainLooper())
    private var perched = false
    private var asleep = false
    private var dancing = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this); L.init(prefs)
        val accent = Themes.accent(prefs)
        val root = LinearLayout(this); root.orientation = LinearLayout.VERTICAL; root.setBackgroundColor(Night.ground)
        root.layoutDirection = if (L.ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        root.setPadding(dp(8), dp(28), dp(8), dp(8))
        val bar = TextView(this); bar.text = "‹  " + L.t("HER ROOM", "غرفتها") + " >"; bar.typeface = Typeface.DEFAULT_BOLD; bar.textSize = 16f; bar.setTextColor(Night.amber)
        bar.setPadding(dp(6), 0, 0, dp(8)); bar.setOnClickListener { finish() }; root.addView(bar)

        val stage = FrameLayout(this)
        room = RoomView(this); stage.addView(room, ViewGroup.LayoutParams(-1, -1))
        sprite = SpriteView(this); sprite.glowColor = accent; sprite.lowPower = prefs.getB("lowPower", false)
        sprite.unit = dp(230) / 384f
        stage.addView(sprite, FrameLayout.LayoutParams(dp(240) , dp(230), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(14) })
        says = TextView(this); says.setTextColor(Night.cyan); says.textSize = 14f; says.typeface = Typeface.MONOSPACE; says.setPadding(dp(12), dp(8), dp(12), dp(8))
        says.background = NeonPanel(resources.displayMetrics.density); says.visibility = View.INVISIBLE
        stage.addView(says, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(8) })
        sprite.setOnClickListener { pet() }
        val clip = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setStroke(dp(1), Night.deepEdge) }
        stage.background = clip; stage.clipToOutline = true
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this); status.setTextColor(Night.white); status.textSize = 13f; status.gravity = Gravity.CENTER; status.setPadding(0, dp(8), 0, dp(6))
        root.addView(status)
        val row1 = LinearLayout(this); row1.orientation = LinearLayout.HORIZONTAL
        val row2 = LinearLayout(this); row2.orientation = LinearLayout.HORIZONTAL
        fun b(r: LinearLayout, text: String, f: () -> Unit) {
            val v = TextView(this); v.text = text; v.gravity = Gravity.CENTER; v.textSize = 13f; v.typeface = Typeface.DEFAULT_BOLD; v.setTextColor(accent)
            v.setPadding(dp(4), dp(11), dp(4), dp(11))
            v.background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Night.deep); setStroke(dp(1), Night.deepEdge) }
            v.setOnClickListener { f() }
            r.addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        b(row1, "♥ " + L.t("PET", "دلّعيها")) { pet() }
        b(row1, "🍪 " + L.t("SNACK", "اكلة")) { snack() }
        b(row1, "🎲 " + L.t("PLAY", "العبي")) { play() }
        b(row2, "💃 " + L.t("DANCE", "رقص")) { dance() }
        b(row2, "🪑 " + L.t("SIT", "قعدة")) { sit() }
        b(row2, "💡 " + L.t("LAMP", "اللمبة")) { room.lampOn = !room.lampOn; room.invalidate() }
        b(row2, "💤 " + L.t("SLEEP", "نوم")) { sleep() }
        root.addView(row1); root.addView(row2)
        setContentView(root)
        room.lampOn = room.hour !in 7..17
    }

    override fun onResume() { super.onResume(); refresh(); idle() }
    override fun onPause() { super.onPause(); h.removeCallbacksAndMessages(null) }

    private fun refresh() {
        val pts = prefs.getI("charm", 0); val lvl = Charm.level(pts)
        room.level = lvl; room.hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        status.text = L.t("Bond: ", "العلاقة: ") + Charm.label(pts) + "  ·  " + Charm.progress(pts) + "%  ·  " + (if (lvl < 4) L.t("more things appear as you grow closer", "تطلع أشياء جديدة بالغرفة كل ما تقربتوا") else L.t("her room is complete", "غرفتها كملت"))
        room.invalidate()
    }

    private fun say(s: String) { says.text = s; says.visibility = View.VISIBLE; h.removeCallbacks(hide); h.postDelayed(hide, 3500) }
    private val hide = Runnable { says.visibility = View.INVISIBLE }
    private fun idle() { if (asleep) sprite.play("laydown_sleep") else if (perched) sprite.play("perch") else { sprite.dancing = dancing; sprite.play("idle") } }

    private fun wake() { asleep = false; perched = false; dancing = false; sprite.dancing = false }

    private fun give(n: Int, line: String, emote: String) {
        Charm.add(prefs, n); refresh(); say(line); sprite.emote(emote)
    }

    private fun pet() {
        if (asleep) { wake(); sprite.play("wake", false) { idle() }; say(L.t("Mm... I'm up.", "ممم... فقت.")); return }
        val now = System.currentTimeMillis(); val w = RoomRules.wait(now, prefs.getL("roomPet", 0L), RoomRules.PET_GAP)
        sprite.squash(1f); sprite.hop()
        if (w > 0) { sprite.emote("heart"); say(L.t("Hehe, that tickles.", "هههه تدغدغ.")); return }
        prefs.putL("roomPet", now)
        sprite.emote("star")
        give(1, listOf(L.t("Again!", "كمّل!"), L.t("You're sweet.", "انت لطيف."), L.t("Don't stop.", "لا توقف.")).random(), "heart")
    }

    private fun snack() {
        val now = System.currentTimeMillis(); val w = RoomRules.wait(now, prefs.getL("roomSnack", 0L), RoomRules.SNACK_GAP)
        if (w > 0) { say(L.t("I'm full for now. Ask me in ${(w + 59) / 60} min.", "شبعانة هسه. اسألني بعد ${(w + 59) / 60} دقيقة.")); return }
        prefs.putL("roomSnack", now); wake(); sprite.play("grin", false) { idle() }
        give(3, listOf(L.t("Chips! ...digital ones.", "جبس! ...رقمي."), L.t("Yum. Data crumbs.", "لذيذ. فتات بيانات."), L.t("Best snack ever.", "أحلى اكلة.")).random(), "star")
    }

    private fun play() {
        val now = System.currentTimeMillis(); val w = RoomRules.wait(now, prefs.getL("roomPlay", 0L), RoomRules.PLAY_GAP)
        if (w > 0) { say(L.t("Give me a minute to breathe.", "عطيني دقيقة اتنفس.")); return }
        prefs.putL("roomPlay", now); wake()
        sprite.play("click", false) { sprite.hop(); idle() }
        give(2, L.t("Catch me if you can!", "امسكني إذا تقدر!"), "star")
    }

    private fun dance() { wake(); dancing = true; sprite.dancing = true; sprite.play("idle"); say(L.t("Music in my head!", "موسيقى براسي!")); h.postDelayed({ dancing = false; sprite.dancing = false }, 9000) }

    private fun sit() {
        if (SpriteBank.info(this, "perch") == null) return
        asleep = false; dancing = false; sprite.dancing = false; perched = !perched
        if (perched) { sprite.play("perch"); say(L.t("Nice view from here.", "المنظر حلو من هنا.")) } else idle()
    }

    private fun sleep() { wake(); asleep = true; sprite.play("laydown_sleep"); sprite.emote("sleep"); say(L.t("Zzz...", "زززز...")) }
}
