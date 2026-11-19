package app.capri.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.sin

// "Circle the send button": a glowing frame that pulses around something on the screen for a few seconds. It never blocks touches.
class RingView(ctx: Context, private val color: Int) : View(ctx) {
    private val start = SystemClock.uptimeMillis()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val box = RectF()

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val t = SystemClock.uptimeMillis() - start
        val pulse = ((sin(t / 130.0) + 1.0) / 2.0).toFloat()
        val inset = 3f * d + 6f * d * pulse
        box.set(inset, inset, width - inset, height - inset)
        val r = 12f * d
        glow.color = color
        glow.alpha = (60 + 110 * pulse).toInt()
        glow.strokeWidth = 10f * d
        line.color = Color.WHITE
        line.strokeWidth = 2.5f * d
        canvas.drawRoundRect(box, r, r, glow)
        canvas.drawRoundRect(box, r, r, line)
        // four small corner ticks, like a targeting reticle
        val tick = 10f * d
        line.color = color
        line.strokeWidth = 4f * d
        canvas.drawLine(box.left, box.top - tick / 2, box.left + tick, box.top - tick / 2, line)
        canvas.drawLine(box.right - tick, box.bottom + tick / 2, box.right, box.bottom + tick / 2, line)
        postInvalidateDelayed(16)
    }
}
