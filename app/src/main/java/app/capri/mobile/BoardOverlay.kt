package app.capri.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

// A see-through layer over the other app's board: a neon arrow for the best move and, if wanted, an outline around the board.
class BoardOverlay(ctx: Context, private val color: Int) : View(ctx) {
    private var arrow: FloatArray? = null
    private var frame: RectF? = null
    private var cell = 0f
    private val d = ctx.resources.displayMetrics.density
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; setShadowLayer(14f * d, 0f, 0f, color) }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; setShadowLayer(14f * d, 0f, 0f, color) }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xFFFFFFFF.toInt() }

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    fun setArrow(x1: Float, y1: Float, x2: Float, y2: Float, cellPx: Float) { arrow = floatArrayOf(x1, y1, x2, y2); cell = cellPx; invalidate() }
    fun setFrame(l: Float, t: Float, size: Float) { frame = RectF(l, t, l + size, t + size); invalidate() }
    fun clearArrow() { arrow = null; invalidate() }

    override fun onDraw(c: Canvas) {
        frame?.let {
            glow.color = color; glow.strokeWidth = 3f * d; glow.alpha = 200
            c.drawRoundRect(it, 6f * d, 6f * d, glow)
        }
        val a = arrow ?: return
        val x1 = a[0]; val y1 = a[1]; val x2 = a[2]; val y2 = a[3]
        val len = Math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
        if (len < 4f) return
        val ux = (x2 - x1) / len; val uy = (y2 - y1) / len
        val w = maxOf(6f * d, cell * 0.16f)
        val head = w * 2.4f
        val bx = x2 - ux * head * 0.9f; val by = y2 - uy * head * 0.9f
        glow.color = color; glow.alpha = 215; glow.strokeWidth = w
        c.drawLine(x1, y1, bx, by, glow)
        core.strokeWidth = w * 0.32f; core.alpha = 230
        c.drawLine(x1, y1, bx, by, core)
        fill.color = color; fill.alpha = 235
        val p = Path()
        p.moveTo(x2, y2)
        p.lineTo(bx - uy * head * 0.62f, by + ux * head * 0.62f)
        p.lineTo(bx + uy * head * 0.62f, by - ux * head * 0.62f)
        p.close()
        c.drawPath(p, fill)
        fill.alpha = 235
        c.drawCircle(x1, y1, w * 0.7f, fill)
    }
}
