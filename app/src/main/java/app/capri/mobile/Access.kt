package app.capri.mobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ScreenNode(val label: String, val kind: String, val x: Int, val y: Int, val node: AccessibilityNodeInfo)

// Her hands and eyes on the phone: reads the screen, taps, types, scrolls, takes screenshots. Only works once the user turns it on in Settings.
class CapriAccess : AccessibilityService() {
    companion object {
        @Volatile var instance: CapriAccess? = null
        @Volatile var foreground: String = ""
        @Volatile var onForeground: ((String) -> Unit)? = null
        @Volatile var onScrolled: ((String) -> Unit)? = null
        // The screenshot is shrunk to 720 px wide; this is how many real pixels each of its pixels is.
        @Volatile var lastShotScale = 1f

        // Is Capri switched ON in Android's accessibility settings (whether or not it is really connected)?
        fun enabledInSettings(ctx: android.content.Context): Boolean {
            val s = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return s.split(':').any { it.startsWith(ctx.packageName + "/") }
        }

        private var lastRebind = 0L

        // "On but not working": Android lists the service as enabled yet never connected it. Turning the component off and on again
        // makes the system bind it, the same as you switching it off and on by hand. Returns true if it tried.
        fun rebind(ctx: android.content.Context): Boolean {
            val now = System.currentTimeMillis()
            if (instance != null || !enabledInSettings(ctx) || now - lastRebind < 20_000L) return false
            lastRebind = now
            return try {
                val pm = ctx.packageManager
                val cn = android.content.ComponentName(ctx, CapriAccess::class.java)
                pm.setComponentEnabledSetting(cn, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
                Handler(Looper.getMainLooper()).postDelayed({
                    try { pm.setComponentEnabledSetting(cn, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, android.content.pm.PackageManager.DONT_KILL_APP) } catch (e: Throwable) { }
                }, 700)
                Diag.log("access", "reconnect attempt")
                true
            } catch (e: Throwable) { false }
        }
    }

    @Volatile private var nodes: List<ScreenNode> = emptyList()
    private val ui = Handler(Looper.getMainLooper())
    private val shots = Executors.newSingleThreadExecutor()
    private var cursor: ImageView? = null
    private var cursorLp: WindowManager.LayoutParams? = null

    override fun onCreate() { super.onCreate(); instance = this }
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // An error thrown here makes Android quietly stop the service while Settings still shows it as ON
        // (the "it's on but doesn't work until I switch it off and on" problem), so nothing may escape this method.
        try {
            if (instance == null) instance = this
            if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val pkg = event.packageName?.toString() ?: return
                // Her own windows and the system UI aren't "the app they're in".
                if (pkg != packageName && pkg != "com.android.systemui") {
                    if (pkg != foreground) { foreground = pkg; onForeground?.invoke(pkg) }
                }
            } else if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                event.packageName?.toString()?.let { if (it != packageName) onScrolled?.invoke(it) }
            }
        } catch (e: Throwable) {
            try { CrashLog.record(this, e, "accessibility event") } catch (x: Throwable) { }
        }
    }

    fun foregroundIsGame(): Boolean {
        val pkg = foreground
        if (pkg.isEmpty()) return false
        return try { packageManager.getApplicationInfo(pkg, 0).category == android.content.pm.ApplicationInfo.CATEGORY_GAME } catch (e: Exception) { false }
    }
    override fun onInterrupt() {}

    // ---- eyes ----

    fun dump(maxChars: Int = 3500): String {
        var root = rootInActiveWindow
        var tries = 0
        // Right after an app opens the window can take a moment; wait a little (only off the main thread).
        while (root == null && tries < 5 && Looper.myLooper() != Looper.getMainLooper()) {
            try { Thread.sleep(300) } catch (e: InterruptedException) { }
            root = rootInActiveWindow
            tries++
        }
        if (root == null) return "(nothing readable on screen)"
        val list = ArrayList<ScreenNode>()
        walk(root, list, 0)
        nodes = list
        val sb = StringBuilder("App: ").append(root.packageName).append('\n')
        for ((i, n) in list.withIndex()) {
            val line = "[$i] ${n.kind} \"${n.label.replace('\n', ' ').take(60)}\" @(${n.x},${n.y})\n"
            if (sb.length + line.length > maxChars) break
            sb.append(line)
        }
        return sb.toString()
    }

    private fun walk(n: AccessibilityNodeInfo?, out: MutableList<ScreenNode>, depth: Int) {
        if (n == null || out.size >= 150 || depth > 40) return
        if (n.isVisibleToUser) {
            var label = (n.text?.toString() ?: n.contentDescription?.toString() ?: "").trim()
            if (label.isEmpty()) label = n.viewIdResourceName?.substringAfterLast('/') ?: ""
            val editable = n.isEditable
            if (n.isPassword) label = "(password field, hidden)"
            if (label.isNotEmpty() || editable) {
                val r = android.graphics.Rect()
                n.getBoundsInScreen(r)
                val kind = when {
                    editable -> "input"
                    n.isClickable -> "button"
                    else -> "text"
                }
                if (r.width() > 0 && r.height() > 0) out.add(ScreenNode(label, kind, r.centerX(), r.centerY(), n))
            }
        }
        for (i in 0 until n.childCount) walk(n.getChild(i), out, depth + 1)
    }

    // A JPEG of the screen as base64, or null (needs Android 11+). Blocks up to 6 seconds.
    fun shotBlocking(): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        var result: String? = null
        val latch = CountDownLatch(1)
        takeScreenshot(Display.DEFAULT_DISPLAY, shots, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                try {
                    val hw = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                    if (hw != null) {
                        val soft = hw.copy(Bitmap.Config.ARGB_8888, false)
                        val scale = 720f / soft.width
                        lastShotScale = if (scale < 1f) 1f / scale else 1f
                        val small = if (scale < 1f) Bitmap.createScaledBitmap(soft, 720, (soft.height * scale).toInt(), true) else soft
                        val bos = ByteArrayOutputStream()
                        small.compress(Bitmap.CompressFormat.JPEG, 70, bos)
                        result = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
                    }
                    screenshot.hardwareBuffer.close()
                } catch (e: Exception) { }
                latch.countDown()
            }
            override fun onFailure(errorCode: Int) { latch.countDown() }
        })
        latch.await(6, TimeUnit.SECONDS)
        return result
    }

    // ---- hands (call these from a background thread: they wait for her cursor to arrive) ----

    fun labelOf(i: Int): String = nodes.getOrNull(i)?.label ?: ""

    fun clickIndex(i: Int): Boolean {
        val n = nodes.getOrNull(i) ?: return false
        return clickNode(n)
    }

    fun clickText(text: String): Boolean {
        dump()
        val q = text.lowercase().trim()
        if (q.isEmpty()) return false
        val hit = nodes.firstOrNull { it.label.lowercase() == q } ?: nodes.firstOrNull { it.label.lowercase().contains(q) } ?: return false
        return clickNode(hit)
    }

    // Where something is on the screen (for "circle the send button"). Uses the button around the text when there is one.
    fun boundsOf(text: String): android.graphics.Rect? {
        dump()
        val q = text.lowercase().trim()
        if (q.isEmpty()) return null
        val hit = nodes.firstOrNull { it.label.lowercase() == q } ?: nodes.firstOrNull { it.label.lowercase().contains(q) } ?: return null
        var cur: AccessibilityNodeInfo? = hit.node
        var best: AccessibilityNodeInfo = hit.node
        var hops = 0
        while (cur != null && hops < 3) {
            if (cur.isClickable) { best = cur; break }
            cur = cur.parent
            hops++
        }
        val r = android.graphics.Rect()
        best.getBoundsInScreen(r)
        return if (r.width() > 0 && r.height() > 0) r else null
    }

    private fun clickNode(n: ScreenNode): Boolean {
        point(n.x, n.y)
        var cur: AccessibilityNodeInfo? = n.node
        var hops = 0
        while (cur != null && hops < 6) {
            if (cur.isClickable && cur.isEnabled && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur.parent
            hops++
        }
        return tap(n.x, n.y)
    }

    fun tap(x: Int, y: Int): Boolean {
        val p = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 70)).build(), null, null)
    }

    fun longPress(i: Int): Boolean {
        val n = nodes.getOrNull(i) ?: return false
        point(n.x, n.y)
        val p = Path().apply { moveTo(n.x.toFloat(), n.y.toFloat()); lineTo(n.x + 1f, n.y.toFloat()) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 800)).build(), null, null)
    }

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Long = 350): Boolean {
        val p = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build(), null, null)
    }

    // scroll_down reveals content further down the page (the finger moves up).
    fun scroll(down: Boolean): Boolean {
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        point(w / 2, h / 2)
        return if (down) swipe(w / 2, h * 72 / 100, w / 2, h * 30 / 100) else swipe(w / 2, h * 30 / 100, w / 2, h * 72 / 100)
    }

    // Types into the box that has focus, or the first text box on screen. append = keep what's already written.
    fun typeText(text: String, append: Boolean = true): Boolean {
        var target: AccessibilityNodeInfo? = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        var spot: ScreenNode? = null
        if (target == null || !target.isEditable) {
            dump()
            spot = nodes.firstOrNull { it.kind == "input" }
            target = spot?.node
        }
        if (target == null) return false
        if (spot != null) point(spot.x, spot.y)
        if (!target.isFocused) {
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            try { Thread.sleep(250) } catch (e: InterruptedException) { }
        }
        val old = if (append && !target.isShowingHintText) (target.text?.toString() ?: "") else ""
        val args = Bundle().putCharSequenceArg(old + text)
        if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
        // Some apps refuse direct text: paste it instead.
        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(android.content.ClipData.newPlainText("Capri", text))
        return target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun Bundle.putCharSequenceArg(text: String): Bundle {
        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return this
    }

    fun submit(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    fun global(name: String): Boolean = when (name) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "notifs" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        "settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        "shot" -> Build.VERSION.SDK_INT >= 28 && performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        "lock" -> Build.VERSION.SDK_INT >= 28 && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        else -> false
    }

    // Waits until the given app is the one on screen.
    fun waitFor(pkg: String, ms: Long): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (foreground == pkg || rootInActiveWindow?.packageName?.toString() == pkg) return true
            try { Thread.sleep(200) } catch (e: InterruptedException) { return false }
        }
        return false
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    // ---- her pointer ----

    // Slides her cursor to the spot and waits a moment, so you can watch where she is about to tap.
    fun point(x: Int, y: Int) {
        ui.post { slideCursor(x, y) }
        try { Thread.sleep(520) } catch (e: InterruptedException) { }
    }

    private fun slideCursor(x: Int, y: Int) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (cursor == null) {
            val iv = ImageView(this)
            iv.setImageBitmap(assets.open("cursor.png").use { BitmapFactory.decodeStream(it) })
            val size = (resources.displayMetrics.density * 44).toInt()
            val lp = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT)
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = resources.displayMetrics.widthPixels / 2
            lp.y = resources.displayMetrics.heightPixels / 2
            cursor = iv
            cursorLp = lp
            wm.addView(iv, lp)
        }
        val lp = cursorLp ?: return
        val iv = cursor ?: return
        val sx = lp.x
        val sy = lp.y
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 420
            addUpdateListener {
                val f = it.animatedValue as Float
                lp.x = (sx + (x - sx) * f).toInt()
                lp.y = (sy + (y - sy) * f).toInt()
                try { wm.updateViewLayout(iv, lp) } catch (e: Exception) { }
            }
            start()
        }
        ui.removeCallbacks(hideCursor)
        ui.postDelayed(hideCursor, 2500)
    }

    private val hideCursor = Runnable {
        val iv = cursor ?: return@Runnable
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(iv) } catch (e: Exception) { }
        cursor = null
        cursorLp = null
    }
}
