package app.capri.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

/**
 * The pill under her when you tap her, drawn like capri-desktop's TalkPill (Talk.cs): a black plate with cut corners, a soft glow,
 * faint scan lines and a pink-to-cyan neon edge. Left half: a pencil and TYPE. Right half: the five-bar wave and TALK, a slanted seam
 * between them. The pressed half is washed cyan. It glitches in for four frames of 45 ms (slivers of the edge torn loose, cyan top right
 * and pink bottom left, and the plate sliced sideways). All the numbers are the desktop's, times a phone scale.
 */
class CapriPill(ctx: Context, private val onType: () -> Unit, private val onTalk: () -> Unit) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val u = d * 1.35f                         // one desktop pixel on a phone
    private val pink = Color.rgb(255, 46, 136)
    private val cyan = Color.rgb(58, 214, 235)
    private val ink = Color.rgb(232, 236, 240)
    private val caption = Color.rgb(168, 174, 184)
    private val bars = intArrayOf(2, 4, 5, 3, 2)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val h = Handler(Looper.getMainLooper())
    private var hover = 0
    private var tear = 0
    private var seed = 1
    private var downPart = 0

    /** True while she is listening: the wave and TALK light up. */
    var lit = false
        set(v) { field = v; invalidate() }

    private val frame = object : Runnable {
        override fun run() { if (tear > 0) { tear--; invalidate(); if (tear > 0) h.postDelayed(this, 45) } }
    }

    fun glitchIn() { tear = TEAR_FRAMES; seed = (Math.random() * 1_000_000).toInt() + 1; invalidate(); h.removeCallbacks(frame); h.postDelayed(frame, 45) }

    override fun onDetachedFromWindow() { h.removeCallbacks(frame); super.onDetachedFromWindow() }

    override fun onMeasure(wSpec: Int, hSpec: Int) { setMeasuredDimension((100 * u).toInt(), (40 * u).toInt()) }

    private fun plate(l: Float, t: Float, r: Float, b: Float, c: Float): Path = Path().apply {
        moveTo(l + c, t); lineTo(r - c, t); lineTo(r, t + c); lineTo(r, b - c); lineTo(r - c, b); lineTo(l + c, b); lineTo(l, b - c); lineTo(l, t + c); close()
    }

    private fun grad(x0: Float, x1: Float, alpha: Int) = LinearGradient(x0, 0f, x1, 0f, Color.argb(alpha, 255, 46, 136), Color.argb(alpha, 58, 214, 235), Shader.TileMode.CLAMP)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val hh = height.toFloat()
        if (tear <= 0) { body(canvas, w, hh); return }
        // glitching in: the plate is cut into strips that slide sideways, less each frame
        val rnd = java.util.Random((seed + tear).toLong())
        val strips = 4
        val amount = tear.toFloat() / TEAR_FRAMES
        for (i in 0 until strips) {
            val y0 = hh * i / strips; val y1 = hh * (i + 1) / strips
            canvas.save()
            canvas.clipRect(0f, y0, w, y1)
            canvas.translate((rnd.nextFloat() - 0.5f) * 16f * u * amount, 0f)
            body(canvas, w, hh)
            canvas.restore()
        }
        val m = 3 * u
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(220, 58, 214, 235)
        canvas.drawRect(w - m - 32 * u, m - 2 * u, w - m - 17 * u, m - 2 * u + Math.max(1f, u), paint)
        paint.color = Color.argb(220, 255, 46, 136)
        canvas.drawRect(m + 12 * u, hh - m + 1 * u, m + 12 * u + 22 * u, hh - m + 1 * u + Math.max(1f, u), paint)
    }

    private fun body(canvas: Canvas, w: Float, hh: Float) {
        val m = 3 * u
        val l = m; val t = m; val r = w - m; val b = hh - m
        val c = Math.max(3f, 6 * u)
        val edge = Math.max(1f, 1.5f * u)
        val mid = w / 2
        val outline = plate(l, t, r, b, c)
        // glow: two wide strokes in the neon gradient
        paint.style = Paint.Style.STROKE
        paint.shader = grad(l - m - 1, r + m + 1, 34); paint.strokeWidth = m * 2; canvas.drawPath(outline, paint)
        paint.shader = grad(l - m - 1, r + m + 1, 70); paint.strokeWidth = m; canvas.drawPath(outline, paint)
        paint.shader = null
        // the black plate, faint scan lines, and the cyan wash on the pressed half
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(246, 9, 11, 15)
        canvas.drawPath(outline, paint)
        canvas.save()
        canvas.clipPath(outline)
        paint.color = Color.argb(9, 255, 255, 255); paint.strokeWidth = Math.max(1f, u); paint.style = Paint.Style.STROKE
        var y = t; while (y < b) { canvas.drawLine(l, y, r, y, paint); y += Math.max(3f, 3 * u) }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(40, 58, 214, 235)
        if (hover == 1) canvas.drawRect(l, t, mid, b, paint) else if (hover == 2) canvas.drawRect(mid, t, r, b, paint)
        canvas.restore()
        // the neon rim, inside the box
        val inset = edge / 2
        paint.style = Paint.Style.STROKE; paint.strokeWidth = edge
        paint.shader = LinearGradient(l - m - 1, 0f, r + m + 1, 0f, pink, cyan, Shader.TileMode.CLAMP)
        canvas.drawPath(plate(l + inset, t + inset, r - inset, b - inset, c - inset * 0.586f), paint)
        paint.shader = null
        // the slanted seam between the halves
        paint.color = Color.rgb(66, 72, 86); paint.strokeWidth = Math.max(1f, u)
        canvas.drawLine(mid + 1.5f * u, t + 10 * u, mid - 1.5f * u, b - 10 * u, paint)

        // the two halves: icon, gap, label, centred in each half
        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.MONOSPACE
        paint.textSize = 8.5f * u * 1.15f
        val midY = t + (b - t) / 2
        val gap = 4 * u
        val typeColor = if (hover == 1) cyan else ink
        val talkOn = hover == 2 || lit
        val label1 = "TYPE"; val label2 = "TALK"
        val w1 = paint.measureText(label1); val w2 = paint.measureText(label2)
        val ic1 = 12.5f * u
        val start1 = l + edge + ((mid - (l + edge)) - ic1 - gap - w1) / 2
        pencil(canvas, start1 + ic1 / 2, midY, 0.9f * u, typeColor)
        paint.color = if (hover == 1) cyan else caption
        canvas.drawText(label1, start1 + ic1 + gap, midY + paint.textSize * 0.35f, paint)
        val pitch = Math.max(3f, 3 * u); val sq = Math.max(2f, 2 * u)
        val ic2 = pitch * 4 + sq
        val start2 = mid + ((r - edge - mid) - ic2 - gap - w2) / 2
        val barColor = if (talkOn) cyan else ink
        paint.color = barColor
        for (bar in bars.indices) for (row in 0 until bars[bar]) {
            val x = start2 + bar * pitch
            val yy = midY + ic1 / 2 - sq - row * pitch
            canvas.drawRect(x, yy, x + sq, yy + sq, paint)
        }
        paint.color = if (talkOn) cyan else caption
        canvas.drawText(label2, start2 + ic2 + gap, midY + paint.textSize * 0.35f, paint)
    }

    // The desktop's pencil: a slanted body, a point, and a short line under it.
    private fun pencil(canvas: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.color = color
        paint.strokeWidth = 3.2f * s
        canvas.drawLine(cx - 2.5f * s * 1.4f, cy + 2.5f * s * 1.4f, cx + 4f * s * 1.4f, cy - 4f * s * 1.4f, paint)
        paint.strokeWidth = 1.6f * s
        canvas.drawLine(cx - 6f * s * 1.4f, cy + 6f * s * 1.4f, cx - 3.6f * s * 1.4f, cy + 6f * s * 1.4f, paint)
        paint.strokeCap = Paint.Cap.BUTT; paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val part = if (e.x < width / 2f) 1 else 2
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downPart = part; hover = part; invalidate() }
            MotionEvent.ACTION_MOVE -> { hover = if (e.x in 0f..width.toFloat() && e.y in 0f..height.toFloat()) part else 0; invalidate() }
            MotionEvent.ACTION_UP -> { val was = hover; hover = 0; invalidate(); if (was != 0) { if (was == 1) onType() else onTalk() } }
            MotionEvent.ACTION_CANCEL -> { hover = 0; invalidate() }
        }
        return true
    }

    companion object { const val TEAR_FRAMES = 4 }
}
