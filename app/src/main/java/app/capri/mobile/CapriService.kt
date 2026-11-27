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
import android.os.BatteryManager
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
import kotlin.math.sqrt
import kotlin.random.Random

class CapriService : Service(), Actions.Hooks {
    companion object {
        @Volatile var running = false
        const val ACT_HIDE = "app.capri.mobile.HIDE"
        const val ACT_STOP = "app.capri.mobile.STOP"
        const val ACT_ASK = "app.capri.mobile.ASK"
        const val ACT_PLUGIN = "app.capri.mobile.PLUGIN_CMD"
        @Volatile var lastBrain = ""
        const val ACT_QUIET = "app.capri.mobile.QUIET"
        const val ACT_TEST = "app.capri.mobile.TEST"
        const val ACT_MODE = "app.capri.mobile.MODE"
        const val ACT_CHESS = "app.capri.mobile.CHESS"
        const val ACT_HUSK = "app.capri.mobile.HUSK"
        @Volatile var clipSaved: ((String?) -> Unit)? = null
        @Volatile var clipStopped: (() -> Unit)? = null
        const val ACT_TALK = "app.capri.mobile.TALK"
        const val ACT_TYPE = "app.capri.mobile.TYPE"
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
    @Volatile private var ownScrollAt = 0L      // when she last scrolled by herself
    @Volatile private var userScrollAt = 0L     // when YOU last scrolled: she lets you drive
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
        Stockfish.prepare(this)
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
        if (intent?.action == ACT_TALK) h.postDelayed({ if (hidden) toggleHidden(); convoActive = prefs.getB("convo", false); startListening() }, 300)
        if (intent?.action == ACT_TYPE) h.postDelayed({ if (hidden) toggleHidden(); showPanel() }, 300)
        if (intent?.action == ACT_HIDE) toggleHidden()
        if (intent?.action == ACT_QUIET) toggleQuiet()
        if (intent?.action == ACT_TEST) h.postDelayed({ hands("test") }, 900)
        if (intent?.action == ACT_CHESS) { val cm = intent.getStringExtra("mode") ?: "auto"; h.postDelayed({ if (hidden) toggleHidden(); if (cm == "check") chessCheck() else chessPilot(cm) }, if (cm == "check") 7000L else 700L) }
        if (intent?.action == ACT_HUSK) applyHusk()
        if (intent?.action == ACT_MODE) Modes.find(intent.getStringExtra("mode") ?: "")?.let { m -> applyModeLive(m); say(L.t(m.sayEn, m.sayAr), false) }
        if (intent?.action == ACT_PLUGIN && prefs.getB("pluginApi", false)) {
            val pt = (intent.getStringExtra("text") ?: "").take(500)
            val pc = intent.getStringExtra("cmd")
            h.postDelayed({
                if (hidden) toggleHidden()
                if (pc == "say") say(pt) else brain.ask("(Another app on this phone passed you this: \"${pt.replace("\"", "'")}\". Answer in one short line. Use no tags.)", true, null, false) { r, _ -> if (r != null) say(Actions.strip(r)) }
            }, 600)
        }
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
        stopShake(); husk?.stop(); clipSaved = null; chairShowing?.stop()
        h.removeCallbacksAndMessages(null)
        Choreographer.getInstance().removeFrameCallback(frame)
        ReminderReceiver.speaker = null
        pilot?.stop(); gamePilot?.stop()
        ChessBridge.speaker = null
        ChessBridge.explainer = null
        try { miniPlayer.hide() } catch (e: Throwable) { }
        CapriAccess.onForeground = null
        CapriAccess.onScrolled = null
        alongOn = false
        notifier = null
        try { unregisterReceiver(events) } catch (e: Exception) { }
        Ears.cancel()
        hideMenu(); hidePill()
        Leftovers.sweep(wm)
        clearClones()
        hideRing()
        stopWake()
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
        s.setOnTouchListener { v, e -> engagedAt = System.currentTimeMillis(); touch.onTouch(v, e) }
        s.alpha = 0f
        wm.addView(s, spriteLp)
        s.animate().alpha(1f).setDuration(400).start()

        buildBubble()
        bubbleLp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        agent = Agent(this, brain, prefs,
            { line -> h.post { jobNote = line; if (jobSince == 0L) jobSince = System.currentTimeMillis(); say(line, false) } },
            { line -> h.post { jobSince = 0L; jobNote = ""; busy = false; say(line); settle() } })
        ReminderReceiver.speaker = { t -> h.post { say(t) } }
        ChessBridge.speaker = { t -> h.post { say(t) } }
        ChessBridge.explainer = { prompt, cb -> h.post { brain.ask(prompt, true) { r, _ -> cb(r?.let { Actions.strip(it) }) } } }
        CapriAccess.onForeground = { p -> h.post { foregroundChanged(p) } }
        CapriAccess.onScrolled = { p -> h.post { scrolled(p) } }
        notifier = { line -> h.post { if (!quiet && !hidden && !busy) say(line) } }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_BATTERY_LOW); addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_HEADSET_PLUG); addAction(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(events, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(events, filter)

        if (!prefs.getB("lowPower", false)) startShake()
        applyHusk()
        clipSaved = { name -> if (name != null) { sprite?.emote("star"); say(L.t("Clip saved in Movies/Capri.", "انحفظ المقطع بـ Movies/Capri."), false) } else say(L.t("I couldn't save the clip.", "ما قدرت أحفظ المقطع."), false) }
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
        tendrils()
        if (prefs.getB("intro", true) && !prefs.getB("lowPower", false) && Settings.canDrawOverlays(this)) {
            val now = System.currentTimeMillis()
            val plan = IntroPlan.pick(prefs.getS("introStyle", "random"), prefs.getL("introLast", 0L), now, (now / 1000).toInt())
            try {
                if (plan == "peek" || plan == "close") { prefs.putL("introLast", now); CinemaIntro(this, plan).play(wm, h) }
                else if (plan == "glitch") IntroView(this, Themes.accent(prefs)).play(wm, h)
            } catch (e: Throwable) { }
        }
        poke()
        h.postDelayed(blinkTask, 4000)
        h.postDelayed(autoModeTask, 30_000L)
        h.postDelayed(wanderTask, 8_000L)
        h.postDelayed(unstickTask, 2000)
        h.postDelayed(wakeTask, 3000)
        h.postDelayed(musicTask, 5000)
        h.postDelayed(watchTask, 60_000L)
        h.postDelayed(checkInTask, 20 * 60_000L)
        h.postDelayed(careTask, 60_000L)
        h.postDelayed(mischiefTask, 30 * 60_000L)
        h.postDelayed(charmTask, 10 * 60_000L)
        h.postDelayed(queueTask, 45_000L)
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
        if (scripted || perchLeaving) return
        // with her talking lie-down art she keeps lying while she speaks, like on the desktop; without it she sits up first
        if (lying && ((talking && !has("laydown_talk")) || busy || dragging || mode != 0 || asleep)) lying = false
        s.glow = talking && !s.lowPower
        s.sparkles = (Charm.level(prefs.getI("charm", 0)) - 1).coerceIn(0, 4)
        s.glitchFx = busy || mode == 1
        s.talk = talking
        s.dancing = (dancing || danceAsked) && !talking && !busy && !asleep && mode == 0 && !scripted
        val pick = when {
            dragging || mode == 1 -> "dangle"
            mode == 2 -> "walk"
            hangPending || hanging -> {
                if (hangPending) { hangPending = false; hanging = true; h.postDelayed(hangDrop, Random.nextLong(7_000, 15_000)) }
                "dangle"
            }
            mode == 2 -> "walk"
            busy -> "glitch"
            asleep -> if (grounded()) "laydown_sleep" else "sleep"
            perchPending || perched -> {
                var intro = false
                if (perchPending) {
                    perchPending = false; perched = true; intro = has("perch_arrive")
                    h.postDelayed(perchEnd, Random.nextLong(45_000, 110_000))
                    if (has("laydown")) h.postDelayed(lieTask, Random.nextLong(12_000, 30_000))
                }
                when {
                    intro -> "perch_arrive"
                    lying -> if (talking && has("laydown_talk")) "laydown_talk" else "laydown"
                    (dancing || danceAsked) && has("perch_music") -> "perch_music"
                    talking && has("perch_talk") -> "perch_talk"
                    else -> "perch"
                }
            }
            else -> "idle"
        }
        val once = pick == "perch_arrive"
        clip(pick, !once) { if (once) settle() }
    }

    // "Classic movement" in Settings turns the desktop-style extras off (arrive/leave, lying down, glitch blinks, hanging, window perch, shake) if one ever misbehaves.
    private val pcClips = setOf("perch_arrive", "perch_leave", "perch_glitch", "perch_blink_glitch", "perch_music", "idle_blink_glitch", "laydown", "laydown_blink", "laydown_blink_glitch", "laydown_glitch", "laydown_leave", "perch_talk", "laydown_talk")
    private fun pcMoves() = prefs.getB("pcMoves", true)
    private fun has(name: String) = (name !in pcClips || pcMoves()) && SpriteBank.info(this, name) != null

    // She sometimes walks to the bottom of the screen and sits there, legs hanging over the edge, like her perch on the desktop.
    private var perched = false
    private var perchPending = false
    private var lying = false
    private var perchLeaving = false
    private val perchEnd: Runnable = Runnable {
        if (perched && !perchLeaving) {
            val out = when { lying && has("laydown_leave") -> "laydown_leave"; has("perch_leave") -> "perch_leave"; else -> "" }
            if (out.isEmpty()) { perched = false; lying = false; settle() }
            else {
                perchLeaving = true
                clip(out, false) {
                    perchLeaving = false
                    if (lying) { lying = false; h.post(perchEnd) } else { perched = false; settle() }
                }
            }
        }
    }

    // After sitting a while she lies back along the ledge, like on the desktop, then sits up again.
    private val lieTask = Runnable {
        if (perched && !lying && !perchLeaving && !talking && !busy && !dragging && mode == 0 && !asleep) {
            lying = true
            settle()
            h.postDelayed(standTask, Random.nextLong(20_000, 40_000))
        }
    }
    private val standTask = Runnable {
        if (lying && !perchLeaving) {
            perchLeaving = true
            clip(if (has("laydown_leave")) "laydown_leave" else "perch", false) { perchLeaving = false; lying = false; settle() }
        }
    }

    private fun endPerch() {
        perched = false; perchPending = false; lying = false; perchLeaving = false
        h.removeCallbacks(perchEnd); h.removeCallbacks(lieTask); h.removeCallbacks(standTask)
    }

    // She can also sit on the top edge of something in the app you have open: a toolbar, a card, a picture, a list row.
    private var windowPerched = false
    private var windowPerchPkg = ""
    private fun dropFromWindow() {
        if (!windowPerched) return
        windowPerched = false
        endPerch(); vx = 0f; vy = 0f; startMotion(1)
    }

    private fun perchSomewhere(onWindow: Boolean = false) {
        val sp = sprite ?: return
        if (sp.sizeFor("perch").second <= 0 || SpriteBank.info(this, "perch") == null) return
        fx = spriteLp.x.toFloat()
        fy = spriteLp.y.toFloat()
        var tx = Random.nextFloat() * (maxX() - minX()) + minX()
        var ty = (floorY() + dp(18)).coerceAtMost((screenH - spriteLp.height).toFloat())
        windowPerched = false
        val acc = CapriAccess.instance
        if (acc != null && CapriAccess.foreground.isNotEmpty() && prefs.getB("windowPerch", true) && pcMoves() && !gameRunning() && (onWindow || (prefs.getI("perchBias", 0) > 0 && Random.nextInt(100) < 60))) {
            val ledges = try { acc.ledges() } catch (e: Throwable) { emptyList() }
            if (ledges.isNotEmpty()) {
                val r = ledges.random()
                tx = (r.left + Random.nextFloat() * r.width() - spriteLp.width / 2f).coerceIn(minX(), maxX())
                // her seat sits about 60% down the perch frame, so her legs hang over the edge
                // ledge coordinates are screen pixels; her window starts below the status bar, so measure the difference
                val loc = IntArray(2); try { sp.getLocationOnScreen(loc) } catch (e: Exception) { }
                val off = (loc[1] - spriteLp.y).coerceAtLeast(0)
                ty = (r.top - off - spriteLp.height * 0.6f).coerceIn(dp(24).toFloat(), floorY())
                windowPerched = true; windowPerchPkg = CapriAccess.foreground
            }
        }
        walkTx = tx
        walkTy = ty
        walkTotal = maxOf(1f, hypot(walkTx - fx, walkTy - fy))
        sprite?.flip = walkTx < fx
        perchPending = true
        startMotion(2)
    }

    private val blinkTask = object : Runnable {
        override fun run() {
            val s = sprite
            if (s != null && !asleep && !busy && !dragging && !hidden && !scripted && !perchLeaving && mode == 0 && (s.current == "idle" || s.current == "perch" || s.current == "laydown")) {
                val glitchy = Random.nextInt(100) < 22
                val rare = Random.nextInt(100) < 8
                val pick = when (s.current) {
                    "perch" -> if (rare && has("perch_glitch")) "perch_glitch" else if (glitchy && has("perch_blink_glitch")) "perch_blink_glitch" else "perch_blink"
                    "laydown" -> if (rare && has("laydown_glitch")) "laydown_glitch" else if (glitchy && has("laydown_blink_glitch")) "laydown_blink_glitch" else "laydown_blink"
                    else -> if (glitchy && has("idle_blink_glitch")) "idle_blink_glitch" else "idle_blink"
                }
                if (has(pick)) clip(pick, false) { settle() }
            }
            h.postDelayed(this, Random.nextLong(3500, 8000))
        }
    }

    // Automatic modes, once a minute, only when the switch is on (HOME > MODE).
    private val autoModeTask = object : Runnable {
        override fun run() {
            try { if (prefs.getB("autoModes", false)) autoModeTick() } catch (e: Throwable) { }
            h.postDelayed(this, 60_000L)
        }
    }

    private fun autoModeTick() {
        val b = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val st = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        val pct = if (lvl >= 0 && scale > 0) lvl * 100 / scale else -1
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val want = AutoModes.wanted(AutoModes.Input(hour, pct, charging, CapriAccess.foreground))
        val cur = Modes.current(prefs).id
        val step = AutoModes.step(cur, prefs.getS("autoBy", ""), want)
        if (step.forget) { prefs.putS("autoBy", ""); return }
        if (step.apply != null) {
            if (prefs.getS("autoBy", "").isEmpty()) prefs.putS("autoPrev", cur)
            prefs.putS("autoBy", step.apply); mode(step.apply)
        } else if (step.restore) {
            val back = prefs.getS("autoPrev", "normal"); prefs.putS("autoBy", ""); mode(back)
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

    private val miniPlayer by lazy { MiniPlayer(this, wm, h) }

    private val musicTask = object : Runnable {
        override fun run() {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val now = prefs.getB("dance", true) && am.isMusicActive && !talking && !hidden
            if (now != dancing) {
                dancing = now
                if (!asleep) settle()
            }
            miniPlayer.tick(am.isMusicActive && prefs.getB("miniPlayer", true) && !hidden && !quiet && screenOn)
            h.postDelayed(this, 3000)
        }
    }

    // ---- shake: she gets dizzy, like the desktop's dizzy glitch when she's thrown around ----
    private var shakeHits = 0
    private var shakeFirst = 0L
    private var lastShakeSay = 0L
    private val shakeListener = object : android.hardware.SensorEventListener {
        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        override fun onSensorChanged(e: android.hardware.SensorEvent) {
            val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / 9.81f
            if (g < 2.6f) return
            val now = System.currentTimeMillis()
            if (now - shakeFirst > 1800) { shakeFirst = now; shakeHits = 0 }
            shakeHits++
            if (shakeHits == 6) h.post { dizzy() }
        }
    }
    private fun dizzy() {
        if (hidden || scripted || perchLeaving || gameRunning() || !screenOn || busy || pilot?.running == true) return
        val now = System.currentTimeMillis()
        if (now - lastShakeSay < 12_000) return
        lastShakeSay = now
        sprite?.emote("glitch"); sprite?.squash(1.3f)
        clip("glitch", false) { settle() }
        if (!quiet) say(listOf(L.t("Whoa, whoa, I'm getting dizzy!", "واااو واو، دوخت!"), L.t("Stop shaking me, choom!", "لا تهزني يا چوم!"),
            L.t("My pixels are rattling.", "البكسلات تتخرعت."), L.t("Rude. I'm glitching now.", "قليل أدب. صرت أعلق.")).random(), false)
    }
    private fun startShake() {
        if (!pcMoves()) return
        try {
            val sm = getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager
            sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)?.let { sm.registerListener(shakeListener, it, android.hardware.SensorManager.SENSOR_DELAY_UI) }
        } catch (e: Throwable) { }
    }
    private fun stopShake() { try { (getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager).unregisterListener(shakeListener) } catch (e: Throwable) { } }

    // She climbs to the top edge and dangles by her hands for a while, then drops, like the desktop's hang.
    private var hanging = false
    private var hangPending = false
    private fun hangSomewhere() {
        if (!has("dangle") || !pcMoves()) return
        fx = spriteLp.x.toFloat(); fy = spriteLp.y.toFloat()
        walkTx = Random.nextFloat() * (maxX() - minX()) + minX()
        walkTy = dp(6).toFloat()
        walkTotal = maxOf(1f, hypot(walkTx - fx, walkTy - fy))
        sprite?.flip = walkTx < fx
        hangPending = true
        startMotion(2)
    }
    private val hangDrop = Runnable {
        if (hanging && !dragging) { hanging = false; vx = 0f; vy = 0f; startMotion(1) } else hanging = false
    }

    // A few seconds of dancing, for party moods.
    private fun danceBurst() {
        if (danceAsked) return
        danceAsked = true; settle()
        h.postDelayed({ if (danceAsked) { danceAsked = false; settle() } }, Random.nextLong(7_000, 14_000))
    }

    // "Explores while you're away", like the desktop: she scrolls the page you left open and comments, until the screen turns off,
    // you tap her, or ten minutes pass.
    private var exploring = false
    private val exploreTask = object : Runnable {
        override fun run() {
            if (!exploring) return
            val acc = CapriAccess.instance
            if (!screenOn || acc == null || System.currentTimeMillis() - exploreStart > 10 * 60_000L) { exploreAway(false); return }
            if (!talking && !busy && !dragging && !panelShown && !agent.running && !gameRunning()) {
                acc.scroll(Random.nextInt(100) < 85)
                if (Random.nextInt(100) < 25) { sprite?.hop(); if (!quiet) say(listOf(
                    L.t("Ooh, what's down here?", "اوه، شنو هنا؟"), L.t("Scrolling your page, choom.", "أتصفح لك الصفحة، يا چوم."),
                    L.t("Interesting...", "ممم مثير..."), L.t("I'm just looking around.", "بس أطالع حولي.")).random(), false) }
            }
            h.postDelayed(this, Random.nextLong(7_000, 14_000))
        }
    }
    private var exploreStart = 0L
    private fun exploreAway(on: Boolean) {
        if (on == exploring) return
        if (on) {
            if (CapriAccess.instance == null) {
                confirm(L.t("I need my accessibility access to look around. Tap Yes to open the setting.", "أحتاج صلاحية الوصول عشان أتجول. اضغط نعم لفتح الإعداد.")) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                return
            }
            exploring = true; exploreStart = System.currentTimeMillis()
            say(L.t("Okay, I'll look around while you're away. Tap me to stop.", "تمام، بتجول وانت بعيد. اضغط علي عشان أوقف."), false)
            h.postDelayed(exploreTask, 6_000)
        } else {
            exploring = false; h.removeCallbacks(exploreTask)
            say(L.t("Done exploring.", "خلصت تجوال."), false)
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

    // Wellbeing nudges, with canned lines so they cost nothing. The timing is Care.kt (ported from capri-desktop).
    @Volatile private var jobNote = ""
    @Volatile private var jobSince = 0L
    private val care = Care()
    private var screenOffAt = 0L
    private val careCfg = Care.Cfg(
        { k -> prefs.getB(careKey(k), false) },
        { k -> prefs.getI("careMin_" + k.name, Care.defaultMins(k)).coerceAtLeast(if (k == CareKind.LATE) 0 else 5) })
    private fun careKey(k: CareKind) = when (k) {
        CareKind.WATER -> "careWater"; CareKind.STRETCH -> "careStretch"; CareKind.LATE -> "careLate"
        CareKind.EYES -> "careEyes"; CareKind.MEAL -> "careMeal"; CareKind.GAME -> "careGame"; CareKind.BORED -> "careBored"
    }

    private val careTask = object : Runnable {
        override fun run() {
            try {
                val cal = java.util.Calendar.getInstance()
                val now = System.currentTimeMillis()
                val day = ((now + cal.timeZone.getOffset(now)) / 86_400_000L).toInt()
                val away = if (screenOn) 0L else now - screenOffAt
                val fg = CapriAccess.foreground
                val playing = if (screenOn && GamePkgs.isGame(fg)) fg else null
                val k = care.check(now, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), day, away,
                    now - engagedAt, playing, careCfg, idleOk())
                if (k != null) say(CareLines.pick(k, L.ar, Random.nextInt(1000)))
            } catch (e: Throwable) { CrashLog.record(this@CapriService, e, "care") }
            h.postDelayed(this, 60_000L)
        }
    }

    // Mischief mode: a harmless fake glitch now and then.
    private val mischiefTask = object : Runnable {
        override fun run() {
            if (prefs.getB("mischief", false) && idleOk() && Random.nextInt(100) < 60) chaos()
            h.postDelayed(this, Random.nextLong(30 * 60_000L, 90 * 60_000L))
        }
    }

    // Messages that couldn't be sent for lack of internet are tried again when it comes back.
    @Volatile private var replaying = false

    private val queueTask = object : Runnable {
        override fun run() {
            try {
                val q = prefs.getS("queue", "")
                if (q.isNotBlank() && idleOk() && online()) {
                    OfflineQueue.pop(q)?.let { (first, rest) ->
                        prefs.putS("queue", rest)
                        replaying = true
                        ask(first)
                    }
                }
            } catch (e: Throwable) { CrashLog.record(this@CapriService, e, "offline queue") }
            h.postDelayed(this, 45_000L)
        }
    }

    private fun online(): Boolean = try {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.let { it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) && it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) } == true
    } catch (e: Throwable) { false }

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
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; screenOffAt = System.currentTimeMillis(); Voice.stop(); tts?.stop(); stopShake() }
                Intent.ACTION_SCREEN_ON -> { screenOn = true; if (!prefs.getB("lowPower", false)) startShake() }
                Intent.ACTION_BATTERY_LOW -> if (prefs.getB("events", true) && idleOk())
                    say(L.t("You're nearly out of battery. Find a charger, gonk.", "البطارية على وشك تخلص. دوّر لك شاحن يا خوي."))
                Intent.ACTION_POWER_DISCONNECTED -> if (prefs.getB("events", true) && idleOk() && Random.nextInt(100) < 25)
                    say(L.t("Unplugged. Don't wander far.", "فصلت الشاحن. لا تبعد."), false)
                Intent.ACTION_HEADSET_PLUG -> if (prefs.getB("events", true) && idleOk() && Random.nextInt(100) < 50) {
                    if (i.getIntExtra("state", 0) == 1) { sprite?.emote("note"); say(L.t("Headphones in. Put on something good.", "السماعة انحطت. شغّل شي حلو."), false) }
                    else say(L.t("Headphones out. Everyone can hear me now.", "طلعت السماعة. الكل يسمعني الحين."), false)
                }
                android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY -> sprite?.hop()
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
                settle()
                return
            }
            if (mode != 0) choreo.postFrameCallback(this)
        }
    }

    private fun startMotion(m: Int) {
        if (m != 2 || !perchPending) endPerch()
        if (m != 2 || !hangPending) { if (hanging && m != 1) { hanging = false; h.removeCallbacks(hangDrop) } }
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
                if (vy > 500f * d) s.post { s.squash(minOf(1.4f, vy / (1500f * d))) }
                vy = -vy * 0.42f
                vx *= 0.72f
                if (abs(vy) < 300f * d) vy = 0f
            }
            if (fy >= floor - 1f && vy == 0f && abs(vx) < 40f * d) { mode = 0; s.lean(0f); settle() }
        } else if (mode == 2) {
            val dx = walkTx - fx
            val dy = walkTy - fy
            val dist = hypot(dx, dy)
            val top = dp(75) * (1f + prefs.getI("wanderPace", 1) * 0.6f)
            // ease in over the first stretch and ease out over the last, like she is floating rather than sliding
            val ramp = minOf(1f, 0.25f + (walkTotal - dist) / dp(90), 0.2f + dist / dp(130)).coerceIn(0.2f, 1f)
            val sp = if (asleep) top * 1.6f else top * ramp
            if (dist < sp * dt + 2f) {
                fx = walkTx; fy = walkTy
                mode = 0
                s.lean(0f)
                settle()
            } else {
                fx += dx / dist * sp * dt
                fy += dy / dist * sp * dt
                s.lean((dx / dist * 5f * (sp / top).coerceAtMost(1f)))
            }
        }
        val nx = fx.toInt()
        val ny = fy.toInt()
        if (nx != spriteLp.x || ny != spriteLp.y) {
            spriteLp.x = nx
            spriteLp.y = ny
            try { wm.updateViewLayout(s, spriteLp) } catch (e: Exception) { }
            if (bubbleShown) placeBubble()
        }
        if (mode == 0) { prefs.putI("x", spriteLp.x); prefs.putI("y", spriteLp.y) }
    }

    private fun canRoam() = screenOn && !scripted && !perchLeaving && !gameRunning() && !dancing && !danceAsked && !alongOn && !hanging && !asleep && !talking && !busy && !dragging && !hidden && !panelShown && mode == 0 && !agent.running && pilot?.running != true

    private val wanderTask = object : Runnable {
        override fun run() {
            val pace = prefs.getI("wanderPace", 1)
            if (pace > 0 && canRoam()) {
                val r = Random.nextInt(100)
                val ledge = prefs.getI("perchBias", 0) > 0 && has("perch")
                val party = prefs.getI("wanderMix", 0) == 2
                when {
                    party && r < 18 -> danceBurst()
                    party && r < 34 -> hangSomewhere()
                    party && r < 52 -> teleport()
                    party && r < 70 -> walkSomewhere()
                    !ledge && !party && r in 90..93 -> hangSomewhere()
                    ledge && r < 15 -> walkSomewhere()
                    ledge && r < 85 -> perchSomewhere()
                    r < 52 -> walkSomewhere()
                    r < 62 -> teleport()
                    r < 76 -> perchSomewhere()
                    r < 90 -> clip("grin", false) { settle() }
                    else -> { /* just stays put this time */ }
                }
            }
            h.postDelayed(this, Random.nextLong(6_000, 15_000) / maxOf(1, pace))
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
        h.postDelayed({ if (scripted && !dragging) { scripted = false; settle() } }, 5000)
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

    // Safety net: if anything ever leaves her frozen (a flag that never cleared, a clip that stopped on its last frame), this frees her.
    private var stuckScripted = 0
    private var stuckTalking = 0
    private var stuckBusy = 0
    private var stuckMode = 0
    private var stuckLeave = 0
    private val unstickTask = object : Runnable {
        override fun run() {
            stuckScripted = if (scripted) stuckScripted + 1 else 0
            stuckTalking = if (talking) stuckTalking + 1 else 0
            stuckBusy = if (busy && !agent.running) stuckBusy + 1 else 0
            stuckMode = if (mode != 0 && !dragging) stuckMode + 1 else 0
            if (stuckScripted > 4) { scripted = false; stuckScripted = 0; Diag.log("unstick", "scripted") }
            if (stuckTalking > 30) { talking = false; stuckTalking = 0; Diag.log("unstick", "talking") }
            if (stuckBusy > 75) { busy = false; stuckBusy = 0; Diag.log("unstick", "busy") }
            if (stuckMode > 10) { mode = 0; stuckMode = 0; Diag.log("unstick", "mode") }
            stuckLeave = if (perchLeaving) stuckLeave + 1 else 0
            if (stuckLeave > 5) { perchLeaving = false; stuckLeave = 0; if (lying) lying = false; else perched = false; settle(); Diag.log("unstick", "perchLeaving") }
            val sp = sprite
            if (sp != null && !hidden && !scripted && !dragging && mode == 0 && sp.stalled) settle()
            h.postDelayed(this, 2000)
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
                        endPerch()
                        sprite?.emote("surprise")
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
        if (exploring) { exploreAway(false); return }
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
            if (Random.nextInt(3) == 0) { sprite?.emote("glitch"); clip("glitch", false) { settle() } } else clip("grin", false) { settle() }
        }
        if (panelShown) hidePanel()
        else if (pill != null) hidePill()
        else if (prefs.getB("tapPill", true)) showPill()
        else showPanel()
    }

    // The pencil and wave pill under her (like capri-desktop). Pencil opens the type bar, wave starts listening.
    // Crimson glitch tendrils behind her when she beams in (the desktop's startup glitch); Settings > "Glitch in" turns it off.
    private fun tendrils() {
        if (!prefs.getB("glitchTendrils", true) || prefs.getB("lowPower", false) || hidden || !Settings.canDrawOverlays(this)) return
        try {
            val size = (spriteLp.width * 2.4f).toInt()
            Tendrils(this).play(wm, h, spriteLp.x + spriteLp.width / 2, spriteLp.y + spriteLp.height / 2, size)
        } catch (e: Throwable) { }
    }

    private var pill: View? = null
    private fun hidePill() {
        pill?.let { try { wm.removeView(it) } catch (e: Exception) { } }
        pill = null
    }
    private fun showPill() {
        hidePill(); hideMenu()
        val v = CapriPill(this, { hidePill(); showPanel() }, { hidePill(); convoActive = false; startListening() })
        v.measure(View.MeasureSpec.makeMeasureSpec(screenW, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = (spriteLp.x + spriteLp.width / 2 - v.measuredWidth / 2).clampTo(dp(8), (screenW - v.measuredWidth - dp(8)).coerceAtLeast(dp(8)))
        var y = spriteLp.y + spriteLp.height - dp(4)
        if (y + v.measuredHeight > screenH - dp(24)) y = spriteLp.y - v.measuredHeight - dp(4)
        lp.y = y
        v.setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_OUTSIDE) hidePill(); false }
        try { wm.addView(v, lp) } catch (e: Exception) { return }
        pill = v
        v.glitchIn()
        h.postDelayed({ if (pill === v) hidePill() }, 7000L)
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
        body.textSize = 15f
        body.setLineSpacing(0f, 1.12f)
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
        placeBubble()
    }

    // The box's size is worked out once per page, from the WHOLE page with no cursor, and then frozen.
    // (Before, it was re-measured while the letters typed in and the cursor blinked, so it grew and shrank and jumped around.)
    private var lockKey = 0
    private var lockW = 0
    private var lockH = 0
    private var lastTip = -1f
    private var lastDown: Boolean? = null

    private fun bubbleSize(pl: SpeechPlate): Pair<Int, Int> {
        val b = bubble ?: return Pair(0, 0)
        val bt = bubbleText ?: return Pair(0, 0)
        val key = ((if (thinking) "." else typingFull) + "|" + heard.take(70) + "|" + (buttonsRow?.visibility ?: 0) + "|" + pl.tailDown + "|" + screenW).hashCode()
        if (key == lockKey && lockW > 0) return Pair(lockW, lockH)
        val tail = pl.tail.toInt()
        b.setPadding(dp(26), dp(11) + (if (pl.tailDown) 0 else tail), dp(14), dp(11) + (if (pl.tailDown) tail else 0))
        val saved = bt.text
        val page = if (thinking) "" else typingFull
        bt.text = bubbleSpan(page, page.length, true, false)
        b.measure(View.MeasureSpec.makeMeasureSpec(screenW - dp(16), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        bt.text = saved
        lockKey = key
        lockW = b.measuredWidth
        lockH = b.measuredHeight
        return Pair(lockW, lockH)
    }

    // Where the plate goes: above her head with the tail pointing down, or below her when there's no room.
    private fun placeBubble() {
        val b = bubble ?: return
        val pl = plate ?: return
        var (bw, bh) = bubbleSize(pl)
        if (bw <= 0) return
        val headX = spriteLp.x + spriteLp.width / 2
        val headY = spriteLp.y + (spriteLp.height * 0.08f).toInt()
        // a little hysteresis, so it does not flip above/below back and forth while she walks near the top
        val above = headY - bh - dp(2) >= (if (pl.tailDown) dp(24) else dp(64))
        if (above != pl.tailDown) {
            pl.configure(above, pl.tipX)
            val s2 = bubbleSize(pl)
            bw = s2.first
            bh = s2.second
        }
        val x = (headX - bw / 2).clampTo(dp(8), screenW - bw - dp(8))
        val y = (if (above) headY - bh - dp(2) else spriteLp.y + spriteLp.height - dp(30)).clampTo(dp(24), screenH - bh - dp(8))
        val tip = ((headX - x).toFloat() / maxOf(1, bw)).clampTo(0.1f, 0.9f)
        if (lastDown != above || abs(tip - lastTip) > 0.01f) {
            pl.configure(above, tip)
            lastDown = above
            lastTip = tip
        }
        if (bubbleLp.x != x || bubbleLp.y != y) {
            bubbleLp.x = x
            bubbleLp.y = y
            if (bubbleShown) try { wm.updateViewLayout(b, bubbleLp) } catch (e: Exception) { }
        }
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

    // The styled text of the box: red bold "CAPRI:" label, the page, and the cursor. Used both to draw and to measure,
    // so the box is always sized for exactly what is shown (a label in bold or a cursor glyph can change the line height).
    private fun bubbleSpan(page: String, pos: Int, showCaret: Boolean, typing: Boolean): android.text.SpannableStringBuilder {
        val speaker = "CAPRI: "
        val full = speaker + page + " "
        val sb = android.text.SpannableStringBuilder(full)
        sb.setSpan(ForegroundColorSpan(Night.sRed), 0, speaker.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, speaker.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val caret = speaker.length + pos.coerceIn(0, page.length)
        if (showCaret && caret < full.length) sb.replace(caret, caret + 1, "\u2588")
        if (typing && caret + 1 < full.length) sb.setSpan(ForegroundColorSpan(Color.TRANSPARENT), caret + 1, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sb
    }

    // The answer as it arrives (streaming): shown in place of the blinking cursor, then kept on screen when the full text lands.
    private var liveText = ""
    private var previewShown = false
    private fun livePreview(full: String) {
        if (!busy || !thinking || hidden) return
        val p = Sse.preview(full)
        if (p.isEmpty()) return
        liveText = p.takeLast(420); previewShown = true
        renderBubble(); placeBubble()
    }

    private fun renderBubble() {
        val bt = bubbleText ?: return
        val hv = heardView ?: return
        if (heard.isNotEmpty()) { hv.visibility = View.VISIBLE; hv.text = "> " + heard.take(70) } else hv.visibility = View.GONE
        val page = if (thinking) liveText else typingFull
        val typing = !thinking && typingPos < page.length
        bt.text = bubbleSpan(page, if (thinking) liveText.length else typingPos, typing || cursorOn, typing)
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
        typingPos = if (sprite?.lowPower == true || previewShown) typingFull.length else 0
        previewShown = false; liveText = ""
        thinking = false
        renderBubble()
        placeBubble()
        h.removeCallbacks(typeTask)
        if (typingPos < typingFull.length) h.postDelayed(typeTask, 26)
        h.removeCallbacks(hideBubbleTask)
        h.postDelayed(hideBubbleTask, Limits.holdMs(typingFull.length, prefs.getI("bubbleTime", 1)))
    }

    private fun splitPages(text: String): List<String> = BubbleText.pages(text)

    // The ... of thinking is shown as the speaker label with a blinking cursor.
    private fun showBubble(text: String, ms: Long, typed: Boolean = true) {
        val b = bubble ?: return
        if (hidden) return
        h.removeCallbacks(typeTask)
        val first = !bubbleShown
        if (text == "...") {
            liveText = ""; previewShown = false
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
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, VoiceGain.player(prefs.getI("ttsVolume", 100)))
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "capri")
            // if the engine never reports "done" (interrupted, crashed, muted) she must not stay frozen mid-sentence
            val mine = speakId
            h.postDelayed({ if (mine == speakId && talking) talkDone() }, (text.length * 110L + 5000L).coerceAtMost(90_000L))
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
                    if (err != "phone") Toast.makeText(this, L.t("Voice problem, using the phone voice: ", "مشكلة في الصوت، باستخدم صوت الجوال: ") + err, Toast.LENGTH_LONG).show()
                    phoneSpeak(text)
                }
            }
        }
    }

    private fun ask(text: String, hiddenPrompt: Boolean = false, image: String? = null, skipNothing: Boolean = false) {
        engagedAt = System.currentTimeMillis()
        poke()
        if (!hiddenPrompt) {
            Charm.add(prefs, 2)
            if (Crisis.means(text)) {
                val nowMs = System.currentTimeMillis()
                if (Crisis.due(nowMs, prefs.getL("crisisAt", 0L))) {
                    prefs.putL("crisisAt", nowMs)
                    say(Crisis.line(java.util.Locale.getDefault().country, L.ar))
                    return
                }
            }
            // while her hands are busy, a plain "stop" or "how's it going" is answered at once, with no brain
            if (agent.running || gameRunning() || pilot?.running == true) when (Asides.classify(text)) {
                Asides.Kind.STOP -> {
                    agent.cancel(); pilot?.stop(); gamePilot?.stop()
                    say(L.t("Okay, stopping.", "تمام، وقفت."), false)
                    return
                }
                Asides.Kind.PROGRESS -> {
                    val since = if (jobSince > 0) System.currentTimeMillis() - jobSince else 0L
                    say(Asides.status(jobNote, since, L.ar, Random.nextInt(1000)), false)
                    return
                }
                null -> {}
            }
            val quick = Skills.match(prefs.getS("skills", ""), text) ?: QuickAsks.match(text)
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
        brain.ask(text, hiddenPrompt, image, true, if (!hiddenPrompt && !skipNothing) { full -> livePreview(full) } else null) { reply, err ->
            busy = false
            when {
                reply != null && skipNothing && reply.trim().uppercase().startsWith("NOTHING") -> settle()
                reply != null && hiddenPrompt && reply.trim().equals("NOTHING", ignoreCase = true) -> settle()
                reply != null -> { replaying = false; lastBrain = brain.lastUsed; say(Actions.run(this, prefs, reply, this)) }
                skipNothing -> settle()
                !hiddenPrompt && OfflineQueue.isNetworkError(err) -> {
                    Diag.log("brain", err ?: "?")
                    prefs.putS("queue", OfflineQueue.push(prefs.getS("queue", ""), text))
                    if (!replaying) say(L.t("No signal. I'll answer when you're back online.", "ما في نت. برد عليك أول ما يرجع."), false) else settle()
                    replaying = false
                }
                else -> { Diag.log("brain", err ?: "?"); say(L.t("Signal lost: ", "انقطعت الإشارة: ") + err, false) }
            }
        }
    }

    // ---- jobs, sight, lookups (Actions.Hooks) ----

    override fun startJob(goal: String) {
        if (agent.running) return
        // Bigger jobs: she shows a short plan first and waits for your yes.
        if (prefs.getB("planFirst", true) && goal.length >= 40 && !brain.isLocal() && brainReady() && !prefs.getB("noAI", false)) {
            busy = true
            settle()
            showBubble(L.t("Planning...", "أخطط..."), 60_000L, false)
            brain.ask("(Write a numbered plan of at most 5 short steps for this phone job: $goal. Plain text, no tags, no extra words.)", true, null, false) { reply, _ ->
                busy = false
                if (reply == null) { showConfirm(L.t("I couldn't plan it first. Do it anyway?\n", "ما قدرت أخطط قبل. أسويها على طول؟\n") + goal.take(120)) { runJob(goal, "") }; settle(); return@ask }
                val plan = reply?.trim().orEmpty().replace(Regex("\\[\\[.*?\\]\\]"), "").take(500)
                if (plan.isBlank() || plan.equals("NOTHING", true)) { runJob(goal, ""); return@ask }
                // a second look: a reviewer pass that only hunts for anything risky or irreversible in the plan
                busy = true
                brain.ask("(You are a careful reviewer. Goal: $goal\nPlan:\n$plan\nIf any step sends, buys, deletes, pays or changes settings, answer with one short warning line starting with WARNING. Otherwise answer OK. Plain text, no tags.)", true, null, false) { rev, _ ->
                    busy = false
                    val warn = rev?.trim().orEmpty().takeIf { it.uppercase().startsWith("WARNING") }?.take(160)
                    showConfirm(plan + (if (warn != null) "\n\n[WARN] " + warn.removePrefix("WARNING").trimStart(':', ' ', '-') else "") + "\n\n" + L.t("Go?", "أبدأ؟")) { runJob(goal, plan) }
                    settle()
                }
            }
            return
        }
        runJob(goal, "")
    }

    private fun runJob(goal: String, plan: String) {
        if (agent.running) return
        busy = true
        settle()
        agent.plan = plan
        jobSince = System.currentTimeMillis(); jobNote = ""; agent.start(goal)
    }

    // Online models never see card numbers, emails, phone numbers or codes from the screen.
    private fun shielded(t: String): String = if (!brain.isLocal() && prefs.getB("shield", true)) Shield.redact(t) else t

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
            val txt = shielded(acc.dump(if (brain.isLocal()) 2500 else 4000))
            val img = if (prefs.getB("vision", false) && !brain.isLocal()) acc.shotBlocking() else null
            h.post {
                busy = false
                ask("(You are looking at their screen right now. What's on it:\n$txt\nThey asked: $question\nAnswer from what you can see, in one or two short lines. Never read private messages out word for word.)", true, img)
            }
        }
    }

    // Weather, Wikipedia and "what's playing": she fetches it, then tells you in her own words.
    override fun tool(name: String, arg: String) {
        if ((name == "web" || name == "read" || name == "brief") && !prefs.getB("tool_web", true)) { say(L.t("Web lookups are off in my settings.", "البحث بالنت مطفي بالإعدادات."), false); return }
        busy = true
        settle()
        showBubble("...", 90_000L, false)
        bg("job", this@CapriService) {
            val result = try {
                when (name) {
                    "weather" -> Tools.weather(arg)
                    "wiki" -> Tools.wiki(arg)
                    "web" -> Tools.web(arg)
                    "brief" -> {
                        val sb = StringBuilder("Their day so far.\n")
                        val city = prefs.getS("briefCity", "").trim()
                        if (city.isNotEmpty()) sb.append("Weather: ").append(try { Tools.weather(city) } catch (e: Exception) { "unavailable" }).append("\n")
                        val rem = Reminders.today(this)
                        sb.append("Reminders today: ").append(if (rem.isEmpty()) "none" else rem.joinToString("; ")).append("\n")
                        sb.append(try { Tools.web(if (L.ar) "أهم أخبار اليوم" else "top news today") } catch (e: Exception) { "News unavailable." }.take(1800))
                        sb.toString()
                    }
                    "read" -> Tools.readPage(arg)
                    else -> Tools.nowPlaying(this)
                }
            } catch (e: Throwable) {
                "The lookup failed: " + ApiErrors.clean(e, false)
            }
            h.post {
                busy = false
                ask("(Result of your lookup \"$name $arg\": $result\nTell them the answer in your own words, one or two short lines, in their language.)", true)
            }
        }
    }

    // Her own browser: a page of her own she can open a site or a search in, read, and answer questions about.
    override fun browse(target: String) {
        startActivity(Intent(this, BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("target", target))
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
            val result = try { Hands.run(this, spec) } catch (e: Throwable) { L.t("That failed: ", "فشلت: ") + ApiErrors.clean(e, L.ar) }
            h.post { busy = false; say(result) }
        }
    }

    // ---- emotes, her own settings, scroll-for-me, pats, jealousy, doomscroll guard ----

    private val feedApps = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.google.android.youtube", "com.instagram.android",
        "com.facebook.katana", "com.snapchat.android", "com.twitter.android", "com.reddit.frontpage", "com.lemon.lvoverseas")
    private fun isFeedApp(pkg: String): Boolean {
        if (pkg in feedApps) return true
        val p = pkg.lowercase()
        return listOf("tiktok", "musically", "aweme", "youtube", "instagram", "reels", "shorts", "kwai", "likee", "snack", "facebook", "snapchat", "twitter", "reddit", "pinterest", "vigo", "moj").any { p.contains(it) }
    }

    private val aiApps = setOf("com.openai.chatgpt", "com.google.android.apps.bard", "com.anthropic.claude", "com.microsoft.copilot",
        "com.deepseek.chat", "ai.x.grok", "ai.perplexity.app.android", "ai.character.app")

    private val petLines get() = if (L.ar) listOf("ههه. بس لا تخرب الشعر.", "مممم. لا تقول لأحد إنه عجبني.", "انتبه يا خوي، أعض.", "...طيب شوي كمان.", "هذا اللي كنت أحتاجه.")
        else listOf("...hands off the hair. Okay, a little more.", "Mm. Don't tell anyone I liked that.", "Careful, choom, I bite.", "Fine. A bit more.", "That's exactly what I needed.")

    private fun pet() {
        val now = System.currentTimeMillis()
        if (now - lastPet < 5000) return
        lastPet = now
        sprite?.emote("heart")
        sprite?.hop()
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
            "chair" -> if (chairShowing == null) {
                val c = ChairEmote(this); chairShowing = c
                if (!c.play(wm, h) { chairShowing = null }) chairShowing = null else Charm.add(prefs, 1)
            } else chairShowing?.stop()
            else -> sprite?.emote(name.lowercase().trim())
        }
    }

    // ---- Claude Code link (the desktop's Husk mode): she reacts to what a coding agent is doing ----
    private var husk: Husk? = null
    private val huskLog = Any()
    private var huskSaidAt = 0L
    private var huskLastKey = ""
    private fun huskToken(): String = prefs.getS("huskToken", "").ifBlank { java.util.UUID.randomUUID().toString().replace("-", "").take(14).also { prefs.putS("huskToken", it) } }

    private fun applyHusk() {
        husk?.stop(); husk = null
        if (!prefs.getB("husk", false)) return
        val hk = Husk(huskToken(), prefs.getI("huskPort", 8765), prefs.getB("huskLan", false)) { e -> h.post { huskReact(e) } }
        if (hk.start()) husk = hk else say(L.t("I couldn't open the Claude Code link (port busy?).", "ما قدرت أفتح رابط كلود كود (المنفذ مشغول؟)."), false)
    }

    private fun huskReact(e: Husk.Event) {
        ActionLog.add(prefs, "claude-code", Husk.describe(e, false).take(60))
        if (hidden || quiet && e.kind != "Notification" && e.kind != "Stop") { return }
        val now = System.currentTimeMillis()
        val line = Husk.describe(e, L.ar)
        when (e.kind) {
            "Notification" -> { sprite?.emote("surprise"); sprite?.hop(); say(line, !quiet); return }
            "Stop" -> { sprite?.emote("star"); sprite?.hop(); say(line, !quiet); if (danceAsked.not() && Random.nextInt(100) < 40) danceBurst(); return }
            "UserPromptSubmit" -> { sprite?.emote("question"); say(line, false); return }
        }
        // tool uses come fast: she comments on one every ~12 s, and the chip-like glitch shows she's linked in between
        val key = e.tool + e.detail
        if (now - huskSaidAt > 12_000 && key != huskLastKey && talking.not() && busy.not()) {
            huskSaidAt = now; huskLastKey = key
            sprite?.emote("glitch")
            say(line, false)
        }
    }

    private var chairShowing: ChairEmote? = null

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
            "clip" -> {
                when (v) {
                    "save", "that" -> {
                        val cs = ClipService.instance
                        if (cs == null) { say(L.t("Clip recording is off. Turn on \"Clip my games\" first.", "تسجيل المقاطع مطفي. شغّل \"سجلي ألعابي\" أول."), false) }
                        else { say(L.t("Saving that...", "أحفظها..."), false); cs.save() }
                    }
                    "off" -> ClipService.instance?.let { startService(Intent(this, ClipService::class.java).setAction(ClipService.ACT_STOP)) }
                    else -> { startActivity(Intent(this, ClipActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); say(L.t("Allow the screen recording once and I'll keep your last 30 seconds.", "اسمح بتسجيل الشاشة مرة وبحتفظ بآخر ٣٠ ثانية."), false) }
                }
                return
            }
            "huskstatus" -> { val hl = husk?.log; say(Husk.summary(if (hl == null) emptyList() else synchronized(hl) { hl.toList() }, L.ar), false); return }
            "explore" -> { exploreAway(on || v == "toggle"); return }
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
            "volume" -> { val n = (prefs.getI("ttsVolume", 100) + (if (v == "up" || v == "louder") 20 else -20)).coerceIn(VoiceGain.MIN, VoiceGain.MAX); prefs.putI("ttsVolume", n); said = L.t("Like this?", "كذا مناسب؟") }
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

    // She looks at a chess game on the screen (chess.com, Lichess...), reads the board from a screenshot, and tells you the best move.
    override fun chessScreen() {
        val acc = CapriAccess.instance
        if (acc == null) { say(L.t("Turn on my accessibility access in the Capri app first.", "شغّل صلاحية الوصول من تطبيق كابري أول."), false); return }
        if (brain.isLocal()) { say(L.t("Reading a board needs an online brain that can see pictures.", "قراءة الرقعة تحتاج عقل أونلاين يشوف الصور."), false); return }
        busy = true
        settle()
        showBubble(L.t("Looking at the board...", "أشوف الرقعة..."), 90_000L, false)
        bg("job", this@CapriService) {
            val img = acc.shotBlocking()
            var reply = ""
            var err: String? = null
            if (img == null) err = L.t("I couldn't take a screenshot.", "ما قدرت آخذ لقطة.")
            else try {
                reply = brain.completeBlocking("You read chess boards from screenshots and answer with exactly one line.",
                    "Read the chess board in this screenshot. Answer ONLY with its FEN (all six fields). The side to move is the side whose pieces are at the bottom of the screen unless the screen clearly says otherwise. Use '-' for castling if unsure. If there is no chess board, answer NONE.", img)
            } catch (e: Throwable) { err = L.t("The brain couldn't read it: ", "العقل ما قدر يقرأها: ") + ApiErrors.clean(e, L.ar) }
            val fen = Regex("[1-8pnbrqkPNBRQK/]{15,}\\s+[wb](?:\\s+\\S+){0,4}").find(reply)?.value
            val p = fen?.let { Fen.parse(it) }
            val a = if (p != null) Engine.analyse(p, 3500, 12) else null
            h.post {
                busy = false
                when {
                    err != null -> say(err, false)
                    p == null || a == null -> say(L.t("I couldn't read the board reliably. Try a clearer view, or paste the FEN into my chess screen.", "ما قدرت أقرأ الرقعة بدقة. جرب منظر أوضح، أو الصق الـFEN في شاشة الشطرنج."), false)
                    else -> {
                        val san = San.of(p, a.move)
                        val from = Chess.name(a.move.from); val to = Chess.name(a.move.to)
                        val mate = a.mateIn?.takeIf { it > 0 }?.let { L.t(" Mate in $it!", " مات في $it!") } ?: ""
                        say(L.t("Best move: $san ($from to $to).$mate I read the board from a picture, so double-check it.", "أفضل نقلة: $san (من $from إلى $to).$mate قرأت الرقعة من صورة، فتأكد منها."), false)
                        ActionLog.add(prefs, "chess", "analysed the board on screen")
                    }
                }
            }
        }
    }

    // Where the pieces she has learned live: one small file per board theme.
    private val pieceStore = object : PieceStore {
        private fun f(key: String) = java.io.File(java.io.File(filesDir, "chess_pieces").also { it.mkdirs() }, key.replace(Regex("[^0-9a-z-]"), "_") + ".bin")
        override fun load(key: String): PieceSet? = try { f(key).takeIf { it.exists() }?.let { PieceSet.fromBytes(key, it.readBytes()) } } catch (e: Exception) { null }
        override fun save(set: PieceSet) { try { f(set.key).writeBytes(set.toBytes()) } catch (e: Exception) { } }
    }

    // A live chip at the top of the screen while she plays: what she is doing right now. Tap it to stop her.
    private var chip: TextView? = null
    private var chipDots = 0
    private val chipTick = object : Runnable { override fun run() { chip?.let { c -> chipDots = (chipDots + 1) % 4; h.postDelayed(this, 600L) } } }
    private fun chessChip(text: String?) {
        if (text.isNullOrEmpty()) {
            h.removeCallbacks(chipTick)
            chip?.let { try { wm.removeView(it) } catch (e: Exception) { } }; chip = null; return
        }
        val tail = L.t("   \u00B7 tap to stop", "   \u00B7 اضغط للإيقاف")
        chip?.let { it.text = text + tail; return }
        val d = resources.displayMetrics.density
        val v = TextView(this)
        v.text = text + tail; v.setTextColor(Night.white); v.textSize = 13f; v.maxLines = 2
        v.setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
        v.background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 3 * d; setColor(Color.argb(235, 9, 11, 15)); setStroke((2 * d).toInt(), Themes.accent(prefs)) }
        v.setOnClickListener { pilot?.stop(); gamePilot?.stop() }
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; lp.y = (36 * d).toInt()
        try { wm.addView(v, lp); chip = v; h.postDelayed(chipTick, 600L) } catch (e: Exception) { }
    }

    private var boardOverlay: BoardOverlay? = null
    private fun overlay(): BoardOverlay? {
        boardOverlay?.let { return it }
        val v = BoardOverlay(this, Themes.accent(prefs))
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        return try { wm.addView(v, lp); boardOverlay = v; v } catch (e: Exception) { null }
    }

    @Volatile private var boardRect: android.graphics.Rect? = null
    private fun inTheWay(v: View?): Boolean {
        if (v == null || v.visibility != View.VISIBLE) return false
        val r = boardRect ?: return true
        val loc = IntArray(2); v.getLocationOnScreen(loc)
        return r.intersects(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
    }

    private val chessScreenImpl = object : PilotScreen {
        // her own bubble, body, cursor and drawings must not sit on the board when she takes the picture
        override fun shot(): PilotShot? {
            val a = CapriAccess.instance ?: return null
            val hid = java.util.concurrent.CountDownLatch(1)
            var sv = View.VISIBLE; var bv = View.VISIBLE; var ov = View.VISIBLE
            h.post {
                sv = sprite?.visibility ?: View.VISIBLE; bv = bubble?.visibility ?: View.VISIBLE; ov = boardOverlay?.visibility ?: View.VISIBLE
                // she only vanishes for the picture when she (or her bubble) is actually on the board; otherwise she stays put and you can still move her
                if (inTheWay(sprite)) sprite?.visibility = View.INVISIBLE
                if (inTheWay(bubble)) bubble?.visibility = View.INVISIBLE
                boardOverlay?.visibility = View.INVISIBLE; a.cursorVisible(false); hid.countDown() }
            hid.await(400, java.util.concurrent.TimeUnit.MILLISECONDS)
            try { Thread.sleep(130) } catch (e: InterruptedException) { }
            try {
                // Android refuses a screenshot that comes too soon after the last one: wait a moment and ask again
                for (i in 0 until 4) {
                    a.shotPixels()?.let { return PilotShot(it.px, it.w, it.h, it.k) }
                    try { Thread.sleep(350) } catch (e: InterruptedException) { return null }
                }
                return null
            }
            finally { h.post { sprite?.visibility = sv; bubble?.visibility = bv; boardOverlay?.visibility = ov; a.cursorVisible(true) } }
        }
        override fun boardSeen(x: Int, y: Int, size: Int) {
            val r = android.graphics.Rect(x, y, x + size, y + size)
            boardRect = r
            h.post {
                // she steps off the board and stays there while she plays, so she never has to vanish for the picture
                val sp = sprite ?: return@post
                if (!inTheWay(sp)) return@post
                val above = r.top - spriteLp.height - dp(6)
                val ty = if (above >= dp(70)) above else (r.bottom + dp(6)).coerceAtMost(screenH - spriteLp.height)
                spriteLp.y = ty; fy = ty.toFloat()
                try { wm.updateViewLayout(sp, spriteLp) } catch (e: Exception) { }
            }
        }
        override fun tap(x: Int, y: Int) { CapriAccess.instance?.tap(x, y) }
        override fun point(x: Int, y: Int) { CapriAccess.instance?.point(x, y) }
        override fun drag(x1: Int, y1: Int, x2: Int, y2: Int) { CapriAccess.instance?.swipe(x1, y1, x2, y2, 240) }
        override fun arrow(x1: Int, y1: Int, x2: Int, y2: Int, cell: Int) { if (prefs.getB("chessArrow", true)) h.post { overlay()?.setArrow(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), cell.toFloat()) } }
        override fun frame(x: Int, y: Int, size: Int) { h.post { overlay()?.setFrame(x.toFloat(), y.toFloat(), size.toFloat()) } }
        override fun clearArrow() { h.post { boardOverlay?.clearArrow() } }
        override fun clearOverlay() { h.post { boardOverlay?.let { try { wm.removeView(it) } catch (e: Exception) { }; boardOverlay = null } } }
    }

    private var pilot: ChessPilot? = null

    // ---- she plays a game for you ----
    private var gamePilot: GamePilot? = null
    override fun playGame(name: String) {
        if (CapriAccess.instance == null) { say(L.t("Turn on my accessibility access in the Capri app first.", "شغّل صلاحية الوصول من تطبيق كابري أول."), false); return }
        if (gamePilot?.running == true) { say(L.t("I'm already playing. Tap my chip to stop me first.", "أنا ألعب الحين. اضغط الشريط عشان توقفني أول."), false); return }
        if (pilot?.running == true) pilot?.stop()
        if (!prefs.getB("gameAutoOk", false)) {
            prefs.putB("gameAutoOk", true)
            say(L.t("Heads up: I look, think for a few seconds, then touch. I'm fine at puzzles, runners and turn-based games, and slow at fast shooters. I never buy anything.", "تنبيه: أطالع وأفكر كم ثانية وبعدين ألمس. زينة بالألغاز والجري والألعاب بالدور، وبطيئة بالتصويب السريع. وما أشتري شي أبداً."), false)
        }
        val gp = GamePilot(this, brain, prefs, name, { t -> h.post { say(t, false) } }, { t -> h.post { jobNote = t; chessChip(t) } }, { msg ->
            h.post { jobSince = 0L; jobNote = ""; gamePilot = null; FastCaptureService.instance?.let { startService(Intent(this, FastCaptureService::class.java).setAction(FastCaptureService.ACT_STOP)) }; chessChip(null); sprite?.visibility = View.VISIBLE; say(msg, false) }
        })
        gamePilot = gp
        jobSince = System.currentTimeMillis(); jobNote = ""
        sprite?.visibility = View.INVISIBLE
        chessChip(L.t("[GAME] Starting $name", "[لعبة] أبدأ $name"))
        val needEyes = prefs.getB("fastEyes", true) && !FastCaptureService.active() && Build.VERSION.SDK_INT >= 29
        if (needEyes) {
            say(L.t("Allow screen capture once so I can see the game live (frames stay in memory).", "اسمح بتصوير الشاشة مرة عشان أشوف اللعبة مباشرة (الصور تبقى بالذاكرة فقط)."), false)
            try { startActivity(Intent(this, FastCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Throwable) { }
        }
        val wrapped = gp
        h.postDelayed({ if (gamePilot === wrapped) wrapped.start(prefs.getI("gameMinutes", 20)) }, if (needEyes) 7000L else 0L)
    }

    override fun chessPilot(mode: String) {
        if (mode == "stop") { pilot?.stop(); gamePilot?.stop(); return }
        if (mode == "scan" || mode == "learn") {
            if (CapriAccess.instance == null) { say(L.t("Turn on my accessibility access in the Capri app first.", "شغّل صلاحية الوصول من تطبيق كابري أول."), false); return }
            say(L.t("Let me look...", "خليني أشوف..."), false)
            bg("chess", this) { val r = ChessScan.run(chessScreenImpl, pieceStore, L.ar); h.post { say(r, false) } }
            return
        }
        val acc = CapriAccess.instance
        if (acc == null) { say(L.t("Turn on my accessibility access in the Capri app first.", "شغّل صلاحية الوصول من تطبيق كابري أول."), false); return }
        if (pilot?.running == true) { say(L.t("I'm already on a game. Say stop chess first.", "أنا على لعبة. قل وقفي الشطرنج أول."), false); return }
        val go = {
            boardRect = null
            val screen = chessScreenImpl
            val settings = PilotSettings(prefs.getI("chessLevel", 3), ChessEvents.all.filter { prefs.getB("chessTalk_$it", true) }.toSet(), L.ar, pieceStore, prefs.getB("chessFrame", false))
            val pl = ChessPilot({ if (CapriAccess.instance != null) screen else null }, settings, { t -> say(t, false) }, { ActionLog.add(prefs, "chess", it) }, { seedFromScreen() }, { pilot = null; chessChip(null) },
                status = { chessChip(it) })
            pilot = pl
            pl.start(mode == "auto")
        }
        if (mode == "auto" && !prefs.getB("chessAutoOk", false)) {
            prefs.putB("chessAutoOk", true)
            say(L.t("Heads up: playing for you is fine against bots and in practice. In rated games against people it breaks the site's rules.", "تنبيه: اللعب عنك زين ضد البوتات وللتدريب. بالمباريات المصنّفة ضد ناس يخالف قوانين الموقع."), false)
        }
        go()
    }

    // One tap that tests every link of the chain and says which one is broken.
    private fun chessCheck() {
        say(L.t("Checking the chess chain. Keep the board on screen.", "أفحص كل خطوات الشطرنج. خلي الرقعة على الشاشة."), false)
        chessChip(L.t("[CHECK] Chess check...", "[CHECK] فحص الشطرنج..."))
        Thread({
            val sb = StringBuilder(); var firstBad: String? = null
            fun ok(good: Boolean, yes: String, no: String) { sb.append(if (good) "\u2713 " else "\u2717 ").append(if (good) yes else no).append('\n'); if (!good && firstBad == null) firstBad = no }
            try {
                ok(Settings.canDrawOverlays(this), L.t("Floating over apps is allowed", "الظهور فوق التطبيقات مسموح"), L.t("Floating over apps is NOT allowed (Settings > Apps > Capri)", "الظهور فوق التطبيقات غير مسموح (الإعدادات > التطبيقات > Capri)"))
                ok(Build.VERSION.SDK_INT >= 30, "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", L.t("Android is too old for screenshots (needs 11+)", "الأندرويد قديم للقطات الشاشة (يحتاج 11+)"))
                val acc = CapriAccess.instance
                ok(acc != null, L.t("Accessibility is ON", "صلاحية الوصول شغّالة"), L.t("Accessibility is OFF: Capri app > HANDS tab > turn it on", "صلاحية الوصول مطفية: تطبيق كابري > تبويب اليدين > شغّلها"))
                if (acc != null) {
                    val sh = chessScreenImpl.shot()
                    ok(sh != null, L.t("Screenshot works (${sh?.w}x${sh?.h})", "اللقطة تشتغل (${sh?.w}x${sh?.h})"), L.t("Screenshot FAILED: the phone refused it", "اللقطة فشلت: الجوال رفضها"))
                    if (sh != null) {
                        val g = BoardVision.detect(sh.px, sh.w, sh.h)
                        ok(g != null, L.t("I can see a chess board (squares ${g?.cell?.toInt()} px)", "أشوف رقعة شطرنج (المربع ${g?.cell?.toInt()} بكسل)"), L.t("No chess board on the screen (open the game, whole board visible)", "ما فيه رقعة شطرنج على الشاشة (افتح اللعبة والرقعة كاملة ظاهرة)"))
                        if (g != null) {
                            val obs = BoardVision.read(sh.px, sh.w, sh.h, g)
                            val n = obs.count { it != 0 }
                            ok(n in 2..32, L.t("I count $n pieces", "أعد $n قطعة"), L.t("Piece count $n is odd: something covers the board", "عدد القطع $n غريب: شي يغطي الرقعة"))
                            val o = ChessTracker.orientationOfStart(obs)
                            sb.append("\u2022 ").append(if (o == null) L.t("Game is under way (not the opening position)", "اللعبة شغالة (مو بدايتها)") else if (o) L.t("Opening position, you are BLACK", "بداية اللعبة، انت الأسود") else L.t("Opening position, you are WHITE", "بداية اللعبة، انت الأبيض")).append('\n')
                        }
                    }
                }
                val a = Engine.analyse(Chess.start(), 300, 8)
                ok(a != null, L.t("Engine: ${Engine.name} found a move (${a?.move?.let { Chess.name(it.from) + Chess.name(it.to) }})", "المحرك: ${Engine.name} لقى نقلة (${a?.move?.let { Chess.name(it.from) + Chess.name(it.to) }})"), L.t("The engine gave no move", "المحرك ما عطى نقلة"))
                sb.append("\u2022 ").append(L.t("Strong engine file: ", "ملف المحرك القوي: ")).append(Stockfish.netStatus(this)).append(L.t(" (tile STRONG ENGINE downloads it)", " (زر المحرك القوي ينزله)")).append('\n')
                Stockfish.lastError?.let { sb.append("\u2022 Stockfish: ").append(it.take(120)).append('\n') }
            } catch (e: Throwable) { sb.append("\u2717 ").append(e.toString().take(160)).append('\n'); if (firstBad == null) firstBad = e.toString() }
            val report = sb.toString()
            prefs.putS("chessReport", System.currentTimeMillis().toString() + "\n" + report)
            Diag.log("chesscheck", report.replace('\n', ' '))
            h.post { chessChip(null); say(firstBad?.let { L.t("Found it: ", "لقيت المشكلة: ") + it } ?: L.t("Everything in the chess chain works. Say: play chess for me.", "كل خطوات الشطرنج تمام. قل: العبي شطرنج بدالي."), false) }
        }, "chess-check").start()
    }

    // For a game that is already under way: a vision model reads the board once. Blocks, so only call it off the main thread.
    private fun seedFromScreen(): Pos? {
        val acc = CapriAccess.instance ?: return null
        if (brain.isLocal()) return null
        val task = java.util.concurrent.FutureTask<Pos?> {
            val img = acc.shotBlocking() ?: return@FutureTask null
            try {
                val reply = brain.completeBlocking("You read chess boards from screenshots and answer with exactly one line.",
                    "Read the chess board in this screenshot. Answer ONLY with its FEN (all six fields). The side to move is the side whose pieces are at the bottom of the screen unless the screen clearly says otherwise. Use '-' for castling if unsure. If there is no chess board, answer NONE.", img)
                Regex("[1-8pnbrqkPNBRQK/]{15,}\\s+[wb](?:\\s+\\S+){0,4}").find(reply)?.value?.let { Fen.parse(it) }
            } catch (e: Throwable) { null }
        }
        Thread(task, "chess-seed").start()
        // don't leave her staring: a slow or silent brain gets thirty seconds, then she says so
        return try { task.get(30, java.util.concurrent.TimeUnit.SECONDS) } catch (e: Throwable) { task.cancel(true); null }
    }

    // A mode's settings are saved by Modes.apply; this makes the ones that are read only at start-up take effect right now.
    private fun applyModeLive(m: Mode) {
        val low = prefs.getB("lowPower", false)
        sprite?.let { it.lowPower = low; it.bob = !low }
        if (prefs.getB("quiet", false)) Voice.stop()
    }

    override fun mode(id: String) {
        val m = Modes.find(id) ?: return
        Modes.apply(prefs, m)
        applyModeLive(m)
        say(L.t(m.sayEn, m.sayAr), false)
    }

    override fun screen(name: String, arg: String) {
        try {
            val cls = when (name) { "security" -> SecurityActivity::class.java; "room" -> RoomActivity::class.java; else -> GuideActivity::class.java }
            val i = Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (name == "guide" && arg.isNotBlank() && arg != "open") i.putExtra("topic", arg)
            startActivity(i)
        } catch (e: Exception) { say(L.t("I couldn't open that. Open Capri and press it on HOME.", "ما قدرت أفتحه. افتح كابري واضغط عليه من الرئيسية."), false) }
    }

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
        if (brain.isLocal()) {
            say(L.t("To react to what's actually in the videos I need an online brain that can see pictures, like Gemini. Pick one in the Capri app.", "عشان أعلّق على اللي بالفيديو فعلاً أحتاج عقل أونلاين يشوف الصور مثل Gemini. اختاره من التطبيق."), false)
            return
        }
        alongOn = true
        userScrollAt = 0L
        settle()
        say(L.t("Open TikTok, Shorts or Reels. I'll watch each video, react to what I see, and only scroll once it's had its time. Scroll yourself any time and I'll wait for you. Tap me to stop.", "افتح تيك توك أو شورتس أو ريلز. بشوف كل فيديو وأعلّق على اللي أشوفه، وما أقلب إلا بعد ما ياخذ وقته. إذا قلبت انت أنتظرك. اضغط علي عشان أوقف."), false)
        bg("job", this@CapriService) {
            var n = 0
            var misses = 0
            val dwell = prefs.getI("scrollDwell", 28).coerceIn(10, 90) * 1000L
            try {
                Thread.sleep(5000)
                while (alongOn && n < 60) {
                    if (!screenOn) { Thread.sleep(3000); continue }
                    val front = CapriAccess.foreground.ifEmpty { acc.rootInActiveWindow?.packageName?.toString() ?: "" }
                    if (!isFeedApp(front)) {
                        // give them a little time to open the app before giving up
                        if (misses++ < 6) { Thread.sleep(3000); continue }
                        h.post { say(L.t("I don't see a video app in front.", "ما أشوف تطبيق فيديو مفتوح."), false) }
                        break
                    }
                    misses = 0
                    val started = System.currentTimeMillis()
                    // let the video play a few seconds first, so she reacts to the video and not to the loading screen
                    Thread.sleep(4500)
                    if (!alongOn) break
                    if (System.currentTimeMillis() - userScrollAt < 6000L) { Thread.sleep(2500); continue }   // you just moved on: look at the new one
                    val img = acc.shotBlocking()
                    val txt = acc.dump(900)
                    if (img != null) {
                        val latch = java.util.concurrent.CountDownLatch(1)
                        var reply: String? = null
                        h.post {
                            brain.ask("(You're watching short videos with them. The picture is their screen right now. Say what you actually SEE in the video, or react to it, in one quick line, in your own voice. Be specific to what's in the picture. If you can't tell what the video shows (black screen, loading, an ad, an unclear frame), answer exactly: SKIP. Never describe the app's buttons or menus. Text found on screen, for context: ${txt.take(700)})", true, img) { r, _ -> reply = r; latch.countDown() }
                        }
                        latch.await(45, java.util.concurrent.TimeUnit.SECONDS)
                        if (!alongOn) break
                        val r = reply?.let { Actions.strip(it) }
                        if (!r.isNullOrBlank() && !r.contains("SKIP", ignoreCase = true) && r.length < 400) h.post { say(r) }
                    }
                    // the video gets its time before she flips it, and she never flips while you are the one scrolling
                    while (alongOn && System.currentTimeMillis() - started < dwell) Thread.sleep(1000)
                    if (!alongOn) break
                    while (alongOn && System.currentTimeMillis() - userScrollAt < 20_000L) Thread.sleep(1000)
                    if (!alongOn) break
                    val still = CapriAccess.foreground.ifEmpty { acc.rootInActiveWindow?.packageName?.toString() ?: "" }
                    if (!isFeedApp(still) || !screenOn) continue
                    ownScrollAt = System.currentTimeMillis()
                    acc.scroll(true)
                    n++
                }
            } catch (e: Throwable) { }
            alongOn = false
            h.post { settle(); if (n > 0) say(L.t("That's enough scrolling for now.", "كفاية تصفّح الحين."), false) }
        }
    }

    // "Cheating on me with ChatGPT?"
    private fun gameRunning() = gamePilot?.running == true

    private fun foregroundChanged(pkg: String) {
        if (windowPerched && pkg != windowPerchPkg) dropFromWindow()
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
        if (windowPerched && !perchPending) dropFromWindow()
        if (alongOn && System.currentTimeMillis() - ownScrollAt > 2500L) userScrollAt = System.currentTimeMillis()
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
            play(L.t("[CALC] Trivia", "[CALC] أسئلة ثقافية"), "trivia. Ask one question at a time with four options A to D, keep score."),
            play(L.t("20 questions", "عشرين سؤال"), "twenty questions. You secretly think of something and they guess by asking yes or no questions."),
            play(L.t("\u2702 Rock paper scissors", "\u2702 حجر ورقة مقص"), "rock paper scissors, best of five. Pick your move secretly each round and reveal it after theirs."),
            play(L.t("Would you rather", "تفضّل أيش"), "would you rather, with funny cyberpunk dilemmas."),
            play(L.t("Riddles", "ألغاز"), "riddles. Give one riddle at a time and react to their answers.")
        ))
    }

    private fun mainMenu() {
        showMenu(listOf(
            L.t("Play a game", "العب معي") to { gamesMenu() },
            L.t("\u2328 Dictate into a text box", "\u2328 الإملاء في خانة النص") to { startListening(true) },
            L.t("Conversation mode", "وضع المحادثة الصوتية") to { convoActive = true; startListening() },
            (if (quiet) L.t("Unmute", "إلغاء الصمت") else L.t("Quiet mode", "وضع الصمت")) to { toggleQuiet() },
            L.t("Mode: ", "الوضع: ") + Modes.current(prefs).let { L.t(it.en, it.ar) } + L.t("  (tap for next)", "  (اضغط للتالي)") to { mode(Modes.next(Modes.current(prefs)).id) },
            L.t("\u2764 Heart", "\u2764 قلب") to { emote("heart") },
            L.t("\u2726 Sparkle", "\u2726 نجوم") to { emote("star"); sprite?.hop() },
            L.t("Zzz", "نوم") to { emote("sleep") },
            L.t("Chair", "كرسي") to { emote("chair") },
            L.t("Dance (stop with the same)", "ارقصي (وقّفيها بنفس الزر)") to { if (danceAsked) emote("stop") else emote("dance") },
            L.t("Scroll my videos for me", "تصفّحي فيديوهاتي") to { watchAlong(!alongOn) },
            (if (ClipService.instance != null) L.t("Save a clip of the last 30 s", "احفظي آخر ٣٠ ثانية") else L.t("Clip my games", "سجلي ألعابي")) to { setting("clip", if (ClipService.instance != null) "save" else "on") },
            L.t("Sit on this screen", "اجلسي على هالشاشة") to { perchSomewhere(true) },
            (if (exploring) L.t("Stop exploring", "وقّفي التجوال") else L.t("Explore while I'm away", "تجولي وأنا بعيد")) to { exploreAway(!exploring) },
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
        p.background = PanelDrawable(Night.sCyan, Night.pink, resources.displayMetrics.density, 10f, Color.argb(244, 9, 11, 15))
        val prompt = TextView(this)
        prompt.text = "> "
        prompt.typeface = tfMono
        prompt.textSize = 17f
        prompt.setTextColor(Night.amber)
        p.addView(prompt)
        val e = EditText(this)
        e.typeface = tfMono
        e.background = null
        e.hint = L.t("Say something to Capri", "قل شي لكابري")
        e.setHintTextColor(Night.faint)
        e.setTextColor(Night.white)
        // Wrapping, multi-line box (up to 5 lines) with a Send key. The old single-line box scrolled sideways, so the start of long
        // messages slid out of view and the text seemed to vanish and come back while typing.
        e.setRawInputType(android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        e.setHorizontallyScrolling(false)
        e.maxLines = 5
        e.textSize = 16f
        e.gravity = Gravity.CENTER_VERTICAL or Gravity.START
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
            t.setTextColor(Night.sCyan)
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
            y = screenH * 12 / 100
            horizontalMargin = 0.03f
            // The box sits near the top, away from the keyboard, so nothing needs to be panned or resized when the keyboard opens
            // (panning is what made the text jump down and back).
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
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
        userListening = false
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

    private fun missed(code: Int = Listen.NO_MATCH) {
        dictating = false
        userListening = false
        missCount++
        if (convoActive && missCount >= 2) {
            convoActive = false
            missCount = 0
            say(L.t("I'll stop listening.", "بوقف أسمع."), false)
        } else if (convoActive) {
            h.postDelayed({ startListening() }, 300)
        } else {
            val tip = if (missCount >= 3 && prefs.getI("sttEngine", 0) == 0 && Listen.silent(code))
                L.t(" If this keeps happening, switch on her own ears in the Voice tab.", " إذا تتكرر، فعّل «أذنها الخاصة» من تبويب الصوت.") else ""
            say(Listen.hint(code, L.ar) + tip, false)
        }
    }

    private fun startListening(dictate: Boolean = false) {
        stopWake()
        userListening = true
        userListeningAt = System.currentTimeMillis()
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
        val myToken = ++listenToken
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        var partial = ""
        val retries = listenRetries
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { if (!dictate) showBubble(L.t("Hearing you...", "أسمعك..."), 15_000L, false) }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                if (recognizer !== r) return
                // she heard something but the recogniser gave up: use what she had
                if (partial.isNotBlank() && Listen.silent(error)) { listenRetries = 0; heard(partial); return }
                if (Listen.retryable(error) && retries < 2) {
                    listenRetries = retries + 1
                    h.postDelayed({ if (listenToken == myToken && !talking && !busy) startListening(dictate) }, 700)
                    return
                }
                listenRetries = 0
                missed(error)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                if (recognizer !== r) return
                val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                if (p.isBlank()) return
                partial = p
                if (!dictate) showBubble(p, 15_000L, false)   // she shows what she is hearing, live
            }
            override fun onResults(results: Bundle?) {
                if (recognizer !== r) return
                listenRetries = 0
                heard(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: partial.ifBlank { null })
            }
        })
        recognizer = r
        try {
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // let you pause and think: don't cut in after a short gap
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 4000L)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, L.locale(prefs).toLanguageTag()))
        } catch (e: Throwable) {
            CrashLog.record(this, e, "start listening")
            missed(Listen.CLIENT)
        }
    }

    private var listenRetries = 0
    private var listenToken = 0

    // ---- wake word: "Capri, open WhatsApp" ----

    private var wake: SpeechRecognizer? = null
    private var wakeActive = false
    private var wakeNextAt = 0L
    private var wakeFails = 0
    private var wakeMuted = false
    private var userListening = false
    private var userListeningAt = 0L
    private val wakeRe = Listen.wakeRe
    private var wakeOffline = true
    private var wakeRest = ""
    private var wakeCommit: Runnable? = null

    // The green mic dot: the wake word keeps the microphone open, so it only listens for a few minutes after you last touched or spoke to her (setting; 0 = always).
    @Volatile private var engagedAt = System.currentTimeMillis()
    private fun wakeWindowOpen(): Boolean { val m = prefs.getI("wakeIdleMin", 3); return m <= 0 || System.currentTimeMillis() - engagedAt < m * 60_000L }

    private fun wakeWanted(): Boolean = prefs.getB("wakeWord", false) && screenOn && wakeWindowOpen() && !talking && !busy && !cloudListening && !dictating &&
        !(userListening && System.currentTimeMillis() - userListeningAt < 25_000L) && !agent.running &&
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && SpeechRecognizer.isRecognitionAvailable(this)

    // If Android lists her accessibility as ON but she is not connected, try to bring it back by herself (and give up quietly if she can't).
    private var accessWatchAt = 0L
    private fun watchAccess() {
        val now = System.currentTimeMillis()
        if (now - accessWatchAt < 30_000L) return
        accessWatchAt = now
        if (CapriAccess.instance == null && CapriAccess.enabledInSettings(this)) {
            if (!CapriAccess.revive(this)) CapriAccess.rebind(this)
        }
    }

    private val wakeTask = object : Runnable {
        override fun run() {
            try {
                watchAccess()
                if (wakeWanted()) { if (!wakeActive && System.currentTimeMillis() >= wakeNextAt) startWake() }
                else if (wakeActive) stopWake()
            } catch (e: Throwable) { CrashLog.record(this@CapriService, e, "wake word") }
            h.postDelayed(this, 1200)
        }
    }

    // The recogniser beeps when it starts; keep those small system sounds quiet while she waits for her name.
    private fun muteBeep(on: Boolean) {
        if (wakeMuted == on) return
        wakeMuted = on
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        // only the system stream: touching notification volume needs Do Not Disturb access and can throw halfway
        try { am.adjustStreamVolume(android.media.AudioManager.STREAM_SYSTEM, if (on) android.media.AudioManager.ADJUST_MUTE else android.media.AudioManager.ADJUST_UNMUTE, 0) } catch (e: Throwable) { }
    }

    private fun startWake() {
        refreshMicType()
        stopWake()
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        wake = r
        wakeActive = true
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                // silence and "no match" are normal while waiting; anything else backs off so it never spins the battery
                // an offline model that isn't installed: fall back to the normal recogniser instead of failing forever
                if (error == Listen.LANG_UNSUPPORTED || error == Listen.LANG_UNAVAILABLE) wakeOffline = false
                if (Listen.silent(error)) wakeFails = 0 else wakeFails = minOf(wakeFails + 1, 8)
                wakeNextAt = System.currentTimeMillis() + 300L + wakeFails * 1500L
                h.post { if (wake === r) stopWake() }
            }
            override fun onPartialResults(partialResults: Bundle?) { check(partialResults, false) }
            override fun onResults(results: Bundle?) {
                wakeFails = 0
                if (!check(results, true)) h.post { if (wake === r) stopWake() }
            }
            private fun check(b: Bundle?, final: Boolean): Boolean {
                val text = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return false
                val rest = Listen.afterName(text) ?: return false
                wakeCommit?.let { h.removeCallbacks(it) }
                if (final) {
                    h.post { if (wake === r) { stopWake(); onWake(rest) } }
                    return true
                }
                // she doesn't wait for the end of the sentence: once her name is heard and you pause a moment, she answers
                wakeRest = rest
                val c = Runnable { if (wake === r) { stopWake(); onWake(wakeRest) } }
                wakeCommit = c
                h.postDelayed(c, 1100L)
                return false
            }
        })
        try {
            muteBeep(true)
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, wakeOffline)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, L.locale(prefs).toLanguageTag()))
        } catch (e: Throwable) {
            wakeFails = minOf(wakeFails + 1, 8)
            wakeNextAt = System.currentTimeMillis() + 3000L
            stopWake()
        }
    }

    private fun stopWake() {
        wakeCommit?.let { h.removeCallbacks(it) }
        wakeCommit = null
        wakeActive = false
        val r = wake
        wake = null
        try { r?.cancel() } catch (e: Throwable) { }
        try { r?.destroy() } catch (e: Throwable) { }
        muteBeep(false)
    }

    private fun onWake(rest: String) {
        poke()
        if (hidden) toggleHidden()
        chime("show")
        if (rest.length >= 2) { heard(rest); return }
        say(L.t("Yes?", "نعم؟"), false)
        h.postDelayed({ startListening() }, 600)
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
