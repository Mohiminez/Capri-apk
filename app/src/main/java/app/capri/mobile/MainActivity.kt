package app.capri.mobile

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var page: LinearLayout
    private lateinit var tabBar: LinearLayout
    private var tab = 0
    private val refreshers = ArrayList<() -> Unit>()

    // fields other code needs to refill
    private var urlE: EditText? = null
    private var keyE: EditText? = null
    private var modelE: EditText? = null
    private var voiceNameE: EditText? = null
    private var voiceModelE: EditText? = null
    private var voiceUrlE: EditText? = null
    private var sttUrlE: EditText? = null
    private var sttModelE: EditText? = null
    private var dlStatus: TextView? = null
    private var providerSpinner: Spinner? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun t(en: String, ar: String) = L.t(en, ar)
    private val accent get() = Themes.accent(prefs)

    private val tabNames get() = listOf(t("Home", "الرئيسية"), t("Brain", "الدماغ"), t("Voice", "الصوت"), t("Behavior", "السلوك"), t("Hands", "اليدين"), t("More", "المزيد"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        L.init(prefs)
        tab = prefs.getI("lastTab", 0)

        val rootCol = LinearLayout(this)
        rootCol.orientation = LinearLayout.VERTICAL
        rootCol.setBackgroundColor(Color.parseColor("#0E1116"))
        rootCol.layoutDirection = if (L.ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

        val head = TextView(this)
        head.text = "CAPRI"
        head.textSize = 24f
        head.setTextColor(Color.WHITE)
        head.typeface = android.graphics.Typeface.DEFAULT_BOLD
        head.setPadding(dp(18), dp(22), dp(18), dp(6))
        rootCol.addView(head)

        val tabScroll = HorizontalScrollView(this)
        tabScroll.isHorizontalScrollBarEnabled = false
        tabBar = LinearLayout(this)
        tabBar.orientation = LinearLayout.HORIZONTAL
        tabBar.setPadding(dp(10), dp(4), dp(10), dp(8))
        tabScroll.addView(tabBar)
        rootCol.addView(tabScroll)

        val scroll = ScrollView(this)
        page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(14), dp(4), dp(14), dp(40))
        scroll.addView(page)
        rootCol.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(rootCol)
        showTab(tab)
    }

    private fun showTab(i: Int) {
        tab = i
        prefs.putI("lastTab", i)
        tabBar.removeAllViews()
        for ((n, name) in tabNames.withIndex()) {
            val v = TextView(this)
            v.text = name
            v.textSize = 14f
            v.setPadding(dp(16), dp(8), dp(16), dp(8))
            val on = n == i
            v.setTextColor(if (on) Color.WHITE else Color.parseColor("#9AA4B0"))
            v.background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (on) accent else Color.parseColor("#1A212B"))
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(dp(4), 0, dp(4), 0)
            v.layoutParams = lp
            v.setOnClickListener { showTab(n) }
            tabBar.addView(v)
        }
        page.removeAllViews()
        refreshers.clear()
        urlE = null; keyE = null; modelE = null
        when (i) {
            0 -> buildHome()
            1 -> buildBrain()
            2 -> buildVoice()
            3 -> buildBehavior()
            4 -> buildHands()
            else -> buildMore()
        }
        refresh()
    }

    // ---- building blocks ----

    private fun card(title: String?): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(14), dp(12), dp(14), dp(12))
        c.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.parseColor("#151A22"))
            setStroke(dp(1), Color.parseColor("#232B36"))
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(6), 0, dp(6))
        page.addView(c, lp)
        if (title != null) label(c, title, 12f, accent, true)
        return c
    }

    private fun label(p: LinearLayout, s: String, size: Float = 14f, color: Int = Color.parseColor("#D5DAE0"), bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = s
        v.textSize = size
        v.setTextColor(color)
        if (bold) v.typeface = android.graphics.Typeface.DEFAULT_BOLD
        v.setPadding(0, dp(4), 0, dp(4))
        p.addView(v)
        return v
    }

    private fun btn(p: LinearLayout, s: String, primary: Boolean = false, click: () -> Unit): TextView {
        val v = TextView(this)
        v.text = s
        v.textSize = 14f
        v.gravity = Gravity.CENTER
        v.setTextColor(Color.WHITE)
        v.setPadding(dp(14), dp(11), dp(14), dp(11))
        v.background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(if (primary) accent else Color.parseColor("#232C38"))
        }
        v.setOnClickListener { click() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(5), 0, dp(5))
        p.addView(v, lp)
        return v
    }

    private fun edit(p: LinearLayout, hint: String, value: String, password: Boolean = false, multiline: Boolean = false, onChange: (String) -> Unit): EditText {
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
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: "") }
        })
        p.addView(e)
        return e
    }

    private fun toggle(p: LinearLayout, text: String, k: String, default: Boolean, extra: ((Boolean) -> Unit)? = null) {
        val s = Switch(this)
        s.text = text
        s.setTextColor(Color.parseColor("#D5DAE0"))
        s.isChecked = prefs.getB(k, default)
        s.setOnCheckedChangeListener { _, on -> prefs.putB(k, on); extra?.invoke(on) }
        s.setPadding(0, dp(7), 0, dp(7))
        p.addView(s)
    }

    private fun slider(p: LinearLayout, text: String, k: String, min: Int, max: Int, default: Int) {
        val l = label(p, "$text: ${prefs.getI(k, default)}")
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = prefs.getI(k, default) - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) { prefs.putI(k, min + v); l.text = "$text: ${min + v}" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        p.addView(sb)
    }

    private fun spin(p: LinearLayout, items: List<String>, selected: Int, onPick: (Int) -> Unit): Spinner {
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        sp.setSelection(selected.coerceIn(0, maxOf(0, items.size - 1)))
        var first = true
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (first) { first = false; return }
                onPick(pos)
            }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        p.addView(sp)
        return sp
    }

    // A row of choices, one highlighted.
    private fun chips(p: LinearLayout, items: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val views = ArrayList<TextView>()
        fun paint(sel: Int) {
            for ((i, v) in views.withIndex()) {
                v.setTextColor(if (i == sel) Color.WHITE else Color.parseColor("#9AA4B0"))
                (v.background as GradientDrawable).setColor(if (i == sel) accent else Color.parseColor("#232C38"))
            }
        }
        for ((i, name) in items.withIndex()) {
            val v = TextView(this)
            v.text = name
            v.textSize = 13f
            v.gravity = Gravity.CENTER
            v.setPadding(dp(6), dp(10), dp(6), dp(10))
            v.background = GradientDrawable().apply { cornerRadius = dp(12).toFloat() }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(3), dp(4), dp(3), dp(4))
            v.setOnClickListener { paint(i); onPick(i) }
            views.add(v)
            row.addView(v, lp)
        }
        paint(selected)
        p.addView(row)
    }

    private fun pickDialog(titleText: String, items: List<String>, onPick: (Int) -> Unit) {
        if (items.isEmpty()) { toast(t("Nothing found.", "ما لقيت شي.")); return }
        AlertDialog.Builder(this).setTitle(titleText).setItems(items.toTypedArray()) { _, i -> onPick(i) }.show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun bg(work: () -> Unit, fail: (String) -> Unit) {
        Thread {
            try { work() } catch (e: Throwable) { runOnUiThread { fail(e.message ?: "error") } }
        }.start()
    }

    private fun openUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    // ---- HOME ----

    private fun buildHome() {
        val hero = card(null)
        label(hero, t("Your quiet, unofficial Capri. She floats over your apps, chats, talks, wanders and does things for you.",
            "كابري غير الرسمية الهادية. تطفو فوق تطبيقاتك، تسولف، تتكلم، تتمشى وتسوي أشياء لك."), 13f)
        val status = label(hero, "", 15f, Color.WHITE, true)
        val startBtn = btn(hero, "", true) { toggleService() }
        val charmLabel = label(hero, "", 13f)
        val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        pb.max = 100
        pb.progressTintList = ColorStateList.valueOf(accent)
        hero.addView(pb)
        refreshers.add {
            val on = CapriService.running
            status.text = if (on) t("\u25CF She's on your screen", "\u25CF هي على شاشتك") else t("\u25CB She's asleep", "\u25CB هي نايمة")
            startBtn.text = if (on) t("Stop Capri", "أوقف كابري") else t("Start Capri", "شغّل كابري")
            val c = prefs.getI("charm", 0)
            charmLabel.text = t("Bond: ", "العلاقة: ") + Charm.label(c)
            pb.progress = Charm.progress(c)
        }

        val modes = card(t("MODE", "الوضع"))
        val mode = if (prefs.getB("quiet", false)) 1 else if (prefs.getB("gameMode", false)) 2 else 0
        chips(modes, listOf(t("Normal", "عادي"), t("Quiet", "صامت"), t("Game", "لعب")), mode) {
            prefs.putB("quiet", it == 1)
            prefs.putB("gameMode", it == 2)
        }
        label(modes, t("Quiet: she only types. Game: she stays out of the way and comments while you play (needs accessibility).",
            "صامت: تكتب بس. لعب: تبعد عن طريقك وتعلّق وانت تلعب (تحتاج صلاحية الوصول)."), 12f, Color.parseColor("#8A94A0"))

        val sec = card(t("SECURITY", "الأمان"))
        val desc = label(sec, "", 12f, Color.parseColor("#8A94A0"))
        fun descText(i: Int) = when (i) {
            0 -> t("Lockdown: chat only. She never touches the phone.", "قفل: سوالف بس. ما تلمس الجوال أبداً.")
            1 -> t("Guarded: she asks first (a bubble you tap) before jobs, looking at your screen or opening links.", "حذر: تسأل أول (فقاعة تضغطها) قبل المهام ومشاهدة الشاشة وفتح الروابط.")
            else -> t("Trusted: she does what you ask without asking again.", "موثوق: تسوي اللي تطلبه بدون ما تسأل.")
        }
        val level = prefs.getI("security", 1)
        desc.text = descText(level)
        chips(sec, listOf(t("Lockdown", "قفل"), t("Guarded", "حذر"), t("Trusted", "موثوق")), level) { prefs.putI("security", it); desc.text = descText(it) }

        val chk = card(t("CHECKLIST", "التجهيز"))
        fun row(name: () -> String, ok: () -> Boolean, fix: () -> Unit) {
            val l = label(chk, "")
            val b = btn(chk, t("Fix", "إصلاح")) { fix() }
            refreshers.add { val good = ok(); l.text = (if (good) "\u2714 " else "\u2716 ") + name(); b.visibility = if (good) View.GONE else View.VISIBLE }
        }
        row({ t("Floating over apps", "الظهور فوق التطبيقات") }, { Settings.canDrawOverlays(this) }) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        row({ t("Microphone and notifications", "المايك والإشعارات") }, { checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }) {
            val perms = arrayListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(perms.toTypedArray(), 1)
        }
        row({ t("Accessibility (her eyes and hands)", "الوصول (عيونها ويدينها)") }, { CapriAccess.instance != null }) { tab = 4; showTab(4) }
        row({ t("A brain is set up", "الدماغ جاهز") }, { Brain(this, prefs).anyReady() }) { showTab(1) }
    }

    // ---- BRAIN ----

    private fun buildBrain() {
        val c = card(t("PROVIDER", "المزود"))
        label(c, t("Each provider keeps its own key, so you can fill in several. Leave Model empty and I'll find a working one.",
            "كل مزود يحفظ مفتاحه، فتقدر تعبي أكثر من واحد. اترك الموديل فاضي وأنا ألقى واحد شغّال."), 12f, Color.parseColor("#8A94A0"))
        providerSpinner = spin(c, Providers.all.map { it.name }, prefs.cur) { pos ->
            prefs.putI("provider", pos)
            urlE?.setText(prefs.url(pos)); keyE?.setText(prefs.key(pos)); modelE?.setText(prefs.model(pos))
        }
        urlE = edit(c, t("Base URL (for a local server, e.g. http://127.0.0.1:11434/v1)", "رابط المزود (للسيرفر المحلي مثل http://127.0.0.1:11434/v1)"), prefs.url(prefs.cur)) { prefs.putUrl(prefs.cur, it) }
        keyE = edit(c, t("API key (not needed for a local server)", "مفتاح API (مو لازم للسيرفر المحلي)"), prefs.key(prefs.cur), password = true) { prefs.putKey(prefs.cur, it) }
        modelE = edit(c, t("Model (leave empty and I'll pick one)", "الموديل (اتركه فاضي وأنا أختار)"), prefs.model(prefs.cur)) { prefs.putModel(prefs.cur, it) }
        btn(c, t("Get a free or paid key for this provider", "جيب مفتاح لهذا المزود")) {
            val kp = Providers.all[prefs.cur].keyPage
            if (kp.isNotEmpty()) openUrl(kp) else toast(t("No key page for this one.", "ما فيه صفحة مفتاح."))
        }
        btn(c, t("Find models for my key", "ابحث عن موديلات لمفتاحي")) {
            toast(t("Looking...", "أبحث..."))
            val i = prefs.cur
            bg({
                val list = Models.fetch(prefs.url(i), prefs.key(i))
                runOnUiThread { pickDialog(t("Pick a model", "اختر موديل"), list) { n -> prefs.putModel(i, list[n]); modelE?.setText(list[n]) } }
            }, { e -> toast(t("Couldn't list models: ", "ما قدرت أجيب الموديلات: ") + e) })
        }
        btn(c, t("Test the brain", "جرّب الدماغ"), true) {
            toast(t("Asking...", "أسأل..."))
            Brain(this, prefs).ask("Say hi in five words.", true) { r, e ->
                toast(if (r != null) "\u2714 " + r.take(120) else "\u2716 " + e)
            }
        }

        val r = card(t("NEVER GO SILENT", "ما تسكت أبداً"))
        toggle(r, t("When a provider fails or is rate-limited, switch to another one that has a key", "لو فشل مزود أو وصل الحد، انتقل لمزود ثاني عنده مفتاح"), "fallback", true)
        toggle(r, t("Use the offline model as a last resort", "استخدم الموديل المحلي كآخر حل"), "offlineFallback", true)
        label(r, t("Job brain: a stronger one for phone jobs and looking at your screen", "دماغ المهام: أقوى للمهام وقراءة الشاشة"), 13f)
        val opts = listOf(t("Same as chat", "نفس المحادثة")) + Providers.all.indices.filter { it != Providers.LOCAL }.map { Providers.all[it].name }
        val idxs = listOf(-1) + Providers.all.indices.filter { it != Providers.LOCAL }
        spin(r, opts, idxs.indexOf(prefs.getI("jobProvider", -1)).coerceAtLeast(0)) { prefs.putI("jobProvider", idxs[it]) }

        val o = card(t("OFFLINE MODEL ON THIS PHONE", "موديل بدون نت على الجوال"))
        label(o, t("Download one once, then pick \"On this phone\" above or keep it as the last resort. Needs a fairly recent phone and 3 GB or more of free memory. It frees its memory after two quiet minutes.",
            "نزّل واحد مرة وحدة، بعدين اختر \"On this phone\" فوق أو خله كآخر حل. يحتاج جوال حديث و٣ جيجا فاضية أو أكثر. يفرّغ الذاكرة بعد دقيقتين خمول."), 12f, Color.parseColor("#8A94A0"))
        val active = label(o, "", 13f)
        refreshers.add {
            val n = prefs.getS("localModel", "")
            val f = LocalLlm.file(this, n)
            active.text = if (n.isNotBlank() && f.exists()) t("Active offline model: ", "الموديل الحالي: ") + n + " (" + f.length() / 1_000_000 + " MB)"
            else t("No offline model downloaded yet.", "ما نزّلت موديل للحين.")
        }
        LocalLlm.catalog.forEach { m -> btn(o, t("Download: ", "تنزيل: ") + m.label) { downloadModel(m.file, m.url) } }
        val customUrl = edit(o, t("Or paste any .litertlm file link", "أو الصق رابط أي ملف .litertlm"), "") { }
        btn(o, t("Download that link", "نزّل هذا الرابط")) {
            val u = customUrl.text.toString().trim()
            if (u.startsWith("http")) {
                var n = u.substringBefore('?').substringAfterLast('/')
                if (!n.endsWith(".litertlm")) n = "custom.litertlm"
                downloadModel(n, u)
            }
        }
        edit(o, t("Hugging Face token (only for gated models like Gemma)", "توكن Hugging Face (فقط للموديلات المقفلة مثل Gemma)"), prefs.getS("hfToken", ""), password = true) { prefs.putS("hfToken", it.trim()) }
        dlStatus = label(o, "", 13f)
        val installed = LocalLlm.dir(this).listFiles { f -> f.name.endsWith(".litertlm") }?.map { it.name } ?: emptyList()
        if (installed.isNotEmpty()) {
            label(o, t("Installed models:", "الموديلات المنزّلة:"), 13f)
            spin(o, installed, installed.indexOf(prefs.getS("localModel", "")).coerceAtLeast(0)) { prefs.putS("localModel", installed[it]); refresh() }
        }
        toggle(o, t("Use the phone's GPU (faster on some phones, may crash on others)", "استخدم كرت الجوال (أسرع على بعض الجوالات)"), "gpu", false)
    }

    private fun downloadModel(name: String, link: String) {
        dlStatus?.text = t("Downloading... 0%", "جاري التنزيل... ٠٪")
        bg({
            val err = LocalLlm.download(this, link, name, prefs.getS("hfToken", "")) { p ->
                runOnUiThread { dlStatus?.text = t("Downloading... $p%", "جاري التنزيل... $p٪") }
            }
            runOnUiThread {
                if (err == null) {
                    prefs.putS("localModel", name)
                    dlStatus?.text = t("Ready. Pick \"On this phone\" as the provider, or leave it as the last resort.", "جاهز. اختر \"On this phone\" كمزود أو خله آخر حل.")
                    refresh()
                } else {
                    dlStatus?.text = t("Failed: ", "فشل: ") + err
                }
            }
        }, { e -> dlStatus?.text = t("Failed: ", "فشل: ") + e })
    }

    // ---- VOICE ----

    private fun buildVoice() {
        val o = card(t("HER VOICE", "صوتها"))
        label(o, t("The phone voice is basic. Groq's Orpheus is free and has a Saudi Arabic voice, Gemini is free with your Gemini key, ElevenLabs is what the Windows version uses.",
            "صوت الجوال عادي. Groq Orpheus مجاني وفيه صوت سعودي، وGemini مجاني بمفتاحك، وElevenLabs هو اللي تستخدمه نسخة الويندوز."), 12f, Color.parseColor("#8A94A0"))
        toggle(o, t("She talks out loud", "تتكلم بصوت"), "voice", true)
        spin(o, Voice.engines, prefs.getI("ttsEngine", 0)) {
            prefs.putI("ttsEngine", it)
            voiceNameE?.setText(if (it == Voice.ELEVEN) prefs.getS("voiceId", "") else prefs.getS("voiceName", ""))
        }
        edit(o, t("Voice key (leave empty to use the brain key)", "مفتاح الصوت (اتركه فاضي لاستخدام مفتاح الدماغ)"), prefs.getS("voiceKey", ""), password = true) { prefs.putS("voiceKey", it.trim()) }
        voiceNameE = edit(o, t("Voice (ElevenLabs id, Gemini name, or OpenAI/Groq voice)", "الصوت (معرّف ElevenLabs أو اسم Gemini أو صوت OpenAI/Groq)"),
            if (prefs.getI("ttsEngine", 0) == Voice.ELEVEN) prefs.getS("voiceId", "") else prefs.getS("voiceName", "")) {
            if (prefs.getI("ttsEngine", 0) == Voice.ELEVEN) prefs.putS("voiceId", it.trim()) else prefs.putS("voiceName", it.trim())
        }
        voiceModelE = edit(o, t("Voice model (optional)", "موديل الصوت (اختياري)"), prefs.getS("voiceModel", "")) { prefs.putS("voiceModel", it.trim()) }
        voiceUrlE = edit(o, t("Server URL (Groq / OpenAI / Kokoro only)", "رابط السيرفر (فقط Groq / OpenAI / Kokoro)"), prefs.getS("voiceUrl", "")) { prefs.putS("voiceUrl", it.trim()) }
        btn(o, t("Presets: Groq, OpenAI, Kokoro", "جاهز: Groq وOpenAI وKokoro")) {
            val names = listOf(t("Groq Orpheus (free, English + Saudi Arabic)", "Groq Orpheus (مجاني، إنجليزي + سعودي)"), "OpenAI tts-1", t("Kokoro server on this phone or PC", "سيرفر Kokoro على الجوال أو الكمبيوتر"))
            pickDialog(t("Pick a preset", "اختر"), names) { n ->
                prefs.putI("ttsEngine", Voice.OPENAI)
                when (n) {
                    0 -> { voiceUrlE?.setText("https://api.groq.com/openai/v1"); voiceModelE?.setText(if (L.ar) "canopylabs/orpheus-arabic-saudi" else "canopylabs/orpheus-v1-english"); voiceNameE?.setText(Voice.groqVoices[0]) }
                    1 -> { voiceUrlE?.setText("https://api.openai.com/v1"); voiceModelE?.setText("tts-1"); voiceNameE?.setText("nova") }
                    else -> { voiceUrlE?.setText("http://127.0.0.1:8880/v1"); voiceModelE?.setText("kokoro"); voiceNameE?.setText("af_heart") }
                }
                toast(if (n == 0) t("Groq asks you to accept the Orpheus terms once in its console before it works.", "Groq يطلب منك توافق على شروط Orpheus مرة وحدة بالكونسول.") else t("Done. Press Test the voice.", "تم. اضغط جرّب الصوت."))
                showTab(2)
            }
        }
        btn(o, t("Pick a voice", "اختر صوت")) { pickVoice() }
        slider(o, t("Voice speed %", "سرعة الصوت %"), "ttsSpeed", 60, 160, 100)
        slider(o, t("Voice pitch %", "حدة الصوت %"), "ttsPitch", 60, 160, 100)
        btn(o, t("Test the voice", "جرّب الصوت"), true) { testVoice() }

        val e = card(t("HER EARS", "أذنها"))
        label(e, t("The phone's recogniser is free but weaker for Arabic dialects. \"Her own ears\" use Whisper (Groq is free and fast).",
            "تعرف الجوال مجاني بس أضعف للهجات العربية. \"أذنها الخاصة\" تستخدم Whisper (Groq مجاني وسريع)."), 12f, Color.parseColor("#8A94A0"))
        spin(e, listOf(t("Phone recogniser", "تعرف الجوال"), t("Her own ears (Whisper)", "أذنها الخاصة (Whisper)")), prefs.getI("sttEngine", 0)) { prefs.putI("sttEngine", it) }
        edit(e, t("Ears key (empty: use the Groq key, else the brain key)", "مفتاح الأذن (فاضي: مفتاح Groq أو الدماغ)"), prefs.getS("sttKey", ""), password = true) { prefs.putS("sttKey", it.trim()) }
        sttUrlE = edit(e, t("Ears server URL (empty: Groq)", "رابط الأذن (فاضي: Groq)"), prefs.getS("sttUrl", "")) { prefs.putS("sttUrl", it.trim()) }
        sttModelE = edit(e, t("Ears model (empty: whisper-large-v3-turbo)", "موديل الأذن (فاضي: whisper-large-v3-turbo)"), prefs.getS("sttModel", "")) { prefs.putS("sttModel", it.trim()) }
        btn(e, t("Presets: Groq, OpenAI", "جاهز: Groq وOpenAI")) {
            pickDialog(t("Pick a preset", "اختر"), listOf("Groq whisper-large-v3-turbo", "OpenAI whisper-1")) { n ->
                if (n == 0) { sttUrlE?.setText("https://api.groq.com/openai/v1"); sttModelE?.setText("whisper-large-v3-turbo") }
                else { sttUrlE?.setText("https://api.openai.com/v1"); sttModelE?.setText("whisper-1") }
            }
        }
        toggle(e, t("Conversation mode: after she answers she listens again, until you say bye", "وضع المحادثة: بعد ما تجاوب تسمعك مرة ثانية، لين تقول باي"), "convo", false)
    }

    private fun pickVoice() {
        when (prefs.getI("ttsEngine", 0)) {
            Voice.GEMINI -> pickDialog(t("Pick a Gemini voice", "اختر صوت Gemini"), Voice.geminiVoices) { voiceNameE?.setText(Voice.geminiVoices[it]) }
            Voice.OPENAI -> {
                val v = if ((voiceUrlE?.text?.toString() ?: "").contains("groq")) Voice.groqVoices else listOf("nova", "shimmer", "alloy", "echo", "fable", "onyx")
                pickDialog(t("Pick a voice", "اختر صوت"), v) { voiceNameE?.setText(v[it]) }
            }
            Voice.ELEVEN -> bg({
                val k = prefs.getS("voiceKey", "").ifEmpty { prefs.key(prefs.cur) }
                val list = Voice.elevenVoices(k)
                runOnUiThread { pickDialog(t("Pick a voice", "اختر صوت"), list.map { it.label }) { i -> voiceNameE?.setText(list[i].id) } }
            }, { e -> toast(e) })
            else -> toast(t("The phone voice is chosen in Android's own text-to-speech settings.", "صوت الجوال تختاره من إعدادات تحويل النص لكلام بأندرويد."))
        }
    }

    private fun testVoice() {
        val line = t("Hi choom. This is my voice. Not bad, right?", "هلا. هذا صوتي. مو سيء، صح؟")
        toast(t("Generating...", "جاري التوليد..."))
        Thread {
            val err = Voice.speak(this, prefs, line) { }
            if (err != null) runOnUiThread {
                toast(if (err == "phone") t("The phone voice is tested from Capri herself, start her and talk.", "صوت الجوال يتجرب من كابري نفسها.") else err)
            }
        }.start()
    }

    // ---- BEHAVIOR ----

    private fun buildBehavior() {
        val look = card(t("HER LOOK", "شكلها"))
        slider(look, t("Size (applies next start)", "الحجم (يتطبق بالتشغيل الجاي)"), "size", 80, 220, 140)
        label(look, t("Theme", "الثيم"))
        spin(look, Themes.names, prefs.getI("theme", 0)) { prefs.putI("theme", it); prefs.putS("modAccent", ""); showTab(3) }
        toggle(look, t("She dances when music is playing", "ترقص لما تشتغل موسيقى"), "dance", true)
        toggle(look, t("Low-power mode: fewer animation frames, no floating", "وضع توفير الطاقة: إطارات أقل وبدون طفو"), "lowPower", false)

        val life = card(t("HER LIFE", "حياتها"))
        slider(life, t("How much she wanders (0 = stays put)", "قد إيش تتمشى (٠ = تثبت)"), "wanderPace", 0, 3, 1)
        toggle(life, t("She checks in on me now and then (uses your key)", "تطمّن علي من وقت لوقت (تستهلك مفتاحك)"), "checkin", false)
        toggle(life, t("Remind me to drink water", "تذكرني أشرب موية"), "careWater", false)
        toggle(life, t("Remind me to stretch and rest my eyes", "تذكرني أتمطط وأريّح عيوني"), "careStretch", false)
        toggle(life, t("Tell me off when it's very late", "تعاتبني لما يكون متأخر"), "careLate", false)
        toggle(life, t("Comment on low battery and charging", "تعلّق على البطارية والشحن"), "events", true)
        toggle(life, t("Mischief mode: harmless fake glitches now and then", "وضع الشقاوة: مقالب وهمية من وقت لوقت"), "mischief", false)
        toggle(life, t("Start with the phone", "تشتغل مع تشغيل الجوال"), "autostart", false)

        val you = card(t("YOU", "أنت"))
        edit(you, t("What she calls you", "وش تناديك"), prefs.getS("nick", "")) { prefs.putS("nick", it.trim()) }
        label(you, t("Language", "اللغة"))
        spin(you, listOf(t("Same as my phone", "نفس لغة الجوال"), "English", "العربية"), prefs.getI("lang", 0)) {
            prefs.putI("lang", it)
            recreate()
        }
    }

    // ---- HANDS ----

    private fun buildHands() {
        val a = card(t("ACCESSIBILITY (EYES AND HANDS)", "الوصول (العيون واليدين)"))
        val st = label(a, "", 14f, Color.WHITE, true)
        refreshers.add { st.text = t("Status: ", "الحالة: ") + if (CapriAccess.instance != null) t("on", "شغّال") else t("off", "مطفي") }
        label(a, t("With it she reads your screen, taps, types and scrolls with her own cursor, and takes screenshots. On Android 13 and newer first open App info, tap the three dots and choose Allow restricted settings.",
            "بها تقرأ شاشتك وتضغط وتكتب وتسحب بمؤشرها هي وتاخذ لقطات. من أندرويد ١٣ افتح معلومات التطبيق أول، اضغط النقاط الثلاث واختر السماح بالإعدادات المقيّدة."), 12f, Color.parseColor("#8A94A0"))
        btn(a, t("Open app info", "معلومات التطبيق")) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        btn(a, t("Open accessibility settings", "إعدادات الوصول"), true) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        val w = card(t("WATCHING", "المراقبة"))
        toggle(w, t("She watches my screen and comments (uses your key)", "تراقب شاشتي وتعلّق (تستهلك مفتاحك)"), "watch", false)
        slider(w, t("Seconds between glances", "الثواني بين كل نظرة"), "watchSecs", 20, 180, 60)
        toggle(w, t("Send screenshots too, not just screen text (needs a model that sees images)", "أرسل لقطات شاشة مو بس النص (يحتاج موديل يشوف الصور)"), "vision", false)
        label(w, t("Game mode (on the Home tab) makes her comment only while a game is open. Online brains receive what is on your screen when she looks; password fields are never read. The offline model keeps everything on the phone.",
            "وضع اللعب (بالرئيسية) يخليها تعلّق بس وقت فتح لعبة. الأدمغة الأونلاين تستلم اللي على شاشتك لما تشوف، وحقول كلمات المرور ما تنقرأ. الموديل المحلي يبقي كل شي بالجوال."), 12f, Color.parseColor("#8A94A0"))

        val n = card(t("NOTIFICATIONS AND MUSIC", "الإشعارات والموسيقى"))
        val ns = label(n, "", 13f)
        refreshers.add {
            val on = (Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: "").contains(packageName)
            ns.text = t("Notification access: ", "صلاحية الإشعارات: ") + if (on) t("on", "شغّالة") else t("off", "مطفية")
        }
        toggle(n, t("She mentions new messages and calls (who from, not the text)", "تقول لك عن الرسائل والمكالمات الجديدة (من مين، مو النص)"), "readNotifs", false)
        toggle(n, t("Include a short bit of the message text", "تضيف جزء قصير من نص الرسالة"), "readNotifContent", false)
        btn(n, t("Open notification access (also lets her tell what song is playing)", "افتح صلاحية الإشعارات (تخليها تعرف وش الأغنية)")) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val x = card(t("EXTRAS", "إضافات"))
        label(x, t("Select any text in any app and choose \"Ask Capri\" (or use Share) to have her explain or translate it. Add the Capri tile to your quick settings to start and stop her from the pull-down. Hold her to talk, tap her to type, tap the menu button for games, dictation and pranks.",
            "حدد أي نص بأي تطبيق واختر \"Ask Capri\" (أو من المشاركة) عشان تشرحه أو تترجمه. أضف مربع Capri للإعدادات السريعة لتشغلها وتوقفها. اضغط مطوّلاً عليها للكلام، اضغط عليها للكتابة، وزر القائمة للألعاب والإملاء والمقالب."), 12f, Color.parseColor("#8A94A0"))
    }

    // ---- MORE ----

    private fun buildMore() {
        val m = card(t("HER NOTES ABOUT YOU", "ملاحظاتها عنك"))
        edit(m, t("Nothing yet. She adds things as you chat.", "ما فيه شي للحين. تضيف وانت تسولف."), prefs.getS("notes", ""), multiline = true) { prefs.putS("notes", it) }

        val p = card(t("HER PERSONALITY", "شخصيتها"))
        val custom = prefs.getS("personality", "")
        val pe = edit(p, "personality", if (custom.isBlank()) Persona.default(this) else custom, multiline = true) {
            prefs.putS("personality", if (it.trim().isEmpty() || it.trim() == Persona.default(this).trim()) "" else it.trim())
        }
        label(p, t("Her safety rules always stay in place after whatever you write here.", "قواعد الأمان تبقى دايماً مهما كتبت هنا."), 12f, Color.parseColor("#8A94A0"))
        btn(p, t("Reset personality to default", "رجّع الشخصية للأصل")) { pe.setText(Persona.default(this)) }

        val mods = card(t("MODS", "المودات"))
        label(mods, t("A mod is a small .json file: {\"name\", \"accent\": \"#RRGGBB\", \"personality\": \"extra lines\", \"nickname\"}. Data only, no code.",
            "المود ملف .json صغير: {\"name\", \"accent\": \"#RRGGBB\", \"personality\": \"سطور إضافية\", \"nickname\"}. بيانات بس بدون كود."), 12f, Color.parseColor("#8A94A0"))
        btn(mods, t("Import a mod (.json)", "استيراد مود (.json)")) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 7)
        }
        btn(mods, t("Remove my mod", "احذف المود")) {
            prefs.putS("modAccent", ""); prefs.putS("modPersona", "")
            toast(t("Mod removed.", "انحذف المود."))
            showTab(5)
        }

        val d = card(t("DATA", "البيانات"))
        btn(d, t("Clear our chat history", "امسح سجل المحادثة")) { prefs.putS("history", "[]"); toast(t("Cleared. Restart her to apply.", "انمسح. أعد تشغيلها.")) }
        btn(d, t("Reset the bond to zero", "صفّر العلاقة")) { prefs.putI("charm", 0); refresh() }

        val c = card(t("CREDITS", "الشكر"))
        val tv = label(c, "Unofficial fan project, free for non-commercial use only. Based on Capri by Lachy: https://github.com/lachydotmcg/capri-desktop " +
            "(PolyForm Noncommercial 1.0.0). Her art and animations are by Capri's artist. Not approved or endorsed by CD PROJEKT RED, " +
            "the anime's rights holders or its voice actors.", 12f)
        Linkify.addLinks(tv, Linkify.WEB_URLS)
        tv.setLinkTextColor(accent)
    }

    // ---- start / stop and bits ----

    private fun toggleService() {
        if (CapriService.running) {
            stopService(Intent(this, CapriService::class.java))
        } else {
            if (!Settings.canDrawOverlays(this)) {
                toast(t("Allow floating over apps first.", "اسمح بالظهور فوق التطبيقات أول."))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                return
            }
            startForegroundService(Intent(this, CapriService::class.java))
        }
        page.postDelayed({ refresh() }, 700)
    }

    private fun refresh() { for (r in refreshers) r() }

    override fun onResume() { super.onResume(); refresh() }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

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
            if (j.has("nickname")) prefs.putS("nick", j.getString("nickname"))
            toast(t("Mod loaded: ", "انحمّل المود: ") + j.optString("name", "?"))
            showTab(tab)
        } catch (e: Exception) {
            toast(t("That isn't a valid mod file.", "هذا مو ملف مود صحيح."))
        }
    }
}
