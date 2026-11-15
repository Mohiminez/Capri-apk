package app.capri.mobile

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.util.Linkify
import android.view.Gravity
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
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout
    private lateinit var url: EditText
    private lateinit var key: EditText
    private lateinit var model: EditText
    private lateinit var nick: EditText
    private lateinit var persona: EditText
    private lateinit var notes: EditText
    private lateinit var hfToken: EditText
    private lateinit var customUrl: EditText
    private lateinit var voiceKey: EditText
    private lateinit var voiceId: EditText
    private lateinit var voiceModel: EditText
    private lateinit var voiceUrl: EditText
    private lateinit var overlayStatus: TextView
    private lateinit var micStatus: TextView
    private lateinit var accessStatus: TextView
    private lateinit var dlStatus: TextView
    private lateinit var activeModel: TextView
    private lateinit var startBtn: Button
    private lateinit var providerSpinner: Spinner

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun t(en: String, ar: String) = L.t(en, ar)
    private val accent get() = Themes.accent(prefs)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        L.init(prefs)
        if (prefs.getS("url", "").isEmpty()) {
            prefs.putS("url", Providers.all[0].url)
            prefs.putS("model", Providers.all[0].model)
        }
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = if (L.ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        root.setPadding(dp(18), dp(24), dp(18), dp(40))
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#0E1116"))
        scroll.addView(root)
        setContentView(scroll)
        build()
    }

    // ---- small builders ----

    private fun title(s: String) {
        val v = TextView(this)
        v.text = s
        v.textSize = 13f
        v.setTextColor(accent)
        v.setPadding(0, dp(22), 0, dp(6))
        root.addView(v)
    }

    private fun text(s: String, size: Float = 14f): TextView {
        val v = TextView(this)
        v.text = s
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
        if (multiline) { e.minLines = 4; e.gravity = Gravity.TOP }
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

    private fun slider(label: String, k: String, min: Int, max: Int, default: Int) {
        text(label)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = prefs.getI(k, default) - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { prefs.putI(k, min + p) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        root.addView(sb)
    }

    private fun spinner(items: List<String>, selected: Int, onPick: (Int) -> Unit): Spinner {
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        sp.setSelection(selected)
        var first = true
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (first) { first = false; return }
                onPick(pos)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        root.addView(sp)
        return sp
    }

    private fun pickDialog(titleText: String, items: List<String>, onPick: (Int) -> Unit) {
        if (items.isEmpty()) { Toast.makeText(this, t("Nothing found.", "ما لقيت شي."), Toast.LENGTH_LONG).show(); return }
        AlertDialog.Builder(this).setTitle(titleText).setItems(items.toTypedArray()) { _, i -> onPick(i) }.show()
    }

    private fun bg(work: () -> Unit, fail: (String) -> Unit) {
        Thread {
            try { work() } catch (e: Throwable) { runOnUiThread { fail(e.message ?: "error") } }
        }.start()
    }

    // ---- the screen ----

    private fun build() {
        val head = TextView(this)
        head.text = "CAPRI"
        head.textSize = 30f
        head.setTextColor(Color.WHITE)
        root.addView(head)
        text(t("A quiet, unofficial Capri companion for your Android phone. She floats over your apps, chats, talks, walks around and does things for you.",
            "رفيقة كابري غير الرسمية لجوالك. تطفو فوق التطبيقات، تسولف، تتكلم، تتمشى وتسوي أشياء لك."))

        title(t("LANGUAGE", "اللغة"))
        spinner(listOf(t("Same as my phone", "نفس لغة الجوال"), "English", "العربية"), prefs.getI("lang", 0)) {
            prefs.putI("lang", it)
            save()
            recreate()
        }

        title(t("1. PERMISSIONS", "١. الصلاحيات"))
        overlayStatus = text("")
        button(t("Allow floating over apps", "السماح بالظهور فوق التطبيقات")) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        micStatus = text("")
        button(t("Allow microphone and notifications", "السماح بالمايك والإشعارات")) {
            val perms = ArrayList<String>()
            perms.add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(perms.toTypedArray(), 1)
        }
        accessStatus = text("")
        text(t("Accessibility lets her see your screen and tap, type and scroll for you. On Android 13 and newer, first open App info, tap the three dots and choose Allow restricted settings.",
            "الوصول يخليها تشوف شاشتك وتضغط وتكتب وتسحب عنك. من أندرويد ١٣، افتح معلومات التطبيق أول، اضغط النقاط الثلاث واختر السماح بالإعدادات المقيّدة."), 12f)
        button(t("Open app info", "معلومات التطبيق")) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        button(t("Open accessibility settings", "إعدادات الوصول")) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        title(t("2. HER BRAIN", "٢. دماغها"))
        text(t("Pick a provider and paste your key. I'll find the model name for you. Gemini and NVIDIA have free keys. Or run her fully offline on the phone.",
            "اختر المزود والصق المفتاح. أنا أجيب اسم الموديل عنك. Gemini وNVIDIA عندهم مفاتيح مجانية. أو شغّلها بدون نت على الجوال."))
        providerSpinner = spinner(Providers.all.map { it.name }, prefs.getI("provider", 0)) { pos ->
            prefs.putI("provider", pos)
            val pr = Providers.all[pos]
            if (pr.url.isNotEmpty()) { url.setText(pr.url); model.setText(pr.model) }
        }
        url = field(t("Base URL (for a local server, e.g. http://127.0.0.1:11434/v1)", "رابط المزود (للسيرفر المحلي مثل http://127.0.0.1:11434/v1)"), prefs.getS("url", ""))
        key = field(t("API key (not needed for a local server)", "مفتاح API (مو لازم للسيرفر المحلي)"), prefs.getS("key", ""), password = true)
        model = field(t("Model (leave empty and I'll pick one)", "الموديل (اتركه فاضي وأنا أختار)"), prefs.getS("model", ""))
        key.setOnFocusChangeListener { _, has -> if (!has) autoModel() }
        button(t("Find models for my key", "ابحث عن موديلات لمفتاحي")) { pickModel() }

        title(t("OFFLINE MODEL ON THIS PHONE", "موديل بدون نت على الجوال"))
        text(t("Download a model once, then pick \"On this phone\" above. It runs without internet. It needs a fairly recent phone and 3 GB or more of free memory.",
            "نزّل موديل مرة وحدة، بعدين اختر \"On this phone\" فوق. يشتغل بدون نت. يحتاج جوال حديث نوعاً ما وذاكرة فاضية ٣ جيجا أو أكثر."), 13f)
        activeModel = text("")
        LocalLlm.catalog.forEach { m ->
            button(t("Download: ", "تنزيل: ") + m.label) { downloadModel(m.file, m.url) }
        }
        customUrl = field(t("Or paste any .litertlm file link", "أو الصق رابط أي ملف .litertlm"), "")
        button(t("Download that link", "نزّل هذا الرابط")) {
            val u = customUrl.text.toString().trim()
            if (u.startsWith("http")) {
                var n = u.substringBefore('?').substringAfterLast('/')
                if (!n.endsWith(".litertlm")) n = "custom.litertlm"
                downloadModel(n, u)
            }
        }
        hfToken = field(t("Hugging Face token (only for gated models like Gemma)", "توكن Hugging Face (فقط للموديلات المقفلة مثل Gemma)"), prefs.getS("hfToken", ""), password = true)
        dlStatus = text("", 13f)
        val installed = LocalLlm.dir(this).listFiles { f -> f.name.endsWith(".litertlm") }?.map { it.name } ?: emptyList()
        if (installed.isNotEmpty()) {
            text(t("Installed models:", "الموديلات المنزّلة:"), 13f)
            spinner(installed, installed.indexOf(prefs.getS("localModel", "")).coerceAtLeast(0)) {
                prefs.putS("localModel", installed[it]); refreshActive()
            }
        }
        toggle(t("Use the phone's GPU (faster on some phones, may crash on others)", "استخدم كرت الجوال (أسرع على بعض الجوالات)"), "gpu", false)

        title(t("3. HER VOICE", "٣. صوتها"))
        text(t("The phone voice is basic. Gemini is free with your Gemini key and sounds much better, ElevenLabs is what the Windows version uses.",
            "صوت الجوال عادي. Gemini مجاني بمفتاح Gemini وصوته أحلى بكثير، وElevenLabs هو اللي تستخدمه نسخة الويندوز."), 13f)
        spinner(Voice.engines, prefs.getI("ttsEngine", 0)) { prefs.putI("ttsEngine", it); fillVoiceDefaults(it) }
        voiceKey = field(t("Voice key (leave empty to use the brain key)", "مفتاح الصوت (اتركه فاضي لاستخدام مفتاح الدماغ)"), prefs.getS("voiceKey", ""), password = true)
        voiceId = field(t("Voice (ElevenLabs voice id, Gemini voice name, or OpenAI voice)", "الصوت (معرّف ElevenLabs أو اسم صوت Gemini أو صوت OpenAI)"),
            if (prefs.getI("ttsEngine", 0) == Voice.ELEVEN) prefs.getS("voiceId", "") else prefs.getS("voiceName", ""))
        voiceModel = field(t("Voice model (optional)", "موديل الصوت (اختياري)"), prefs.getS("voiceModel", ""))
        voiceUrl = field(t("Server URL (only for OpenAI-style, e.g. your Kokoro server)", "رابط السيرفر (فقط لـ OpenAI أو سيرفر Kokoro)"), prefs.getS("voiceUrl", ""))
        button(t("Pick a voice", "اختر صوت")) { pickVoice() }
        button(t("Test the voice", "جرّب الصوت")) { testVoice() }

        title(t("4. YOU", "٤. أنت"))
        nick = field(t("What she calls you", "وش تناديك"), prefs.getS("nick", ""))
        toggle(t("She talks out loud", "تتكلم بصوت"), "voice", true)
        toggle(t("She checks in on me now and then (uses your key)", "تطمّن علي من وقت لوقت (تستهلك مفتاحك)"), "checkin", false)
        toggle(t("She dances when music is playing", "ترقص لما تشتغل موسيقى"), "dance", true)
        slider(t("How much she wanders (0 = stays put)", "قد إيش تتمشى (٠ = تثبت)"), "wanderPace", 0, 3, 1)
        slider(t("Her size (applies next time you start her)", "حجمها (يتطبق بالتشغيل الجاي)"), "size", 80, 220, 140)
        text(t("Theme", "الثيم"), 14f)
        spinner(Themes.names, prefs.getI("theme", 0)) { prefs.putI("theme", it); prefs.putS("modAccent", "") }
        button(t("Import a mod (.json)", "استيراد مود (.json)")) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 7)
        }
        button(t("Remove my mod", "احذف المود")) {
            prefs.putS("modAccent", ""); prefs.putS("modPersona", "")
            Toast.makeText(this, t("Mod removed.", "انحذف المود."), Toast.LENGTH_SHORT).show()
        }

        title(t("5. SCREEN AND HANDS (needs accessibility)", "٥. الشاشة واليدين (تحتاج الوصول)"))
        toggle(t("She watches my screen and comments, like on a game (uses your key)", "تراقب شاشتي وتعلّق، مثل وقت اللعب (تستهلك مفتاحك)"), "watch", false)
        slider(t("Seconds between glances", "الثواني بين كل نظرة"), "watchSecs", 20, 180, 60)
        toggle(t("Send screenshots too, not just screen text (needs a model that sees images)", "أرسل لقطات شاشة مو بس النص (يحتاج موديل يشوف الصور)"), "vision", false)
        text(t("Online brains receive what is on your screen when she looks. Password fields are never read. Use the offline model to keep everything on the phone.",
            "الأدمغة الأونلاين تستلم اللي على شاشتك لما تشوف. حقول كلمات المرور ما تنقرأ أبداً. استخدم الموديل المحلي عشان كل شي يبقى بالجوال."), 12f)

        title(t("HER NOTES ABOUT YOU", "ملاحظاتها عنك"))
        notes = field(t("Nothing yet. She adds things as you chat.", "ما فيه شي للحين. تضيف وانت تسولف."), prefs.getS("notes", ""), multiline = true)

        title(t("HER PERSONALITY", "شخصيتها"))
        val custom = prefs.getS("personality", "")
        persona = field("personality", if (custom.isBlank()) Persona.default(this) else custom, multiline = true)
        text(t("Her safety rules always stay in place after whatever you write here.", "قواعد الأمان تبقى دايماً مهما كتبت هنا."), 12f)
        button(t("Reset personality to default", "رجّع الشخصية للأصل")) { persona.setText(Persona.default(this)) }
        button(t("Clear our chat history", "امسح سجل المحادثة")) {
            prefs.putS("history", "[]")
            Toast.makeText(this, t("Cleared. Restart her to apply.", "انمسح. أعد تشغيلها."), Toast.LENGTH_SHORT).show()
        }

        title(t("6. START", "٦. ابدأ"))
        startBtn = button("") { toggleService() }
        text(t("Drag her, even throw her. Tap her to type. Hold her to talk. Tap her while she works on a job to stop it.",
            "اسحبها وحتى ارميها. اضغط عليها للكتابة. اضغط مطوّلاً للكلام. اضغط عليها وهي تشتغل لتوقفها."), 13f)

        title(t("WHAT SHE CAN DO", "وش تقدر تسوي"))
        text(t("Chat in your language, speak, remember things, set reminders, timers and alarms, open apps and sites, play music, pause or skip, look at your screen, comment while you play, and do jobs for you by tapping, typing and scrolling (like joining a Discord voice channel). " +
            "She can't hear other apps' audio, so she can't listen in on calls, and she isn't good enough to play real-time games herself.",
            "تسولف بلغتك، تتكلم، تتذكر، تضبط تذكيرات ومؤقتات ومنبهات، تفتح التطبيقات والمواقع، تشغّل أغاني، توقف وتقلّب، تشوف شاشتك، تعلّق وانت تلعب، وتسوي مهام بالضغط والكتابة والسحب (مثل دخول روم صوتي بديسكورد). " +
            "ما تقدر تسمع صوت التطبيقات الثانية فما تسمع المكالمات، وما تلعب ألعاب سريعة بنفسها."), 13f)

        title(t("CREDITS", "الشكر"))
        val c = text("Unofficial fan project, free for non-commercial use only. Based on Capri by Lachy: https://github.com/lachydotmcg/capri-desktop " +
            "(PolyForm Noncommercial 1.0.0). Her art and animations are by Capri's artist. Not approved or endorsed by CD PROJEKT RED, " +
            "the anime's rights holders or its voice actors.", 12f)
        Linkify.addLinks(c, Linkify.WEB_URLS)
        c.setLinkTextColor(accent)
    }

    // ---- brain helpers ----

    private fun autoModel() {
        if (prefs.getI("provider", 0) == Providers.LOCAL) return
        val k = key.text.toString().trim()
        val u = url.text.toString().trim()
        if (k.isEmpty() || u.isEmpty() || model.text.toString().isNotBlank()) return
        bg({
            val pick = Models.choose(Models.fetch(u, k), "")
            if (pick != null) runOnUiThread { model.setText(pick); prefs.putS("model", pick) }
        }, { })
    }

    private fun pickModel() {
        save()
        Toast.makeText(this, t("Looking...", "أبحث..."), Toast.LENGTH_SHORT).show()
        bg({
            val list = Models.fetch(url.text.toString(), key.text.toString())
            runOnUiThread {
                pickDialog(t("Pick a model", "اختر موديل"), list) { i -> model.setText(list[i]); prefs.putS("model", list[i]) }
            }
        }, { e -> Toast.makeText(this, t("Couldn't list models: ", "ما قدرت أجيب الموديلات: ") + e, Toast.LENGTH_LONG).show() })
    }

    private fun refreshActive() {
        val n = prefs.getS("localModel", "")
        val f = LocalLlm.file(this, n)
        activeModel.text = if (n.isNotBlank() && f.exists()) t("Active offline model: ", "الموديل الحالي: ") + n + " (" + f.length() / 1_000_000 + " MB)"
        else t("No offline model downloaded yet.", "ما نزّلت موديل للحين.")
    }

    private fun downloadModel(name: String, link: String) {
        prefs.putS("hfToken", hfToken.text.toString().trim())
        dlStatus.text = t("Downloading... 0%", "جاري التنزيل... ٠٪")
        bg({
            val err = LocalLlm.download(this, link, name, hfToken.text.toString().trim()) { p ->
                runOnUiThread { dlStatus.text = t("Downloading... $p%", "جاري التنزيل... $p٪") }
            }
            runOnUiThread {
                if (err == null) {
                    prefs.putS("localModel", name)
                    prefs.putI("provider", Providers.LOCAL)
                    providerSpinner.setSelection(Providers.LOCAL)
                    dlStatus.text = t("Ready. She will use it offline.", "جاهز. بتستخدمه بدون نت.")
                    refreshActive()
                } else {
                    dlStatus.text = t("Failed: ", "فشل: ") + err
                }
            }
        }, { e -> dlStatus.text = t("Failed: ", "فشل: ") + e })
    }

    // ---- voice helpers ----

    private fun fillVoiceDefaults(engine: Int) {
        when (engine) {
            Voice.GEMINI -> { voiceId.setText("Kore"); voiceModel.setText("") }
            Voice.ELEVEN -> { voiceId.setText(prefs.getS("voiceId", "")); voiceModel.setText("eleven_multilingual_v2") }
            Voice.OPENAI -> { voiceId.setText("nova"); voiceModel.setText("tts-1"); voiceUrl.setText("https://api.openai.com/v1") }
        }
    }

    private fun pickVoice() {
        save()
        when (prefs.getI("ttsEngine", 0)) {
            Voice.GEMINI -> pickDialog(t("Pick a Gemini voice", "اختر صوت Gemini"), Voice.geminiVoices) { voiceId.setText(Voice.geminiVoices[it]) }
            Voice.OPENAI -> {
                val v = listOf("nova", "shimmer", "alloy", "echo", "fable", "onyx")
                pickDialog(t("Pick a voice", "اختر صوت"), v) { voiceId.setText(v[it]) }
            }
            Voice.ELEVEN -> bg({
                val k = voiceKey.text.toString().trim().ifEmpty { key.text.toString().trim() }
                val list = Voice.elevenVoices(k)
                runOnUiThread { pickDialog(t("Pick a voice", "اختر صوت"), list.map { it.label }) { i -> voiceId.setText(list[i].id) } }
            }, { e -> Toast.makeText(this, e, Toast.LENGTH_LONG).show() })
            else -> Toast.makeText(this, t("The phone voice is chosen in Android's own text-to-speech settings.", "صوت الجوال تختاره من إعدادات تحويل النص لكلام بأندرويد."), Toast.LENGTH_LONG).show()
        }
    }

    private fun testVoice() {
        save()
        val line = t("Hi choom. This is my voice. Not bad, right?", "هلا. هذا صوتي. مو سيء، صح؟")
        Toast.makeText(this, t("Generating...", "جاري التوليد..."), Toast.LENGTH_SHORT).show()
        Thread {
            val err = Voice.speak(this, prefs, line) { }
            if (err != null) runOnUiThread {
                Toast.makeText(this, if (err == "phone") t("The phone voice has no test here, start her and talk.", "صوت الجوال يتجرب من كابري نفسها.") else err, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // ---- saving, starting ----

    private fun save() {
        prefs.putS("url", url.text.toString().trim())
        prefs.putS("key", key.text.toString().trim())
        prefs.putS("model", model.text.toString().trim())
        prefs.putS("nick", nick.text.toString().trim())
        prefs.putS("notes", notes.text.toString())
        prefs.putS("hfToken", hfToken.text.toString().trim())
        prefs.putS("voiceKey", voiceKey.text.toString().trim())
        prefs.putS("voiceModel", voiceModel.text.toString().trim())
        prefs.putS("voiceUrl", voiceUrl.text.toString().trim())
        val vid = voiceId.text.toString().trim()
        if (prefs.getI("ttsEngine", 0) == Voice.ELEVEN) prefs.putS("voiceId", vid) else prefs.putS("voiceName", vid)
        val p = persona.text.toString().trim()
        prefs.putS("personality", if (p.isEmpty() || p == Persona.default(this).trim()) "" else p)
    }

    private fun toggleService() {
        save()
        if (CapriService.running) {
            stopService(Intent(this, CapriService::class.java))
        } else {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, t("Allow floating over apps first.", "اسمح بالظهور فوق التطبيقات أول."), Toast.LENGTH_LONG).show()
                return
            }
            startForegroundService(Intent(this, CapriService::class.java))
        }
        startBtn.postDelayed({ refresh() }, 700)
    }

    private fun refresh() {
        val ok = t("allowed", "مسموح")
        val no = t("not allowed yet", "مو مسموح للحين")
        overlayStatus.text = t("Floating over apps: ", "الظهور فوق التطبيقات: ") + if (Settings.canDrawOverlays(this)) ok else no
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        micStatus.text = t("Microphone: ", "المايك: ") + if (mic) ok else no
        accessStatus.text = t("Accessibility (screen and hands): ", "الوصول (الشاشة واليدين): ") + if (CapriAccess.instance != null) t("on", "شغّال") else t("off", "مطفي")
        startBtn.text = if (CapriService.running) t("Stop Capri", "أوقف كابري") else t("Start Capri", "شغّل كابري")
        refreshActive()
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onPause() { save(); super.onPause() }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    // A mod is a small JSON file: {"name": "...", "accent": "#RRGGBB", "personality": "extra lines", "nickname": "..."}. Data only, no code.
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 7 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val txt = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
            val j = JSONObject(txt)
            if (j.has("accent")) prefs.putS("modAccent", j.getString("accent"))
            if (j.has("personality")) prefs.putS("modPersona", j.getString("personality").take(2000))
            if (j.has("nickname")) { prefs.putS("nick", j.getString("nickname")); nick.setText(j.getString("nickname")) }
            Toast.makeText(this, t("Mod loaded: ", "انحمّل المود: ") + j.optString("name", "?"), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, t("That isn't a valid mod file.", "هذا مو ملف مود صحيح."), Toast.LENGTH_LONG).show()
        }
    }
}
