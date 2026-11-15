package app.capri.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import org.json.JSONObject

// Plays one of her animations. Each clip is a grid sheet of frames (see tools/prepare_assets.py).
class SpriteView(ctx: Context) : View(ctx) {
    private class Clip(val frames: Int, val fps: Int, val w: Int, val h: Int, val cols: Int, val bmp: Bitmap)

    private val meta = JSONObject(ctx.assets.open("sprites.json").bufferedReader().use { it.readText() })
    private val clips = HashMap<String, Clip>()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val src = Rect()
    private val dst = RectF()
    private var clip: Clip? = null
    private var looping = true
    private var done: (() -> Unit)? = null
    private var started = 0L
    private var finished = false

    var current = ""
        private set

    // Mirrors her so she can face left; her walk is a drift, so one sheet does both ways.
    var flip = false
        set(v) { field = v; invalidate() }

    private fun load(name: String): Clip? {
        clips[name]?.let { return it }
        return try {
            val o = meta.getJSONObject(name)
            val bmp = context.assets.open("sprites/$name.png").use { BitmapFactory.decodeStream(it) }
            Clip(o.getInt("frames"), o.getInt("fps"), o.getInt("w"), o.getInt("h"), o.getInt("cols"), bmp).also { clips[name] = it }
        } catch (e: Exception) {
            null
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

    override fun onDraw(canvas: Canvas) {
        val c = clip ?: return
        var i = ((SystemClock.uptimeMillis() - started) * c.fps / 1000).toInt()
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
        dst.set(left, 0f, left + dw, height.toFloat())
        if (flip) { canvas.save(); canvas.scale(-1f, 1f, width / 2f, height / 2f) }
        canvas.drawBitmap(c.bmp, src, dst, paint)
        if (flip) canvas.restore()
        if (!finished) postInvalidateDelayed(maxOf(16L, 1000L / c.fps))
    }
}
