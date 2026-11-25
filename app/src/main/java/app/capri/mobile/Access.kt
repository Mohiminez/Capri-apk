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

        fun canWriteSecure(ctx: android.content.Context) =
            ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED

        // Android sometimes marks a service "currently unavailable" (after it was killed or crashed) and ignores the on/off switch.
        // Rewriting the list of enabled services clears that state: the same thing you do by switching it off and on by hand,
        // but without you having to. Needs the one-time permission WRITE_SECURE_SETTINGS (see grantCommand).
        fun revive(ctx: android.content.Context): Boolean {
            if (instance != null || !canWriteSecure(ctx)) return false
            return try {
                val cr = ctx.contentResolver
                val me = ctx.packageName + "/" + CapriAccess::class.java.name
                val all = (android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").split(':').filter { it.isNotBlank() }
                val others = all.filter { it != me && !it.startsWith(ctx.packageName + "/") }
                android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others.joinToString(":"))
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (others + me).joinToString(":"))
                        android.provider.Settings.Secure.putInt(cr, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                    } catch (e: Throwable) { }
                }, 800)
                Diag.log("access", "revived through secure settings")
                true
            } catch (e: Throwable) { false }
        }

        fun grantCommand(ctx: android.content.Context) = "adb shell pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS"

        /** For rooted phones: give ourselves that permission without a computer. */
        fun grantWithRoot(ctx: android.content.Context): Boolean = try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm grant ${ctx.packageName} android.permission.WRITE_SECURE_SETTINGS"))
            p.waitFor(6, TimeUnit.SECONDS) && p.exitValue() == 0 && canWriteSecure(ctx)
        } catch (e: Throwable) { false }

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

    /** Flat edges in the app on screen she can sit on: the top edge of wide, visible boxes (toolbars, cards, images, list rows). Main thread safe. */
    fun ledges(): List<android.graphics.Rect> {
        val root = try { rootInActiveWindow } catch (e: Throwable) { null } ?: return emptyList()
        if (root.packageName?.toString() == packageName) return emptyList()
        val dm = resources.displayMetrics
        val out = ArrayList<android.graphics.Rect>()
        var seen = 0
        fun go(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 30 || seen > 500) return
            seen++
            if (n.isVisibleToUser) {
                val r = android.graphics.Rect(); n.getBoundsInScreen(r)
                if (r.width() >= dm.widthPixels * 0.3f && r.height() >= 24 * dm.density && r.top > 90 * dm.density && r.top < dm.heightPixels - 220 * dm.density &&
                    out.none { Math.abs(it.top - r.top) < 14 * dm.density && it.left < r.right && r.left < it.right }) out.add(r)
            }
            for (i in 0 until n.childCount) go(n.getChild(i), depth + 1)
        }
        go(root, 0)
        return out
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

    class ShotPixels(val px: IntArray, val w: Int, val h: Int, val k: Float)   // k = real screen pixels per pixel here

    // The screen as raw pixels, shrunk to about maxW wide, for her own board reading (nothing leaves the phone). Android 11+.
    fun shotPixels(maxW: Int = 520): ShotPixels? {
        if (Build.VERSION.SDK_INT < 30) return null
        var result: ShotPixels? = null
        val latch = CountDownLatch(1)
        takeScreenshot(Display.DEFAULT_DISPLAY, shots, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                try {
                    val hw = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                    if (hw != null) {
                        val soft = hw.copy(Bitmap.Config.ARGB_8888, false)
                        val scale = if (soft.width > maxW) maxW.toFloat() / soft.width else 1f
                        val small = if (scale < 1f) Bitmap.createScaledBitmap(soft, maxW, (soft.height * scale).toInt(), true) else soft
                        val px = IntArray(small.width * small.height)
                        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
                        result = ShotPixels(px, small.width, small.height, 1f / scale)
                    }
                    screenshot.hardwareBuffer.close()
                } catch (e: Throwable) { }
                latch.countDown()
            }
            override fun onFailure(errorCode: Int) { latch.countDown() }
        })
        latch.await(6, TimeUnit.SECONDS)
        return result
    }

    /** One look at the screen for the game player: the JPEG (optionally with a labelled grid, Set-of-Mark style: cells A1, B1 ...),
     *  a small pixel copy for local colour tests, and the scale back to real screen pixels. */
    class Seen(val b64: String, val frame: Reflex.Frame, val scale: Float, val cols: Int, val rows: Int, val cellW: Float, val cellH: Float, val clean: Bitmap? = null)

    fun shotSee(grid: Boolean, keepClean: Boolean = false): Seen? {
        if (Build.VERSION.SDK_INT < 30) return null
        var result: Seen? = null
        val latch = CountDownLatch(1)
        takeScreenshot(Display.DEFAULT_DISPLAY, shots, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                try {
                    val hw = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                    if (hw != null) {
                        val soft = hw.copy(Bitmap.Config.ARGB_8888, true)
                        val sc = if (soft.width > 720) 720f / soft.width else 1f
                        val big = if (sc < 1f) Bitmap.createScaledBitmap(soft, 720, (soft.height * sc).toInt(), true) else soft
                        val tiny = Bitmap.createScaledBitmap(big, 144, (big.height * 144f / big.width).toInt().coerceAtLeast(2), true)
                        val px = IntArray(tiny.width * tiny.height); tiny.getPixels(px, 0, tiny.width, 0, 0, tiny.width, tiny.height)
                        val frame = Reflex.Frame(px, tiny.width, tiny.height)
                        val cols = 8; val cellW = big.width / cols.toFloat(); val rows = (big.height / cellW).toInt().coerceAtLeast(1); val cellH = big.height / rows.toFloat()
                        // a copy without the yellow grid, for reading the words on the screen
                        val clean = if (keepClean) big.copy(Bitmap.Config.ARGB_8888, false) else null
                        val canvasBmp = if (big.isMutable) big else big.copy(Bitmap.Config.ARGB_8888, true)
                        if (grid) {
                            val c = android.graphics.Canvas(canvasBmp)
                            val line = android.graphics.Paint().apply { color = 0x66FFFF00; strokeWidth = 1f }
                            val txt = android.graphics.Paint().apply { color = 0xCCFFFF00.toInt(); textSize = cellW * 0.2f; isAntiAlias = true; setShadowLayer(2f, 0f, 0f, 0xFF000000.toInt()) }
                            for (i in 1 until cols) c.drawLine(i * cellW, 0f, i * cellW, canvasBmp.height.toFloat(), line)
                            for (j in 1 until rows) c.drawLine(0f, j * cellH, canvasBmp.width.toFloat(), j * cellH, line)
                            for (j in 0 until rows) for (i in 0 until cols) c.drawText(('A' + i).toString() + (j + 1), i * cellW + 3f, j * cellH + txt.textSize, txt)
                        }
                        val bos = ByteArrayOutputStream()
                        canvasBmp.compress(Bitmap.CompressFormat.JPEG, 70, bos)
                        result = Seen(Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP), frame, 1f / sc, cols, rows, cellW, cellH, clean)
                        lastShotScale = 1f / sc
                    }
                    screenshot.hardwareBuffer.close()
                } catch (e: Throwable) { }
                latch.countDown()
            }
            override fun onFailure(errorCode: Int) { latch.countDown() }
        })
        latch.await(6, TimeUnit.SECONDS)
        return result
    }

    /** Buttons and texts the system itself reports (not guessed from pixels): "label @x,y" in the same pixels as the screenshot sent. */
    fun controlsList(scale: Float, max: Int = 14): List<String> {
        dump()
        val r = android.graphics.Rect(); val out = ArrayList<String>()
        for (n in nodes) {
            if (n.label.isBlank() || out.size >= max) continue
            try { n.node.getBoundsInScreen(r) } catch (e: Exception) { continue }
            if (r.width() <= 0) continue
            out.add(n.label.take(24) + " @" + (r.centerX() / scale).toInt() + "," + (r.centerY() / scale).toInt())
        }
        return out
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

    // The words on whatever sits under a screen point (all the controls that contain it), so a blind tap can be checked first.
    fun labelAt(x: Int, y: Int): String {
        dump()
        val r = android.graphics.Rect()
        val out = ArrayList<String>()
        for (n in nodes) {
            try { n.node.getBoundsInScreen(r) } catch (e: Exception) { continue }
            if (r.contains(x, y) && n.label.isNotBlank()) out.add(n.label)
        }
        return out.joinToString(" | ")
    }

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

    /** One finger of a multi-touch move: a press (x1==x2, y1==y2) or a drag, starting [start] ms into the batch and lasting [dur] ms. */
    class Finger(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val start: Long, val dur: Long)

    /** Several fingers at once (for example a joystick thumb and a fire button), sent as one gesture; waits until it has played. */
    fun fingers(list: List<Finger>): Boolean {
        if (list.isEmpty()) return false
        return try {
            val b = GestureDescription.Builder()
            for (f in list.take(6)) {
                val p = Path().apply { moveTo(f.x1.toFloat(), f.y1.toFloat()); if (f.x1 != f.x2 || f.y1 != f.y2) lineTo(f.x2.toFloat(), f.y2.toFloat()) else lineTo(f.x1 + 0.5f, f.y1.toFloat()) }
                b.addStroke(GestureDescription.StrokeDescription(p, f.start.coerceAtLeast(0), f.dur.coerceIn(30, 4000)))
            }
            val latch = CountDownLatch(1)
            val sent = dispatchGesture(b.build(), object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { latch.countDown() }
                override fun onCancelled(g: GestureDescription?) { latch.countDown() }
            }, null)
            if (sent) latch.await(6, TimeUnit.SECONDS)
            sent
        } catch (e: Throwable) { false }
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
        // never type into a password box, whoever asked
        if (target.isPassword) return false
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

    // What the focused text box calls itself (its hint or description), to tell a search bar from a message box.
    fun focusedLabel(): String {
        val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return ""
        val hint = if (Build.VERSION.SDK_INT >= 26) f.hintText?.toString() ?: "" else ""
        return (hint + " " + (f.contentDescription ?: "") + " " + (f.viewIdResourceName ?: "")).lowercase()
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

    fun cursorVisible(v: Boolean) { ui.post { cursor?.visibility = if (v) android.view.View.VISIBLE else android.view.View.INVISIBLE } }

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
