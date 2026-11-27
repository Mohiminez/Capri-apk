package app.capri.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

// The little pill that appears under her when you tap her, like capri-desktop's: a pencil (write to her) and a sound wave (talk to her).
// Drawn with paths, no image files. Tapping her again, tapping outside, or a few seconds hide it.
class CapriPill(ctx: Context, private val onType: () -> Unit, private val onTalk: () -> Unit) : LinearLayout(ctx) {
    private val d = ctx.resources.displayMetrics.density

    private inner class Icon(c: Context, val kind: Int) : View(c) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Night.cyan; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 2.6f * d }
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val s = minOf(w, h)
            val cx = w / 2; val cy = h / 2
            if (kind == 0) {
                // pencil: a slanted body with a point
                p.strokeWidth = 4.2f * d
                c.drawLine(cx - s * 0.14f, cy + s * 0.14f, cx + s * 0.16f, cy - s * 0.16f, p)
                p.strokeWidth = 2.2f * d
                c.drawLine(cx - s * 0.26f, cy + s * 0.26f, cx - s * 0.2f, cy + s * 0.2f, p)
                c.drawLine(cx + s * 0.04f, cy + s * 0.28f, cx + s * 0.2f, cy + s * 0.28f, p)
            } else {
                // sound wave: bars of different heights
                p.strokeWidth = 2.4f * d
                val hs = floatArrayOf(0.14f, 0.3f, 0.46f, 0.26f, 0.38f, 0.16f)
                val gap = s * 0.15f
                var x = cx - gap * (hs.size - 1) / 2
                for (k in hs) { c.drawLine(x, cy - s * k, x, cy + s * k, p); x += gap }
            }
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        background = GradientDrawable().apply { cornerRadius = 40f * d; setColor(Color.argb(238, 16, 18, 24)); setStroke((1.2f * d).toInt().coerceAtLeast(1), Color.argb(70, 255, 255, 255)) }
        setPadding((6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
        elevation = 8f * d
        val size = (46 * d).toInt()
        addView(Icon(ctx, 0).apply { contentDescription = "Type"; setOnClickListener { onType() } }, LayoutParams(size, size))
        addView(View(ctx).apply { setBackgroundColor(Color.argb(60, 255, 255, 255)) }, LayoutParams((1.2f * d).toInt().coerceAtLeast(1), (22 * d).toInt()))
        addView(Icon(ctx, 1).apply { contentDescription = "Talk"; setOnClickListener { onTalk() } }, LayoutParams(size, size))
    }
}
