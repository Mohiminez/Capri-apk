package app.capri.mobile

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

// The desktop's panel look: dark glass with cut corners, a pink-to-cyan edge, faint scanlines and a soft glow.
class PanelDrawable(
    private val edgeA: Int,
    private val edgeB: Int,
    private val density: Float,
    private val chamferDp: Float = 6f,
    fill: Int = Color.parseColor("#F00A1014")
) : Drawable() {
    private val path = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * density }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f * density }
    private val scan = Paint().apply { color = Night.white; alpha = 9 }

    override fun onBoundsChange(b: Rect) {
        val c = chamferDp * density
        val l = b.left + 1f
        val t = b.top + 1f
        val r = b.right - 1f
        val bt = b.bottom - 1f
        path.reset()
        path.moveTo(l + c, t); path.lineTo(r - c, t); path.lineTo(r, t + c); path.lineTo(r, bt - c)
        path.lineTo(r - c, bt); path.lineTo(l + c, bt); path.lineTo(l, bt - c); path.lineTo(l, t + c)
        path.close()
        val shader = LinearGradient(l, t, r, bt, edgeA, edgeB, Shader.TileMode.CLAMP)
        edge.shader = shader
        glow.shader = shader
        glow.alpha = 60
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawPath(path, fillPaint)
        canvas.save()
        canvas.clipPath(path)
        var y = b.top.toFloat()
        val step = 3f * density
        while (y < b.bottom) {
            canvas.drawRect(b.left.toFloat(), y, b.right.toFloat(), y + density, scan)
            y += step
        }
        canvas.restore()
        canvas.drawPath(path, glow)
        canvas.drawPath(path, edge)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// The desktop's "Night City" palette (app/Neon.cs) and her speech-box colours (app/Talk.cs Look).
object Night {
    fun alpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
    val looks = listOf("Night City", "Black", "White", "Grey")
    // The desktop's four Mainframe looks, the same 30 colours each (Neon.cs), in the same order.
    private fun g(vararg v: Int) = IntArray(v.size) { Color.rgb(v[it], v[it], v[it]) }
    private val palettes: List<IntArray> = listOf(
        intArrayOf(Color.rgb(10,10,16), Color.rgb(54,14,26), Color.rgb(16,12,20), Color.rgb(44,122,134), Color.rgb(255,93,102), Color.rgb(236,236,241), Color.rgb(146,130,140), Color.rgb(17,16,28), Color.rgb(30,20,36), Color.rgb(46,22,38), Color.rgb(92,24,36), Color.rgb(232,66,70), Color.rgb(94,240,246), Color.rgb(9,30,33), Color.rgb(40,120,132), Color.rgb(6,14,18), Color.rgb(78,28,40), Color.rgb(245,90,86), Color.rgb(120,30,44), Color.rgb(12,11,20), Color.rgb(72,28,40), Color.rgb(244,214,70), Color.rgb(9,11,15), Color.rgb(232,236,240), Color.rgb(122,128,138), Color.rgb(16,13,22), Color.rgb(58,214,235), Color.rgb(232,36,64), Color.rgb(56,176,255), Color.rgb(255,46,136)),
        g(0,24,8,110,255,238,150,16,34,52,84,245,205,22,120,1,70,250,96,6,76,230,4,232,140,14,210,160,225,248),
        g(255,236,248,120,0,20,96,244,230,214,170,30,64,238,110,254,190,10,150,250,180,60,252,26,104,243,6,100,40,16),
        g(92,108,84,170,255,250,222,78,104,118,140,246,236,72,190,30,130,252,160,40,150,244,88,248,216,80,254,210,240,253))
    private var c = palettes[0]
    var look = 0
        private set
    fun use(i: Int) { look = if (i in palettes.indices) i else 0; c = palettes[look] }
    val mono get() = look != 0
    val ground get() = c[0]; val glow get() = c[1]; val band get() = c[2]; val rule get() = c[3]; val rose get() = c[4]
    val white get() = c[5]; val faint get() = c[6]; val field get() = c[7]; val hover get() = c[8]; val edge get() = c[10]
    val hot get() = c[11]; val cyan get() = c[12]; val deep get() = c[13]; val deepEdge get() = c[14]
    val well get() = c[19]; val wellEdge get() = c[20]; val amber get() = c[21]; val menuBack get() = c[25]
    // speech box
    val panel get() = c[22]; val sCyan get() = c[26]; val sRed get() = c[27]; val neon get() = c[28]; val dim get() = c[24]; val pink get() = c[29]
}

// Her speech bubble like the desktop's SpeechBox: dark plate, cyan edge and glow, scanlines, a red bar inside and a tail to her head.
class SpeechPlate(private val density: Float) : Drawable() {
    var tipX = 0.5f
    var tailDown = true
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Night.alpha(Night.panel, 238) }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * density; color = Night.sCyan }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7f * density; color = Night.alpha(Night.sCyan, 46) }
    private val scan = Paint().apply { color = Night.white; alpha = 9 }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Night.sRed }
    val tail get() = 14f * density

    // Before the box has been laid out its bounds are empty; the shape is built once the real size arrives (onBoundsChange).
    fun configure(down: Boolean, x: Float) {
        tailDown = down
        tipX = x
        if (!bounds.isEmpty) onBoundsChange(bounds)
        invalidateSelf()
    }

    override fun onBoundsChange(b: Rect) {
        val c = 10f * density
        val half = 9f * density
        val l = b.left + 2f
        val r = b.right - 2f
        val t = b.top + 2f + (if (tailDown) 0f else tail)
        val bt = b.bottom - 2f - (if (tailDown) tail else 0f)
        // Never coerceIn() here: on an empty or very narrow box the range is empty and that throws, which crashed her on her first line.
        val room = maxOf(0f, r - l - 2 * (c + half))
        val tx = l + c + half + room * tipX.clampTo(0f, 1f)
        path.reset()
        path.moveTo(l + c, t)
        if (!tailDown) { path.lineTo(tx - half, t); path.lineTo(tx, b.top + 2f); path.lineTo(tx + half, t) }
        path.lineTo(r - c, t); path.lineTo(r, t + c); path.lineTo(r, bt - c); path.lineTo(r - c, bt)
        if (tailDown) { path.lineTo(tx + half, bt); path.lineTo(tx, b.bottom - 2f); path.lineTo(tx - half, bt) }
        path.lineTo(l + c, bt); path.lineTo(l, bt - c); path.lineTo(l, t + c)
        path.close()
        barTop = t + 13f * density
        barBottom = bt - 13f * density
        barLeft = l + 10f * density
    }

    private var barTop = 0f
    private var barBottom = 0f
    private var barLeft = 0f

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawPath(path, glow)
        canvas.drawPath(path, fill)
        canvas.save()
        canvas.clipPath(path)
        var y = b.top.toFloat()
        while (y < b.bottom) { canvas.drawRect(b.left.toFloat(), y, b.right.toFloat(), y + density, scan); y += 3f * density }
        canvas.restore()
        canvas.drawPath(path, edge)
        canvas.drawRect(barLeft, barTop, barLeft + 3f * density, barBottom, bar)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// The Mainframe panel: big clipped corners at the top left and bottom right, a dark red rim and hot red strokes over the big corners.
class NeonPanel(private val density: Float, private val fillColor: Int = Night.field) : Drawable() {
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f * density; color = Night.edge }
    private val hot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.6f * density; color = Night.hot; strokeCap = Paint.Cap.SQUARE }
    private val hotPath = Path()

    override fun onBoundsChange(b: Rect) {
        val big = 18f * density
        val small = 3f * density
        val run = 26f * density
        val l = b.left + 1f
        val t = b.top + 1f
        val r = b.right - 1f
        val bt = b.bottom - 1f
        path.reset()
        path.moveTo(l + big, t); path.lineTo(r - small, t); path.lineTo(r, t + small); path.lineTo(r, bt - big)
        path.lineTo(r - big, bt); path.lineTo(l + small, bt); path.lineTo(l, bt - small); path.lineTo(l, t + big)
        path.close()
        hotPath.reset()
        hotPath.moveTo(l, t + big + run); hotPath.lineTo(l, t + big); hotPath.lineTo(l + big, t); hotPath.lineTo(l + big + run, t)
        hotPath.moveTo(r, bt - big - run); hotPath.lineTo(r, bt - big); hotPath.lineTo(r - big, bt); hotPath.lineTo(r - big - run, bt)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, fill)
        canvas.drawPath(path, rim)
        canvas.drawPath(hotPath, hot)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// A section in the Mainframe style: no box, just the red rule down the left side.
class RuleBar(private val density: Float, private val color: Int = Night.hot) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val soft = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = 3f * density
        val rtl = false
        canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.left + w, b.bottom.toFloat(), p)
        soft.shader = LinearGradient(b.left.toFloat(), 0f, b.left + 90f * density, 0f, Night.alpha(Night.hot, 26), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(b.left + w, b.top.toFloat(), b.left + 90f * density, b.bottom.toFloat(), soft)
        if (rtl) return
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// A neon frame around the whole screen, like the Mainframe window.
class NeonFrame(private val density: Float) : Drawable() {
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * density }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f * density; color = Night.alpha(Night.cyan, 40) }
    private val r = RectF()
    override fun draw(canvas: Canvas) {
        val b = bounds
        r.set(b.left + 3f * density, b.top + 2f * density, b.right - 3f * density, b.bottom - 2f * density)
        edge.shader = LinearGradient(r.left, r.top, r.right, r.bottom, intArrayOf(Night.cyan, Night.alpha(Night.rule, 200), Night.cyan), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(r, 16f * density, 16f * density, glow)
        canvas.drawRoundRect(r, 16f * density, 16f * density, edge)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// Puts the neon frame over a screen. Taps pass straight through it.
fun neonWrap(ctx: android.content.Context, content: android.view.View): android.widget.FrameLayout {
    val f = android.widget.FrameLayout(ctx)
    f.addView(content, android.widget.FrameLayout.LayoutParams(-1, -1))
    val over = android.view.View(ctx)
    over.background = NeonFrame(ctx.resources.displayMetrics.density)
    over.isClickable = false
    over.isFocusable = false
    f.addView(over, android.widget.FrameLayout.LayoutParams(-1, -1))
    return f
}

// A card like the desktop's mod cards: cut corners at the top left and bottom right, a neon edge in the card's own colour.
class CardDrawable(private val density: Float, private val edgeColor: Int) : Drawable() {
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(250, 10, 8, 16) }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f * density; color = edgeColor }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f * density; color = Color.argb(48, Color.red(edgeColor), Color.green(edgeColor), Color.blue(edgeColor)) }
    override fun onBoundsChange(b: Rect) {
        val c = 14f * density
        val l = b.left + 1.5f; val t = b.top + 1.5f; val r = b.right - 1.5f; val bt = b.bottom - 1.5f
        path.reset()
        path.moveTo(l + c, t); path.lineTo(r, t); path.lineTo(r, bt - c); path.lineTo(r - c, bt); path.lineTo(l, bt); path.lineTo(l, t + c)
        path.close()
    }
    override fun draw(canvas: Canvas) { canvas.drawPath(path, fill); canvas.drawPath(path, glow); canvas.drawPath(path, edge) }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

// A dialog with the card look: cut corners and a cyan neon edge.
fun darkDialog(d: android.app.AlertDialog) {
    d.show()
    val dens = d.context.resources.displayMetrics.density
    d.window?.setBackgroundDrawable(CardDrawable(dens, Night.cyan))
    d.window?.decorView?.setPadding((10 * dens).toInt(), (10 * dens).toInt(), (10 * dens).toInt(), (10 * dens).toInt())
}
