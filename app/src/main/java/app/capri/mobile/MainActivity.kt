package app.capri.mobile

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.util.Linkify
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var url: EditText
    private lateinit var key: EditText
    private lateinit var model: EditText
    private lateinit var nick: EditText
    private lateinit var persona: EditText
    private lateinit var notes: EditText
    private lateinit var overlayStatus: TextView
    private lateinit var micStatus: TextView
    private lateinit var startBtn: Button
    private lateinit var root: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (prefs.getS("url", "").isEmpty()) {
            prefs.putS("url", Providers.all[0].url)
            prefs.putS("model", Providers.all[0].model)
        }
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(24), dp(18), dp(40))
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#0E1116"))
        scroll.addView(root)
        setContentView(scroll)
        build()
    }

    private fun title(t: String) {
        val v = TextView(this)
        v.text = t
        v.textSize = 13f
        v.setTextColor(Color.parseColor("#FF2A6D"))
        v.setPadding(0, dp(22), 0, dp(6))
        root.addView(v)
    }

    private fun text(t: String, size: Float = 14f): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(Color.parseColor("#D5DAE0"))
        root.addView(v)
        return v
    }

    private fun field(hint: String, value: String, password: Boolean = false, multiline: Boolean = false): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(value)
        e.setTextColor(Color.WHITE)
        e.setHintTextColor(Color.parseColor("#6B7480"))
        e.textSize = 14f
        e.inputType = when {
            password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        if (multiline) { e.minLines = 4; e.gravity = android.view.Gravity.TOP }
        root.addView(e)
        return e
    }

    private fun toggle(label: String, k: String, default: Boolean) {
        val s = Switch(this)
        s.text = label
        s.setTextColor(Color.parseColor("#D5DAE0"))
        s.isChecked = prefs.getB(k, default)
        s.setOnCheckedChangeListener { _, on -> prefs.putB(k, on) }
        s.setPadding(0, dp(6), 0, dp(6))
        root.addView(s)
    }

    private fun button(label: String, click: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setOnClickListener { click() }
        root.addView(b)
        return b
    }

    private fun build() {
        val head = TextView(this)
        head.text = "CAPRI"
        head.textSize = 30f
        head.setTextColor(Color.WHITE)
        root.addView(head)
        text("A quiet, unofficial Capri companion for your Android phone. She floats over your apps, chats, talks and does small things for you.")

        title("1. PERMISSIONS")
        overlayStatus = text("")
        button("Allow floating over apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        micStatus = text("")
        button("Allow microphone and notifications") {
            val perms = ArrayList<String>()
            perms.add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(perms.toTypedArray(), 1)
        }

        title("2. HER BRAIN")
        text("Pick a provider and paste your API key. Gemini and NVIDIA both have free keys.")
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Providers.all.map { it.name })
        root.addView(spinner)
        url = field("Base URL", prefs.getS("url", ""))
        key = field("API key", prefs.getS("key", ""), password = true)
        model = field("Model", prefs.getS("model", ""))
        var first = true
        spinner.setSelection(prefs.getI("provider", 0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (first) { first = false; return }
                prefs.putI("provider", pos)
                val pr = Providers.all[pos]
                if (pr.url.isNotEmpty()) { url.setText(pr.url); model.setText(pr.model) }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        title("3. YOU")
        nick = field("What she calls you", prefs.getS("nick", ""))
        toggle("She talks out loud", "voice", true)
        toggle("She checks in on me now and then (uses your key)", "checkin", false)
        text("Her size (applies next time you start her)")
        val size = SeekBar(this)
        size.max = 140
        size.progress = prefs.getI("size", 140) - 80
        size.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { prefs.putI("size", 80 + p) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        root.addView(size)

        title("HER NOTES ABOUT YOU")
        notes = field("Nothing yet. She adds things as you chat.", prefs.getS("notes", ""), multiline = true)

        title("HER PERSONALITY")
        val custom = prefs.getS("personality", "")
        persona = field("personality", if (custom.isBlank()) Persona.default(this) else custom, multiline = true)
        text("Her safety rules always stay in place after whatever you write here.", 12f)
        button("Reset personality to default") { persona.setText(Persona.default(this)) }

        title("4. START")
        startBtn = button("") { toggleService() }
        text("Drag her anywhere. Tap her to type. Hold her to talk. Use the notification to hide her or stop her.", 13f)

        title("WHAT SHE CAN DO")
        text("Chat in your language, speak out loud, remember things, open apps and websites, play music, pause or skip media, set timers and alarms. " +
            "She can't see your screen, control other apps with taps, join calls or play games like the Windows version.", 13f)

        title("CREDITS")
        val c = text("Unofficial fan project, free for non-commercial use only. Based on Capri by Lachy: https://github.com/lachydotmcg/capri-desktop " +
            "(PolyForm Noncommercial 1.0.0). Her art and animations are by Capri's artist. Not approved or endorsed by CD PROJEKT RED, " +
            "the anime's rights holders or its voice actors.", 12f)
        Linkify.addLinks(c, Linkify.WEB_URLS)
        c.setLinkTextColor(Color.parseColor("#FF2A6D"))
    }

    private fun save() {
        prefs.putS("url", url.text.toString())
        prefs.putS("key", key.text.toString().trim())
        prefs.putS("model", model.text.toString())
        prefs.putS("nick", nick.text.toString().trim())
        prefs.putS("notes", notes.text.toString())
        val p = persona.text.toString().trim()
        prefs.putS("personality", if (p.isEmpty() || p == Persona.default(this).trim()) "" else p)
    }

    private fun toggleService() {
        save()
        if (CapriService.running) {
            stopService(Intent(this, CapriService::class.java))
        } else {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Allow floating over apps first.", Toast.LENGTH_LONG).show()
                return
            }
            startForegroundService(Intent(this, CapriService::class.java))
        }
        startBtn.postDelayed({ refresh() }, 600)
    }

    private fun refresh() {
        overlayStatus.text = if (Settings.canDrawOverlays(this)) "Floating over apps: allowed" else "Floating over apps: not allowed yet"
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        micStatus.text = if (mic) "Microphone: allowed" else "Microphone: not allowed yet (needed for talking to her)"
        startBtn.text = if (CapriService.running) "Stop Capri" else "Start Capri"
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onPause() { save(); super.onPause() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }
}
