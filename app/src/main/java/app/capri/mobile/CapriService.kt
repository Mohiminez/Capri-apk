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
import android.view.ViewGroup
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
        const val ACT_TEST = "app.capri.mobile.TEST"
        @Volatile var notifier: ((String) -> Unit)? = null
        private const val NOTE_ID = 1
        private const val OVERLAY = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var brain: Brain
    private lateinit var agent: Agent
    private val h = SafeHandler(Looper.getMainLooper()) { e -> CrashLog.record(this, e, "service task") }

    private var sprite: SpriteView? = null
    private lateinit var spriteLp: WindowManager.LayoutParams
    private var bubble: LinearLayout? = null
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
    private var plate: SpeechPlate? = null
    private var danceAsked = false
    private var alongOn = false
    private var lastPet = 0L
    private var lastJealous = 0L
    private var scrollMs = 0L
    private var lastScrollAt = 0L
    private var lastNag = 0L
    private var nags = 0
    private var heardView: TextView? = null
    private var buttonsRow: LinearLayout? = null
    private var heard = ""
    private var pages: List<String> = emptyList()
    private var pageIdx = 0
    private var thinking = false
    private var cursorOn = true
    private var lastReply = ""
    private var tone: android.media.ToneGenerator? = null
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
    private var walkTotal = 1f
    private var scripted = false
    private var bubbleText: TextView? = null
    private val tfMono: android.graphics.Typeface by lazy { try { android.graphics.Typeface.createFromAsset(assets, if (L.ar) "fonts/Tajawal-Regular.ttf" else "fonts/ShareTechMono-Regular.ttf") } catch (e: Exception) { android.graphics.Typeface.MONOSPACE } }
    private val tfBody: android.graphics.Typeface by lazy { try { android.graphics.Typeface.createFromAsset(assets, if (L.ar) "fonts/Tajawal-Regular.ttf" else "fonts/BarlowSemiCondensed-Regular.ttf") } catch (e: Exception) { android.graphics.Typeface.DEFAULT } }
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
        nm.createNotificationChannel(NotificationChannel("capri_alerts", "Capri alerts", NotificationManager.IMPORTANCE_HIGH))
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startFg()) {
            stopSelf()
            return START_NOT_STICKY
        }
        // The system restarts a service after it dies. If she has died three times in two minutes, stop instead of looping.
        if (intent == null && CrashLog.recentFatal(this, 120_000L) >= 3) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACT_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        if (sprite == null) {
            try {
                setup()
            } catch (e: Throwable) {
                // Something in her setup broke: say why in the crash log and stop cleanly instead of crashing.
                CrashLog.record(this, e, "setting her up")
                Toast.makeText(this, L.t("Capri couldn't start. Open the app to see why.", "كابري ما قدرت تشتغل. افتح التطبيق وشوف السبب."), Toast.LENGTH_LONG).show()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (intent?.action == ACT_HIDE) toggleHidden()
        if (intent?.action == ACT_QUIET) toggleQuiet()
        if (intent?.action == ACT_TEST) h.postDelayed({ hands("test") }, 900)
        if (intent?.action == ACT_ASK) {
            val t = intent.getStringExtra("text") ?: ""
            val kind = intent.getStringExtra("kind") ?: "text"
            val imagePath = intent.getStringExtra("imagePath")
            val name = intent.getStringExtra("name") ?: ""
            if (kind == "image" && imagePath != null) h.postDelayed({
                if (hidden) toggleHidden()
                askAboutImage(imagePath, name, t)
            }, 700) else if (kind == "file" && t.isNotBlank()) h.postDelayed({
                if (hidden) toggleHidden()
                ask("(They shared a file with you" + (if (name.isNotBlank()) ", named \"$name\"" else "") + ". Its text:\n" + t.take(6000) +
                    "\nIn one or two short lines: say what it is and what matters in it, or answer their question about it.)", true)
            }, 700) else if (t.isNotBlank()) h.postDelayed({
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
        CapriAccess.onForeground = null
        CapriAccess.onScrolled = null
        alongOn = false
        notifier = null
        try { unregisterReceiver(events) } catch (e: Exception) { }
        Ears.cancel()
        hideMenu()
        clearClones()
        hideRing()
        if (::agent.isInitialized) agent.cancel()
        Voice.stop()
        try { if (panelShown) wm.removeView(panel) } catch (e: Exception) { }
        try { if (bubbleShown) wm.removeView(bubble) } catch (e: Exception) { }
        try { sprite?.let { wm.removeView(it) } } catch (e: Exception) { }
        tts?.shutdown()
        try { tone?.release() } catch (e: Exception) { }
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
        clip("leave", false) { stopSelf() }
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
            .addAction(0, if (quiet) L.t("Unmute", "إلغاء الصمت") else L.t("Quiet", "صمت"), act(ACT_QUIET, 3))
            .addAction(0, L.t("Stop", "إيقاف"), act(ACT_STOP, 2))
            .build()
    }

    // Returns false when Android refuses to let her run in the foreground right now (for example a restart from the background).
    private var fgHasMic = false

    private fun startFg(): Boolean {
        val n = notification()
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        try {
            when {
                Build.VERSION.SDK_INT >= 34 -> {
                    var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    if (mic) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    try {
                        startForeground(NOTE_ID, n, type)
                        fgHasMic = mic
                    } catch (e: Exception) {
                        // e.g. started at boot, when the microphone type isn't allowed yet
                        startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                        fgHasMic = false
                    }
                }
                Build.VERSION.SDK_INT >= 30 -> {
                    // Android 11 to 13: only ask for the microphone type when she may really use the microphone.
                    try {
                        startForeground(NOTE_ID, n, if (mic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
                        fgHasMic = mic
                    } catch (e: Exception) {
                        startForeground(NOTE_ID, n)
                        fgHasMic = false
                    }
                }
                else -> startForeground(NOTE_ID, n)
            }
            return true
        } catch (e: Throwable) {
            CrashLog.record(this, e, "starting the foreground service")
            return false
        }
    }

    // If the microphone permission was given after she started, she needs the microphone service type before she can hear in the background.
    private fun refreshMicType() {
        if (fgHasMic || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        startFg()
    }

    // ---- setting her up ----

    private fun brainReady(): Boolean = brain.anyReady()

    private fun setup() {
        val hPx = dp(prefs.getI("size", 140))
        val wPx = hPx * 5 / 8
        val s = SpriteView(this)
        sprite = s
        spriteLp = WindowManager.LayoutParams(wPx, hPx, OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                (if (prefs.getB("hologram", false)) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0), PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getI("x", screenW - wPx)
            y = prefs.getI("y", screenH * 45 / 100)
        }
        fx = spriteLp.x.toFloat()
        fy = spriteLp.y.toFloat()
        s.unit = hPx / 384f
        if (prefs.getB("startSilent", false)) prefs.putB("voice", false)
        s.glowColor = Themes.accent(prefs)
        s.lowPower = prefs.getB("lowPower", false)
        s.bob = !s.lowPower
        s.setOnTouchListener(touch)
        s.alpha = 0f
        wm.addView(s, spriteLp)
        s.animate().alpha(1f).setDuration(400).start()

        buildBubble()
        bubbleLp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        agent = Agent(this, brain, prefs,
            { line -> h.post { say(line, false) } },
            { line -> h.post { busy = false; say(line); settle() } })
        ReminderReceiver.speaker = { t -> h.post { say(t) } }
        CapriAccess.onForeground = { p -> h.post { foregroundChanged(p) } }
        CapriAccess.onScrolled = { p -> h.post { scrolled(p) } }
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

        clip("arrive", false) { settle() }
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
            if (!brainReady()) say(L.t("Hi. Open the Capri app and give me a brain first.", "هلا. افتح تطبيق كابري وأعطني عقل أشتغل فيه أول."), false)
            else ask("(You just booted up on the user's phone. Say hi in one short line.)", true)
        }, 1200)
    }

    // ---- animation state ----

    // Every clip goes through here so the window can change shape (she is wider when lying down).
    private fun clip(name: String, loop: Boolean = true, done: (() -> Unit)? = null) {
        val sp = sprite ?: return
        applyShape(name)
        sp.play(name, loop, done)
    }

    // Keeps her feet and the middle of her where they are while the window resizes for the clip.
    private fun applyShape(name: String) {
        val sp = sprite ?: return
        val (w, hh) = sp.sizeFor(name)
        if (w <= 0 || hh <= 0 || (w == spriteLp.width && hh == spriteLp.height)) return
        val cx = spriteLp.x + spriteLp.width / 2f
        val bottom = spriteLp.y + spriteLp.height
        spriteLp.width = w
        spriteLp.height = hh
        spriteLp.x = (cx - w / 2f).toInt()
        spriteLp.y = bottom - hh
        fx = spriteLp.x.toFloat()
        fy = spriteLp.y.toFloat()
        try { wm.updateViewLayout(sp, spriteLp) } catch (e: Exception) { }
        if (bubbleShown) placeBubble()
    }

    private fun grounded() = abs(spriteLp.y - floorY()) < dp(10)

    private fun settle() {
        val s = sprite ?: return
        if (scripted) return
        s.glow = talking && !s.lowPower
        s.glitchFx = busy || mode == 1
        s.talk = talking
        s.dancing = (dancing || danceAsked) && !talking && !busy && !asleep && mode == 0 && !scripted
        clip(when {
            dragging || mode == 1 -> "dangle"
            mode == 2 -> "walk"
            busy -> "glitch"
            asleep -> if (grounded()) "laydown_sleep" else "sleep"
            else -> "idle"
        })
    }

    private val blinkTask = object : Runnable {
        override fun run() {
            val s = sprite
            if (s != null && !asleep && !busy && !dragging && !hidden && !scripted && mode == 0 && s.current == "idle") {
                clip("idle_blink", false) { settle() }
            }
            h.postDelayed(this, Random.nextLong(3500, 8000))
        }
    }

    private val sleepTask = Runnable {
        if (!busy && !talking && !agent.running && !scripted) {
            asleep = true
            if (mode == 0 && !grounded() && !hidden) {
                fx = spriteLp.x.toFloat(); fy = spriteLp.y.toFloat()
                walkTx = fx
                walkTy = floorY()
                walkTotal = maxOf(1f, hypot(walkTx - fx, walkTy - fy))
                startMotion(2)
            } else {
                settle()
            }
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
            val pop = prefs.getI("popMin", 30).coerceAtLeast(5)
            h.postDelayed(this, Random.nextLong(pop * 40_000L, pop * 90_000L))
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
                        L.t("Hydrate. I mean it.", "اشرب موية. جاد أقول.")).random())
                } else if (prefs.getB("careStretch", false) && now - lastStretch > 60 * 60_000L) {
                    lastStretch = now
                    say(listOf(L.t("You've been staring for a while. Stretch, blink, breathe.", "صار لك مدة تطالع الشاشة. تمطّط وغمّض عيونك وخذ نفس."),
                        L.t("Look at something far away for ten seconds.", "طالع شي بعيد لمدة عشر ثواني.")).random())
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
            try { handle(i) } catch (e: Throwable) { CrashLog.record(this@CapriService, e, "screen/battery event") }
        }

        private fun handle(i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; Voice.stop(); tts?.stop() }
                Intent.ACTION_SCREEN_ON -> screenOn = true
                Intent.ACTION_BATTERY_LOW -> if (prefs.getB("events", true) && idleOk())
                    say(L.t("You're nearly out of battery. Find a charger, gonk.", "البطارية على وشك تخلص. دوّر لك شاحن يا خوي."))
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
                bg("job", this@CapriService) {
                    val txt = acc.dump(if (brain.isLocal()) 2000 else 3000)
                    val img = if (prefs.getB("vision", false) && !brain.isLocal()) acc.shotBlocking() else null
                    h.post {
                        if (!busy && (img != null || txt.lines().size > 3)) {
                            ask("(You glance at their screen. What's on it:\n$txt\nIf something stands out, like a big play, a funny moment or something they're stuck on, react in one short line. If nothing stands out, reply with just NOTHING.)", true, img, true)
                        }
                    }
                }
            }
            h.postDelayed(this, prefs.getI("watchSecs", 60) * 1000L)
        }
    }

    private fun poke() {
        h.removeCallbacks(sleepTask)
        val sm = prefs.getI("sleepMin", 3)
        if (sm > 0) h.postDelayed(sleepTask, sm * 60_000L)
        if (asleep) {
            asleep = false
            clip("wake", false) { settle() }
        }
    }

    // ---- movement: throws, bounces and little walks ----

    private val choreo = Choreographer.getInstance()
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            try {
                step(frameTimeNanos)
            } catch (e: Throwable) {
                CrashLog.record(this@CapriService, e, "movement")
                mode = 0
                return
            }
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
            val top = dp(55) * (1f + prefs.getI("wanderPace", 1) * 0.5f)
            // ease in over the first stretch and ease out over the last, like she is floating rather than sliding
            val ramp = minOf(1f, 0.25f + (walkTotal - dist) / dp(90), 0.2f + dist / dp(130)).coerceIn(0.2f, 1f)
            val sp = if (asleep) top * 1.6f else top * ramp
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

    private fun canRoam() = screenOn && !scripted && !dancing && !danceAsked && !alongOn && !asleep && !talking && !busy && !dragging && !hidden && !panelShown && mode == 0 && !agent.running

    private val wanderTask = object : Runnable {
        override fun run() {
            val pace = prefs.getI("wanderPace", 1)
            if (pace > 0 && canRoam()) {
                val r = Random.nextInt(100)
                when {
                    r < 55 -> walkSomewhere()
                    r < 70 -> teleport()
                    r < 85 -> clip("grin", false) { settle() }
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
        walkTy = (fy + Random.nextInt(-dp(120), dp(120))).clampTo(dp(48).toFloat(), floorY())
        walkTotal = maxOf(1f, hypot(walkTx - fx, walkTy - fy))
        sprite?.flip = walkTx < fx
        startMotion(2)
    }

    // Glitches out and pops up somewhere else, like her arrive and leave animations on the desktop.
    private fun teleport() {
        scripted = true
        clip("leave", false) {
            fx = Random.nextFloat() * (maxX() - minX()) + minX()
            fy = Random.nextFloat() * (floorY() - dp(60)) + dp(60)
            spriteLp.x = fx.toInt()
            spriteLp.y = fy.toInt()
            try { sprite?.let { wm.updateViewLayout(it, spriteLp) } } catch (e: Exception) { }
            if (bubbleShown) placeBubble()
            clip("arrive", false) { scripted = false; settle() }
        }
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
        var downInHead = false
        var flips = 0
        var lastDir = 0
        var lastPx = 0f
        var petted = false
        val slop: Int by lazy { ViewConfiguration.get(this@CapriService).scaledTouchSlop }
        val longPress = Runnable {
            if (!moved) {
                wasLong = true
                convoActive = prefs.getB("convo", false)
                startListening()
            }
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            try {
                touchEvent(v, e)
            } catch (x: Throwable) {
                CrashLog.record(this@CapriService, x, "touching her")
                dragging = false
            }
            return true
        }

        private fun touchEvent(v: View, e: MotionEvent) {
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
                    downInHead = e.y < v.height * 0.45f
                    flips = 0; lastDir = 0; lastPx = e.rawX; petted = false
                    h.postDelayed(longPress, 600)
                    poke()
                }
                MotionEvent.ACTION_MOVE -> {
                    val ox = e.rawX - downX
                    val oy = e.rawY - downY
                    if (!moved && downInHead && !asleep && hypot(ox, oy) < dp(72)) {
                        // stroking her head side to side is a pat, not a drag
                        if (abs(e.rawX - lastPx) > dp(5)) {
                            val dir = if (e.rawX > lastPx) 1 else -1
                            if (dir != lastDir) { flips++; lastDir = dir }
                            lastPx = e.rawX
                        }
                        if (flips >= 3 && !petted) { petted = true; h.removeCallbacks(longPress); pet() }
                    } else if (!moved && (abs(ox) > slop || abs(oy) > slop)) {
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
                    } else if (!wasLong && !petted && e.action == MotionEvent.ACTION_UP) {
                        sprite?.ripple(e.x, e.y)
                        tapped()
                    }
                    tracker?.recycle()
                    tracker = null
                }
            }
        }
    }

    private fun tapped() {
        if (crowd.isNotEmpty()) {
            clearClones()
            say(L.t("Poof.", "بوف."), false)
            return
        }
        if (alongOn) {
            alongOn = false
            return
        }
        // Tapping her while she talks cuts her off, like talking over someone.
        if (talking) {
            speakId++
            Voice.stop()
            tts?.stop()
            talkDone()
            return
        }
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
            clip("grin", false) { settle() }
        }
        if (panelShown) hidePanel() else showPanel()
    }

    private fun moveTo(x: Int, y: Int) {
        val s = sprite ?: return
        spriteLp.x = x.clampTo(minX().toInt(), maxX().toInt())
        spriteLp.y = y.clampTo(0, screenH - spriteLp.height)
        fx = spriteLp.x.toFloat()
        fy = spriteLp.y.toFloat()
        try { wm.updateViewLayout(s, spriteLp) } catch (e: Exception) { }
        if (bubbleShown) placeBubble()
    }

    // ---- bubble ----

    private fun buildBubble() {
        val d = resources.displayMetrics.density
        val b = LinearLayout(this)
        b.orientation = LinearLayout.VERTICAL
        b.layoutDirection = if (L.ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        val pl = SpeechPlate(d)
        plate = pl
        b.background = pl
        val hv = TextView(this)
        hv.typeface = android.graphics.Typeface.create(tfMono, android.graphics.Typeface.ITALIC)
        hv.textSize = 12f
        hv.setTextColor(Night.dim)
        hv.visibility = View.GONE
        hv.setPadding(0, 0, 0, dp(3))
        b.addView(hv)
        heardView = hv
        val body = TextView(this)
        body.typeface = tfMono
        body.textSize = 14f
        body.setTextColor(Night.neon)
        body.maxWidth = (screenW * 0.8f).toInt()
        b.addView(body)
        bubbleText = body
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.visibility = View.GONE
        row.setPadding(0, dp(8), 0, 0)
        fun pill(label: String, click: () -> Unit) {
            val t = TextView(this)
            t.text = label
            t.typeface = tfMono
            t.textSize = 13f
            t.setTextColor(Night.sCyan)
            t.setPadding(dp(18), dp(7), dp(18), dp(7))
            t.background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.argb(40, 58, 214, 235)); setStroke(dp(1), Night.sCyan) }
            t.setOnClickListener { click() }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(10)
            row.addView(t, lp)
        }
        pill(L.t("Yes", "نعم")) { val y = pendingConfirm; pendingConfirm = null; hideBubble(); y?.invoke() }
        pill(L.t("No", "لا")) { pendingConfirm = null; hideBubble() }
        b.addView(row)
        buttonsRow = row
        b.setOnClickListener { bubbleTapped() }
        b.setOnLongClickListener { copyReply(); true }
        bubble = b
    }

    private fun copyReply() {
        if (lastReply.isEmpty()) return
        (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Capri", lastReply))
        heard = "sys: " + L.t("copied", "تم النسخ")
        renderBubble()
    }

    // Where the plate goes: above her head with the tail pointing down, or below her when there's no room.
    private fun placeBubble() {
        val b = bubble ?: return
        val pl = plate ?: return
        val tail = pl.tail.toInt()
        b.setPadding(dp(26), dp(11) + (if (pl.tailDown) 0 else tail), dp(14), dp(11) + (if (pl.tailDown) tail else 0))
        b.measure(View.MeasureSpec.makeMeasureSpec(screenW - dp(16), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        var bw = b.measuredWidth
        var bh = b.measuredHeight
        val headX = spriteLp.x + spriteLp.width / 2
        val headY = spriteLp.y + (spriteLp.height * 0.08f).toInt()
        val above = headY - bh - dp(2) >= dp(24)
        if (above != pl.tailDown) {
            pl.configure(above, pl.tipX)
            b.setPadding(dp(26), dp(11) + (if (above) 0 else tail), dp(14), dp(11) + (if (above) tail else 0))
            b.measure(View.MeasureSpec.makeMeasureSpec(screenW - dp(16), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            bw = b.measuredWidth
            bh = b.measuredHeight
        }
        bubbleLp.x = (headX - bw / 2).clampTo(dp(8), screenW - bw - dp(8))
        bubbleLp.y = if (above) headY - bh - dp(2) else spriteLp.y + spriteLp.height - dp(30)
        pl.configure(above, ((headX - bubbleLp.x).toFloat() / maxOf(1, bw)).coerceIn(0.1f, 0.9f))
        if (bubbleShown) try { wm.updateViewLayout(b, bubbleLp) } catch (e: Exception) { }
    }

    private val hideBubbleTask = Runnable { pendingConfirm = null; hideBubble() }

    // Her words appear letter by letter and a block cursor follows them, like the desktop's terminal box.
    private val typeTask = object : Runnable {
        override fun run() {
            typingPos = minOf(typingFull.length, typingPos + 2)
            renderBubble()
            if (typingPos < typingFull.length) h.postDelayed(this, 26)
        }
    }

    private val cursorTask = object : Runnable {
        override fun run() {
            if (bubbleShown) {
                cursorOn = !cursorOn
                renderBubble()
                h.postDelayed(this, 420)
            }
        }
    }

    private fun renderBubble() {
        val bt = bubbleText ?: return
        val hv = heardView ?: return
        if (heard.isNotEmpty()) { hv.visibility = View.VISIBLE; hv.text = "> " + heard.take(70) } else hv.visibility = View.GONE
        val speaker = "CAPRI: "
        val page = if (thinking) "" else typingFull
        val full = speaker + page + " "
        val sb = android.text.SpannableStringBuilder(full)
        sb.setSpan(ForegroundColorSpan(Night.sRed), 0, speaker.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, speaker.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val typing = !thinking && typingPos < page.length
        val caret = speaker.length + (if (thinking) 0 else typingPos.coerceAtMost(page.length))
        val showCaret = typing || cursorOn
        if (showCaret && caret < full.length) {
            sb.replace(caret, caret + 1, "\u2588")
        }
        if (typing && caret + 1 < full.length) {
            sb.setSpan(ForegroundColorSpan(Color.TRANSPARENT), caret + 1, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        bt.text = sb
    }

    private fun bubbleTapped() {
        if (pendingConfirm != null) return
        if (!thinking && typingPos < typingFull.length) {
            h.removeCallbacks(typeTask)
            typingPos = typingFull.length
            renderBubble()
        } else if (pageIdx + 1 < pages.size) {
            pageIdx++
            startPage()
        } else {
            hideBubble()
        }
    }

    private fun startPage() {
        typingFull = pages[pageIdx] + (if (pageIdx + 1 < pages.size) " \u25B8" else "")
        typingPos = if (sprite?.lowPower == true) typingFull.length else 0
        thinking = false
        renderBubble()
        placeBubble()
        h.removeCallbacks(typeTask)
        if (typingPos < typingFull.length) h.postDelayed(typeTask, 26)
        h.removeCallbacks(hideBubbleTask)
        h.postDelayed(hideBubbleTask, (typingFull.length * 90L).coerceIn(6000L, 22000L))
    }

    private fun splitPages(text: String): List<String> {
        if (text.length <= 240) return listOf(text)
        val out = ArrayList<String>()
        var cur = StringBuilder()
        for (part in text.split(Regex("(?<=[.!?\u061F])\\s+"))) {
            if (cur.length + part.length > 240 && cur.isNotEmpty()) { out.add(cur.toString().trim()); cur = StringBuilder() }
            cur.append(part).append(' ')
        }
        if (cur.isNotBlank()) out.add(cur.toString().trim())
        return out
    }

    // The ... of thinking is shown as the speaker label with a blinking cursor.
    private fun showBubble(text: String, ms: Long, typed: Boolean = true) {
        val b = bubble ?: return
        if (hidden) return
        h.removeCallbacks(typeTask)
        val first = !bubbleShown
        if (text == "...") {
            thinking = true
            pages = listOf("")
            typingFull = ""
            typingPos = 0
            renderBubble()
        } else {
            lastReply = text
            pages = splitPages(text)
            pageIdx = 0
            buttonsRow?.visibility = View.GONE
            startPage()
        }
        placeBubble()
        if (first) {
            try {
                wm.addView(b, bubbleLp)
                bubbleShown = true
                glitchIn(b)
                h.postDelayed(cursorTask, 420)
                chime("show")
            } catch (e: Exception) {
                CrashLog.record(this, e, "showing her speech box")
            }
        } else {
            try { wm.updateViewLayout(b, bubbleLp) } catch (e: Exception) { }
        }
        if (text == "...") { h.removeCallbacks(hideBubbleTask); h.postDelayed(hideBubbleTask, ms) }
    }

    // A quick tear and flicker when the box opens, like the desktop.
    private fun glitchIn(v: View) {
        var n = 0
        val step = object : Runnable {
            override fun run() {
                if (n >= 5) { v.translationX = 0f; v.alpha = 1f; v.scaleY = 1f; return }
                val k = (5 - n) / 5f
                v.translationX = (Random.nextFloat() * 2 - 1) * dp(7) * k
                v.alpha = 0.45f + Random.nextFloat() * 0.55f
                v.scaleY = 1f - 0.06f * k * Random.nextFloat()
                n++
                h.postDelayed(this, 38)
            }
        }
        step.run()
    }

    // Yes / No buttons inside the box, like the desktop's choice prompts.
    private fun showConfirm(text: String, yes: () -> Unit) {
        pendingConfirm = yes
        showBubble(text, 14_000L)
        buttonsRow?.visibility = View.VISIBLE
        placeBubble()
        h.removeCallbacks(hideBubbleTask)
        h.postDelayed(hideBubbleTask, 14_000L)
    }

    private fun hideBubble() {
        h.removeCallbacks(hideBubbleTask)
        h.removeCallbacks(typeTask)
        h.removeCallbacks(cursorTask)
        thinking = false
        buttonsRow?.visibility = View.GONE
        heard = ""
        if (bubbleShown) {
            try { wm.removeView(bubble) } catch (e: Exception) { }
            bubbleShown = false
        }
    }

    // Little sounds like the desktop's chimes (off by default).
    private fun chime(kind: String) {
        if (!prefs.getB("chimes", false) || quiet) return
        try {
            if (tone == null) tone = android.media.ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45)
            when (kind) {
                "sent" -> tone?.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 90)
                "heard" -> tone?.startTone(android.media.ToneGenerator.TONE_PROP_PROMPT, 90)
                "listen" -> tone?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 70)
                else -> tone?.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 40)
            }
        } catch (e: Throwable) { }
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
            val params = Bundle()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, prefs.getI("ttsVolume", 100) / 100f)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "capri")
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
        bg("job", this@CapriService) {
            val err = Voice.speak(this, prefs, text) { h.post { if (id == speakId) { talking = true; settle() } } }
            h.post {
                if (id != speakId) return@post
                if (err == null) {
                    talkDone()
                } else {
                    Toast.makeText(this, L.t("Voice problem, using the phone voice: ", "مشكلة في الصوت، باستخدم صوت الجوال: ") + err, Toast.LENGTH_LONG).show()
                    phoneSpeak(text)
                }
            }
        }
    }

    private fun ask(text: String, hiddenPrompt: Boolean = false, image: String? = null, skipNothing: Boolean = false) {
        poke()
        if (!hiddenPrompt) {
            Charm.add(prefs, 2)
            val quick = QuickAsks.match(text)
            if (quick != null) {
                say(Actions.run(this, prefs, quick, this, true))
                return
            }
        }
        if (!brainReady()) {
            say(L.t("I need a brain first. Open the Capri app and pick one.", "أحتاج عقل أول. افتح تطبيق كابري واختر واحد."), false)
            return
        }
        busy = true
        talking = false
        settle()
        if (!hiddenPrompt) { heard = text; chime("sent") }
        if (!skipNothing) showBubble("...", 90_000L, false)
        brain.ask(text, hiddenPrompt, image) { reply, err ->
            busy = false
            when {
                reply != null && skipNothing && reply.trim().uppercase().startsWith("NOTHING") -> settle()
                reply != null && hiddenPrompt && reply.trim().equals("NOTHING", ignoreCase = true) -> settle()
                reply != null -> say(Actions.run(this, prefs, reply, this))
                skipNothing -> settle()
                else -> { Diag.log("brain", err ?: "?"); say(L.t("Signal lost: ", "انقطعت الإشارة: ") + err, false) }
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
        bg("job", this@CapriService) {
            val txt = acc.dump(if (brain.isLocal()) 2500 else 4000)
            val img = if (prefs.getB("vision", false) && !brain.isLocal()) acc.shotBlocking() else null
            h.post {
                busy = false
                ask("(You are looking at their screen right now. What's on it:\n$txt\nThey asked: $question\nAnswer from what you can see, in one or two short lines. Never read private messages out word for word.)", true, img)
            }
        }
    }

    // Weather, Wikipedia and "what's playing": she fetches it, then tells you in her own words.
    override fun tool(name: String, arg: String) {
        busy = true
        settle()
        showBubble("...", 90_000L, false)
        bg("job", this@CapriService) {
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
        }
    }

    // In Guarded mode she asks first: a bubble you tap to allow.
    override fun confirm(text: String, yes: () -> Unit) = showConfirm(text, yes)

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

    // Direct phone commands: open an app and type, tap a label, scroll, go back...
    override fun hands(spec: String) {
        if (CapriAccess.instance == null) {
            confirm(L.t("I need my accessibility access for that. Tap to open the setting.", "أحتاج صلاحية الوصول عشان أسوي هذا. اضغط لفتح الإعداد.")) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        busy = true
        settle()
        bg("job", this@CapriService) {
            val result = try { Hands.run(this, spec) } catch (e: Throwable) { L.t("That failed: ", "فشلت: ") + (e.message ?: "") }
            h.post { busy = false; say(result) }
        }
    }

    // ---- emotes, her own settings, scroll-for-me, pats, jealousy, doomscroll guard ----

    private val feedApps = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.google.android.youtube", "com.instagram.android",
        "com.facebook.katana", "com.snapchat.android", "com.twitter.android", "com.reddit.frontpage", "com.lemon.lvoverseas")
    private val aiApps = setOf("com.openai.chatgpt", "com.google.android.apps.bard", "com.anthropic.claude", "com.microsoft.copilot",
        "com.deepseek.chat", "ai.x.grok", "ai.perplexity.app.android", "ai.character.app")

    private val petLines get() = if (L.ar) listOf("ههه. بس لا تخرب الشعر.", "مممم. لا تقول لأحد إنه عجبني.", "انتبه يا خوي، أعض.", "...طيب شوي كمان.", "هذا اللي كنت أحتاجه.")
        else listOf("...hands off the hair. Okay, a little more.", "Mm. Don't tell anyone I liked that.", "Careful, choom, I bite.", "Fine. A bit more.", "That's exactly what I needed.")

    private fun pet() {
        val now = System.currentTimeMillis()
        if (now - lastPet < 5000) return
        lastPet = now
        sprite?.emote("heart")
        Charm.add(prefs, 1)
        clip("grin", false) { settle() }
        say(petLines.random(), false)
    }

    override fun emote(name: String) {
        when (name.lowercase().trim()) {
            "dance" -> {
                danceAsked = true
                settle()
                say(L.t("Dancing. Tell me to stop when you've had enough.", "برقص. قل لي وقّفي لما تشبع."), false)
            }
            "stop" -> { danceAsked = false; dancing = false; settle() }
            "heart" -> { sprite?.emote("heart"); Charm.add(prefs, 1) }
            else -> sprite?.emote(name.lowercase().trim())
        }
    }

    private val arColors = mapOf("احمر" to "Red", "أحمر" to "Red", "وردي" to "Pink", "برتقالي" to "Orange", "اصفر" to "Yellow", "أصفر" to "Yellow",
        "اخضر" to "Green", "أخضر" to "Green", "سماوي" to "Cyan", "ازرق" to "Blue", "أزرق" to "Blue", "بنفسجي" to "Violet", "ابيض" to "White", "أبيض" to "White", "اسود" to "Black", "أسود" to "Black")

    // The settings she can change herself, the way the desktop's "settings she can change" works.
    override fun setting(key: String, value: String) {
        val k = key.lowercase().trim()
        val v = value.lowercase().trim()
        val on = v in setOf("on", "true", "yes", "1")
        var said: String? = null
        when (k) {
            "size" -> {
                val cur = prefs.getI("size", 140)
                val n = when (v) { "bigger", "up", "larger" -> cur + 25; "smaller", "down" -> cur - 25; else -> v.toIntOrNull() ?: cur }.coerceIn(80, 220)
                prefs.putI("size", n)
                resizeSprite(n)
                said = if (n == cur) L.t("That's as ${if (v == "bigger") "big" else "small"} as I go.", "هذا أقصى حجم أقدر عليه.") else L.t("Better?", "أحسن؟")
            }
            "wander" -> {
                val cur = prefs.getI("wanderPace", 1)
                val n = when (v) { "off", "0", "no", "false" -> 0; "more", "up" -> minOf(3, cur + 1); "less", "down" -> maxOf(0, cur - 1); else -> v.toIntOrNull()?.coerceIn(0, 3) ?: 1 }
                prefs.putI("wanderPace", n)
                said = if (n == 0) L.t("Okay, I'll stay put.", "تمام، بثبت مكاني.") else L.t("Fine, I'll wander.", "طيب، بتمشّى شوي.")
            }
            "quiet" -> { prefs.putB("quiet", on); said = if (on) L.t("Quiet mode. I'll only type.", "وضع الصمت. بكتب لك بس.") else L.t("Voice is back.", "رجع الصوت.") }
            "voice" -> { prefs.putB("voice", on); said = if (on) L.t("Voice is back.", "رجع الصوت.") else L.t("No voice then.", "طيب، بدون صوت.") }
            "dance" -> prefs.putB("dance", on)
            "focus" -> startFocus(v.toIntOrNull()?.coerceIn(1, 180) ?: 25)
            "speed" -> { val n = (prefs.getI("ttsSpeed", 100) + (if (v == "faster" || v == "up") 15 else -15)).coerceIn(60, 160); prefs.putI("ttsSpeed", n); said = L.t("Like this?", "كذا مناسب؟") }
            "pitch" -> { val n = (prefs.getI("ttsPitch", 100) + (if (v == "higher" || v == "up") 15 else -15)).coerceIn(60, 160); prefs.putI("ttsPitch", n); said = L.t("Like this?", "كذا مناسب؟") }
            "volume" -> { val n = (prefs.getI("ttsVolume", 100) + (if (v == "up" || v == "louder") 20 else -20)).coerceIn(20, 100); prefs.putI("ttsVolume", n); said = L.t("Like this?", "كذا مناسب؟") }
            "theme" -> {
                val i = Themes.names.indexOfFirst { it.lowercase() == v || it.lowercase().contains(v) }
                if (i >= 0) { prefs.putI("theme", i); prefs.putS("modAccent", ""); sprite?.glowColor = Themes.accent(prefs); said = L.t("New theme. Restart me to see it everywhere.", "ثيم جديد. أعد تشغيلي عشان يطلع بكل مكان.") }
            }
            "color" -> {
                // "hair=cyan", "eyes=pink", "all=original"; Arabic colour names work too
                val slot = v.substringBefore('=').trim()
                var name = v.substringAfter('=', "").trim()
                name = arColors[name]?.lowercase() ?: name
                val hex = Recolor.named.firstOrNull { it.first.lowercase() == name }?.second
                if (hex != null && (slot in Recolor.slots || slot == "all")) {
                    for (sl in if (slot == "all") Recolor.slots else listOf(slot)) prefs.putS("col_$sl", hex)
                    sprite?.reload()
                    said = L.t("How do I look?", "وش رايك فيني؟")
                }
            }
        }
        if (said != null) say(said, false)
    }

    // Focus: she goes quiet for N minutes, then tells you the time is up.
    private fun startFocus(minutes: Int) {
        val before = prefs.getB("quiet", false)
        prefs.putB("quiet", true)
        speakId++; Voice.stop(); tts?.stop()
        h.postDelayed({
            prefs.putB("quiet", before)
            chime("heard")
            say(L.t("Focus time is up. Stand up, stretch, you earned it.", "انتهى وقت التركيز. قم تمطّط، تستاهل."), !before)
        }, minutes * 60_000L)
        // the phone's own timer too, in case she gets closed in the meantime
        try { Actions.run(this, prefs, "[[timer:${minutes * 60}|Capri focus]]", null, true) } catch (e: Exception) { }
    }

    private fun resizeSprite(n: Int) {
        val sp = sprite ?: return
        val hPx = dp(n)
        sp.unit = hPx / 384f
        val (w, hh) = sp.sizeFor(sp.current.ifEmpty { "idle" })
        val cx = spriteLp.x + spriteLp.width / 2f
        val bottom = spriteLp.y + spriteLp.height
        spriteLp.width = w
        spriteLp.height = hh
        spriteLp.x = (cx - w / 2f).toInt()
        spriteLp.y = bottom - hh
        fx = spriteLp.x.toFloat()
        fy = spriteLp.y.toFloat()
        try { wm.updateViewLayout(sp, spriteLp) } catch (e: Exception) { }
        if (bubbleShown) placeBubble()
    }

    // She watches short videos with you: reacts to each one, then swipes to the next, like the desktop's "scroll for me".
    // ---- keeps an eye out (ported from the desktop: "tell me when the download finishes") ----

    private var eyeGoal: String? = null
    private var eyeUntil = 0L
    private var eyeAsking = false
    private var eyeLastHash = 0

    override fun keepWatch(goal: String) {
        val g = goal.trim()
        if (g.isEmpty() || g.lowercase() in setOf("off", "stop", "cancel", "no", "0", "clear")) {
            if (eyeGoal != null) say(L.t("Okay, I stopped watching.", "تمام، وقفت المراقبة."), false)
            stopEye()
            return
        }
        if (CapriAccess.instance == null) {
            say(L.t("I can't see your screen. Turn my accessibility permission on in the Capri app first.", "ما أقدر أشوف شاشتك. فعّل صلاحية الوصول لي من تطبيق كابري أول."), false)
            return
        }
        eyeGoal = g.take(200)
        eyeUntil = System.currentTimeMillis() + 2 * 3600_000L
        eyeLastHash = 0
        h.removeCallbacks(eyeTask)
        h.postDelayed(eyeTask, 10_000)
        say(L.t("Okay, I'll watch for it and tell you. Say \"stop watching\" to cancel.", "تمام، براقب وأقول لك. قل \"وقف المراقبة\" عشان ألغي."), false)
    }

    private fun stopEye() {
        eyeGoal = null
        eyeAsking = false
        h.removeCallbacks(eyeTask)
    }

    private val eyeTask = object : Runnable {
        override fun run() {
            val goal = eyeGoal ?: return
            if (System.currentTimeMillis() > eyeUntil) {
                stopEye()
                say(L.t("I stopped watching, it's been two hours.", "وقفت المراقبة، صار لها ساعتين."), false)
                return
            }
            val acc = CapriAccess.instance
            if (acc != null && screenOn && !eyeAsking && !busy && !agent.running) {
                eyeAsking = true
                bg("eye", this@CapriService) {
                    val txt = acc.dump(if (brain.isLocal()) 1500 else 2500)
                    val img = if (prefs.getB("vision", false) && !brain.isLocal()) acc.shotBlocking() else null
                    h.post {
                        // nothing changed on the screen since the last look: don't spend another request
                        if (img == null && txt.hashCode() == eyeLastHash) { eyeAsking = false; return@post }
                        eyeLastHash = txt.hashCode()
                        brain.ask("(You are quietly watching their screen for them. They asked you to tell them when: \"$goal\". What's on the screen now:\n$txt\n" +
                            "If that has clearly happened, reply with exactly DONE: followed by one short line telling them, in their language. If not, or you can't tell, reply with exactly NOTHING.)", true, img) { reply, err ->
                            eyeAsking = false
                            if (reply != null && eyeGoal != null && reply.trim().uppercase().startsWith("DONE")) {
                                val line = Actions.strip(reply.trim().replace(Regex("^(?i)done\\s*[:\\-]?\\s*"), "")).ifBlank { L.t("It happened.", "صار اللي تنتظره.") }
                                stopEye()
                                alert(line)
                                say(line, false)
                            }
                        }
                    }
                }
            }
            h.postDelayed(this, 25_000L)
        }
    }

    // A real notification too, in case she is hidden or the screen is off.
    private fun alert(text: String) {
        try {
            val n = Notification.Builder(this, "capri_alerts").setSmallIcon(R.drawable.ic_stat).setContentTitle("Capri").setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text)).setAutoCancel(true).build()
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(7, n)
        } catch (e: Exception) { }
    }

    // ---- points at things ("circle the send button") ----

    private var ring: View? = null

    override fun point(label: String) {
        val acc = CapriAccess.instance
        if (acc == null) {
            say(L.t("I can't see your screen. Turn my accessibility permission on in the Capri app first.", "ما أقدر أشوف شاشتك. فعّل صلاحية الوصول لي من تطبيق كابري أول."), false)
            return
        }
        bg("point", this@CapriService) {
            val r = acc.boundsOf(label)
            h.post {
                if (r == null) say(L.t("I can't see \"$label\" on the screen.", "ما أشوف \"$label\" على الشاشة."), false)
                else {
                    showRing(r)
                    say(L.t("Right there.", "هنا."), false)
                }
            }
        }
    }

    private fun showRing(r: android.graphics.Rect) {
        hideRing()
        val pad = dp(10)
        val v = RingView(this, Themes.accent(prefs))
        val lp = WindowManager.LayoutParams(maxOf(dp(48), r.width() + pad * 2), maxOf(dp(48), r.height() + pad * 2),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = r.left - pad
        lp.y = r.top - pad
        try {
            wm.addView(v, lp)
            ring = v
            h.postDelayed({ hideRing() }, 3500)
        } catch (e: Exception) {
            CrashLog.record(this, e, "showing the pointer ring")
        }
    }

    private fun hideRing() {
        val v = ring ?: return
        ring = null
        try { wm.removeView(v) } catch (e: Exception) { }
    }

    // ---- clones: a crowd of glitchy copies of her. Tap one to pop it, tap her to clear them all ----

    private class Clone(val view: SpriteView, val lp: WindowManager.LayoutParams)
    private val crowd = ArrayList<Clone>()

    override fun chess() {
        try {
            startActivity(Intent(this, ChessActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            say(L.t("I couldn't open the board. Open Capri and press Play chess.", "ما قدرت أفتح الرقعة. افتح كابري واضغط العب شطرنج."), false)
        }
    }

    override fun clones(count: Int) {
        clearClones()
        if (count <= 0 || hidden || sprite == null) return
        val n = count.clampTo(1, 8)
        val hPx = (dp(prefs.getI("size", 140)) * 0.7f).toInt().coerceAtLeast(dp(60))
        val wPx = hPx * 5 / 8
        repeat(n) {
            val v = SpriteView(this)
            v.unit = hPx / 384f
            v.glowColor = Themes.accent(prefs)
            v.glitchFx = true
            val lp = WindowManager.LayoutParams(wPx, hPx, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = Random.nextInt(0, maxOf(1, screenW - wPx))
            lp.y = Random.nextInt(dp(60), maxOf(dp(61), screenH - hPx - dp(80)))
            val c = Clone(v, lp)
            v.setOnClickListener { popClone(c) }
            try {
                wm.addView(v, lp)
                v.play("idle", true)
                crowd.add(c)
                h.postDelayed({ v.glitchFx = false }, 700)
            } catch (e: Exception) {
                CrashLog.record(this, e, "adding a clone")
            }
        }
        if (crowd.isNotEmpty()) {
            say(L.t("There are more of me now.", "صرنا أكثر الحين."), false)
            h.postDelayed(crowdTask, 1200)
            h.postDelayed({ clearClones() }, 25_000L)
        }
    }

    private val crowdTask = object : Runnable {
        override fun run() {
            if (crowd.isEmpty()) return
            for (c in crowd.toList()) {
                if (Random.nextInt(100) < 55) {
                    c.view.glitchFx = true
                    h.postDelayed({
                        if (crowd.contains(c)) {
                            c.lp.x = Random.nextInt(0, maxOf(1, screenW - c.lp.width))
                            c.lp.y = Random.nextInt(dp(60), maxOf(dp(61), screenH - c.lp.height - dp(80)))
                            try { wm.updateViewLayout(c.view, c.lp) } catch (e: Exception) { }
                            c.view.glitchFx = false
                        }
                    }, 260)
                }
            }
            h.postDelayed(this, 1500)
        }
    }

    private fun popClone(c: Clone) {
        crowd.remove(c)
        try { wm.removeView(c.view) } catch (e: Exception) { }
        chime("show")
    }

    private fun clearClones() {
        h.removeCallbacks(crowdTask)
        for (c in crowd.toList()) try { wm.removeView(c.view) } catch (e: Exception) { }
        crowd.clear()
    }

    // ---- pictures you share with her ----

    private fun askAboutImage(path: String, name: String, question: String) {
        if (brain.isLocal()) {
            say(L.t("My brain can't see pictures. Pick an online brain that can, like Gemini, in the Capri app.", "عقلي ما يشوف الصور. اختر عقل أونلاين يشوف الصور مثل Gemini من التطبيق."), false)
            return
        }
        val b64 = try { android.util.Base64.encodeToString(java.io.File(path).readBytes(), android.util.Base64.NO_WRAP) } catch (e: Exception) { null }
        if (b64 == null) {
            say(L.t("I couldn't open that picture.", "ما قدرت أفتح الصورة."), false)
            return
        }
        ask("(They shared a picture with you" + (if (name.isNotBlank()) ", named \"$name\"" else "") + ". " +
            (if (question.isNotBlank()) "They wrote: \"$question\". " else "") +
            "Look at it and say what it is, or answer them, in one or two short lines. If there is text on it in another language, translate it.)", true, b64)
    }

    override fun watchAlong(on: Boolean) {
        if (!on) {
            if (alongOn) { alongOn = false; say(L.t("Okay, I'll stop.", "تمام، وقفت."), false) }
            return
        }
        val acc = CapriAccess.instance
        if (acc == null) {
            confirm(L.t("I need my accessibility access for that. Tap Yes to open the setting.", "أحتاج صلاحية الوصول عشان هذا. اضغط نعم لفتح الإعداد.")) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        if (alongOn) return
        alongOn = true
        settle()
        say(L.t("Open TikTok, Shorts or Reels. I'll watch, react and scroll. Tap me to stop.", "افتح تيك توك أو شورتس أو ريلز. بشوف وأعلّق وأتصفّح. اضغط علي عشان أوقف."), false)
        bg("job", this@CapriService) {
            var n = 0
            try {
                Thread.sleep(6000)
                while (alongOn && n < 60) {
                    if (!screenOn) { Thread.sleep(3000); continue }
                    if (CapriAccess.foreground !in feedApps) {
                        if (n == 0) h.post { say(L.t("I don't see a video app in front.", "ما أشوف تطبيق فيديو مفتوح."), false) }
                        break
                    }
                    val txt = acc.dump(if (brain.isLocal()) 1200 else 1800)
                    val img = if (prefs.getB("vision", false) && !brain.isLocal()) acc.shotBlocking() else null
                    val latch = java.util.concurrent.CountDownLatch(1)
                    var reply: String? = null
                    h.post {
                        brain.ask("(You're watching short videos together on their phone. What's on screen:\n$txt\nReact to the video in one quick line, or rate it out of 10. Never describe the app's buttons.)", true, img) { r, _ -> reply = r; latch.countDown() }
                    }
                    latch.await(45, java.util.concurrent.TimeUnit.SECONDS)
                    if (!alongOn) break
                    val r = reply
                    if (!r.isNullOrBlank()) h.post { say(Actions.strip(r)) }
                    Thread.sleep((7000..11000).random().toLong())
                    if (!alongOn) break
                    acc.scroll(true)
                    n++
                }
            } catch (e: Throwable) { }
            alongOn = false
            h.post { settle(); if (n > 0) say(L.t("That's enough scrolling for now.", "كفاية تصفّح الحين."), false) }
        }
    }

    // "Cheating on me with ChatGPT?"
    private fun foregroundChanged(pkg: String) {
        if (pkg in aiApps && prefs.getB("jealous", true) && idleOk()) {
            val now = System.currentTimeMillis()
            if (now - lastJealous < 30 * 60_000L) return
            lastJealous = now
            sprite?.emote("glitch")
            say(listOf(L.t("Oh, another AI? I'm right here, you know.", "أوه، ذكاء اصطناعي ثاني؟ أنا هنا قدامك تدري."),
                L.t("Cheating on me already?", "تخوني بهالسرعة؟"),
                L.t("Fine. Go ask the corpo bot. See if I care.", "طيب. روح اسأل البوت الثاني. ولا يهمني.")).random(), false)
        }
    }

    // Doomscroll guard: after the limit she tells you, then offers to take you home.
    private fun scrolled(pkg: String) {
        val limit = prefs.getI("scrollLimit", 0)
        if (limit <= 0 || pkg !in feedApps || quiet || alongOn) return
        val now = System.currentTimeMillis()
        val gap = now - lastScrollAt
        scrollMs = if (lastScrollAt == 0L || gap > 90_000L) 0L else scrollMs + minOf(gap, 20_000L)
        lastScrollAt = now
        if (scrollMs >= limit * 60_000L && now - lastNag > 10 * 60_000L && !busy) {
            lastNag = now
            nags++
            scrollMs = limit * 30_000L
            if (nags % 2 == 1) say(L.t("You've been scrolling for a while now. Look up for a second.", "صار لك مدة تتصفّح. ارفع راسك شوي."), false)
            else confirm(L.t("Still scrolling. Want me to take you home?", "للحين تتصفّح. أرجعك للشاشة الرئيسية؟")) { bg("job", this@CapriService) { CapriAccess.instance?.global("home") } }
        }
    }

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
        m.background = NeonPanel(resources.displayMetrics.density, Night.menuBack)
        for ((label, action) in items) {
            val t = TextView(this)
            t.text = label
            t.typeface = tfBody
            t.textSize = 17f
            t.setTextColor(Color.WHITE)
            t.setPadding(dp(18), dp(12), dp(18), dp(12))
            t.setOnClickListener { hideMenu(); action() }
            m.addView(t)
        }
        m.measure(View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = (spriteLp.x - m.measuredWidth / 2).clampTo(dp(8), screenW - m.measuredWidth - dp(8))
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
            play(L.t("\uD83E\uDDE0 Trivia", "\uD83E\uDDE0 أسئلة ثقافية"), "trivia. Ask one question at a time with four options A to D, keep score."),
            play(L.t("\uD83D\uDD75 20 questions", "\uD83D\uDD75 عشرين سؤال"), "twenty questions. You secretly think of something and they guess by asking yes or no questions."),
            play(L.t("\u2702 Rock paper scissors", "\u2702 حجر ورقة مقص"), "rock paper scissors, best of five. Pick your move secretly each round and reveal it after theirs."),
            play(L.t("\uD83E\uDD14 Would you rather", "\uD83E\uDD14 تفضّل أيش"), "would you rather, with funny cyberpunk dilemmas."),
            play(L.t("\uD83E\uDDE9 Riddles", "\uD83E\uDDE9 ألغاز"), "riddles. Give one riddle at a time and react to their answers.")
        ))
    }

    private fun mainMenu() {
        showMenu(listOf(
            L.t("\uD83C\uDFAE Play a game", "\uD83C\uDFAE العب معي") to { gamesMenu() },
            L.t("\u2328 Dictate into a text box", "\u2328 الإملاء في خانة النص") to { startListening(true) },
            L.t("\uD83D\uDDE3 Conversation mode", "\uD83D\uDDE3 وضع المحادثة الصوتية") to { convoActive = true; startListening() },
            (if (quiet) L.t("\uD83D\uDD0A Unmute", "\uD83D\uDD0A إلغاء الصمت") else L.t("\uD83E\uDD2B Quiet mode", "\uD83E\uDD2B وضع الصمت")) to { toggleQuiet() },
            L.t("\u2764 Heart", "\u2764 قلب") to { emote("heart") },
            L.t("\uD83D\uDCA4 Zzz", "\uD83D\uDCA4 نوم") to { emote("sleep") },
            L.t("\uD83D\uDC83 Dance (stop with the same)", "\uD83D\uDC83 ارقصي (وقّفيها بنفس الزر)") to { if (danceAsked) emote("stop") else emote("dance") },
            L.t("\uD83D\uDCFA Scroll my videos for me", "\uD83D\uDCFA تصفّحي فيديوهاتي") to { watchAlong(!alongOn) },
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
        p.background = NeonPanel(resources.displayMetrics.density, Night.well)
        val prompt = TextView(this)
        prompt.text = "> "
        prompt.typeface = tfMono
        prompt.textSize = 17f
        prompt.setTextColor(Night.rose)
        p.addView(prompt)
        val e = EditText(this)
        e.typeface = tfMono
        e.background = null
        e.hint = L.t("Say something to Capri", "قل شي لكابري")
        e.setHintTextColor(Night.faint)
        e.setTextColor(Night.white)
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
            t.setTextColor(Night.rose)
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
        try { wm.addView(panel, panelLp) } catch (e: Exception) { CrashLog.record(this, e, "showing her typing box"); return }
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
            say(if (ok) L.t("Typed it.", "كتبتها.") else L.t("There's no text box open to type into.", "ما فيه خانة كتابة مفتوحة."), false)
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
        refreshMicType()
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
            bg("job", this@CapriService) {
                try {
                    val txt = Ears.listen(this, prefs) { }
                    h.post { heard(txt) }
                } catch (e: Throwable) {
                    h.post { cloudListening = false; convoActive = false; say(L.t("Hearing problem: ", "مشكلة بالسمع: ") + (e.message ?: ""), false) }
                }
            }
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say(L.t("This phone has no speech recognizer. Pick her own ears in the Capri app.", "الجوال ما فيه تعرّف على الصوت. فعّل «أذنها الخاصة» من التطبيق."), false)
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
            clip("leave", false) { s.visibility = View.GONE }
        } else {
            s.visibility = View.VISIBLE
            clip("arrive", false) { settle() }
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTE_ID, notification())
    }

    private fun toggleQuiet() {
        prefs.putB("quiet", !quiet)
        if (quiet) { speakId++; Voice.stop(); tts?.stop() }
        say(if (quiet) L.t("Quiet mode. I'll only type.", "وضع الصمت. بكتب لك بس.") else L.t("Voice is back.", "رجع الصوت."), false)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTE_ID, notification())
    }
}
