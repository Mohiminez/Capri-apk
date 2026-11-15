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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

class CapriService : Service() {
    companion object {
        @Volatile var running = false
        const val ACT_HIDE = "app.capri.mobile.HIDE"
        const val ACT_STOP = "app.capri.mobile.STOP"
        private const val NOTE_ID = 1
        private const val OVERLAY = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var brain: Brain
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
    private var recognizer: SpeechRecognizer? = null

    private var asleep = false
    private var talking = false
    private var busy = false
    private var hidden = false
    private var dragging = false

    private val screenW get() = resources.displayMetrics.widthPixels
    private val screenH get() = resources.displayMetrics.heightPixels
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        brain = Brain(this, prefs)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("capri", "Capri", NotificationManager.IMPORTANCE_LOW))
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFg()
        if (intent?.action == ACT_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (sprite == null) setup()
        if (intent?.action == ACT_HIDE) toggleHidden()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        h.removeCallbacksAndMessages(null)
        try { if (panelShown) wm.removeView(panel) } catch (e: Exception) { }
        try { if (bubbleShown) wm.removeView(bubble) } catch (e: Exception) { }
        try { sprite?.let { wm.removeView(it) } } catch (e: Exception) { }
        tts?.shutdown()
        recognizer?.destroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // ---- notification ----

    private fun notification(): Notification {
        fun act(a: String, code: Int) = PendingIntent.getService(
            this, code, Intent(this, CapriService::class.java).setAction(a),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "capri")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (hidden) "Capri is hidden" else "Capri is on your screen")
            .setContentText("Tap to open her settings")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, if (hidden) "Show" else "Hide", act(ACT_HIDE, 1))
            .addAction(0, "Stop", act(ACT_STOP, 2))
            .build()
    }

    private fun startFg() {
        val n = notification()
        if (Build.VERSION.SDK_INT >= 34) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTE_ID, n, type)
        } else {
            startForeground(NOTE_ID, n)
        }
    }

    // ---- setting her up ----

    private fun setup() {
        val hPx = dp(prefs.getI("size", 140))
        val wPx = hPx * 192 / 307
        val s = SpriteView(this)
        sprite = s
        spriteLp = WindowManager.LayoutParams(wPx, hPx, OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getI("x", screenW - wPx)
            y = prefs.getI("y", screenH * 45 / 100)
        }
        s.setOnTouchListener(touch)
        wm.addView(s, spriteLp)

        val b = TextView(this)
        b.setTextColor(Color.parseColor("#F2F2F2"))
        b.textSize = 15f
        b.setPadding(dp(12), dp(9), dp(12), dp(9))
        b.maxWidth = (screenW * 0.7f).toInt()
        b.background = GradientDrawable().apply {
            setColor(Color.parseColor("#EC10141B"))
            setStroke(dp(2), Color.parseColor("#FF2A6D"))
            cornerRadius = dp(14).toFloat()
        }
        b.setOnClickListener { hideBubble() }
        bubble = b
        bubbleLp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setPitch(1.1f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { h.post { talking = false; settle() } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { h.post { talking = false; settle() } }
                })
                ttsReady = true
            }
        }

        s.play("arrive", false) { settle() }
        poke()
        h.postDelayed(blinkTask, 4000)
        h.postDelayed(checkInTask, 20 * 60_000L)
        h.postDelayed({
            if (prefs.getS("key", "").isBlank()) say("Hi. Open the Capri app and give me a brain first.", false)
            else ask("(You just booted up on the user's phone. Say hi in one short line.)", true)
        }, 1200)
    }

    // ---- animation state ----

    private fun settle() {
        val s = sprite ?: return
        when {
            dragging -> s.play("dangle")
            busy -> s.play("glitch")
            talking -> s.play("idle_mouth")
            asleep -> s.play("sleep")
            else -> s.play("idle")
        }
    }

    private val blinkTask = object : Runnable {
        override fun run() {
            val s = sprite
            if (s != null && !asleep && !talking && !busy && !dragging && !hidden && s.current == "idle") {
                s.play("idle_blink", false) { settle() }
            }
            h.postDelayed(this, Random.nextLong(3500, 8000))
        }
    }

    private val sleepTask = Runnable {
        if (!busy && !talking) {
            asleep = true
            settle()
        }
    }

    private val checkInTask = object : Runnable {
        override fun run() {
            if (prefs.getB("checkin", false) && !hidden && !busy && !asleep && !panelShown) {
                ask("(They haven't spoken to you in a while. Pop in with one short line of your own. Don't ask what's up.)", true)
            }
            h.postDelayed(this, Random.nextLong(20 * 60_000L, 45 * 60_000L))
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

    // ---- touch: drag, tap, long press ----

    private val touch = object : View.OnTouchListener {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var wasLong = false
        // lazy: a Service has no context yet while its properties are being created
        val slop: Int by lazy { ViewConfiguration.get(this@CapriService).scaledTouchSlop }
        val longPress = Runnable {
            if (!moved) {
                wasLong = true
                startListening()
            }
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
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
                        prefs.putI("x", spriteLp.x)
                        prefs.putI("y", spriteLp.y)
                        settle()
                    } else if (!wasLong && e.action == MotionEvent.ACTION_UP) {
                        sprite?.play("grin", false) { settle() }
                        if (panelShown) hidePanel() else showPanel()
                    }
                }
            }
            return true
        }
    }

    private fun moveTo(x: Int, y: Int) {
        val s = sprite ?: return
        spriteLp.x = x.coerceIn(-spriteLp.width / 3, screenW - spriteLp.width * 2 / 3)
        spriteLp.y = y.coerceIn(0, screenH - spriteLp.height)
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
        if (bubbleShown) wm.updateViewLayout(b, bubbleLp)
    }

    private val hideBubbleTask = Runnable { hideBubble() }

    private fun showBubble(text: String, ms: Long) {
        val b = bubble ?: return
        if (hidden) return
        b.text = text
        placeBubble()
        if (!bubbleShown) {
            wm.addView(b, bubbleLp)
            bubbleShown = true
        } else {
            wm.updateViewLayout(b, bubbleLp)
        }
        h.removeCallbacks(hideBubbleTask)
        h.postDelayed(hideBubbleTask, ms)
    }

    private fun hideBubble() {
        h.removeCallbacks(hideBubbleTask)
        if (bubbleShown) {
            try { wm.removeView(bubble) } catch (e: Exception) { }
            bubbleShown = false
        }
    }

    // ---- talking ----

    private fun say(text: String, speak: Boolean = true) {
        if (hidden || text.isBlank()) return
        showBubble(text, (text.length * 80L).coerceIn(5000L, 15000L))
        talking = true
        settle()
        if (speak && prefs.getB("voice", true) && ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "capri")
        } else {
            h.postDelayed({ talking = false; settle() }, (text.length * 55L).coerceIn(1200L, 4500L))
        }
    }

    private fun ask(text: String, hiddenPrompt: Boolean = false) {
        poke()
        if (prefs.getS("key", "").isBlank()) {
            say("I need a brain first. Open the Capri app and paste a key.", false)
            return
        }
        busy = true
        talking = false
        settle()
        showBubble("...", 60_000L)
        brain.ask(text, hiddenPrompt) { reply, err ->
            busy = false
            if (reply != null) say(Actions.run(this, prefs, reply))
            else say("Signal lost: $err", false)
        }
    }

    // ---- typing ----

    private fun buildPanel() {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.HORIZONTAL
        p.gravity = Gravity.CENTER_VERTICAL
        p.setPadding(dp(12), dp(6), dp(6), dp(6))
        p.background = GradientDrawable().apply {
            setColor(Color.parseColor("#F010141B"))
            setStroke(dp(2), Color.parseColor("#FF2A6D"))
            cornerRadius = dp(16).toFloat()
        }
        val e = EditText(this)
        e.hint = "Say something to Capri"
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
            t.textSize = 22f
            t.setTextColor(Color.WHITE)
            t.setPadding(dp(12), dp(8), dp(12), dp(8))
            t.setOnClickListener { click() }
            p.addView(t)
        }
        button("\uD83C\uDFA4") { hidePanel(); startListening() }
        button("\u27A4") { sendTyped() }
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

    private fun startListening() {
        poke()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            say("I need the microphone permission. Open the Capri app.", false)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("This phone has no speech recognizer.", false)
            return
        }
        tts?.stop()
        talking = false
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
            override fun onError(error: Int) { say("Didn't catch that.", false) }
            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) say("Didn't catch that.", false) else ask(t)
            }
        })
        recognizer = r
        showBubble("Listening...", 10_000L)
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag()))
    }

    // ---- hide / show ----

    private fun toggleHidden() {
        hidden = !hidden
        sprite?.visibility = if (hidden) View.GONE else View.VISIBLE
        if (hidden) { hideBubble(); hidePanel(); tts?.stop() }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTE_ID, notification())
    }
}
