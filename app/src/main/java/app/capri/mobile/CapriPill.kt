package app.capri.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

/**
 * The pill under her when you tap her: capri-desktop's TalkPill (Talk.cs), drawn the same way with the same numbers times a phone scale.
 * A black plate with cut corners, a soft glow, faint scan lines and a pink-to-cyan neon edge. Left half: the square-cut pencil and TYPE.
 * Right half: five rounded wave bars and TALK, a slanted seam between them. The pressed half is washed cyan. Two slivers of the edge
 * (cyan top right, pink bottom left) are always torn loose. It glitches in for four frames of 45 ms: rows of it slip sideways and
 * take a tint of pink or cyan, less each frame. In voice chat ([lit]) the wave and TALK light up and the bars stretch with [level].
 */
class CapriPill(ctx: Context, private val onType: () -> Unit, private val onTalk: () -> Unit) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val s = d * 1.35f                         // the desktop's scale: one desktop pixel on a phone
    private val pink = Night.pink
    private val cyan = Night.sCyan
    private val ink = if (Night.mono) Night.dim else Night.white     // Idle
    private val caption = if (Night.mono) Night.dim else Color.rgb(168, 174, 184)
    private val fill = (Night.panel and 0x00FFFFFF) or (246 shl 24)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val h = Handler(Looper.getMainLooper())
    private val font: Typeface = try { Typeface.createFromAsset(ctx.assets, "fonts/ShareTechMono-Regular.ttf") } catch (e: Throwable) { Typeface.MONOSPACE }
    private var hover = 0
    private var tear = 0
    private var seed = 1

    /** True while she is listening: the wave and TALK light up. */
    var lit = false
        set(v) { field = v; invalidate() }

    /** The mic level 0 to 6 in voice chat: the bars stretch with it. */
    var level = 0
        set(v) { field = v.coerceIn(0, 6); invalidate() }

    private val frame = object : Runnable {
        override fun run() { if (tear > 0) { tear--; invalidate(); if (tear > 0) h.postDelayed(this, 45) } }
    }

    fun glitchIn() { tear = TEAR_FRAMES; seed = (Math.random() * 1_000_000).toInt() + 1; invalidate(); h.removeCallbacks(frame); h.postDelayed(frame, 45) }

    override fun onDetachedFromWindow() { h.removeCallbacks(frame); super.onDetachedFromWindow() }

    private fun r(v: Float) = Math.round(v)
    override fun onMeasure(wSpec: Int, hSpec: Int) { setMeasuredDimension(r(100 * s), r(40 * s)) }

    private fun body(): RectF { val m = r(3 * s).toFloat(); return RectF(m, m, width - m, height - m) }
    private fun cut() = Math.max(3, r(6 * s)).toFloat()
    private fun edgeW() = Math.max(1, r(1.5f * s)).toFloat()

    private fun plate(l: Float, t: Float, rr: Float, b: Float, c: Float): Path = Path().apply {
        moveTo(l + c, t); lineTo(rr - c, t); lineTo(rr, t + c); lineTo(rr, b - c); lineTo(rr - c, b); lineTo(l + c, b); lineTo(l, b - c); lineTo(l, t + c); close()
    }

    private fun grad(x0: Float, x1: Float, a0: Int, a1: Int, from: Int, to: Int) = LinearGradient(x0, 0f, x1, 0f, (from and 0x00FFFFFF) or (a0 shl 24), (to and 0x00FFFFFF) or (a1 shl 24), Shader.TileMode.CLAMP)

    override fun onDraw(canvas: Canvas) {
        if (tear <= 0) { paintPill(canvas); return }
        val clean = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        paintPill(Canvas(clean))
        val out = Bitmap.createBitmap(clean)
        val c = Canvas(out)
        val roll = java.util.Random((seed + tear).toLong())
        val amount = Math.min(1f, tear.toFloat() / TEAR_FRAMES)
        val bands = 2 + r(2 * amount)
        val bodyBox = body()
        for (i in 0 until bands) {
            val band = Math.max(2, r((2 + roll.nextInt(6)) * s))
            val top = roll.nextInt(Math.max(1, height - band))
            val shift = (if (roll.nextInt(2) == 0) -1 else 1) * Math.max(1, r((2 + roll.nextInt(6)) * s * amount))
            c.save()
            c.clipRect(0, top, width, top + band)
            c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            c.drawBitmap(clean, shift.toFloat(), 0f, null)
            c.restore()
            c.save()
            c.clipRect(0, top, width, top + band)
            c.clipRect(bodyBox)
            paint.shader = null; paint.style = Paint.Style.FILL
            paint.color = ((if (i % 2 == 0) pink else cyan) and 0x00FFFFFF) or ((96 * amount).toInt() shl 24)
            c.drawRect(0f, top.toFloat(), width.toFloat(), (top + band).toFloat(), paint)
            c.restore()
        }
        canvas.drawBitmap(out, 0f, 0f, null)
        clean.recycle(); out.recycle()
    }

    private fun paintPill(canvas: Canvas) {
        val w = width.toFloat()
        val b = body()
        val mid = w / 2
        val c = cut(); val edge = edgeW()
        val m = r(3 * s).toFloat()
        val outline = plate(b.left, b.top, b.right, b.bottom, c)
        // glow: two strokes in the neon gradient
        paint.style = Paint.Style.STROKE
        paint.shader = grad(b.left - m - 1, b.right + m + 1, 34, 34, pink, cyan); paint.strokeWidth = m * 2; canvas.drawPath(outline, paint)
        paint.shader = grad(b.left - m - 1, b.right + m + 1, 70, 70, pink, cyan); paint.strokeWidth = m; canvas.drawPath(outline, paint)
        paint.shader = null
        // the black plate, faint scan lines, and the cyan wash on the pressed half
        paint.style = Paint.Style.FILL; paint.color = fill
        canvas.drawPath(outline, paint)
        canvas.save()
        canvas.clipPath(outline)
        val line = Math.max(1, r(s)).toFloat()
        paint.color = Color.argb(9, 255, 255, 255); paint.strokeWidth = line; paint.style = Paint.Style.STROKE
        var y = b.top; while (y < b.bottom) { canvas.drawLine(b.left, y + line / 2, b.right, y + line / 2, paint); y += Math.max(3, r(3 * s)) }
        paint.style = Paint.Style.FILL
        paint.color = (cyan and 0x00FFFFFF) or (40 shl 24)
        if (hover == 1) canvas.drawRect(b.left, b.top, mid, b.bottom, paint) else if (hover == 2) canvas.drawRect(mid, b.top, b.right, b.bottom, paint)
        canvas.restore()
        // the neon rim, inside the box
        val inset = edge / 2
        paint.style = Paint.Style.STROKE; paint.strokeWidth = edge
        paint.shader = LinearGradient(b.left - m - 1, 0f, b.right + m + 1, 0f, pink, cyan, Shader.TileMode.CLAMP)
        canvas.drawPath(plate(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset, c - inset * 0.586f), paint)
        paint.shader = null
        // the slanted seam between the halves
        paint.color = if (Night.mono) Night.dim else Color.rgb(66, 72, 86); paint.strokeWidth = Math.max(1, r(s)).toFloat()
        canvas.drawLine(mid + 1.5f * s, b.top + 10 * s, mid - 1.5f * s, b.bottom - 10 * s, paint)
        // her glitch, always there: slivers of the edge torn loose, cyan top right and pink bottom left
        val sliver = Math.max(1, r(s)).toFloat()
        paint.style = Paint.Style.FILL
        paint.color = (cyan and 0x00FFFFFF) or (220 shl 24)
        canvas.drawRect(b.right - r(32 * s), b.top - r(2 * s), b.right - r(32 * s) + r(15 * s), b.top - r(2 * s) + sliver, paint)
        paint.color = (pink and 0x00FFFFFF) or (220 shl 24)
        canvas.drawRect(b.left + r(12 * s), b.bottom + r(2 * s) - sliver, b.left + r(12 * s) + r(11 * s), b.bottom + r(2 * s), paint)

        // the two halves: icon, gap, label, the pair centred in each half (the label placed by where its letters land)
        paint.typeface = font
        paint.textSize = 8.5f * s
        val midY = b.top + b.height() / 2
        val gap = r(4 * s).toFloat()
        val talkOn = hover == 2 || lit
        val wash = mixWash()
        val t1 = ink1(); val t2 = "TALK"
        val bx1 = inkBox(t1); val bx2 = inkBox(t2)
        val wide1 = r(12.5f * s).toFloat()
        val start1 = b.left + edge + Math.floor(((mid - (b.left + edge)) - wide1 - gap - bx1.width() + 1) / 2.0).toFloat()
        pencil(canvas, start1 + wide1 / 2f, midY, 0.9f * s, if (hover == 1) cyan else ink, if (hover == 1) wash else fill)
        label(canvas, t1, start1 + wide1 + gap, midY - bx1.height() / 2f, bx1, if (hover == 1) cyan else caption)
        val pitch = Math.max(3, r(3 * s)).toFloat(); val sq = Math.max(2, r(2 * s)).toFloat()
        val wide2 = pitch * 4 + sq
        val start2 = mid + Math.floor(((b.right - edge - mid) - wide2 - gap - bx2.width() + 1) / 2.0).toFloat()
        meter(canvas, start2, midY - wide2 / 2, wide2, if (hover == 2 || lit) cyan else ink)
        label(canvas, t2, start2 + wide2 + gap, midY - bx2.height() / 2f, bx2, if (hover == 2 || lit) cyan else caption)
    }

    private fun ink1() = "TYPE"

    private fun mixWash(): Int {
        val a = 40
        fun mx(u: Int, o: Int) = u + (o - u) * a / 255
        return Color.rgb(mx(Color.red(fill), Color.red(cyan)), mx(Color.green(fill), Color.green(cyan)), mx(Color.blue(fill), Color.blue(cyan)))
    }

    // Where a label's letters land, from where it's drawn (ink bounds, like the desktop's GraphicsPath bounds).
    private fun inkBox(text: String): Rect { val rc = Rect(); paint.typeface = font; paint.textSize = 8.5f * s; paint.getTextBounds(text, 0, text.length, rc); return rc }

    private fun label(canvas: Canvas, text: String, x: Float, y: Float, bx: Rect, color: Int) {
        paint.style = Paint.Style.FILL; paint.shader = null; paint.color = color; paint.typeface = font; paint.textSize = 8.5f * s
        canvas.drawText(text, x - bx.left, y - bx.top, paint)
    }

    // The wave: five rounded bars, stretching with the mic level in voice chat.
    private fun meter(canvas: Canvas, x: Float, y: Float, wide: Float, color: Int) {
        val heights = floatArrayOf(5f, 10f, 14f, 9f, 4f)
        val stretch = if (!lit) 1f else 0.45f + level * 0.14f
        val cx = x + wide / 2f; val cy = y + wide / 2f
        paint.style = Paint.Style.STROKE; paint.shader = null; paint.color = color; paint.strokeWidth = 2.3f * s; paint.strokeCap = Paint.Cap.ROUND
        for (i in heights.indices) {
            val bx = cx + (i - 2) * 3.6f * s
            val half = Math.max(1.5f, heights[i] * stretch) * s / 2
            canvas.drawLine(bx, cy - half, bx, cy + half, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT; paint.style = Paint.Style.FILL
    }

    // The desktop's square-cut pencil at 45 degrees, with two gaps cut across it.
    private fun pencil(canvas: Canvas, cx: Float, cy: Float, k: Float, color: Int, back: Int) {
        canvas.save()
        canvas.translate(cx + 0.5f * k, cy - 0.5f * k)
        canvas.rotate(-45f)
        paint.style = Paint.Style.FILL; paint.shader = null; paint.color = color
        canvas.drawRect(-8 * k, -2.5f * k, -8 * k + 12.6f * k, 2.5f * k, paint)
        val tip = Path().apply { moveTo(4.6f * k, -2.5f * k); lineTo(9 * k, 0f); lineTo(4.6f * k, 2.5f * k); close() }
        canvas.drawPath(tip, paint)
        paint.style = Paint.Style.STROKE; paint.color = back; paint.strokeWidth = 1.1f * k
        canvas.drawLine(-4.8f * k, -3 * k, -4.8f * k, 3 * k, paint)
        canvas.drawLine(4f * k, -3 * k, 4f * k, 3 * k, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val part = if (e.x < width / 2f) 1 else 2
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { hover = part; invalidate() }
            MotionEvent.ACTION_MOVE -> { hover = if (e.x in 0f..width.toFloat() && e.y in 0f..height.toFloat()) part else 0; invalidate() }
            MotionEvent.ACTION_UP -> { val was = hover; hover = 0; invalidate(); if (was != 0) { if (was == 1) onType() else onTalk() } }
            MotionEvent.ACTION_CANCEL -> { hover = 0; invalidate() }
        }
        return true
    }

    companion object { const val TEAR_FRAMES = 4 }
}
