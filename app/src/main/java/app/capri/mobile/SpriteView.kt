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
import android.graphics.Typeface
import kotlin.math.sin
import kotlin.math.floor
import kotlin.random.Random

class SpriteClip(val frames: Int, val fps: Int, val w: Int, val h: Int, val cols: Int, val sx: Float, val bmp: Bitmap) {
    val bytes get() = bmp.byteCount
}

// All her sprite sheets live here, shared by her, her clones and the little one on the home screen,
// so the same sheet is never decoded twice. Least recently used sheets are dropped to stay under the limit.
object SpriteBank {
    private var meta: JSONObject? = null
    private val clips = LinkedHashMap<String, SpriteClip>(8, 0.75f, true)
    private const val LIMIT = 34_000_000L

    @Synchronized
    fun info(ctx: Context, name: String): JSONObject? {
        if (CustomLook.active(ctx)) return CustomLook.info(ctx, name)
        if (meta == null) meta = JSONObject(ctx.assets.open("sprites.json").bufferedReader().use { it.readText() })
        return meta?.optJSONObject(name)
    }

    @Synchronized
    fun clear() { clips.clear() }

    @Synchronized
    fun get(ctx: Context, prefs: Prefs, name: String, keep: SpriteClip? = null): SpriteClip? {
        clips[name]?.let { return it }
        val o = try { info(ctx, name) } catch (e: Exception) { null } ?: return null
        for (attempt in 0..1) {
            try {
                val opts = BitmapFactory.Options().apply { inMutable = true }
                val custom = CustomLook.active(ctx)
                val bmp = (if (custom) CustomLook.file(ctx, name)?.inputStream()?.use { BitmapFactory.decodeStream(it, null, opts) }
                    else ctx.assets.open("sprites/$name.png").use { BitmapFactory.decodeStream(it, null, opts) }) ?: return null
                if (!custom) Recolor.apply(prefs, bmp)
                val c = SpriteClip(o.getInt("frames"), o.getInt("fps"), o.getInt("w"), o.getInt("h"), o.getInt("cols"), o.optDouble("sx", 0.0).toFloat(), bmp)
                clips[name] = c
                trim(c, keep)
                return c
            } catch (e: OutOfMemoryError) {
                // Low on memory: let go of every other sheet and try once more.
                val it = clips.entries.iterator()
                while (it.hasNext()) { if (it.next().value !== keep) it.remove() }
                System.gc()
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    private fun trim(newest: SpriteClip, keep: SpriteClip?) {
        var total = clips.values.sumOf { it.bytes.toLong() }
        val it = clips.entries.iterator()
        while (total > LIMIT && it.hasNext()) {
            val e = it.next()
            if (e.value === newest || e.value === keep) continue
            total -= e.value.bytes
            it.remove()
        }
    }
}

// Plays her animations like the desktop: the body keeps moving while the mouth overlay flaps, every clip is drawn at one scale
// and sits on the bottom of the window, plus a soft glow while she talks, an RGB split while she thinks and a ripple where you tap.
class SpriteView(ctx: Context) : View(ctx) {
    private val prefs = Prefs(ctx)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val ghostA = Paint(Paint.FILTER_BITMAP_FLAG)
    private val ghostB = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val src = Rect()
    private val dst = RectF()
    private var clip: SpriteClip? = null
    private var looping = true
    private var done: (() -> Unit)? = null
    private var started = 0L
    private var finished = false
    private var ripple: SpriteClip? = null
    private var rippleAt = 0L
    private var rx = 0f
    private var ry = 0f
    private val floaty = setOf("idle", "idle_mouth", "idle_blink", "dance")

    var current = ""
        private set

    // Mirrors her so she can face left; her walk is a drift, so one sheet does both ways.
    var flip = false
        set(v) { field = v; invalidate() }

    // dancing: her idle with a nod on top, like the desktop (the old dance sheet was never what it used)
    var dancing = false
        set(v) { field = v; if (v) { danceStart = SystemClock.uptimeMillis(); lastBeat = -1 }; invalidate() }
    var bpm = 108.0
    private var danceStart = 0L
    private var lastBeat = -1
    private var beatCount = 0
    private var nextNote = 0L

    // mouth follows how loud her voice is right now (0 to 1); a negative value means unknown, so it just flaps
    @Volatile var mouthLevel = -1f

    private class Puff(val born: Long, val glyph: String, val colour: Int, val side: Float, val seed: Float, val life: Long, val rise: Float)
    private val puffs = ArrayList<Puff>()
    private val puffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    var unit = 0f             // screen pixels per source pixel; 0 = the view height is one full 384px frame
    var talk = false          // mouth overlay on top of whatever she is doing
    var glowColor = Color.parseColor("#FF2A6D")
    var glow = false          // soft aura while she talks
    var glitchFx = false      // RGB split while she thinks or is thrown
    var bob = true            // slow float
    var lowPower = false      // fewer frames per second

    init {
        ghostA.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 229f, 0f, 0f, 0f, 0f, 255f, 0f, 0f, 0f, 0.5f, 0f)))
        ghostB.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            0f, 0f, 0f, 0f, 255f, 0f, 0f, 0f, 0f, 40f, 0f, 0f, 0f, 0f, 160f, 0f, 0f, 0f, 0.5f, 0f)))
    }

    private fun u(): Float = if (unit > 0f) unit else height / 384f

    // The window size this clip needs: never narrower than a standing frame, as tall as the clip itself.
    fun sizeFor(name: String): Pair<Int, Int> {
        val o = SpriteBank.info(context, name) ?: return Pair(width, height)
        val k = u()
        return Pair((maxOf(o.getInt("w"), 240) * k).toInt(), (o.getInt("h") * k).toInt())
    }

    private fun load(name: String): SpriteClip? = SpriteBank.get(context, prefs, name, clip)

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

    // Floating hearts, zzz, notes and so on above her.
    fun emote(name: String) {
        val now = SystemClock.uptimeMillis()
        val rnd = Random(now)
        val pink = Color.parseColor("#FF2E88")
        val red = Color.parseColor("#E82440")
        when (name) {
            "heart" -> for (i in 0 until 7) puffs.add(Puff(now + i * 170L, "\u2665", if (i % 2 == 0) pink else red, rnd.nextFloat() * 2 - 1, rnd.nextFloat() * 6, 1800, 0.5f))
            "sleep" -> for (i in 0 until 3) puffs.add(Puff(now + i * 600L, if (i == 2) "Z" else "z", Color.parseColor("#5EF0F6"), 0.5f + i * 0.15f, i.toFloat(), 2200, 0.35f))
            "glitch" -> glitchUntil = now + 900
            else -> for (i in 0 until 5) puffs.add(Puff(now + i * 200L, listOf("\u266A", "\u266B", "\u266C").random(), pink, rnd.nextFloat() * 2 - 1, rnd.nextFloat() * 6, 1500, 0.45f))
        }
        invalidate()
    }
    private var glitchUntil = 0L

    // Reloads her art (after her colors change).
    fun reload() {
        val n = current
        val lp = looping
        SpriteBank.clear()
        clip = null
        current = ""
        if (n.isNotEmpty()) play(n, lp)
    }

    // A small ripple where she was touched.
    fun ripple(x: Float, y: Float) {
        ripple = load("click") ?: return
        rx = x
        ry = y
        rippleAt = SystemClock.uptimeMillis()
        invalidate()
    }

    private fun frameRect(c: SpriteClip, i: Int) = src.set((i % c.cols) * c.w, (i / c.cols) * c.h, (i % c.cols + 1) * c.w, (i / c.cols + 1) * c.h)

    private fun place(c: SpriteClip, k: Float, floatY: Float) {
        val dw = c.w * k
        val dh = c.h * k
        val left = (width - dw) / 2f + c.sx * k
        val top = height - dh + floatY
        dst.set(left, top, left + dw, top + dh)
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
        val k = u()
        val floatY = if (bob && current in floaty) (sin(now / 900.0) * 384 * k * 0.012).toFloat() else 0f
        frameRect(c, i)
        place(c, k, floatY)

        if (glow) {
            val pulse = 0.55f + 0.25f * sin(now / 220.0).toFloat()
            val r = 384 * k * 0.5f
            glowPaint.shader = RadialGradient(width / 2f, height - 384 * k * 0.55f, r,
                Color.argb((70 * pulse).toInt(), Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor)),
                Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(width / 2f, height - 384 * k * 0.55f, r, glowPaint)
        }

        if (flip) { canvas.save(); canvas.scale(-1f, 1f, width / 2f, height / 2f) }
        if (glitchFx || now < glitchUntil) {
            val step = (now / 70) % 9
            val dx = (step - 4) * 384 * k * 0.006f
            canvas.save(); canvas.translate(-dx, 0f); canvas.drawBitmap(c.bmp, src, dst, ghostA); canvas.restore()
            canvas.save(); canvas.translate(dx, 0f); canvas.drawBitmap(c.bmp, src, dst, ghostB); canvas.restore()
        }
        val nod = if (dancing && c.w == 116) nodDepth(now) else 0f
        if (nod > 0.002f) drawNodding(canvas, c, i, k, nod) else canvas.drawBitmap(c.bmp, src, dst, paint)

        // her mouth moves on top, so the body keeps living while she talks
        if (talk && nod <= 0.002f) {
            val ov = load(if (c.w == 116) "idle_mouth" else if (c.w == 240) "mouth" else "")
            if (ov != null) {
                val lv = if (Voice.level >= 0f) Voice.level else mouthLevel
                val mi = if (lv >= 0f) (lv * 5.5f).toInt().coerceIn(0, ov.frames - 1) else ((now * ov.fps / 1000) % ov.frames).toInt()
                frameRect(ov, mi)
                place(ov, k, floatY)
                canvas.drawBitmap(ov.bmp, src, dst, paint)
            }
        }
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

        if (dancing) tickDance(now)
        val live = drawPuffs(canvas, now)

        val step = if (lowPower) 83L else maxOf(16L, 1000L / c.fps)
        postInvalidateDelayed(if (glow || glitchFx || rippling || talk || live || dancing || now < glitchUntil) 33L else step)
    }

    // ---- dancing, ported from the desktop's Nod / NodDepth / Jam ----

    private val neck = floatArrayOf(118f, 156f, 132f)   // rows where her head ends, chest begins, neck sits (her standing frame)

    private fun phase(now: Long): Double {
        val beat = 60000.0 / bpm
        return ((now - danceStart) / beat) % 1.0
    }

    private fun gain(now: Long): Float = minOf(1f, (now - danceStart) / 600f)

    private fun nodDepth(now: Long): Float {
        val p = phase(now)
        val rise = if (p < 0.45) 1 - p / 0.45 else 0.0
        val fall = if (p > 0.75) (p - 0.75) / 0.25 else 0.0
        return (gain(now) * (rise * rise + fall * fall)).toFloat()
    }

    // a new note every other beat, floating up beside her
    private fun tickDance(now: Long) {
        val beatNo = floor((now - danceStart) / (60000.0 / bpm)).toInt()
        if (beatNo != lastBeat) {
            lastBeat = beatNo
            beatCount++
            if (beatCount % 2 == 0 && puffs.size < 5) {
                val rnd = Random(now)
                val cols = intArrayOf(Color.parseColor("#FF2E88"), Color.parseColor("#E82440"), Color.parseColor("#50E6AA"))
                puffs.add(Puff(now, listOf("\u266A", "\u266B", "\u266C").random(), cols[rnd.nextInt(3)], if (rnd.nextBoolean()) 1f else -1f, rnd.nextFloat() * 6, 1500, 0.4f))
            }
        }
    }

    private fun drawNodding(canvas: Canvas, c: SpriteClip, index: Int, k: Float, nod: Float) {
        val head = neck[0]
        val chest = neck[1]
        val nk = neck[2]
        val drop = nod * 10f
        val lean = nod * 0.22f
        val col = index % c.cols
        val row = index / c.cols
        val baseX = col * c.w
        val baseY = row * c.h
        // piece() overwrites dst, so the frame's own rectangle is copied first (it used to drift down with every piece)
        val frameL = dst.left
        val frameT = dst.top
        val frameR = dst.right
        fun piece(top: Float, bottom: Float, dx: Float, dy: Float) {
            src.set(baseX, baseY + top.toInt(), baseX + c.w, baseY + bottom.toInt())
            dst.set(frameL + dx * k, frameT + top * k + dy * k, frameR + dx * k, frameT + bottom * k + dy * k)
            canvas.drawBitmap(c.bmp, src, dst, paint)
        }
        piece(chest, c.h.toFloat(), 0f, 0f)
        var y = chest
        while (y > head) {
            val top = maxOf(head, y - 3f)
            val mid = (top + y) / 2f
            var w = (chest - mid) / (chest - head)
            w = w * w * (3 - 2 * w)
            piece(top, y + 1f, (nk - mid) * lean * w, drop * w)
            y = top
        }
        // the head and hair: drop and lean about the neck
        val pivot = frameT + nk * k + drop * k
        canvas.save()
        canvas.translate(0f, pivot)
        canvas.skew(-lean, 0f)
        canvas.translate(0f, -pivot)
        piece(0f, head + 1f, 0f, drop)
        canvas.restore()
    }

    private fun drawPuffs(canvas: Canvas, now: Long): Boolean {
        if (puffs.isEmpty()) return false
        puffs.removeAll { now - it.born > it.life }
        val k = u()
        puffPaint.textSize = 38f * k
        puffPaint.typeface = Typeface.DEFAULT_BOLD
        for (p in puffs) {
            val age = (now - p.born).toFloat()
            if (age < 0) continue
            val t = age / p.life
            val x = width / 2f + p.side * width * 0.3f + sin(p.seed + t * 5f) * width * 0.04f
            val y = height - 384 * k * 0.72f - t * 384 * k * p.rise
            puffPaint.color = p.colour
            puffPaint.alpha = (255 * (if (t < 0.7f) 1f else (1f - t) / 0.3f)).toInt().coerceIn(0, 255)
            canvas.drawText(p.glyph, x, y, puffPaint)
        }
        return puffs.isNotEmpty()
    }
}
