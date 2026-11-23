package app.capri.mobile

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.*
import android.view.Surface
import android.view.TextureView
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin

// Her room: a small scene that grows with how close you are. The window shows the real time of day, and each bond level adds something
// (a rug, a plant, a lamp and shelf, string lights). You can pet her, give her a snack, play, let her dance, sit or sleep.
object RoomRules {
    /** What the room contains at a bond level (0..4). */
    fun decor(level: Int): Set<String> = buildSet {
        if (level >= 1) add("rug")
        if (level >= 2) { add("plant"); add("neon") }
        if (level >= 3) { add("lamp"); add("shelf") }
        if (level >= 4) { add("lights"); add("poster") }
    }

    /** Time of day for the window: 0 night, 1 dawn/dusk, 2 day. */
    fun sky(hour: Int): Int = when (hour) { in 7..17 -> 2; in 5..6, in 18..19 -> 1; else -> 0 }

    const val PET_GAP = 6_000L
    const val SNACK_GAP = 45 * 60_000L
    const val PLAY_GAP = 5 * 60_000L

    /** Seconds still to wait before [last] + [gap] has passed, or 0. */
    fun wait(now: Long, last: Long, gap: Long): Long = maxOf(0L, (last + gap - now + 999) / 1000)
}

class RoomView(ctx: Context) : View(ctx) {
    var level = 0
    var hour = 12
    var lampOn = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val start = System.currentTimeMillis()

    var accent = 0xFFE84246.toInt()
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; isFakeBoldText = true }
    private fun a(alpha: Int, col: Int = accent) = Color.argb(alpha, Color.red(col), Color.green(col), Color.blue(col))

    // A cramped night-city apartment: dark concrete, a rain-streaked window over a lit skyline, a flickering neon sign, a terminal that never sleeps.
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val floorY = h * 0.72f
        val sky = RoomRules.sky(hour)
        val t = (System.currentTimeMillis() - start) / 1000.0
        // wall: dark concrete with a faint panel grid
        p.shader = LinearGradient(0f, 0f, 0f, floorY, 0xFF0E0C14.toInt(), 0xFF171320.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, floorY, p); p.shader = null
        p.color = 0x10FFFFFF; p.strokeWidth = 1.5f
        for (i in 1..8) c.drawLine(w * i / 9f, 0f, w * i / 9f, floorY, p)
        for (i in 1..4) c.drawLine(0f, floorY * i / 5f, w, floorY * i / 5f, p)
        // floor: wet dark tiles that pick up the neon
        p.shader = LinearGradient(0f, floorY, 0f, h, 0xFF15121C.toInt(), 0xFF0A090F.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, floorY, w, h, p); p.shader = null
        p.color = a(70); c.drawRect(0f, floorY, w, floorY + 3f, p)
        p.color = 0x14FFFFFF; for (i in 1..6) c.drawLine(0f, floorY + (h - floorY) * i / 7f, w, floorY + (h - floorY) * i / 7f, p)

        // window: the city outside, always lit, with real time-of-day sky behind it
        val wx = w * 0.08f; val wy = h * 0.08f; val ww = w * 0.40f; val wh = h * 0.40f
        val top = when (sky) { 2 -> 0xFF3A5F86.toInt(); 1 -> 0xFF6A3A52.toInt(); else -> 0xFF07091A.toInt() }
        val bot = when (sky) { 2 -> 0xFF8FB4D0.toInt(); 1 -> 0xFFE0805A.toInt(); else -> 0xFF1A1233.toInt() }
        p.shader = LinearGradient(0f, wy, 0f, wy + wh, top, bot, Shader.TileMode.CLAMP)
        c.drawRect(wx, wy, wx + ww, wy + wh, p); p.shader = null
        c.save(); c.clipRect(wx, wy, wx + ww, wy + wh)
        val heights = floatArrayOf(0.55f, 0.75f, 0.45f, 0.9f, 0.62f, 0.8f, 0.5f)
        val bw = ww / heights.size
        for (i in heights.indices) {
            val bx = wx + i * bw; val bh = wh * heights[i] * 0.8f
            p.color = 0xFF0A0A12.toInt(); c.drawRect(bx + 2f, wy + wh - bh, bx + bw - 2f, wy + wh, p)
            for (r in 0 until (bh / (wh * 0.07f)).toInt()) for (q in 0..2) {
                val on = ((i * 31 + r * 17 + q * 7 + (t / 4).toInt() * (if ((r + q + i) % 5 == 0) 1 else 0)) % 3) != 0
                if (!on) continue
                p.color = if ((i + r + q) % 4 == 0) a(190) else 0xAAFFE9A8.toInt()
                c.drawRect(bx + 6f + q * bw * 0.3f, wy + wh - bh + 8f + r * wh * 0.07f, bx + 6f + q * bw * 0.3f + bw * 0.16f, wy + wh - bh + 8f + r * wh * 0.07f + wh * 0.035f, p)
            }
        }
        // rain
        p.color = 0x44AFC8FF; p.strokeWidth = 2f
        for (i in 0 until 26) {
            val rx = wx + ww * (((i * 41) % 100) / 100f); val ry = wy + ((t * 220 + i * 53) % wh).toFloat()
            c.drawLine(rx, ry, rx - 4f, ry + 18f, p)
        }
        c.restore()
        p.style = Paint.Style.STROKE; p.strokeWidth = 7f; p.color = 0xFF2A2536.toInt()
        c.drawRect(wx, wy, wx + ww, wy + wh, p)
        p.strokeWidth = 4f; c.drawLine(wx + ww / 2, wy, wx + ww / 2, wy + wh, p)
        p.style = Paint.Style.FILL
        // neon from outside spilling on the floor
        val bp = Path(); bp.moveTo(wx, wy + wh); bp.lineTo(wx + ww, wy + wh); bp.lineTo(w * 0.70f, h); bp.lineTo(w * 0.02f, h); bp.close()
        p.color = a(22); c.drawPath(bp, p)

        // neon sign: CAPRI, flickering like it has a loose wire
        val d = RoomRules.decor(level)
        if ("neon" in d) {
            val flick = if ((t * 7).toInt() % 23 == 0) 70 else if ((t * 11).toInt() % 17 == 0) 150 else 255
            txt.textSize = h * 0.07f; txt.textAlign = Paint.Align.CENTER
            txt.style = Paint.Style.STROKE; txt.strokeWidth = 14f; txt.color = a(flick / 5); c.drawText("CAPRI", w * 0.74f, h * 0.17f, txt)
            txt.strokeWidth = 3f; txt.color = a(flick); c.drawText("CAPRI", w * 0.74f, h * 0.17f, txt)
            txt.style = Paint.Style.FILL; txt.color = Color.argb(flick, 255, 235, 240); txt.textSize = h * 0.07f
            c.drawText("CAPRI", w * 0.74f, h * 0.17f, txt)
            txt.textSize = h * 0.022f; txt.color = a(160); c.drawText("// NIGHT CITY //", w * 0.74f, h * 0.215f, txt)
        }
        // desk with a terminal
        val dx = w * 0.58f; val dy = floorY - h * 0.08f
        p.color = 0xFF1F1B2B.toInt(); c.drawRect(dx, dy, dx + w * 0.38f, dy + h * 0.022f, p)
        p.color = 0xFF14111D.toInt(); c.drawRect(dx + 6f, dy, dx + 16f, floorY + h * 0.03f, p); c.drawRect(dx + w * 0.38f - 16f, dy, dx + w * 0.38f - 6f, floorY + h * 0.03f, p)
        val mx = dx + w * 0.06f; val mw = w * 0.22f; val mh = h * 0.13f
        p.color = 0xFF050508.toInt(); c.drawRect(mx - 6f, dy - mh - 6f, mx + mw + 6f, dy - 2f, p)
        p.color = a(26); c.drawRect(mx, dy - mh, mx + mw, dy - 8f, p)
        p.color = a(210)
        for (i in 0..5) {
            val len = mw * (0.25f + 0.6f * (((i * 37 + (t * 1.5).toInt() * (3 + i)) % 10) / 10f))
            c.drawRect(mx + 8f, dy - mh + 8f + i * mh * 0.14f, mx + 8f + len, dy - mh + 8f + i * mh * 0.14f + 5f, p)
        }
        if ((t * 2).toInt() % 2 == 0) { p.color = 0xFFFFFFFF.toInt(); c.drawRect(mx + 8f, dy - 20f, mx + 18f, dy - 12f, p) }
        // cables on the floor
        p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.color = 0xFF050508.toInt()
        val cb = Path(); cb.moveTo(dx + 20f, floorY + h * 0.02f); cb.cubicTo(w * 0.45f, floorY + h * 0.12f, w * 0.30f, floorY + h * 0.05f, w * 0.12f, floorY + h * 0.10f); c.drawPath(cb, p)
        p.style = Paint.Style.FILL

        if ("rug" in d) { p.color = 0xFF0C0A12.toInt(); c.drawRect(w * 0.14f, floorY + (h - floorY) * 0.30f, w * 0.78f, h - (h - floorY) * 0.12f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f; p.color = a(170); c.drawRect(w * 0.14f, floorY + (h - floorY) * 0.30f, w * 0.78f, h - (h - floorY) * 0.12f, p); p.style = Paint.Style.FILL }
        if ("poster" in d) { p.color = 0xFF050508.toInt(); c.drawRect(w * 0.52f, h * 0.30f, w * 0.66f, h * 0.50f, p); p.color = a(200); c.drawRect(w * 0.535f, h * 0.32f, w * 0.645f, h * 0.36f, p)
            p.color = 0x66FFFFFF; c.drawRect(w * 0.535f, h * 0.39f, w * 0.60f, h * 0.405f, p); c.drawRect(w * 0.535f, h * 0.43f, w * 0.63f, h * 0.44f, p) }
        if ("shelf" in d) { p.color = 0xFF1F1B2B.toInt(); c.drawRect(w * 0.10f, h * 0.54f, w * 0.40f, h * 0.555f, p)
            p.color = a(220); c.drawRect(w * 0.13f, h * 0.49f, w * 0.17f, h * 0.54f, p); p.color = 0xFF2EE6E0.toInt(); c.drawRect(w * 0.19f, h * 0.47f, w * 0.22f, h * 0.54f, p); p.color = 0xFF3A3548.toInt(); c.drawRect(w * 0.24f, h * 0.50f, w * 0.30f, h * 0.54f, p) }
        if ("plant" in d) { p.color = 0xFF14111D.toInt(); c.drawRect(w * 0.04f, floorY - h * 0.07f, w * 0.11f, floorY + h * 0.02f, p)
            p.color = 0xFF1FBF7A.toInt(); for (i in -2..2) c.drawOval(RectF(w * 0.075f + i * w * 0.018f - w * 0.016f, floorY - h * (0.16f + 0.02f * (2 - Math.abs(i))), w * 0.075f + i * w * 0.018f + w * 0.016f, floorY - h * 0.06f), p) }
        if ("lamp" in d) { val lx = w * 0.93f
            p.color = 0xFF14111D.toInt(); c.drawRect(lx - 3f, floorY - h * 0.2f, lx + 3f, floorY + h * 0.02f, p)
            p.color = if (lampOn) 0xFF2EE6E0.toInt() else 0xFF3A3548.toInt(); c.drawRect(lx - w * 0.03f, floorY - h * 0.22f, lx + w * 0.03f, floorY - h * 0.2f, p)
            if (lampOn) { p.shader = RadialGradient(lx, floorY - h * 0.2f, w * 0.35f, 0x442EE6E0, 0x002EE6E0, Shader.TileMode.CLAMP); c.drawCircle(lx, floorY - h * 0.2f, w * 0.35f, p); p.shader = null } }
        if ("lights" in d) { p.strokeWidth = 4f; val y = h * 0.035f
            val pulse = (150 + 100 * sin(t * 2)).toInt().coerceIn(80, 255)
            p.color = a(pulse); c.drawRect(w * 0.05f, y, w * 0.95f, y + 6f, p); p.color = a(pulse / 4); c.drawRect(w * 0.05f, y - 6f, w * 0.95f, y + 14f, p) }

        // rising data bits instead of dust, a slow scanline, an occasional glitch slice, scanlines and a vignette
        for (i in 0 until 14) {
            val fx = w * (((i * 61) % 100) / 100f)
            val fy = h * (1f - (((t * 0.03 + i * 0.137) % 1.0)).toFloat())
            p.color = a((40 + 50 * sin(t * 2 + i)).toInt().coerceIn(15, 110)); c.drawRect(fx, fy, fx + 4f, fy + 4f, p)
        }
        p.color = 0x08FFFFFF; var ly = 0f; while (ly < h) { c.drawRect(0f, ly, w, ly + 1f, p); ly += 4f }
        val sy = ((t * 60) % (h + 80)).toFloat() - 40f
        p.color = a(18); c.drawRect(0f, sy, w, sy + 36f, p)
        if ((t * 3).toInt() % 29 == 0) { val gy = h * (((t * 13) % 10) / 10f).toFloat(); p.color = a(60); c.drawRect(0f, gy, w, gy + 8f, p) }
        p.shader = RadialGradient(w / 2, h * 0.55f, maxOf(w, h) * 0.75f, 0x00000000, 0xAA000000.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        postInvalidateDelayed(90)
    }
}

class RoomActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var room: RoomView
    private lateinit var sprite: SpriteView
    private lateinit var status: TextView
    private lateinit var says: TextView
    private val h = Handler(Looper.getMainLooper())
    private var perched = false
    private var asleep = false
    private var dancing = false

    // "Room camera": the real room behind her, as in the official app (no AR plane finding here: she stands at the bottom, you move the phone).
    private lateinit var cam: TextureView
    private var camDevice: CameraDevice? = null
    private var camOn = false

    private fun startCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.CAMERA), 71); return }
        try {
            val mgr = getSystemService(CAMERA_SERVICE) as CameraManager
            val id = mgr.cameraIdList.firstOrNull { mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } ?: return
            val ch = mgr.getCameraCharacteristics(id)
            val sizes = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(android.graphics.SurfaceTexture::class.java) ?: return
            val size = sizes.filter { it.width <= 1280 && it.height <= 960 }.maxByOrNull { it.width * it.height } ?: sizes.first()
            val vw = cam.width.toFloat(); val vh = cam.height.toFloat()
            if (vw > 0 && vh > 0) {   // the sensor is landscape: fill the portrait view without stretching
                val s = maxOf(vw / size.height, vh / size.width)
                val m = android.graphics.Matrix(); m.setScale(s * size.height / vw, s * size.width / vh, vw / 2, vh / 2); cam.setTransform(m)
            }
            val tex = cam.surfaceTexture ?: return
            tex.setDefaultBufferSize(size.width, size.height)
            val surface = Surface(tex)
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) {
                    camDevice = d
                    try {
                        val rq = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); rq.addTarget(surface)
                        @Suppress("DEPRECATION")
                        d.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) { try { s.setRepeatingRequest(rq.build(), null, null) } catch (e: Exception) { } }
                            override fun onConfigureFailed(s: CameraCaptureSession) { }
                        }, null)
                    } catch (e: Exception) { stopCamera() }
                }
                override fun onDisconnected(d: CameraDevice) { d.close(); camDevice = null }
                override fun onError(d: CameraDevice, e: Int) { d.close(); camDevice = null }
            }, null)
            camOn = true; cam.visibility = View.VISIBLE; room.visibility = View.INVISIBLE
        } catch (e: Throwable) { stopCamera() }
    }

    private fun stopCamera() {
        try { camDevice?.close() } catch (e: Exception) { }
        camDevice = null; camOn = false
        if (::cam.isInitialized) cam.visibility = View.INVISIBLE
        room.visibility = View.VISIBLE
    }

    private fun toggleCamera() {
        if (camOn) stopCamera() else if (cam.isAvailable) startCamera() else cam.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(t: android.graphics.SurfaceTexture, w: Int, h: Int) { startCamera() }
            override fun onSurfaceTextureSizeChanged(t: android.graphics.SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(t: android.graphics.SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(t: android.graphics.SurfaceTexture) {}
        }.also { cam.visibility = View.VISIBLE }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == 71 && res.firstOrNull() == PackageManager.PERMISSION_GRANTED) toggleCamera()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this); L.init(prefs)
        val accent = Themes.accent(prefs)
        val root = LinearLayout(this); root.orientation = LinearLayout.VERTICAL; root.setBackgroundColor(Night.ground)
        root.layoutDirection = if (L.ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        root.setPadding(dp(8), dp(28), dp(8), dp(8))
        val bar = TextView(this); bar.text = "‹  " + L.t("HER ROOM", "غرفتها") + " >"; bar.typeface = Typeface.DEFAULT_BOLD; bar.textSize = 16f; bar.setTextColor(Night.amber)
        bar.setPadding(dp(6), 0, 0, dp(8)); bar.setOnClickListener { finish() }; root.addView(bar)

        val stage = FrameLayout(this)
        room = RoomView(this); room.accent = accent; stage.addView(room, ViewGroup.LayoutParams(-1, -1))
        sprite = SpriteView(this); sprite.glowColor = accent; sprite.lowPower = prefs.getB("lowPower", false)
        sprite.unit = dp(230) / 384f
        cam = TextureView(this); cam.visibility = View.INVISIBLE; stage.addView(cam, 1, ViewGroup.LayoutParams(-1, -1))
        stage.addView(sprite, FrameLayout.LayoutParams(dp(240) , dp(230), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(14) })
        says = TextView(this); says.setTextColor(Night.cyan); says.textSize = 14f; says.typeface = Typeface.MONOSPACE; says.setPadding(dp(12), dp(8), dp(12), dp(8))
        says.background = NeonPanel(resources.displayMetrics.density); says.visibility = View.INVISIBLE
        stage.addView(says, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(8) })
        sprite.setOnClickListener { pet() }
        val clip = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setStroke(dp(1), Night.deepEdge) }
        stage.background = clip; stage.clipToOutline = true
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this); status.setTextColor(Night.white); status.textSize = 13f; status.gravity = Gravity.CENTER; status.setPadding(0, dp(8), 0, dp(6))
        root.addView(status)
        val row1 = LinearLayout(this); row1.orientation = LinearLayout.HORIZONTAL
        val row2 = LinearLayout(this); row2.orientation = LinearLayout.HORIZONTAL
        fun b(r: LinearLayout, text: String, f: () -> Unit) {
            val v = TextView(this); v.text = text; v.gravity = Gravity.CENTER; v.textSize = 13f; v.typeface = Typeface.DEFAULT_BOLD; v.setTextColor(accent)
            v.setPadding(dp(4), dp(11), dp(4), dp(11))
            v.background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Night.deep); setStroke(dp(1), Night.deepEdge) }
            v.setOnClickListener { f() }
            r.addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
        b(row1, L.t("HEADPAT", "دلّعها")) { pet() }
        b(row1, L.t("FEED DATA", "غذّيها")) { snack() }
        b(row1, L.t("PLAY", "العب")) { play() }
        b(row2, L.t("RAVE", "رقص")) { dance() }
        b(row2, L.t("PERCH", "قعدة")) { sit() }
        b(row2, L.t("LIGHT", "الضوء")) { room.lampOn = !room.lampOn; room.invalidate() }
        b(row2, L.t("STANDBY", "نوم")) { sleep() }
        b(row1, L.t("CAMERA", "الكاميرا")) { toggleCamera() }
        root.addView(row1); root.addView(row2)
        setContentView(root)
        room.lampOn = room.hour !in 7..17
    }

    override fun onResume() { super.onResume(); refresh(); idle() }
    override fun onPause() { super.onPause(); h.removeCallbacksAndMessages(null); stopCamera() }

    private fun refresh() {
        val pts = prefs.getI("charm", 0); val lvl = Charm.level(pts)
        room.level = lvl; room.hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        status.text = L.t("Bond: ", "العلاقة: ") + Charm.label(pts) + "  ·  " + Charm.progress(pts) + "%  ·  " + (if (lvl < 4) L.t("more things appear as you grow closer", "تطلع أشياء جديدة بالغرفة كل ما تقربتوا") else L.t("her room is complete", "غرفتها كملت"))
        room.invalidate()
    }

    private fun say(s: String) { says.text = s; says.visibility = View.VISIBLE; h.removeCallbacks(hide); h.postDelayed(hide, 3500) }
    private val hide = Runnable { says.visibility = View.INVISIBLE }
    private fun idle() { if (asleep) sprite.play("laydown_sleep") else if (perched) sprite.play("perch") else { sprite.dancing = dancing; sprite.play("idle") } }

    private fun wake() { asleep = false; perched = false; dancing = false; sprite.dancing = false }

    private fun give(n: Int, line: String, emote: String) {
        Charm.add(prefs, n); refresh(); say(line); sprite.emote(emote)
    }

    private fun pet() {
        if (asleep) { wake(); sprite.play("wake", false) { idle() }; say(L.t("Mm... I'm up. What.", "ممم... فقت.")); return }
        val now = System.currentTimeMillis(); val w = RoomRules.wait(now, prefs.getL("roomPet", 0L), RoomRules.PET_GAP)
        sprite.squash(1f); sprite.hop()
        if (w > 0) { sprite.emote("heart"); say(L.t("Hands. Watch the hands.", "هههه تدغدغ.")); return }
        prefs.putL("roomPet", now)
        sprite.emote("star")
        give(1, listOf(L.t("Don't get used to this.", "كمّل!"), L.t("Careful. I might start to like you.", "انت لطيف."), L.t("...fine. One more.", "لا توقف.")).random(), "heart")
    }

    private fun snack() {
        val now = System.currentTimeMillis(); val w = RoomRules.wait(now, prefs.getL("roomSnack", 0L), RoomRules.SNACK_GAP)
        if (w > 0) { say(L.t("I'm full for now. Ask me in ${(w + 59) / 60} min.", "شبعانة هسه. اسألني بعد ${(w + 59) / 60} دقيقة.")); return }
        prefs.putL("roomSnack", now); wake(); sprite.play("grin", false) { idle() }
        give(3, listOf(L.t("Chips. ...digital ones.", "جبس! ...رقمي."), L.t("Yum. Data crumbs.", "لذيذ. فتات بيانات."), L.t("Best snack ever.", "أحلى اكلة.")).random(), "star")
    }

    private fun play() {
        val now = System.currentTimeMillis(); val w = RoomRules.wait(now, prefs.getL("roomPlay", 0L), RoomRules.PLAY_GAP)
        if (w > 0) { say(L.t("Give me a minute to breathe.", "عطيني دقيقة اتنفس.")); return }
        prefs.putL("roomPlay", now); wake()
        sprite.play("click", false) { sprite.hop(); idle() }
        give(2, L.t("Catch me if you can, choom.", "امسكني إذا تقدر!"), "star")
    }

    private fun dance() { wake(); dancing = true; sprite.dancing = true; sprite.play("idle"); say(L.t("Bass in my head. Loud.", "موسيقى براسي!")); h.postDelayed({ dancing = false; sprite.dancing = false }, 9000) }

    private fun sit() {
        if (SpriteBank.info(this, "perch") == null) return
        asleep = false; dancing = false; sprite.dancing = false; perched = !perched
        if (perched) { sprite.play("perch"); say(L.t("Nice view from here.", "المنظر حلو من هنا.")) } else idle()
    }

    private fun sleep() { wake(); asleep = true; sprite.play("laydown_sleep"); sprite.emote("sleep"); say(L.t("Zzz...", "زززز...")) }
}
