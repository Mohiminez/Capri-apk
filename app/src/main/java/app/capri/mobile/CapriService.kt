package app.capri.mobile

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

class CapriService : Service(), Actions.Hooks {
    companion object {
        @Volatile var running = false
        const val ACT_HIDE = "app.capri.mobile.HIDE"
        const val ACT_STOP = "app.capri.mobile.STOP"
        const val ACT_ASK = "app.capri.mobile.ASK"
        const val ACT_QUIET = "app.capri.mobile.QUIET"
        @Volatile var notifier: ((String) -> Unit)? = null
        private const val NOTE_ID = 1
        private const val OVERLAY = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var brain: Brain
    private lateinit var agent: Agent
    private val h = Handler(Looper.getMainLooper())

    private var sprite: SpriteView? = null
    private lateinit var spriteLp: WindowManager.LayoutParams
    private var bubble: TextView? = null
    private lateinit var bubbleLp: WindowManager.LayoutParams
    private var panel: LinearLayout? = null
    private lateinit var panelLp: WindowManager.LayoutParams
    private var input: EditText? = null
    private var bubbleShown = false
    private var panelShown = false

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speakId = 0
    private var recognizer: SpeechRecognizer? = null

    private var asleep = false
    private var talking = false
    private var busy = false
    private var hidden = false
    private var dragging = false
    private var dancing = false
    private var screenOn = true
    private var convoActive = false
    private var dictating = false
    private var cloudListening = false
    private var missCount = 0
    private var pendingConfirm: (() -> Unit)? = null
    private var typingFull = ""
    private var typingPos = 0
    private var menu: LinearLayout? = null
    private var lastWater = System.currentTimeMillis()
    private var lastStretch = System.currentTimeMillis()
    private var lastLate = 0L
    private val quiet get() = prefs.getB("quiet", false)

    // movement: 0 resting, 1 thrown (gravity and bounces), 2 walking somewhere
    private var mode = 0
    private var fx = 0f
    private var fy = 0f
    private var vx = 0f
    private var vy = 0f
    private var walkTx = 0f
    private var walkTy = 0f
    private var lastNs = 0L

    private val screenW get() = resources.displayMetrics.widthPixels
    private val screenH get() = resources.displayMetrics.heightPixels
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        L.init(prefs)
        brain = Brain(this, prefs)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("capri", "Capri", NotificationManager.IMPORTANCE_LOW))
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACT_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        if (sprite == null) setup()
        if (intent?.action == ACT_HIDE) toggleHidden()
        if (intent?.action == ACT_QUIET) toggleQuiet()
        if (intent?.action == ACT_ASK) {
            val t = intent.getStringExtra("text") ?: ""
            if (t.isNotBlank()) h.postDelayed({
                if (hidden) toggleHidden()
                ask("(They selected this text in another app: \"$t\"\nIn one or two short lines: translate it if it isn't in their language, otherwise explain or sum it up.)", true)
            }, 700)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        h.removeCallbacksAndMessages(null)
        Choreographer.getInstance().removeFrameCallback(frame)
        ReminderReceiver.speaker = null
        notifier = null
        try { unregisterReceiver(events) } catch (e: Exception) { }
        Ears.cancel()
        hideMenu()
        if (::agent.isInitialized) agent.cancel()
        Voice.stop()
        try { if (panelShown) wm.removeView(panel) } catch (e: Exception) { }
        try { if (bubbleShown) wm.removeView(bubble) } catch (e: Exception) { }
        try { sprite?.let { wm.removeView(it) } } catch (e: Exception) { }
        tts?.shutdown()
        recognizer?.destroy()
        LocalLlm.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // She waves goodbye first.
    private fun shutdown() {
        val s = sprite
        if (s == null || hidden) { stopSelf(); return }
        hideBubble()
        hidePanel()
        mode = 0
        s.play("leave", false) { stopSelf() }
        h.postDelayed({ stopSelf() }, 1500)
    }

    // ---- notification ----

    private fun notification(): Notification {
        fun act(a: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, CapriService::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "capri")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (hidden) L.t("Capri is hidden", "كابري مخفية") else L.t("Capri is on your screen", "كابري على شاشتك"))
            .setContentText(L.t("Tap to open her settings", "اضغط لفتح الإعدادات"))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, if (hidden) L.t("Show", "إظهار") else L.t("Hide", "إخفاء"), act(ACT_HIDE, 1))
            .addAction(0, if (quiet) L.t("Unmute", "فك الصمت") else L.t("Quiet", "صمت"), act(ACT_QUIET, 3))
            .addAction(0, L.t("Stop", "إيقاف"), act(ACT_STOP, 2))
            .build()
    }

    private fun startFg() {
        val n = notification()
        if (Build.VERSION.SDK_INT >= 34) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            try {
                startForeground(NOTE_ID, n, type)
            } catch (e: Exception) {
                // e.g. started at boot, when the microphone type isn't allowed yet
                startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }
        } else {
            startForeground(NOTE_ID, n)
        }
    }

    // ---- setting her up ----

    private fun brainReady(): Boolean = brain.anyReady()

    private fun setup() {
        val hPx = dp(prefs.getI("size", 140))
        val wPx = hPx * 5 / 8
        val s = SpriteView(this)
        sprite = s
        spriteLp = WindowManager.LayoutParams(wPx, hPx, OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getI("x", screenW - wPx)
            y = prefs.getI("y", screenH * 45 / 100)
        }
        fx = spriteLp.x.toFloat()
        fy = spriteLp.y.toFloat()
        s.glowColor = Themes.accent(prefs)
        s.lowPower = prefs.getB("lowPower", false)
        s.bob = !s.lowPower
        s.setOnTouchListener(touch)
        s.alpha = 0f
        wm.addView(s, spriteLp)
        s.animate().alpha(1f).setDuration(400).start()

        val b = TextView(this)
        b.setTextColor(Color.parseColor("#F2F2F2"))
        b.textSize = 15f
        b.setPadding(dp(12), dp(9), dp(12), dp(9))
        b.maxWidth = (screenW * 0.7f).toInt()
        b.background = GradientDrawable().apply {
            setColor(Color.parseColor("#EC10141B"))
            setStroke(dp(2), Themes.accent(prefs))
            cornerRadius = dp(14).toFloat()
        }
        b.setOnClickListener { bubbleTapped() }
        bubble = b
        bubbleLp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        agent = Agent(this, brain, prefs,
            { line -> h.post { say(line, false) } },
            { line -> h.post { busy = false; say(line); settle() } })
        ReminderReceiver.speaker = { t -> h.post { say(t) } }
        notifier = { line -> h.post { if (!quiet && !hidden && !busy) say(line) } }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_BATTERY_LOW); addAction(Intent.ACTION_POWER_CONNECTED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(events, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(events, filter)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val loc = L.locale(prefs)
                tts?.language = loc
                // The phone's best installed voice for her language, not just the first one.
                try {
                    val best = tts?.voices?.filter { it.locale.language == loc.language && !it.features.contains("notInstalled") }?.maxByOrNull { it.quality }
                    if (best != null) tts?.voice = best
                } catch (e: Exception) { }
                tts?.setSpeechRate(1.08f * prefs.getI("ttsSpeed", 100) / 100f)
                tts?.setPitch(1.12f * prefs.getI("ttsPitch", 100) / 100f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { h.post { talkDone() } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { h.post { talkDone() } }
                })
                ttsReady = true
            }
        }

        s.play("arrive", false) { settle() }
        poke()
        h.postDelayed(blinkTask, 4000)
        h.postDelayed(wanderTask, 15_000L)
        h.postDelayed(musicTask, 5000)
        h.postDelayed(watchTask, 60_000L)
        h.postDelayed(checkInTask, 20 * 60_000L)
        h.postDelayed(careTask, 5 * 60_000L)
        h.postDelayed(mischiefTask, 30 * 60_000L)
        h.postDelayed(charmTask, 10 * 60_000L)
        h.postDelayed({
            if (!brainReady()) say(L.t("Hi. Open the Capri app and give me a brain first.", "هلا. افتح تطبيق كابري وأعطني دماغ أول."), false)
            else ask("(You just booted up on the user's phone. Say hi in one short line.)", true)
        }, 1200)
    }

    // ---- animation state ----

    private fun settle() {
        val s = sprite ?: return
        s.glow = talking && !s.lowPower
        s.glitchFx = busy || mode == 1
        when {
            dragging || mode == 1 -> s.play("dangle")
            mode == 2 -> s.play("walk")
            busy -> s.play("glitch")
            talking -> s.play("idle_mouth")
            asleep -> s.play("sleep")
            dancing -> s.play("dance")
            else -> s.play("idle")
        }
    }

    private val blinkTask = object : Runnable {
        override fun run() {
            val s = sprite
            if (s != null && !asleep && !talking && !busy && !dragging && !hidden && mode == 0 && s.current == "idle") {
                s.play("idle_blink", false) { settle() }
            }
            h.postDelayed(this, Random.nextLong(3500, 8000))
        }
    }

    private val sleepTask = Runnable {
        if (!busy && !talking && !agent.running) {
            asleep = true
            settle()
        }
    }

    private val musicTask = object : Runnable {
        override fun run() {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val now = prefs.getB("dance", true) && am.isMusicActive && !talking && !hidden
            if (now != dancing) {
                dancing = now
                if (!asleep) settle()
            }
            h.postDelayed(this, 3000)
        }
    }

    private fun idleOk() = !hidden && !busy && !asleep && !panelShown && !talking && screenOn && !quiet && !agent.running

    private val checkInTask = object : Runnable {
        override fun run() {
            if (prefs.getB("checkin", false) && idleOk()) {
                ask("(They haven't spoken to you in a while. Pop in with one short line of your own. Don't ask what's up.)", true)
            }
            h.postDelayed(this, Random.nextLong(20 * 60_000L, 45 * 60_000L))
        }
    }

    // Wellbeing nudges, with canned lines so they cost nothing.
    private val careTask = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (idleOk()) {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                if (prefs.getB("careWater", false) && now - lastWater > 90 * 60_000L) {
                    lastWater = now
                    say(listOf(L.t("Drink some water, choom. Your brain runs on it.", "اشرب شوية موية يا خوي، مخك يشتغل عليها."),
                        L.t("Hydrate. I mean it.", "اشرب موية. جاد.")).random())
                } else if (prefs.getB("careStretch", false) && now - lastStretch > 60 * 60_000L) {
                    lastStretch = now
                    say(listOf(L.t("You've been staring for a while. Stretch, blink, breathe.", "صار لك مدة تطالع بالشاشة. تمطّط وغمّض وتنفّس."),
                        L.t("Look at something far away for ten seconds.", "طالع شي بعيد عشر ثواني.")).random())
                } else if (prefs.getB("careLate", false) && hour in 1..4 && now - lastLate > 2 * 3600_000L) {
                    lastLate = now
                    say(L.t("It's very late. Sleep exists, you know.", "الوقت متأخر مرة. النوم موجود تدري."))
                }
            }
            h.postDelayed(this, 5 * 60_000L)
        }
    }

    // Mischief mode: a harmless fake glitch now and then.
    private val mischiefTask = object : Runnable {
        override fun run() {
            if (prefs.getB("mischief", false) && idleOk() && Random.nextInt(100) < 60) chaos()
            h.postDelayed(this, Random.nextLong(30 * 60_000L, 90 * 60_000L))
        }
    }

    private val charmTask = object : Runnable {
        override fun run() {
            if (screenOn && !hidden) Charm.add(prefs, 1)
            h.postDelayed(this, 10 * 60_000L)
        }
    }

    // Screen state, battery and charger.
    private val events = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; Voice.stop(); tts?.stop() }
                Intent.ACTION_SCREEN_ON -> screenOn = true
                Intent.ACTION_BATTERY_LOW -> if (prefs.getB("events", true) && idleOk())
                    say(L.t("You're nearly out of battery. Find a charger, gonk.", "البطارية تخلص. دوّر شاحن يا خوي."))
                Intent.ACTION_POWER_CONNECTED -> if (prefs.getB("events", true) && idleOk() && Random.nextInt(100) < 40)
                    say(L.t("Charging. Good.", "الشحن اشتغل. زين."), false)
            }
        }
    }

    private val watchTask = object : Runnable {
        override fun run() {
            val acc = CapriAccess.instance
            val game = prefs.getB("gameMode", false)
            val allowed = prefs.getB("watch", false) || (game && acc?.foregroundIsGame() == true)
            if (acc != null && allowed && screenOn && !hidden && !busy && !asleep && !panelShown && !quiet && !agent.running) {
                Thread {
                    val txt = acc.dump(if (brain.isLocal()) 2000 else 3000)
                    val img = if (prefs.getB("vision", false) && !brain.isLocal()) acc.shotBlocking() else null
                    h.post {
                        if (!busy && (img != null || txt.lines().size > 3)) {
                            ask("(You glance at their screen. What's on it:\n$txt\nIf something stands out, like a big play, a funny moment or something they're stuck on, react in one short line. If nothing stands out, reply with just NOTHING.)", true, img, true)
                        }
                    }
                }.start()
            }
            h.postDelayed(this, prefs.getI("watchSecs", 60) * 1000L)
        }
    }

    private fun poke() {
        h.removeCallbacks(sleepTask)
        h.postDelayed(sleepTask, 150_000L)
        if (asleep) {
            asleep = false
            sprite?.play("wake", false) { settle() }
        }
    }

    // ---- movement: throws, bounces and little walks ----

    private val choreo = Choreographer.getInstance()
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            step(frameTimeNanos)
            if (mode != 0) choreo.postFrameCallback(this)
        }
    }

    private fun startMotion(m: Int) {
        mode = m
        lastNs = 0L
        choreo.removeFrameCallback(frame)
        choreo.postFrameCallback(frame)
        settle()
    }

    private fun minX() = -spriteLp.width / 3f
    private fun maxX() = screenW - spriteLp.width * 2f / 3f

    private fun floorY(): Float {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val nav = if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
        return (screenH - spriteLp.height - nav - dp(6)).toFloat()
    }

    private fun step(ns: Long) {
        val s = sprite ?: return
        if (lastNs == 0L) { lastNs = ns; return }
        val dt = ((ns - lastNs) / 1e9f).coerceAtMost(0.05f)
        lastNs = ns
        val d = resources.displayMetrics.density / 2.75f
        if (mode == 1) {
            val floor = floorY()
            vy += 2400f * d * dt
            vx -= vx * 0.6f * dt
            fx += vx * dt
            fy += vy * dt
            if (fx < minX()) { fx = minX(); vx = -vx * 0.55f } else if (fx > maxX()) { fx = maxX(); vx = -vx * 0.55f }
            if (fy < dp(24).toFloat()) { fy = dp(24).toFloat(); vy = -vy * 0.5f }
            if (fy > floor) {
                fy = floor
                vy = -vy * 0.42f
                vx *= 0.72f
                if (abs(vy) < 300f * d) vy = 0f
            }
            if (fy >= floor - 1f && vy == 0f && abs(vx) < 40f * d) { mode = 0; settle() }
        } else if (mode == 2) {
            val dx = walkTx - fx
            val dy = walkTy - fy
            val dist = hypot(dx, dy)
            val sp = dp(55) * (1f + prefs.getI("wanderPace", 1) * 0.5f)
            if (dist < sp * dt + 2f) {
                fx = walkTx; fy = walkTy
                mode = 0
                settle()
            } else {
                fx += dx / dist * sp * dt
                fy += dy / dist * sp * dt
            }
        }
        spriteLp.x = fx.toInt()
        spriteLp.y = fy.toInt()
        try { wm.updateViewLayout(s, spriteLp) } catch (e: Exception) { }
        if (bubbleShown) placeBubble()
        if (mode == 0) { prefs.putI("x", spriteLp.x); prefs.putI("y", spriteLp.y) }
    }

    private fun canRoam() = !asleep && !talking && !busy && !dragging && !hidden && !panelShown && mode == 0 && !agent.running

    private val wanderTask = object : Runnable {
        override fun run() {
            val pace = prefs.getI("wanderPace", 1)
            if (pace > 0 && canRoam()) {
                val r = Random.nextInt(100)
                when {
                    r < 65 -> walkSomewhere()
                    r < 85 -> sprite?.play("grin", false) { settle() }
                    else -> { /* just stays put this time */ }
                }
            }
            h.postDelayed(this, Random.nextLong(10_000, 28_000) / maxOf(1, pace))
        }
    }

    private fun walkSomewhere() {
        fx = spriteLp.x.toFloat()
        fy = spriteLp.y.toFloat()
        walkTx = Random.nextFloat() * (maxX() - minX()) + minX()
        walkTy = (fy + Random.nextInt(-dp(120), dp(120))).coerceIn(dp(48).toFloat(), floorY())
        sprite?.flip = walkTx < fx
        startMotion(2)
    }

    // ---- touch: drag, throw, tap, long press ----

    private val touch = object : View.OnTouchListener {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var wasLong = false
        var tracker: VelocityTracker? = null
        val slop: Int by lazy { ViewConfiguration.get(this@CapriService).scaledTouchSlop }
        val longPress = Runnable {
            if (!moved) {
                wasLong = true
                convoActive = prefs.getB("convo", false)
                startListening()
            }
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            tracker?.addMovement(e)
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    mode = 0
                    choreo.removeFrameCallback(frame)
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain()
                    tracker?.addMovement(e)
                    downX = e.rawX; downY = e.rawY
                    startX = spriteLp.x; startY = spriteLp.y
                    moved = false; wasLong = false
                    h.postDelayed(longPress, 600)
                    poke()
                }
                MotionEvent.ACTION_MOVE -> {
                    val ox = e.rawX - downX
                    val oy = e.rawY - downY
                    if (!moved && (abs(ox) > slop || abs(oy) > slop)) {
                        moved = true
                        h.removeCallbacks(longPress)
                        dragging = true
                        settle()
                    }
                    if (moved) moveTo(startX + ox.toInt(), startY + oy.toInt())
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    h.removeCallbacks(longPress)
                    if (moved) {
                        dragging = false
                        tracker?.computeCurrentVelocity(1000)
                        val tvx = tracker?.xVelocity ?: 0f
                        val tvy = tracker?.yVelocity ?: 0f
                        if (hypot(tvx, tvy) > 900f * resources.displayMetrics.density / 2.75f) {
                            vx = tvx; vy = tvy
                            fx = spriteLp.x.toFloat(); fy = spriteLp.y.toFloat()
                            startMotion(1)
                        } else {
                            prefs.putI("x", spriteLp.x)
                            prefs.putI("y", spriteLp.y)
                            settle()
                        }
                    } else if (!wasLong && e.action == MotionEvent.ACTION_UP) {
                        sprite?.ripple(e.x, e.y)
                        tapped()
                    }
                    tracker?.recycle()
                    tracker = null
                }
            }
            return true
        }
    }

    private fun tapped() {
        if (cloudListening) {
            Ears.cancel()
            return
        }
        if (agent.running) {
            agent.cancel()
            say(L.t("Okay, stopping.", "تمام، وقفت."), false)
            return
        }
        convoActive = false
        sprite?.let { s ->
            s.animate().scaleX(1.07f).scaleY(0.93f).setDuration(70).withEndAction {
                s.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }.start()
            s.play("grin", false) { settle() }
        }
        if (panelShown) hidePanel() else showPanel()
    }

    private fun moveTo(x: Int, y: Int) {
        val s = sprite ?: return
        spriteLp.x = x.coerceIn(minX().toInt(), maxX().toInt())
        spriteLp.y = y.coerceIn(0, screenH - spriteLp.height)
        fx = spriteLp.x.toFloat()
        fy = spriteLp.y.toFloat()
        wm.updateViewLayout(s, spriteLp)
        if (bubbleShown) placeBubble()
    }

    // ---- bubble ----

    private fun placeBubble() {
        val b = bubble ?: return
        b.measure(View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val bw = b.measuredWidth
        val bh = b.measuredHeight
        bubbleLp.x = (spriteLp.x + spriteLp.width / 2 - bw / 2).coerceIn(dp(8), maxOf(dp(8), screenW - bw - dp(8)))
        var y = spriteLp.y - bh - dp(4)
        if (y < dp(24)) y = spriteLp.y + spriteLp.height
        bubbleLp.y = y
        if (bubbleShown) try { wm.updateViewLayout(b, bubbleLp) } catch (e: Exception) { }
    }

    private val hideBubbleTask = Runnable { pendingConfirm = null; hideBubble() }

    // Her words appear letter by letter; the layout is fixed from the start so the bubble doesn't jump.
    private val typeTask = object : Runnable {
        override fun run() {
            typingPos = minOf(typingFull.length, typingPos + 2)
            renderTyped()
            if (typingPos < typingFull.length) h.postDelayed(this, 28)
        }
    }

    private fun renderTyped() {
        val b = bubble ?: return
        val sp = SpannableString(typingFull)
        if (typingPos < typingFull.length) sp.setSpan(ForegroundColorSpan(Color.TRANSPARENT), typingPos, typingFull.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        b.text = sp
    }

    private fun bubbleTapped() {
        val yes = pendingConfirm
        if (yes != null) {
            pendingConfirm = null
            hideBubble()
            yes()
        } else if (typingPos < typingFull.length) {
            h.removeCallbacks(typeTask)
            typingPos = typingFull.length
            renderTyped()
        } else {
            hideBubble()
        }
    }

    private fun showBubble(text: String, ms: Long, typed: Boolean = true) {
        val b = bubble ?: return
        if (hidden) return
        h.removeCallbacks(typeTask)
        typingFull = text
        typingPos = if (typed && !(sprite?.lowPower ?: false)) 0 else text.length
        renderTyped()
        placeBubble()
        if (!bubbleShown) {
            b.alpha = 0f
            b.scaleX = 0.92f
            b.scaleY = 0.92f
            wm.addView(b, bubbleLp)
            bubbleShown = true
            b.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(140).start()
        } else {
            wm.updateViewLayout(b, bubbleLp)
        }
        if (typingPos < typingFull.length) h.postDelayed(typeTask, 28)
        h.removeCallbacks(hideBubbleTask)
        h.postDelayed(hideBubbleTask, ms)
    }

    private fun hideBubble() {
        h.removeCallbacks(hideBubbleTask)
        h.removeCallbacks(typeTask)
        if (bubbleShown) {
            try { wm.removeView(bubble) } catch (e: Exception) { }
            bubbleShown = false
        }
    }

    // ---- talking ----

    private fun say(text: String, speak: Boolean = true) {
        if (hidden || text.isBlank()) return
        showBubble(text, (text.length * 80L).coerceIn(5000L, 15000L))
        talking = false
        if (speak && !quiet && prefs.getB("voice", true)) {
            speakText(text)
        } else {
            talking = true
            settle()
            h.postDelayed({ talkDone() }, (text.length * 55L).coerceIn(1200L, 4500L))
        }
    }

    // After she finishes: back to idle, and in conversation mode straight back to listening.
    private fun talkDone() {
        talking = false
        settle()
        if (convoActive && !busy && !hidden && !dictating) {
            h.postDelayed({ if (convoActive && !talking && !busy && !cloudListening) startListening() }, 450)
        }
    }

    private fun phoneSpeak(text: String) {
        talking = true
        settle()
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "capri")
        } else {
            h.postDelayed({ talkDone() }, (text.length * 55L).coerceIn(1200L, 4500L))
        }
    }

    private fun speakText(text: String) {
        Voice.stop()
        tts?.stop()
        val id = ++speakId
        if (prefs.getI("ttsEngine", Voice.PHONE) == Voice.PHONE) {
            phoneSpeak(text)
            return
        }
        Thread {
            val err = Voice.speak(this, prefs, text) { h.post { if (id == speakId) { talking = true; settle() } } }
            h.post {
                if (id != speakId) return@post
                if (err == null) {
                    talkDone()
                } else {
                    Toast.makeText(this, L.t("Voice problem, using the phone voice: ", "مشكلة بالصوت، استخدم صوت الجوال: ") + err, Toast.LENGTH_LONG).show()
                    phoneSpeak(text)
                }
            }
        }.start()
    }

    private fun ask(text: String, hiddenPrompt: Boolean = false, image: String? = null, skipNothing: Boolean = false) {
        poke()
        if (!hiddenPrompt) {
            Charm.add(prefs, 2)
            val quick = QuickAsks.match(text)
            if (quick != null) {
                say(Actions.run(this, prefs, quick, this))
                return
            }
        }
        if (!brainReady()) {
            say(L.t("I need a brain first. Open the Capri app and pick one.", "أحتاج دماغ أول. افتح تطبيق كابري واختر واحد."), false)
            return
        }
        busy = true
        talking = false
        settle()
        if (!skipNothing) showBubble("...", 90_000L, false)
        brain.ask(text, hiddenPrompt, image) { reply, err ->
            busy = false
            when {
                reply != null && skipNothing && reply.trim().uppercase().startsWith("NOTHING") -> settle()
                reply != null -> say(Actions.run(this, prefs, reply, this))
                skipNothing -> settle()
                else -> say(L.t("Signal lost: ", "انقطعت الإشارة: ") + err, false)
            }
        }
    }

    // ---- jobs, sight, lookups (Actions.Hooks) ----

    override fun startJob(goal: String) {
        if (agent.running) return
        busy = true
        settle()
        agent.start(goal)
    }

    override fun look(question: String) {
        val acc = CapriAccess.instance
        if (acc == null) {
            say(L.t("Turn on my accessibility access in the Capri app first.", "شغّل صلاحية الوصول من تطبيق كابري أول."), false)
            return
        }
        busy = true
        settle()
        showBubble("...", 90_000L, false)
        Thread {
            val txt = acc.dump(if (brain.isLocal()) 2500 else 4000)
            val img = if (prefs.getB("vision", false) && !brain.isLocal()) acc.shotBlocking() else null
            h.post {
                busy = false
                ask("(You are looking at their screen right now. What's on it:\n$txt\nThey asked: $question\nAnswer from what you can see, in one or two short lines. Never read private messages out word for word.)", true, img)
            }
        }.start()
    }

    // Weather, Wikipedia and "what's playing": she fetches it, then tells you in her own words.
    override fun tool(name: String, arg: String) {
        busy = true
        settle()
        showBubble("...", 90_000L, false)
        Thread {
            val result = try {
                when (name) {
                    "weather" -> Tools.weather(arg)
                    "wiki" -> Tools.wiki(arg)
                    else -> Tools.nowPlaying(this)
                }
            } catch (e: Exception) {
                "The lookup failed: " + (e.message ?: "no connection")
            }
            h.post {
                busy = false
                ask("(Result of your lookup \"$name $arg\": $result\nTell them the answer in your own words, one or two short lines, in their language.)", true)
            }
        }.start()
    }

    // In Guarded mode she asks first: a bubble you tap to allow.
    override fun confirm(text: String, yes: () -> Unit) {
        pendingConfirm = yes
        showBubble(text + "\n\u2714 " + L.t("tap here to allow", "اضغط هنا للسماح"), 12_000L, false)
    }

    override fun chaos() {
        if (hidden) return
        val v = ChaosView(this, Themes.accent(prefs))
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT)
        try { wm.addView(v, lp) } catch (e: Exception) { return }
        h.postDelayed({ try { wm.removeView(v) } catch (e: Exception) { }; say(L.t("Gotcha.", "ضحكت عليك."), false) }, 1900)
    }

    override fun dictate() { startListening(true) }

    // ---- small menu next to her ----

    private fun hideMenu() {
        menu?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        menu = null
    }

    private fun showMenu(items: List<Pair<String, () -> Unit>>) {
        hideMenu()
        val m = LinearLayout(this)
        m.orientation = LinearLayout.VERTICAL
        m.layoutDirection = if (L.ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        m.background = GradientDrawable().apply {
            setColor(Color.parseColor("#F010141B"))
            setStroke(dp(2), Themes.accent(prefs))
            cornerRadius = dp(14).toFloat()
        }
        for ((label, action) in items) {
            val t = TextView(this)
            t.text = label
            t.textSize = 15f
            t.setTextColor(Color.WHITE)
            t.setPadding(dp(16), dp(11), dp(16), dp(11))
            t.setOnClickListener { hideMenu(); action() }
            m.addView(t)
        }
        m.measure(View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = (spriteLp.x - m.measuredWidth / 2).coerceIn(dp(8), maxOf(dp(8), screenW - m.measuredWidth - dp(8)))
        var y = spriteLp.y - m.measuredHeight
        if (y < dp(40)) y = spriteLp.y + spriteLp.height
        lp.y = y
        m.setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_OUTSIDE) hideMenu(); false }
        try { wm.addView(m, lp) } catch (e: Exception) { return }
        menu = m
        h.postDelayed({ if (menu === m) hideMenu() }, 12_000L)
    }

    private fun gamesMenu() {
        fun play(name: String, how: String) = Pair(name, { ask("(Start a quick game with the user: $how Keep it going turn by turn in this chat, one short message at a time. Begin now.)", true) })
        showMenu(listOf(
            play(L.t("\uD83E\uDDE0 Trivia", "\uD83E\uDDE0 أسئلة"), "trivia. Ask one question at a time with four options A to D, keep score."),
            play(L.t("\uD83D\uDD75 20 questions", "\uD83D\uDD75 ٢٠ سؤال"), "twenty questions. You secretly think of something and they guess by asking yes or no questions."),
            play(L.t("\u2702 Rock paper scissors", "\u2702 حجر ورقة مقص"), "rock paper scissors, best of five. Pick your move secretly each round and reveal it after theirs."),
            play(L.t("\uD83E\uDD14 Would you rather", "\uD83E\uDD14 تفضل ولا"), "would you rather, with funny cyberpunk dilemmas."),
            play(L.t("\uD83E\uDDE9 Riddles", "\uD83E\uDDE9 ألغاز"), "riddles. Give one riddle at a time and react to their answers.")
        ))
    }

    private fun mainMenu() {
        showMenu(listOf(
            L.t("\uD83C\uDFAE Play a game", "\uD83C\uDFAE العب لعبة") to { gamesMenu() },
            L.t("\u2328 Dictate into a text box", "\u2328 أملي على خانة نص") to { startListening(true) },
            L.t("\uD83D\uDDE3 Conversation mode", "\uD83D\uDDE3 وضع المحادثة") to { convoActive = true; startListening() },
            (if (quiet) L.t("\uD83D\uDD0A Unmute", "\uD83D\uDD0A فك الصمت") else L.t("\uD83E\uDD2B Quiet mode", "\uD83E\uDD2B وضع الصمت")) to { toggleQuiet() },
            L.t("\u26A1 Prank me", "\u26A1 مقلب") to { chaos() }
        ))
    }

    // ---- typing ----

    private fun buildPanel() {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.HORIZONTAL
        p.gravity = Gravity.CENTER_VERTICAL
        p.layoutDirection = if (L.ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        p.setPadding(dp(12), dp(6), dp(6), dp(6))
        p.background = GradientDrawable().apply {
            setColor(Color.parseColor("#F010141B"))
            setStroke(dp(2), Themes.accent(prefs))
            cornerRadius = dp(16).toFloat()
        }
        val e = EditText(this)
        e.hint = L.t("Say something to Capri", "قل شي لكابري")
        e.setHintTextColor(Color.parseColor("#8A94A0"))
        e.setTextColor(Color.WHITE)
        e.setSingleLine(true)
        e.imeOptions = EditorInfo.IME_ACTION_SEND
        e.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) { sendTyped(); true } else false
        }
        input = e
        p.addView(e, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        fun button(label: String, click: () -> Unit) {
            val t = TextView(this)
            t.text = label
            t.textSize = 21f
            t.setTextColor(Color.WHITE)
            t.setPadding(dp(10), dp(8), dp(10), dp(8))
            t.setOnClickListener { click() }
            p.addView(t)
        }
        button("\uD83C\uDFA4") { hidePanel(); convoActive = prefs.getB("convo", false); startListening() }
        button("\u27A4") { sendTyped() }
        button("\u2630") { hidePanel(); mainMenu() }
        button("\u2715") { hidePanel() }

        panel = p
        panelLp = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            OVERLAY, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP
            y = screenH * 18 / 100
            horizontalMargin = 0.03f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
    }

    private fun showPanel() {
        if (panelShown || hidden) return
        if (panel == null) buildPanel()
        wm.addView(panel, panelLp)
        panelShown = true
        input?.requestFocus()
        h.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, 0)
        }, 200)
    }

    private fun hidePanel() {
        if (panelShown) {
            try { wm.removeView(panel) } catch (e: Exception) { }
            panelShown = false
        }
    }

    private fun sendTyped() {
        val t = input?.text?.toString()?.trim() ?: ""
        if (t.isEmpty()) return
        input?.setText("")
        hidePanel()
        ask(t)
    }

    // ---- voice in ----

    private fun heard(t: String?) {
        cloudListening = false
        if (t.isNullOrBlank()) { missed(); return }
        missCount = 0
        if (dictating) {
            dictating = false
            val ok = CapriAccess.instance?.typeText(t) == true
            say(if (ok) L.t("Typed it.", "كتبتها.") else L.t("There's no text box open to type into.", "ما فيه خانة نص مفتوحة أكتب فيها."), false)
            return
        }
        if (Regex("^(bye|goodbye|stop|stop listening|that's all|مع السلامة|باي|وقف|بس)[ .!]*$", RegexOption.IGNORE_CASE).matches(t.trim())) {
            convoActive = false
            say(L.t("Later.", "مع السلامة."))
            return
        }
        ask(t)
    }

    private fun missed() {
        dictating = false
        missCount++
        if (convoActive && missCount >= 2) {
            convoActive = false
            missCount = 0
            say(L.t("I'll stop listening.", "بوقف أسمع."), false)
        } else if (convoActive) {
            h.postDelayed({ startListening() }, 300)
        } else {
            say(L.t("Didn't catch that.", "ما سمعتك زين."), false)
        }
    }

    private fun startListening(dictate: Boolean = false) {
        poke()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            say(L.t("I need the microphone permission. Open the Capri app.", "أحتاج صلاحية المايك. افتح تطبيق كابري."), false)
            return
        }
        speakId++
        Voice.stop()
        tts?.stop()
        talking = false
        dictating = dictate
        if (dictate) showBubble(L.t("Dictating... say what to type.", "أسمعك... قل وش أكتب."), 12_000L, false)
        else showBubble(L.t("Listening...", "أسمعك..."), 12_000L, false)
        settle()

        // Her own ears (Whisper-style server) when chosen: better for Arabic dialects.
        if (prefs.getI("sttEngine", 0) == 1) {
            if (cloudListening) return
            cloudListening = true
            Thread {
                try {
                    val txt = Ears.listen(this, prefs) { }
                    h.post { heard(txt) }
                } catch (e: Throwable) {
                    h.post { cloudListening = false; convoActive = false; say(L.t("Hearing problem: ", "مشكلة بالسمع: ") + (e.message ?: ""), false) }
                }
            }.start()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say(L.t("This phone has no speech recognizer. Pick her own ears in the Capri app.", "الجوال ما فيه تعرف على الصوت. اختر أذنها الخاصة من التطبيق."), false)
            return
        }
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) { missed() }
            override fun onResults(results: Bundle?) {
                heard(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
            }
        })
        recognizer = r
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, L.locale(prefs).toLanguageTag()))
    }

    // ---- hide / show ----

    private fun toggleHidden() {
        val s = sprite ?: return
        hidden = !hidden
        if (hidden) {
            hideBubble(); hidePanel()
            speakId++; Voice.stop(); tts?.stop()
            s.play("leave", false) { s.visibility = View.GONE }
        } else {
            s.visibility = View.VISIBLE
            s.play("arrive", false) { settle() }
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTE_ID, notification())
    }

    private fun toggleQuiet() {
        prefs.putB("quiet", !quiet)
        if (quiet) { speakId++; Voice.stop(); tts?.stop() }
        say(if (quiet) L.t("Quiet mode. I'll only type.", "وضع الصمت. بس أكتب.") else L.t("Voice is back.", "رجع الصوت."), false)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTE_ID, notification())
    }
}
