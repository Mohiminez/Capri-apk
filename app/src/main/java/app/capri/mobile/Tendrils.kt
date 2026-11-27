package app.capri.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.view.View
import android.view.WindowManager

/**
 * The desktop's startup glitch: crimson tendrils tear out from behind her, different every time, while she beams in.
 * A short overlay around her (touches pass through); the strands start at the edge of her body so she stays clear.
 */
class Tendrils(ctx: Context) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.MITER }
    private var frame = 0
    private val seed = (Math.random() * 1_000_000).toLong()

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val inner = width * 0.17f; val outer = width * 0.5f
        val fade = 1f - frame / FRAMES.toFloat()
        val rnd = java.util.Random(seed + frame / 2)          // the strands re-form every other frame, so they flicker
        val strands = 9
        for (i in 0 until strands) {
            var a = (i * 2 * Math.PI / strands) + rnd.nextFloat() * 0.5
            var r = inner
            val path = Path(); path.moveTo(cx + (Math.cos(a) * r).toFloat(), cy + (Math.sin(a) * r).toFloat())
            val reach = outer * (0.55f + 0.45f * rnd.nextFloat()) * (0.5f + 0.5f * (1f - fade * 0.4f))
            while (r < reach) {
                r += (6 + rnd.nextInt(12)) * d
                a += (rnd.nextFloat() - 0.5f) * 0.6
                path.lineTo(cx + (Math.cos(a) * r).toFloat(), cy + (Math.sin(a) * r).toFloat())
            }
            p.strokeWidth = (2.6f + rnd.nextFloat() * 2f) * d
            p.color = Color.argb((200 * fade).toInt(), 232, 36, 64); c.drawPath(path, p)
            p.strokeWidth = 1f * d
            p.color = Color.argb((230 * fade).toInt(), 255, 170, 180); c.drawPath(path, p)
        }
    }

    /** Plays around the point (cx, cy) on screen, then removes itself. */
    fun play(wm: WindowManager, h: Handler, cx: Int, cy: Int, size: Int) {
        val lp = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
        lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
        lp.x = cx - size / 2; lp.y = cy - size / 2
        try { wm.addView(this, lp); Leftovers.add(this) } catch (e: Throwable) { return }
        val step = object : Runnable {
            override fun run() {
                frame++
                if (frame >= FRAMES) { Leftovers.done(this@Tendrils); try { wm.removeView(this@Tendrils) } catch (e: Throwable) { }; return }
                invalidate(); h.postDelayed(this, 45)
            }
        }
        h.postDelayed(step, 45)
    }

    companion object { const val FRAMES = 14 }
}

/**
 * Short-lived full overlays (the startup glitch, the cinematic intros, the tendrils) remove themselves when they finish, but their timer
 * dies with the service. This keeps a list so that stopping her always sweeps any that are still up.
 */
object Leftovers {
    private val live = java.util.Collections.synchronizedSet(HashSet<View>())
    fun add(v: View) { live.add(v) }
    fun done(v: View) { live.remove(v) }
    fun sweep(wm: WindowManager) {
        val all = synchronized(live) { ArrayList(live).also { live.clear() } }
        for (v in all) try { wm.removeView(v) } catch (e: Throwable) { }
    }
}
