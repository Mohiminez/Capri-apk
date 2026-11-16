package app.capri.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import org.json.JSONObject
import kotlin.math.sin

// Plays her animations and a few effects: a soft glow while she talks, a cyberpunk RGB-split while she thinks, a gentle float, and a ripple where you tap.
// Memory: only a few animation sheets stay loaded at a time.
class SpriteView(ctx: Context) : View(ctx) {
    private class Clip(val frames: Int, val fps: Int, val w: Int, val h: Int, val cols: Int, val bmp: Bitmap) {
        val bytes get() = bmp.byteCount
    }

    private val meta = JSONObject(ctx.assets.open("sprites.json").bufferedReader().use { it.readText() })
    private val clips = LinkedHashMap<String, Clip>(8, 0.75f, true)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val ghostA = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 120 }
    private val ghostB = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 120 }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val src = Rect()
    private val dst = RectF()
    private var clip: Clip? = null
    private var looping = true
    private var done: (() -> Unit)? = null
    private var started = 0L
    private var finished = false
    private var ripple: Clip? = null
    private var rippleAt = 0L
    private var rx = 0f
    private var ry = 0f

    var current = ""
        private set

    private val floaty = setOf("idle", "idle_mouth", "idle_blink", "dance")

    // Mirrors her so she can face left; her walk is a drift, so one sheet does both ways.
    var flip = false
        set(v) { field = v; invalidate() }

    var glowColor = Color.parseColor("#FF2A6D")
    var glow = false          // soft aura while she talks
    var glitchFx = false      // RGB split while she thinks or is thrown
    var bob = true            // slow float
    var lowPower = false      // fewer frames per second

    init {
        ghostA.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 229f,
            0f, 0f, 0f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f)))
        ghostB.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            0f, 0f, 0f, 0f, 255f,
            0f, 0f, 0f, 0f, 40f,
            0f, 0f, 0f, 0f, 160f,
            0f, 0f, 0f, 1f, 0f)))
    }

    private fun load(name: String): Clip? {
        clips[name]?.let { return it }
        return try {
            val o = meta.getJSONObject(name)
            val bmp = context.assets.open("sprites/$name.png").use { BitmapFactory.decodeStream(it) }
            val c = Clip(o.getInt("frames"), o.getInt("fps"), o.getInt("w"), o.getInt("h"), o.getInt("cols"), bmp)
            clips[name] = c
            trim(c)
            c
        } catch (e: Exception) {
            null
        }
    }

    // Keep the sheets in memory under about 24 MB, dropping the least recently used.
    private fun trim(keep: Clip) {
        var total = clips.values.sumOf { it.bytes.toLong() }
        val it = clips.entries.iterator()
        while (total > 24_000_000L && it.hasNext()) {
            val e = it.next()
            if (e.value === keep || e.value === clip) continue
            total -= e.value.bytes
            it.remove()
        }
    }

    fun play(name: String, loop: Boolean = true, onDone: (() -> Unit)? = null) {
        if (loop && looping && current == name && clip != null) return
        val c = load(name) ?: return
        clip = c
        current = name
        looping = loop
        done = onDone
        finished = false
        started = SystemClock.uptimeMillis()
        invalidate()
    }

    // A small ripple where she was touched.
    fun ripple(x: Float, y: Float) {
        ripple = load("click") ?: return
        rx = x
        ry = y
        rippleAt = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val c = clip ?: return
        val now = SystemClock.uptimeMillis()
        var i = ((now - started) * c.fps / 1000).toInt()
        if (i >= c.frames) {
            if (looping) {
                i %= c.frames
            } else {
                i = c.frames - 1
                if (!finished) {
                    finished = true
                    val d = done
                    post { d?.invoke() }
                }
            }
        }
        val col = i % c.cols
        val row = i / c.cols
        src.set(col * c.w, row * c.h, (col + 1) * c.w, (row + 1) * c.h)
        val scale = height.toFloat() / c.h
        val dw = c.w * scale
        val left = (width - dw) / 2f
        val floatY = if (bob && current in floaty) (sin(now / 900.0) * height * 0.012).toFloat() else 0f
        dst.set(left, floatY, left + dw, height.toFloat() + floatY)

        if (glow) {
            val pulse = 0.55f + 0.25f * sin(now / 220.0).toFloat()
            val r = height * 0.55f
            glowPaint.shader = RadialGradient(width / 2f, height * 0.42f, r,
                Color.argb((70 * pulse).toInt(), Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(width / 2f, height * 0.42f, r, glowPaint)
        }

        if (flip) { canvas.save(); canvas.scale(-1f, 1f, width / 2f, height / 2f) }
        if (glitchFx) {
            // chromatic split that jumps around, like a bad signal
            val k = (now / 70) % 9
            val dx = (k - 4) * height * 0.006f
            canvas.save(); canvas.translate(-dx, 0f); canvas.drawBitmap(c.bmp, src, dst, ghostA); canvas.restore()
            canvas.save(); canvas.translate(dx, 0f); canvas.drawBitmap(c.bmp, src, dst, ghostB); canvas.restore()
        }
        canvas.drawBitmap(c.bmp, src, dst, paint)
        if (flip) canvas.restore()

        val rp = ripple
        var rippling = false
        if (rp != null) {
            val ri = ((now - rippleAt) * rp.fps / 1000).toInt()
            if (ri < rp.frames) {
                rippling = true
                val rs = Rect((ri % rp.cols) * rp.w, (ri / rp.cols) * rp.h, (ri % rp.cols + 1) * rp.w, (ri / rp.cols + 1) * rp.h)
                val rw = width * 1.1f
                val rh = rw * rp.h / rp.w
                canvas.drawBitmap(rp.bmp, rs, RectF(rx - rw / 2, ry - rh / 2, rx + rw / 2, ry + rh / 2), paint)
            } else {
                ripple = null
            }
        }

        val animating = !finished || glow || glitchFx || bob || rippling
        if (animating) {
            val step = if (lowPower) 83L else maxOf(16L, 1000L / c.fps)
            postInvalidateDelayed(if (glow || glitchFx || rippling) 33L else step)
        }
    }
}
