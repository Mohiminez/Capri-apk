package app.capri.mobile

import android.content.ComponentName
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

// The desktop's cyberpunk mini player: title, artist, progress and controls, shown while music plays.
// It reads the active media session, so it needs notification access (the same one "what's playing" uses).
class MiniPlayer(private val ctx: Context, private val wm: WindowManager, private val h: Handler) {
    private var root: LinearLayout? = null
    private var titleV: TextView? = null
    private var artistV: TextView? = null
    private var timeV: TextView? = null
    private var playV: TextView? = null
    private var bar: Bar? = null
    private var controller: MediaController? = null
    private var dismissedKey = ""
    private var shownKey = ""
    private val tfMono: Typeface = try { Typeface.createFromAsset(ctx.assets, "fonts/ShareTechMono-Regular.ttf") } catch (e: Exception) { Typeface.MONOSPACE }
    private val dm = ctx.resources.displayMetrics
    private fun dp(v: Int) = (v * dm.density).toInt()

    private class Bar(c: Context, val d: Float) : View(c) {
        var frac = 0f
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            val y = height / 2f
            p.color = Color.argb(60, 255, 255, 255); p.strokeWidth = 3f * d; p.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(4f * d, y, width - 4f * d, y, p)
            val x = 4f * d + (width - 8f * d) * frac.coerceIn(0f, 1f)
            p.color = Night.pink
            canvas.drawLine(4f * d, y, x, y, p)
            p.color = Color.WHITE
            canvas.drawCircle(x, y, 6f * d, p)
            p.color = Night.pink; p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d
            canvas.drawCircle(x, y, 6f * d, p)
            p.style = Paint.Style.FILL
        }
    }

    private fun session(): MediaController? = try {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val all = msm.getActiveSessions(ComponentName(ctx, CapriNotifListener::class.java))
        all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: all.firstOrNull()
    } catch (e: Throwable) { null }

    // Called every few seconds with whether music is playing and whether she should show the player.
    fun tick(wanted: Boolean) {
        if (!wanted) { hide(); return }
        val c = session()
        val st = c?.playbackState
        val md = c?.metadata
        if (c == null || md == null || st == null || (st.state != PlaybackState.STATE_PLAYING && st.state != PlaybackState.STATE_PAUSED)) { hide(); return }
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return hide()
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val key = title + "|" + artist
        if (key == dismissedKey) { hide(); return }
        controller = c
        if (root == null) build()
        if (key != shownKey) { shownKey = key; titleV?.text = title; artistV?.text = artist }
        update()
        h.removeCallbacks(ticker)
        h.postDelayed(ticker, 1000)
    }

    private val ticker = object : Runnable {
        override fun run() { if (root != null) { update(); h.postDelayed(this, 1000) } }
    }

    private fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

    private fun update() {
        val c = controller ?: return
        val st = c.playbackState ?: return
        val dur = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        var pos = st.position
        if (st.state == PlaybackState.STATE_PLAYING) pos += ((android.os.SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        if (dur > 0) pos = pos.coerceAtMost(dur)
        bar?.frac = if (dur > 0) pos.toFloat() / dur else 0f
        bar?.invalidate()
        timeV?.text = fmt(pos) + (if (dur > 0) " / " + fmt(dur) else "")
        playV?.text = if (st.state == PlaybackState.STATE_PLAYING) "❚❚" else "▶"
    }

    private fun ctl(label: String, size: Float = 22f, click: () -> Unit): TextView {
        val t = TextView(ctx)
        t.text = label; t.textSize = size; t.setTextColor(Color.WHITE); t.gravity = Gravity.CENTER
        t.setPadding(dp(18), dp(8), dp(18), dp(8))
        t.setOnClickListener { try { click() } catch (e: Throwable) { } ; update() }
        return t
    }

    private fun build() {
        val p = LinearLayout(ctx)
        p.orientation = LinearLayout.VERTICAL
        p.setPadding(dp(16), dp(10), dp(14), dp(8))
        p.background = PanelDrawable(Night.pink, Night.sCyan, dm.density, 10f)

        val top = LinearLayout(ctx)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val note = TextView(ctx); note.text = "♫"; note.textSize = 22f; note.setTextColor(Night.pink); note.setPadding(0, 0, dp(10), 0)
        top.addView(note)
        val col = LinearLayout(ctx); col.orientation = LinearLayout.VERTICAL
        val t = TextView(ctx); t.typeface = tfMono; t.textSize = 14f; t.setTextColor(Color.WHITE); t.maxLines = 1; t.ellipsize = TextUtils.TruncateAt.END
        val a = TextView(ctx); a.typeface = tfMono; a.textSize = 12f; a.setTextColor(Night.sCyan); a.maxLines = 1; a.ellipsize = TextUtils.TruncateAt.END
        col.addView(t); col.addView(a)
        top.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val tm = TextView(ctx); tm.typeface = tfMono; tm.textSize = 11f; tm.setTextColor(Night.dim); tm.setPadding(dp(8), 0, dp(4), 0)
        top.addView(tm)
        val x = TextView(ctx); x.text = "✕"; x.textSize = 16f; x.setTextColor(Night.dim); x.setPadding(dp(8), dp(4), dp(4), dp(4))
        x.setOnClickListener { dismissedKey = shownKey; hide() }
        top.addView(x)
        p.addView(top)

        val b = Bar(ctx, dm.density)
        p.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(22)).apply { topMargin = dp(6) })

        val row = LinearLayout(ctx); row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER
        row.addView(ctl("⏮︎") { controller?.transportControls?.skipToPrevious() })
        val play = ctl("❚❚", 20f) {
            val s = controller?.playbackState?.state
            if (s == PlaybackState.STATE_PLAYING) controller?.transportControls?.pause() else controller?.transportControls?.play()
        }
        play.background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.argb(235, 236, 236, 241)); setStroke(dp(2), Night.pink) }
        play.setTextColor(Color.parseColor("#0A0A10"))
        row.addView(play, LinearLayout.LayoutParams(dp(54), dp(54)).apply { setMargins(dp(10), 0, dp(10), 0) })
        row.addView(ctl("⏭︎") { controller?.transportControls?.skipToNext() })
        p.addView(row)

        titleV = t; artistV = a; timeV = tm; playV = play; bar = b; root = p
        val w = minOf((dm.widthPixels * 0.94f).toInt(), dp(430))
        val lp = WindowManager.LayoutParams(w, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        lp.y = dp(84)
        try { wm.addView(p, lp) } catch (e: Throwable) { root = null }
    }

    fun hide() {
        h.removeCallbacks(ticker)
        val r = root ?: return
        try { wm.removeView(r) } catch (e: Throwable) { }
        root = null; shownKey = ""
    }
}

// A short glitch-in when she wakes up, like the desktop's startup intros.
class IntroView(ctx: Context, private val accent: Int) : View(ctx) {
    var t = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = ctx.resources.displayMetrics.density
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = try { Typeface.createFromAsset(ctx.assets, "fonts/CapriMainframe-Bold.ttf") } catch (e: Exception) { Typeface.DEFAULT_BOLD } }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE; color = Night.sCyan }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val fade = if (t > 0.72f) 1f - (t - 0.72f) / 0.28f else 1f
        p.style = Paint.Style.FILL
        p.color = Color.argb((235 * fade).toInt(), 6, 6, 12)
        canvas.drawRect(0f, 0f, w, h, p)
        val rnd = java.util.Random((t * 24).toInt().toLong() * 7919)
        val bars = if (t < 0.65f) 10 else 3
        for (i in 0 until bars) {
            val y = rnd.nextFloat() * h
            val bh = (6 + rnd.nextInt(34)) * d
            val off = (rnd.nextFloat() - 0.5f) * 90f * d * (1f - t)
            p.color = Color.argb((70 * fade).toInt(), if (rnd.nextBoolean()) 94 else 255, if (rnd.nextBoolean()) 240 else 46, if (rnd.nextBoolean()) 246 else 136)
            canvas.drawRect(off, y, w + off, y + bh, p)
        }
        val reveal = (t / 0.55f).coerceIn(0f, 1f)
        title.textSize = 64f * d
        val cx = w / 2f; val cy = h * 0.42f
        val shake = (1f - reveal) * 24f * d
        val a = (255 * fade).toInt()
        title.color = Color.argb((190 * fade).toInt(), 94, 240, 246); canvas.drawText("CAPRI", cx - shake, cy, title)
        title.color = Color.argb((190 * fade).toInt(), 255, 46, 136); canvas.drawText("CAPRI", cx + shake, cy, title)
        title.color = Color.argb(a, 255, 255, 255); canvas.drawText("CAPRI", cx, cy, title)
        p.color = Color.argb(a, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRect(cx - 110f * d * reveal, cy + 14f * d, cx + 110f * d * reveal, cy + 18f * d, p)
        sub.textSize = 13f * d
        sub.alpha = a
        canvas.drawText(if (reveal > 0.9f) "// ONLINE" else "// BOOTING", cx, cy + 44f * d, sub)
    }

    // Runs the animation and removes itself.
    fun play(wm: WindowManager, h: Handler) {
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
        try { wm.addView(this, lp) } catch (e: Throwable) { return }
        val start = android.os.SystemClock.uptimeMillis()
        val step = object : Runnable {
            override fun run() {
                t = ((android.os.SystemClock.uptimeMillis() - start) / 1500f)
                if (t >= 1f) { try { wm.removeView(this@IntroView) } catch (e: Throwable) { }; return }
                invalidate()
                h.postDelayed(this, 40)
            }
        }
        h.post(step)
    }
}
