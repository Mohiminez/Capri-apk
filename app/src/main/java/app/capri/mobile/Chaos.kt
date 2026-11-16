package app.capri.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.random.Random

// A harmless fake glitch over the whole screen for a moment: scanlines, colour bars and a "hacked" note. It never blocks touches.
class ChaosView(ctx: Context, private val accent: Int) : View(ctx) {
    private val paint = Paint()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; isFakeBoldText = true }
    private val start = SystemClock.uptimeMillis()
    private val notes = listOf("YOU'VE BEEN HACKED", "SIGNAL LOST", "CAPRI WAS HERE", "ERROR 0xCA9R1", "REBOOTING REALITY...", "ARASAKA SUCKS")

    private val seed = Random.nextInt()
    private val note = notes.random()

    override fun onDraw(canvas: Canvas) {
        val t = SystemClock.uptimeMillis() - start
        if (t > 1800) return
        val rnd = Random(seed + (t / 60).toInt())
        val colors = intArrayOf(accent, Color.parseColor("#00E5FF"), Color.parseColor("#FF2AD4"), Color.WHITE)
        val bars = 6 + rnd.nextInt(8)
        for (i in 0 until bars) {
            paint.color = colors[rnd.nextInt(colors.size)]
            paint.alpha = 40 + rnd.nextInt(90)
            val y = rnd.nextFloat() * height
            canvas.drawRect(rnd.nextFloat() * width * 0.3f, y, width.toFloat(), y + 3 + rnd.nextFloat() * height * 0.04f, paint)
        }
        paint.color = Color.BLACK
        paint.alpha = 38
        var y = 0f
        while (y < height) { canvas.drawRect(0f, y, width.toFloat(), y + 2f, paint); y += 8f }
        if (t in 500..1500) {
            text.textSize = width * 0.075f
            text.color = accent
            text.alpha = 230
            canvas.drawText(note, width * 0.08f + rnd.nextInt(-6, 7), height * 0.45f + rnd.nextInt(-6, 7), text)
            text.color = Color.parseColor("#00E5FF")
            text.alpha = 110
            canvas.drawText(note, width * 0.08f + 6, height * 0.45f - 4, text)
        }
        postInvalidateDelayed(40)
    }
}
