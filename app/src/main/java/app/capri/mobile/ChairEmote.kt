package app.capri.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

// Her secret chair emote from the desktop (drawings by Qewiw): she sweeps into a chair, turns to you, and fades out.
// The frames are small WebP files (see tools/make_chair.py); one is decoded per tick. Tap it to close early.
class ChairEmote(private val ctx: Context) : View(ctx) {
    private var count = 0
    private var fps = 15
    private var bw = 640
    private var bh = 360
    private var index = 0
    private var bmp: Bitmap? = null
    private var wm: WindowManager? = null
    private var h: Handler? = null
    private var done: (() -> Unit)? = null
    private var running = false
    private val src = Rect()
    private val dst = Rect()

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (index >= count) { stop(); return }
            try {
                ctx.assets.open("emotes/chair/%03d.webp".format(index)).use { BitmapFactory.decodeStream(it) }?.let { bmp = it }
            } catch (e: Exception) { }
            index++
            invalidate()
            h?.postDelayed(this, 1000L / fps)
        }
    }

    fun play(wm: WindowManager, h: Handler, whenDone: (() -> Unit)? = null): Boolean {
        try {
            val info = ctx.assets.open("emotes/chair/info.txt").bufferedReader().use { it.readText() }.trim().split(" ").map { it.toInt() }
            count = info[0]; fps = info[1]; bw = info[2]; bh = info[3]
        } catch (e: Exception) { return false }
        this.wm = wm; this.h = h; this.done = whenDone
        val w = (ctx.resources.displayMetrics.widthPixels * 0.98f).toInt()
        val lp = WindowManager.LayoutParams(w, w * bh / bw, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.CENTER
        try { wm.addView(this, lp) } catch (e: Exception) { return false }
        running = true
        h.post(tick)
        return true
    }

    fun stop() {
        if (!running) return
        running = false
        h?.removeCallbacks(tick)
        try { wm?.removeView(this) } catch (e: Exception) { }
        bmp = null
        done?.invoke()
    }

    override fun onDraw(canvas: Canvas) {
        val b = bmp ?: return
        src.set(0, 0, b.width, b.height); dst.set(0, 0, width, height)
        canvas.drawBitmap(b, src, dst, null)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP) stop()
        return true
    }
}
