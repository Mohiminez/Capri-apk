package app.capri.mobile

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
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
    private val scan = Paint().apply { color = Color.WHITE; alpha = 9 }

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
    val ground = Color.parseColor("#0A0A10")
    val glow = Color.parseColor("#360E1A")
    val band = Color.parseColor("#100C14")
    val rule = Color.parseColor("#2A7A86")
    val rose = Color.parseColor("#FF5D66")
    val white = Color.parseColor("#ECECF1")
    val faint = Color.parseColor("#92828C")
    val field = Color.parseColor("#11101C")
    val hover = Color.parseColor("#1E1424")
    val edge = Color.parseColor("#5C1824")
    val hot = Color.parseColor("#E84246")
    val cyan = Color.parseColor("#5EF0F6")
    val deep = Color.parseColor("#091E21")
    val deepEdge = Color.parseColor("#287884")
    val well = Color.parseColor("#0C0B14")
    val wellEdge = Color.parseColor("#481C28")
    val amber = Color.parseColor("#F4D646")
    val menuBack = Color.parseColor("#100D16")

    // speech box
    val panel = Color.parseColor("#090B0F")
    val sCyan = Color.parseColor("#3AD6EB")
    val sRed = Color.parseColor("#E82440")
    val neon = Color.parseColor("#38B0FF")
    val dim = Color.parseColor("#7A808A")
    val pink = Color.parseColor("#FF2E88")
}

// Her speech bubble like the desktop's SpeechBox: dark plate, cyan edge and glow, scanlines, a red bar inside and a tail to her head.
class SpeechPlate(private val density: Float) : Drawable() {
    var tipX = 0.5f
    var tailDown = true
    private val path = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(238, 9, 11, 15) }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f * density; color = Night.sCyan }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7f * density; color = Color.argb(46, 58, 214, 235) }
    private val scan = Paint().apply { color = Color.WHITE; alpha = 9 }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Night.sRed }
    val tail get() = 14f * density

    fun configure(down: Boolean, x: Float) { tailDown = down; tipX = x; onBoundsChange(bounds); invalidateSelf() }

    override fun onBoundsChange(b: Rect) {
        val c = 10f * density
        val half = 9f * density
        val l = b.left + 2f
        val r = b.right - 2f
        val t = b.top + 2f + (if (tailDown) 0f else tail)
        val bt = b.bottom - 2f - (if (tailDown) tail else 0f)
        val tx = (l + c + half + (r - l - 2 * (c + half)) * tipX).coerceIn(l + c + half, r - c - half)
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
