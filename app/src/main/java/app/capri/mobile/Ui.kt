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
