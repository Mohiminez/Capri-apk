package app.capri.mobile

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Handler
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView

// Which start-up intro to play. Pure and unit tested. The two cinematic ones ("peek" and "close") are the full-screen intros from
// capri-desktop; "glitch" is the glitch-in the app already had. Cinematics play at most once every [COOLDOWN_MS] so a restart is never annoying.
object IntroPlan {
    const val COOLDOWN_MS = 6 * 3600_000L
    val styles = listOf("random", "peek", "close", "glitch", "off")

    /** style = the saved choice, last = when a cinematic last played, roll = a number 0..2 for "random". null = no intro at all. */
    fun pick(style: String, last: Long, now: Long, roll: Int): String? {
        if (style == "off") return null
        val cooled = last <= 0L || now - last >= COOLDOWN_MS || now < last
        return when (style) {
            "glitch" -> "glitch"
            "peek", "close" -> if (cooled) style else "glitch"
            else -> if (!cooled) "glitch" else when (Math.floorMod(roll, 3)) { 0 -> "peek"; 1 -> "close"; else -> "glitch" }
        }
    }
}

// Plays the frames of assets/intros/<name>/NN.webp over everything, once, and removes itself. Touches go through it.
class CinemaIntro(private val ctx: Context, private val name: String, private val fps: Int = 20) {
    fun play(wm: WindowManager, h: Handler, done: () -> Unit = {}) {
        val files = try { ctx.assets.list("intros/$name")?.filter { it.endsWith(".webp") }?.sorted() ?: emptyList() } catch (e: Throwable) { emptyList() }
        if (files.isEmpty()) { done(); return }
        val view = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        try { wm.addView(view, lp) } catch (e: Throwable) { done(); return }
        var i = 0
        val step = object : Runnable {
            override fun run() {
                if (i >= files.size) { try { wm.removeView(view) } catch (e: Throwable) { }; done(); return }
                try {
                    ctx.assets.open("intros/$name/${files[i]}").use { view.setImageBitmap(BitmapFactory.decodeStream(it)) }
                } catch (e: Throwable) { try { wm.removeView(view) } catch (e2: Throwable) { }; done(); return }
                i++
                h.postDelayed(this, 1000L / fps)
            }
        }
        h.post(step)
    }
}
