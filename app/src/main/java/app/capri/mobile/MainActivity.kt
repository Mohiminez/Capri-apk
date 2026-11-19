package app.capri.mobile

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
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

// The settings screen, styled like the desktop "Mainframe": dark, mono labels, a red edge, cyan outlines.
class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var page: LinearLayout
    private lateinit var tabBar: LinearLayout
    private var tab = 0
    private val refreshers = ArrayList<() -> Unit>()

    private var urlE: EditText? = null
    private var keyE: EditText? = null
    private var modelE: EditText? = null
    private var voiceNameE: EditText? = null
    private var voiceModelE: EditText? = null
    private var voiceUrlE: EditText? = null
    private var sttUrlE: EditText? = null
    private var sttModelE: EditText? = null
    private var dlStatus: TextView? = null
    private var heroSprite: SpriteView? = null

    // palette
    private val bg = Night.ground
    private val panel = Night.field
    private val edge = Night.edge
    private val cyan = Night.cyan
    private val yellow = Night.rose
    private val text = Night.white
    private val muted = Night.faint
    private val accent get() = Themes.accent(prefs)

    private lateinit var tfTitle: Typeface
    private lateinit var tfMono: Typeface
    private lateinit var tfBody: Typeface
    private lateinit var tfBold: Typeface

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun t(en: String, ar: String) = L.t(en, ar)
    private fun font(name: String, fallback: Typeface) = try { Typeface.createFromAsset(assets, "fonts/$name") } catch (e: Exception) { fallback }

    private val tabNames get() = listOf(t("HOME", "الرئيسية"), t("BRAIN", "العقل"), t("VOICE", "الصوت"), t("BEHAVIOR", "السلوك"), t("HANDS", "اليدين"), t("MORE", "المزيد"))

    // brain tiers, like the desktop's NORMAL / HIGHER / MAX
    private val tier1 = listOf(0, 1, 2, 3, 4, 11)
    private val tier2 = listOf(5, 6, 7, 8, 9, 10, 12, 13)
    private fun tierOf(i: Int) = when {
        i == Providers.LOCAL -> 0
        i in tier1 -> 1
        i in tier2 -> 2
        else -> 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        L.init(prefs)
        tfTitle = font("CapriMainframe-Bold.ttf", Typeface.DEFAULT_BOLD)
        if (L.ar) {
            tfBody = font("Tajawal-Regular.ttf", Typeface.DEFAULT)
            tfMono = tfBody
            tfBold = font("Tajawal-Bold.ttf", Typeface.DEFAULT_BOLD)
        } else {
            tfMono = font("ShareTechMono-Regular.ttf", Typeface.MONOSPACE)
            tfBody = font("BarlowSemiCondensed-Regular.ttf", Typeface.DEFAULT)
            tfBold = font("BarlowSemiCondensed-SemiBold.ttf", Typeface.DEFAULT_BOLD)
        }
        tab = prefs.getI("lastTab", 0).coerceIn(0, 5)

        val rootCol = LinearLayout(this)
        rootCol.orientation = LinearLayout.VERTICAL
        rootCol.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Night.glow, Night.ground, Night.ground))
        rootCol.layoutDirection = if (L.ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(18), dp(24), dp(18), dp(8))
        val title = TextView(this)
        title.text = "CAPRI"
        title.typeface = tfTitle
        title.textSize = 26f
        title.setTextColor(Color.WHITE)
        head.addView(title)
        val ver = TextView(this)
        ver.text = "  mobile \u00B7 v" + Updater.current(this)
        ver.typeface = tfMono
        ver.textSize = 12f
        ver.setTextColor(muted)
        head.addView(ver)
        rootCol.addView(head)

        val tabScroll = HorizontalScrollView(this)
        tabScroll.isHorizontalScrollBarEnabled = false
        tabBar = LinearLayout(this)
        tabBar.orientation = LinearLayout.HORIZONTAL
        tabBar.setPadding(dp(10), 0, dp(10), 0)
        tabScroll.addView(tabBar)
        rootCol.addView(tabScroll)
        val line = View(this)
        line.setBackgroundColor(edge)
        rootCol.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))

        val scroll = ScrollView(this)
        page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(12), dp(8), dp(12), dp(48))
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
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.setPadding(dp(12), dp(10), dp(12), 0)
            val v = TextView(this)
            v.text = name
            v.typeface = tfMono
            v.textSize = 13f
            v.setTextColor(if (n == i) Color.WHITE else muted)
            col.addView(v)
            val bar = View(this)
            bar.setBackgroundColor(if (n == i) accent else Color.TRANSPARENT)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
            lp.topMargin = dp(8)
            col.addView(bar, lp)
            col.setOnClickListener { showTab(n) }
            tabBar.addView(col)
        }
        page.removeAllViews()
        refreshers.clear()
        urlE = null; keyE = null; modelE = null; heroSprite = null
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

    // A panel with the red edge and a yellow "TITLE >" label, like the desktop.
    private fun section(title: String?): LinearLayout {
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.HORIZONTAL
        outer.background = NeonPanel(resources.displayMetrics.density, Color.argb(246, 17, 16, 28))
        val bar = View(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), dp(12), dp(14), dp(12))
        outer.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(7), 0, dp(7))
        page.addView(outer, lp)
        if (title != null) heading(col, "$title >")
        return col
    }

    private fun heading(p: LinearLayout, s: String): TextView {
        val v = TextView(this)
        v.text = "\u25A0 " + (if (L.ar) s.removeSuffix(" >") else s.removeSuffix(" >").uppercase())
        v.typeface = tfBold
        v.textSize = 14f
        v.setTextColor(Night.rose)
        v.setPadding(0, 0, 0, dp(8))
        p.addView(v)
        return v
    }

    private fun body(p: LinearLayout, s: String, size: Float = 15f, color: Int = text, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = s
        v.typeface = if (bold) tfBold else tfBody
        v.textSize = size
        v.setTextColor(color)
        v.setPadding(0, dp(3), 0, dp(3))
        p.addView(v)
        return v
    }

    private fun note(p: LinearLayout, s: String) = body(p, s, 13f, muted)

    private fun dots(p: LinearLayout) {
        val v = TextView(this)
        v.text = "\u25AA \u25AA \u25AA \u25AA \u25AA \u25AA \u25AA \u25AA \u25AA \u25AA \u25AA \u25AA \u25AA \u25AA \u25AA \u25AA \u25AA \u25AA"
        v.setTextColor(edge)
        v.textSize = 10f
        v.setPadding(0, dp(6), 0, dp(6))
        v.maxLines = 1
        p.addView(v)
    }

    private fun btn(p: LinearLayout, s: String, primary: Boolean = false, click: () -> Unit): TextView {
        val v = TextView(this)
        v.text = s
        v.typeface = tfMono
        v.textSize = 13f
        v.gravity = Gravity.CENTER
        v.setTextColor(Night.white)
        v.setPadding(dp(14), dp(12), dp(14), dp(12))
        v.background = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(if (primary) Night.hot else Night.hover)
            setStroke(dp(2), if (primary) Night.hot else Night.edge)
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
        e.typeface = tfMono
        e.setTextColor(Color.WHITE)
        e.setHintTextColor(muted)
        e.textSize = 14f
        e.setPadding(dp(12), dp(10), dp(12), dp(10))
        e.background = GradientDrawable().apply {
            cornerRadius = dp(6).toFloat()
            setColor(bg)
            setStroke(dp(1), edge)
        }
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
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(5), 0, dp(5))
        p.addView(e, lp)
        return e
    }

    private fun toggle(p: LinearLayout, s: String, k: String, default: Boolean, extra: ((Boolean) -> Unit)? = null) {
        val sw = Switch(this)
        sw.text = s
        sw.typeface = tfBody
        sw.textSize = 15f
        sw.setTextColor(text)
        sw.isChecked = prefs.getB(k, default)
        sw.thumbTintList = ColorStateList.valueOf(cyan)
        sw.setOnCheckedChangeListener { _, on -> prefs.putB(k, on); extra?.invoke(on) }
        sw.setPadding(0, dp(8), 0, dp(8))
        p.addView(sw)
    }

    private fun slider(p: LinearLayout, s: String, k: String, min: Int, max: Int, default: Int) {
        val l = body(p, "$s: ${prefs.getI(k, default)}", 14f, muted)
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = prefs.getI(k, default) - min
        sb.progressTintList = ColorStateList.valueOf(accent)
        sb.thumbTintList = ColorStateList.valueOf(cyan)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(x: SeekBar?, v: Int, fromUser: Boolean) { prefs.putI(k, min + v); l.text = "$s: ${min + v}" }
            override fun onStartTrackingTouch(x: SeekBar?) {}
            override fun onStopTrackingTouch(x: SeekBar?) {}
        })
        p.addView(sb)
    }

    private fun spin(p: LinearLayout, items: List<String>, selected: Int, onPick: (Int) -> Unit): Spinner {
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        sp.setSelection(selected.coerceIn(0, maxOf(0, items.size - 1)))
        sp.backgroundTintList = ColorStateList.valueOf(cyan)
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

    class Opt(val label: String, val desc: String = "", val tag: String = "")

    // One-of-many list: a cyan dot, a yellow name, a grey explanation.
    private fun radios(p: LinearLayout, items: List<Opt>, selected: Int, onPick: (Int) -> Unit) {
        val dotsV = ArrayList<TextView>()
        val names = ArrayList<TextView>()
        fun paint(sel: Int) {
            for (i in dotsV.indices) {
                dotsV[i].text = if (i == sel) "\u25C9" else "\u25CB"
                dotsV[i].setTextColor(if (i == sel) cyan else muted)
                names[i].setTextColor(if (i == sel) Night.rose else text)
            }
        }
        for ((i, o) in items.withIndex()) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(0, dp(7), 0, dp(7))
            val d = TextView(this)
            d.textSize = 20f
            d.setPadding(0, 0, dp(12), 0)
            row.addView(d)
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val n = TextView(this)
            n.text = o.label
            n.typeface = tfMono
            n.textSize = 15f
            col.addView(n)
            if (o.desc.isNotEmpty()) {
                val de = TextView(this)
                de.text = o.desc
                de.typeface = tfBody
                de.textSize = 13f
                de.setTextColor(muted)
                col.addView(de)
            }
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (o.tag.isNotEmpty()) {
                val tg = TextView(this)
                tg.text = o.tag
                tg.typeface = tfMono
                tg.textSize = 12f
                tg.setTextColor(yellow)
                row.addView(tg)
            }
            row.setOnClickListener { paint(i); onPick(i) }
            dotsV.add(d); names.add(n)
            p.addView(row)
        }
        paint(selected)
    }

    private fun pickDialog(titleText: String, items: List<String>, onPick: (Int) -> Unit) {
        if (items.isEmpty()) { toast(t("Nothing found.", "ما لقيت شي.")); return }
        AlertDialog.Builder(this).setTitle(titleText).setItems(items.toTypedArray()) { _, i -> onPick(i) }.show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun bgWork(work: () -> Unit, fail: (String) -> Unit) {
        Thread {
            try { work() } catch (e: Throwable) { runOnUiThread { fail(e.message ?: "error") } }
        }.start()
    }

    private fun openUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun askMic() {
        val perms = arrayListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(perms.toTypedArray(), 1)
    }

    // ---- HOME ----

    private fun buildHome() {
        val hero = section(null)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val sp = SpriteView(this)
        sp.glowColor = accent
        sp.play("idle")
        sp.setOnClickListener { sp.play("grin", false) { sp.play("idle") } }
        heroSprite = sp
        row.addView(sp, LinearLayout.LayoutParams(dp(92), dp(147)))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(12), 0, 0, 0)
        val status = body(col, "", 16f, Color.WHITE, true)
        val charmLabel = body(col, "", 14f, muted)
        val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        pb.max = 100
        pb.progressTintList = ColorStateList.valueOf(accent)
        col.addView(pb)
        val startBtn = btn(col, "", true) { toggleService() }
        btn(col, t("PLAY CHESS WITH HER", "العب شطرنج معها")) { startActivity(Intent(this, ChessActivity::class.java)) }
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hero.addView(row)
        refreshers.add {
            val on = CapriService.running
            status.text = if (on) t("\u25CF On your screen", "\u25CF على شاشتك") else t("\u25CB Asleep", "\u25CB نايمة")
            startBtn.text = if (on) t("STOP CAPRI", "إيقاف كابري") else t("WAKE HER UP", "صحّيها")
            val c = prefs.getI("charm", 0)
            charmLabel.text = t("Bond: ", "العلاقة: ") + Charm.label(c)
            pb.progress = Charm.progress(c)
        }

        // setup assistant, like the desktop's numbered steps
        val setup = section(t("SETUP", "التجهيز"))
        class Step(val name: () -> String, val ok: () -> Boolean, val fix: () -> Unit, val optional: Boolean = false)
        val steps = listOf(
            Step({ t("Allow floating over apps", "اسمح بالظهور فوق التطبيقات") }, { Settings.canDrawOverlays(this) }, {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }),
            Step({ t("Give her a brain", "أعطها عقل") }, { Brain(this, prefs).anyReady() }, { showTab(1) }),
            Step({ t("Microphone and notifications", "المايك والإشعارات") }, { hasMic() }, { askMic() }),
            Step({ t("Her hands: accessibility (optional)", "اليدين: صلاحية الوصول (اختياري)") }, { CapriAccess.instance != null }, { showTab(4) }, true),
            Step({ t("Keep her alive in the background (battery)", "خلّها تشتغل بالخلفية (البطارية)") },
                { (getSystemService(POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(packageName) },
                { try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) } catch (e: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }, true),
            Step({ t("Wake her up", "صحّيها") }, { CapriService.running }, { toggleService() })
        )
        val stepViews = steps.map { body(setup, "", 15f) }
        dots(setup)
        val next = btn(setup, "", true) { }
        refreshers.add {
            val firstTodo = steps.indexOfFirst { !it.ok() && !it.optional }
            for ((i, s) in steps.withIndex()) {
                val done = s.ok()
                stepViews[i].text = (if (done) "\u2713 " else "${i + 1} ") + s.name()
                stepViews[i].setTextColor(if (done) cyan else if (i == firstTodo) Color.WHITE else muted)
                stepViews[i].typeface = if (i == firstTodo) tfBold else tfBody
            }
            if (firstTodo < 0) {
                next.text = t("All set", "كل شي جاهز")
                next.setOnClickListener { }
            } else {
                next.text = t("Next: ", "التالي: ") + steps[firstTodo].name()
                next.setOnClickListener { steps[firstTodo].fix() }
            }
        }

        val modes = section(t("MODE", "الوضع"))
        val mode = if (prefs.getB("quiet", false)) 1 else if (prefs.getB("gameMode", false)) 2 else 0
        radios(modes, listOf(
            Opt(t("NORMAL", "عادي"), t("She talks, wanders, reacts", "تتكلم وتتمشى وتتفاعل")),
            Opt(t("QUIET", "هادئ"), t("She only types, never speaks aloud", "تكتب بس وما تتكلم بصوت")),
            Opt(t("GAME", "لعب"), t("Stays out of the way, comments while a game is open", "تبعد عن طريقك وتعلّق وقت اللعب"), t("needs hands", "تحتاج اليدين"))
        ), mode) {
            prefs.putB("quiet", it == 1)
            prefs.putB("gameMode", it == 2)
        }

        val sec = section(t("SECURITY", "الأمان"))
        // levels: 0 lockdown, 3 ask every time, 1 guarded, 2 trusted
        val order = listOf(0, 3, 1, 2)
        radios(sec, listOf(
            Opt(t("LOCKDOWN", "إغلاق كامل"), t("Chat only. She never touches the phone", "سوالف بس. ما تلمس الجوال أبداً")),
            Opt(t("ASK EVERY TIME", "تسأل كل مرة"), t("Even your own orders wait for a Yes", "حتى أوامرك تنتظر منك «نعم»")),
            Opt(t("GUARDED", "حذر"), t("She asks first for her own ideas; your direct orders just run", "تسأل أول عن أفكارها، وأوامرك المباشرة تنفّذ على طول"), t("default", "الافتراضي")),
            Opt(t("TRUSTED", "موثوق"), t("She does what's asked without asking again", "تسوي المطلوب بدون ما تسأل مرة ثانية"))
        ), order.indexOf(prefs.getI("security", 1)).coerceAtLeast(0)) { prefs.putI("security", order[it]) }

        val hands = section(t("TRY HER HANDS", "جرّب يدينها"))
        note(hands, t("Say or type: \"open WhatsApp and type hi\", \"scroll down\", \"tap Send\", \"go back\", \"take a screenshot\".",
            "قل أو اكتب: \"افتح واتساب واكتب مرحبا\"، \"انزل\"، \"اضغط إرسال\"، \"ارجع\"، \"لقطة شاشة\"."))
        btn(hands, t("TEST HER HANDS", "اختبار اليدين")) {
            if (CapriAccess.instance == null) {
                toast(t("Turn on accessibility first.", "شغّل صلاحية الوصول أول."))
                showTab(4)
            } else if (!Settings.canDrawOverlays(this)) {
                toast(t("Allow floating over apps first.", "اسمح بالظهور فوق التطبيقات أول."))
            } else {
                startForegroundService(Intent(this, CapriService::class.java).setAction(CapriService.ACT_TEST))
            }
        }
    }

    // ---- BRAIN ----

    private fun buildBrain() {
        val cur = prefs.cur
        val tier = tierOf(cur)
        val t0 = section(t("BRAIN", "العقل"))
        note(t0, t("Pick one. She can use more than one: any provider you add a key for becomes her backup.", "اختر واحد. تقدر تستخدم أكثر من مزوّد: أي مزوّد تضيف له مفتاح يصير احتياطي."))
        radios(t0, listOf(
            Opt(t("NORMAL", "عادي"), t("Free \u00B7 runs on this phone, no internet", "مجاني \u00B7 يشتغل على الجوال بدون إنترنت"), t("start here", "ابدأ من هنا")),
            Opt(t("HIGHER", "أعلى"), t("Free keys online: Gemini, Groq, NVIDIA...", "مفاتيح مجانية أونلاين: Gemini وGroq وNVIDIA...")),
            Opt(t("MAX", "الأقصى"), t("ChatGPT, Claude, Grok, Mistral...", "ChatGPT وClaude وGrok وMistral...")),
            Opt(t("OWN SERVER", "سيرفرك"), t("Ollama, LM Studio or any OpenAI-style address", "Ollama أو LM Studio أو أي عنوان بنمط OpenAI"))
        ), tier) { n ->
            val target = when (n) { 0 -> Providers.LOCAL; 1 -> tier1[0]; 2 -> tier2[0]; else -> Providers.CUSTOM }
            if (tierOf(prefs.cur) != n) prefs.putI("provider", target)
            showTab(1)
        }

        if (tier == 0) {
            buildOffline()
        } else {
            val list = when (tier) { 1 -> tier1; 2 -> tier2; else -> listOf(Providers.CUSTOM) }
            val c = section(t("PROVIDER", "المزوّد"))
            if (list.size > 1) spin(c, list.map { Providers.all[it].name }, list.indexOf(cur).coerceAtLeast(0)) { n ->
                prefs.putI("provider", list[n])
                urlE?.setText(prefs.url(list[n])); keyE?.setText(prefs.key(list[n])); modelE?.setText(prefs.model(list[n]))
            }
            urlE = edit(c, t("Base URL (local server: http://127.0.0.1:11434/v1)", "عنوان المزوّد (للسيرفر المحلي: http://127.0.0.1:11434/v1)"), prefs.url(prefs.cur)) { prefs.putUrl(prefs.cur, it) }
            keyE = edit(c, t("API key (not needed for a local server)", "مفتاح API (ما يلزم للسيرفر المحلي)"), prefs.key(prefs.cur), password = true) { prefs.putKey(prefs.cur, it) }
            modelE = edit(c, t("Model (empty = I pick one)", "الموديل (فاضي = أنا أختار)"), prefs.model(prefs.cur)) { prefs.putModel(prefs.cur, it) }
            btn(c, t("GET A KEY", "احصل على مفتاح")) {
                val kp = Providers.all[prefs.cur].keyPage
                if (kp.isNotEmpty()) openUrl(kp) else toast(t("No key page for this one.", "ما فيه صفحة مفاتيح لهذا المزوّد."))
            }
            btn(c, t("FIND MODELS FOR MY KEY", "ابحث عن موديلات لمفتاحي")) {
                toast(t("Looking...", "أبحث..."))
                val i = prefs.cur
                bgWork({
                    val models = Models.fetch(prefs.url(i), prefs.key(i))
                    runOnUiThread { pickDialog(t("Pick a model", "اختر موديل"), models) { n -> prefs.putModel(i, models[n]); modelE?.setText(models[n]) } }
                }, { e -> toast(t("Couldn't list models: ", "ما قدرت أجيب قائمة الموديلات: ") + e) })
            }
        }

        val noai = section(t("NO AI MODE", "وضع بدون ذكاء اصطناعي"))
        note(noai, t("She still opens apps, types, scrolls and sets timers from your commands, and answers chat with a few canned lines. Nothing leaves the phone and nothing costs anything.",
            "تبقى تفتح التطبيقات وتكتب وتسحب وتضبط المؤقتات من أوامرك، وترد على السوالف بكم جملة جاهزة. ما يطلع شي من الجوال وما يكلّف شي."))
        toggle(noai, t("No AI mode", "وضع بدون ذكاء اصطناعي"), "noAI", false)

        val test = section(t("CHECK", "فحص"))
        btn(test, t("TEST THE BRAIN", "جرّب العقل"), true) {
            toast(t("Asking...", "أسأل..."))
            val b = Brain(this, prefs)
            b.ask("Say hi in five words.", true) { r, e ->
                toast(if (r != null) "\u2714 ${b.lastUsed}: " + r.take(120) else "\u2716 " + e)
            }
        }

        val r = section(t("NEVER GO SILENT", "ما تسكت أبداً"))
        toggle(r, t("When one provider fails, switch to another that has a key", "إذا تعطّل مزوّد، انتقل لمزوّد ثاني عندك له مفتاح"), "fallback", true)
        toggle(r, t("Use the offline model as a last resort", "استخدم الموديل المحلي كحل أخير"), "offlineFallback", true)
        toggle(r, t("Let offline mode use online brains when the offline model fails (off keeps everything on the phone)", "اسمح للوضع المحلي يستخدم مزوّدات أونلاين إذا فشل الموديل المحلي (إذا طفّيته يبقى كل شي على الجوال)"), "localToCloud", false)
        body(r, t("Job brain (phone jobs and looking at the screen)", "عقل المهام (لمهام الجوال وقراءة الشاشة)"), 14f, muted)
        val opts = listOf(t("Same as chat", "نفس عقل المحادثة")) + Providers.all.indices.filter { it != Providers.LOCAL }.map { Providers.all[it].name }
        val idxs = listOf(-1) + Providers.all.indices.filter { it != Providers.LOCAL }
        spin(r, opts, idxs.indexOf(prefs.getI("jobProvider", -1)).coerceAtLeast(0)) { prefs.putI("jobProvider", idxs[it]) }
    }

    private fun buildOffline() {
        val o = section(t("ON THIS PHONE", "على الجوال"))
        note(o, t("Download one model once. It needs a fairly recent phone and 3 GB or more of free memory, and it frees that memory after two quiet minutes.",
            "نزّل موديل واحد مرة وحدة. يحتاج جوال حديث ومساحة ذاكرة فاضية ٣ جيجا أو أكثر، وبعد دقيقتين من عدم الاستخدام يفرّغ الذاكرة."))
        val active = body(o, "", 14f, cyan)
        refreshers.add {
            val n = prefs.getS("localModel", "")
            val f = LocalLlm.file(this, n)
            active.text = if (n.isNotBlank() && f.exists()) "\u2713 " + n + " (" + f.length() / 1_000_000 + " MB)" else t("No model downloaded yet.", "ما نزّلت أي موديل للحين.")
        }
        val mi = android.app.ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(mi)
        val totalMb = (mi.totalMem / 1_000_000).toInt()
        note(o, t("Your phone has about $totalMb MB of memory.", "جوالك فيه حوالي $totalMb ميجا ذاكرة."))
        LocalLlm.catalog.forEach { m ->
            val fits = totalMb >= m.needMb * 2
            btn(o, t("DOWNLOAD ", "تنزيل ") + m.label + "  " + (if (fits) "\u2713" else t("(tight on memory)", "(الذاكرة ضيقة)"))) { downloadModel(m.file, m.url) }
        }
        val customUrl = edit(o, t("Or paste any .litertlm link", "أو الصق رابط أي ملف .litertlm"), "") { }
        btn(o, t("DOWNLOAD THAT LINK", "نزّل هذا الرابط")) {
            val u = customUrl.text.toString().trim()
            if (u.startsWith("http")) {
                var n = u.substringBefore('?').substringAfterLast('/')
                if (!n.endsWith(".litertlm")) n = "custom.litertlm"
                downloadModel(n, u)
            }
        }
        edit(o, t("Hugging Face token (gated models like Gemma)", "توكن Hugging Face (للموديلات المقفلة مثل Gemma)"), prefs.getSecret("hfToken"), password = true) { prefs.putSecret("hfToken", it) }
        dlStatus = body(o, "", 14f, yellow)
        val installed = LocalLlm.dir(this).listFiles { f -> f.name.endsWith(".litertlm") }?.map { it.name } ?: emptyList()
        if (installed.isNotEmpty()) {
            body(o, t("Installed:", "المنزّلة:"), 14f, muted)
            spin(o, installed, installed.indexOf(prefs.getS("localModel", "")).coerceAtLeast(0)) { prefs.putS("localModel", installed[it]); refresh() }
        }
        toggle(o, t("Use the GPU (faster on some phones, may crash on others)", "استخدم معالج الرسوم (أسرع في بعض الجوالات وقد يتعطل في غيرها)"), "gpu", false)
    }

    private fun downloadModel(name: String, link: String) {
        dlStatus?.text = t("Downloading... 0%", "جاري التنزيل... ٠٪")
        bgWork({
            val err = LocalLlm.download(this, link, name, prefs.getSecret("hfToken")) { p ->
                runOnUiThread { dlStatus?.text = t("Downloading... $p%", "جاري التنزيل... $p٪") }
            }
            runOnUiThread {
                if (err == null) {
                    prefs.putS("localModel", name)
                    prefs.putI("provider", Providers.LOCAL)
                    dlStatus?.text = t("Ready. She will use it offline.", "جاهز. بتشتغل بدون إنترنت.")
                    refresh()
                } else {
                    dlStatus?.text = t("Failed: ", "فشل: ") + err
                }
            }
        }, { e -> dlStatus?.text = t("Failed: ", "فشل: ") + e })
    }

    // ---- VOICE ----

    private fun buildVoice() {
        val o = section(t("HER VOICE", "صوتها"))
        note(o, t("The phone voice is basic. Groq's Orpheus is free and has a Saudi Arabic voice, Gemini is free with your Gemini key, ElevenLabs is what the Windows version uses.",
            "صوت الجوال عادي. Groq Orpheus مجاني وفيه صوت سعودي، وGemini مجاني بمفتاحك، وElevenLabs هو اللي تستخدمه نسخة الويندوز."))
        toggle(o, t("She talks out loud", "تتكلم بصوت مسموع"), "voice", true)
        spin(o, Voice.engines, prefs.getI("ttsEngine", 0)) {
            prefs.putI("ttsEngine", it)
            voiceNameE?.setText(if (it == Voice.ELEVEN) prefs.getS("voiceId", "") else prefs.getS("voiceName", ""))
        }
        edit(o, t("Voice key (empty = use the brain key)", "مفتاح الصوت (فاضي = يستخدم مفتاح العقل)"), prefs.getSecret("voiceKey"), password = true) { prefs.putSecret("voiceKey", it) }
        voiceNameE = edit(o, t("Voice (ElevenLabs id, Gemini name, or OpenAI/Groq voice)", "الصوت (معرّف ElevenLabs أو اسم صوت Gemini أو صوت OpenAI/Groq)"),
            if (prefs.getI("ttsEngine", 0) == Voice.ELEVEN) prefs.getS("voiceId", "") else prefs.getS("voiceName", "")) {
            if (prefs.getI("ttsEngine", 0) == Voice.ELEVEN) prefs.putS("voiceId", it.trim()) else prefs.putS("voiceName", it.trim())
        }
        voiceModelE = edit(o, t("Voice model (optional)", "موديل الصوت (اختياري)"), prefs.getS("voiceModel", "")) { prefs.putS("voiceModel", it.trim()) }
        voiceUrlE = edit(o, t("Server URL (Groq / OpenAI / Kokoro only)", "عنوان السيرفر (فقط لـ Groq / OpenAI / Kokoro)"), prefs.getS("voiceUrl", "")) { prefs.putS("voiceUrl", it.trim()) }
        btn(o, t("PRESETS: GROQ, OPENAI, KOKORO", "إعدادات جاهزة: Groq وOpenAI وKokoro")) {
            val names = listOf(t("Groq Orpheus (free, English + Saudi Arabic)", "Groq Orpheus (مجاني، إنجليزي وعربي سعودي)"), "OpenAI tts-1", t("Kokoro server on this phone or PC", "سيرفر Kokoro على الجوال أو الكمبيوتر"))
            pickDialog(t("Pick a preset", "اختر إعداداً جاهزاً"), names) { n ->
                prefs.putI("ttsEngine", Voice.OPENAI)
                when (n) {
                    0 -> { voiceUrlE?.setText("https://api.groq.com/openai/v1"); voiceModelE?.setText(if (L.ar) "canopylabs/orpheus-arabic-saudi" else "canopylabs/orpheus-v1-english"); voiceNameE?.setText(Voice.groqVoices[0]) }
                    1 -> { voiceUrlE?.setText("https://api.openai.com/v1"); voiceModelE?.setText("tts-1"); voiceNameE?.setText("nova") }
                    else -> { voiceUrlE?.setText("http://127.0.0.1:8880/v1"); voiceModelE?.setText("kokoro"); voiceNameE?.setText("af_heart") }
                }
                toast(if (n == 0) t("Groq asks you to accept the Orpheus terms once in its console before it works.", "Groq يطلب منك الموافقة على شروط Orpheus مرة وحدة من الكونسول قبل ما يشتغل.") else t("Done. Press Test the voice.", "تم. اضغط «جرّب الصوت»."))
                showTab(2)
            }
        }
        btn(o, t("PICK A VOICE", "اختر صوتاً")) { pickVoice() }
        slider(o, t("Volume %", "مستوى الصوت %"), "ttsVolume", 20, 100, 100)
        toggle(o, t("Start with her voice off every time (privacy)", "تبدأ وصوتها مقفل كل مرة (للخصوصية)"), "startSilent", false)
        slider(o, t("Speed %", "السرعة %"), "ttsSpeed", 60, 160, 100)
        slider(o, t("Pitch %", "حدّة الصوت %"), "ttsPitch", 60, 160, 100)
        btn(o, t("TEST THE VOICE", "جرّب الصوت"), true) { testVoice() }

        val e = section(t("HER EARS", "أذنها"))
        note(e, t("The phone's recogniser is free but weaker for Arabic dialects. \"Her own ears\" use Whisper (Groq is free and fast).",
            "تعرّف الجوال على الصوت مجاني بس أضعف مع اللهجات العربية. «أذنها الخاصة» تستخدم Whisper (وGroq مجاني وسريع)."))
        spin(e, listOf(t("Phone recogniser", "تعرّف الجوال"), t("Her own ears (Whisper)", "أذنها الخاصة (Whisper)")), prefs.getI("sttEngine", 0)) { prefs.putI("sttEngine", it) }
        edit(e, t("Ears key (empty = Groq key, then brain key)", "مفتاح الأذن (فاضي = مفتاح Groq ثم مفتاح العقل)"), prefs.getSecret("sttKey"), password = true) { prefs.putSecret("sttKey", it) }
        sttUrlE = edit(e, t("Ears server URL (empty = Groq)", "عنوان سيرفر الأذن (فاضي = Groq)"), prefs.getS("sttUrl", "")) { prefs.putS("sttUrl", it.trim()) }
        sttModelE = edit(e, t("Ears model (empty = whisper-large-v3-turbo)", "موديل الأذن (فاضي = whisper-large-v3-turbo)"), prefs.getS("sttModel", "")) { prefs.putS("sttModel", it.trim()) }
        btn(e, t("PRESETS: GROQ, OPENAI", "إعدادات جاهزة: Groq وOpenAI")) {
            pickDialog(t("Pick a preset", "اختر إعداداً جاهزاً"), listOf("Groq whisper-large-v3-turbo", "OpenAI whisper-1")) { n ->
                if (n == 0) { sttUrlE?.setText("https://api.groq.com/openai/v1"); sttModelE?.setText("whisper-large-v3-turbo") }
                else { sttUrlE?.setText("https://api.openai.com/v1"); sttModelE?.setText("whisper-1") }
            }
        }
        toggle(e, t("Conversation mode: she listens again after answering, until you say bye", "وضع المحادثة: بعد ما تجاوب تسمعك مرة ثانية، لين تقول «باي»"), "convo", false)
    }

    private fun pickVoice() {
        when (prefs.getI("ttsEngine", 0)) {
            Voice.GEMINI -> pickDialog(t("Pick a Gemini voice", "اختر صوت Gemini"), Voice.geminiVoices) { voiceNameE?.setText(Voice.geminiVoices[it]) }
            Voice.OPENAI -> {
                val v = if ((voiceUrlE?.text?.toString() ?: "").contains("groq")) Voice.groqVoices else listOf("nova", "shimmer", "alloy", "echo", "fable", "onyx")
                pickDialog(t("Pick a voice", "اختر صوتاً"), v) { voiceNameE?.setText(v[it]) }
            }
            Voice.ELEVEN -> bgWork({
                val k = prefs.getSecret("voiceKey").ifEmpty { prefs.key(prefs.cur) }
                val list = Voice.elevenVoices(k)
                runOnUiThread { pickDialog(t("Pick a voice", "اختر صوتاً"), list.map { it.label }) { i -> voiceNameE?.setText(list[i].id) } }
            }, { e -> toast(e) })
            else -> toast(t("The phone voice is chosen in Android's own text-to-speech settings.", "صوت الجوال تختاره من إعدادات تحويل النص إلى كلام في أندرويد."))
        }
    }

    private fun testVoice() {
        val line = t("Hi choom. This is my voice. Not bad, right?", "هلا يا خوي. هذا صوتي. مو بطّال، صح؟")
        toast(t("Generating...", "جاري التوليد..."))
        Thread {
            val err = Voice.speak(this, prefs, line) { }
            if (err != null) runOnUiThread {
                toast(if (err == "phone") t("The phone voice is tested from Capri herself, start her and talk.", "صوت الجوال يتجرّب من كابري نفسها: شغّلها وكلّمها.") else err)
            }
        }.start()
    }

    // ---- BEHAVIOR ----

    private fun buildBehavior() {
        val look = section(t("HER LOOK", "شكلها"))
        slider(look, t("Size (applies next start)", "الحجم (يتطبّق عند التشغيل الجاي)"), "size", 80, 220, 140)
        body(look, t("Theme", "الثيم"), 14f, muted)
        spin(look, Themes.names, prefs.getI("theme", 0)) { prefs.putI("theme", it); prefs.putS("modAccent", ""); showTab(3) }
        toggle(look, t("She dances when music is playing", "ترقص لما تشتغل موسيقى"), "dance", true)
        toggle(look, t("A small blip sound when she speaks", "صوت تنبيه صغير لما تتكلم"), "chimes", false)
        toggle(look, t("Low-power mode: fewer frames, no floating, no glow", "وضع توفير الطاقة: إطارات أقل وبدون طفو وبدون هالة"), "lowPower", false)

        val cols = section(t("HER COLORS", "ألوانها"))
        note(cols, t("Recolor her like the desktop version. It applies the next time you start her.", "لوّنها مثل نسخة الويندوز. يتطبّق عند التشغيل الجاي."))
        val slotNames = listOf(t("Hair", "الشعر"), t("Accents", "التفاصيل"), t("Eyes", "العيون"), t("Edges", "الحواف"), t("Body", "الجسم"))
        for ((i, slot) in Recolor.slots.withIndex()) {
            val b = btn(cols, "") { }
            fun label() {
                val hex = prefs.getS("col_$slot", "")
                val nm = Recolor.named.firstOrNull { it.second == hex }?.first ?: hex
                b.text = slotNames[i].uppercase() + ":  " + (if (nm.isEmpty() || nm == "Original") t("Original", "الأصلي") else nm)
            }
            label()
            b.setOnClickListener {
                pickDialog(slotNames[i], Recolor.named.map { it.first }) { n ->
                    prefs.putS("col_$slot", Recolor.named[n].second)
                    label()
                }
            }
        }
        btn(cols, t("RESET ALL COLORS", "أعد كل الألوان للأصل")) {
            for (slot in Recolor.slots) prefs.putS("col_$slot", "")
            showTab(3)
        }

        val tone = section(t("HER TONE", "أسلوبها"))
        radios(tone, listOf(
            Opt("CAPRI", t("Her usual teasing chaos", "أسلوبها المعتاد: شقاوة وتحدّي")),
            Opt(t("CALM", "هادئ"), t("Gentle and supportive", "لطيفة وداعمة")),
            Opt(t("PRO", "رسمي"), t("Short, little slang", "مختصرة وبدون عامية كثير")),
            Opt(t("WILD", "جامح"), t("Cheekier, never cruel", "أجرأ وأكثر فوضى، بدون قسوة"))
        ), prefs.getI("tone", 0)) { prefs.putI("tone", it) }
        edit(tone, t("Always reply in this language (optional, e.g. Arabic, French)", "ترد دايماً بهذه اللغة (اختياري، مثلاً: العربية، الفرنسية)"), prefs.getS("replyLang", "")) { prefs.putS("replyLang", it.trim()) }

        val life = section(t("HER LIFE", "حياتها"))
        slider(life, t("Falls asleep after (minutes, 0 = never)", "تنام بعد (دقائق، ٠ = ما تنام)"), "sleepMin", 0, 30, 3)
        slider(life, t("How often she pops in (minutes)", "كل كم دقيقة تطلّ عليك"), "popMin", 10, 120, 30)
        slider(life, t("Doomscroll guard: nag me after (minutes, 0 = off, needs hands)", "حارس التصفّح: نبّهني بعد (دقائق، ٠ = مطفي، يحتاج اليدين)"), "scrollLimit", 0, 60, 0)
        toggle(life, t("She gets jealous when I open another AI app", "تغار لما أفتح تطبيق ذكاء اصطناعي ثاني"), "jealous", true)
        toggle(life, t("Hologram: she can't be touched, taps go through (restart her)", "هولوغرام: ما تنلمس والضغطات تمرّ من خلالها (أعد تشغيلها)"), "hologram", false)
        slider(life, t("How much she wanders (0 = stays put)", "قد إيش تتمشى (٠ = تثبت مكانها)"), "wanderPace", 0, 3, 1)
        toggle(life, t("She checks in on me now and then (uses your key)", "تطمّن علي من وقت لوقت (تستهلك مفتاحك)"), "checkin", false)
        toggle(life, t("Remind me to drink water", "ذكّرني أشرب موية"), "careWater", false)
        toggle(life, t("Remind me to stretch and rest my eyes", "ذكّرني أتمطّط وأريّح عيوني"), "careStretch", false)
        toggle(life, t("Tell me off when it's very late", "عاتبني لما يصير الوقت متأخر"), "careLate", false)
        toggle(life, t("Comment on low battery and charging", "تعلّق على البطارية والشحن"), "events", true)
        toggle(life, t("Mischief mode: harmless fake glitches now and then", "وضع الشقاوة: مقالب وهمية بريئة من وقت لوقت"), "mischief", false)
        toggle(life, t("Start with the phone", "تشتغل مع تشغيل الجوال"), "autostart", false)

        val you = section(t("YOU", "أنت"))
        edit(you, t("What she calls you", "وش تناديك"), prefs.getS("nick", "")) { prefs.putS("nick", it.trim()) }
        body(you, t("Language", "اللغة"), 14f, muted)
        spin(you, listOf(t("Same as my phone", "نفس لغة الجوال"), "English", "العربية"), prefs.getI("lang", 0)) {
            prefs.putI("lang", it)
            recreate()
        }
    }

    // ---- HANDS ----

    private fun buildHands() {
        val a = section(t("ACCESSIBILITY", "صلاحية الوصول"))
        val st = body(a, "", 16f, Color.WHITE, true)
        refreshers.add { st.text = if (CapriAccess.instance != null) "\u25CF " + t("ON: she can see and use your phone", "شغّال: تقدر تشوف وتستخدم جوالك") else "\u25CB " + t("OFF", "مطفي") }
        note(a, t("With it she reads your screen, taps, types and scrolls with her own cursor, and takes screenshots.\n\nTo turn it on: 1) Open app info, tap the three dots and choose Allow restricted settings (Android 13+). 2) Open accessibility settings, find Capri under Installed apps and switch it on.\n\nOn POCO or Xiaomi phones also open app info > Other permissions and allow Display pop-up windows while running in the background and Start in the background, and set battery to No restrictions.",
            "بها تقرأ شاشتك وتضغط وتكتب وتسحب بمؤشرها هي وتاخذ لقطات شاشة.\n\nلتشغيلها: ١) افتح «معلومات التطبيق» واضغط النقاط الثلاث واختر «السماح بالإعدادات المقيّدة» (أندرويد ١٣ وما فوق). ٢) افتح إعدادات «إمكانية الوصول»، وابحث عن كابري تحت «التطبيقات المثبّتة» وشغّلها.\n\nإذا كان جوالك POCO أو Xiaomi: من معلومات التطبيق > الأذونات الأخرى فعّل «عرض النوافذ المنبثقة أثناء التشغيل في الخلفية» و«البدء في الخلفية»، وخلّ البطارية «بدون قيود»."))
        btn(a, t("1. OPEN APP INFO", "١. معلومات التطبيق")) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        btn(a, t("2. OPEN ACCESSIBILITY SETTINGS", "٢. إعدادات إمكانية الوصول"), true) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        val tools = section(t("TOOLS SHE MAY USE", "الأدوات المسموحة لها"))
        note(tools, t("Switch off anything you don't want her touching, on top of the Security level.", "أطفئ أي شي ما تبيها تستخدمه، فوق مستوى الأمان."))
        toggle(tools, t("Open apps and links", "فتح التطبيقات والروابط"), "tool_apps", true)
        toggle(tools, t("Look at my screen", "رؤية شاشتي"), "tool_screen", true)
        toggle(tools, t("Tap, type, scroll and jobs", "الضغط والكتابة والسحب والمهام"), "tool_control", true)
        toggle(tools, t("Music, media and volume", "الموسيقى والميديا والصوت"), "tool_media", true)
        toggle(tools, t("Timers, alarms and reminders", "المؤقتات والمنبهات والتذكيرات"), "tool_alerts", true)
        toggle(tools, t("Torch, maps, search and clipboard", "الكشاف والخرائط والبحث والحافظة"), "tool_device", true)
        toggle(tools, t("Weather, Wikipedia and what's playing", "الطقس وويكيبيديا والأغنية الحالية"), "tool_lookup", true)
        btn(tools, t("TEST HER HANDS", "اختبار اليدين"), true) {
            if (CapriAccess.instance == null) toast(t("Turn on accessibility first.", "شغّل صلاحية الوصول أول."))
            else if (!Settings.canDrawOverlays(this)) toast(t("Allow floating over apps first.", "اسمح بالظهور فوق التطبيقات أول."))
            else startForegroundService(Intent(this, CapriService::class.java).setAction(CapriService.ACT_TEST))
        }

        val w = section(t("WATCHING", "المراقبة"))
        toggle(w, t("She watches my screen and comments (uses your key)", "تراقب شاشتي وتعلّق (تستهلك مفتاحك)"), "watch", false)
        slider(w, t("Seconds between glances", "الثواني بين كل نظرة"), "watchSecs", 20, 180, 60)
        toggle(w, t("Send screenshots too, not just screen text (needs a model that sees images)", "أرسل لقطات شاشة وليس النص فقط (يحتاج موديل يفهم الصور)"), "vision", false)
        note(w, t("Game mode (Home tab) makes her comment only while a game is open. Online brains receive what is on your screen when she looks; password fields are never read. The offline model keeps everything on the phone.",
            "وضع اللعب (في الرئيسية) يخليها تعلّق فقط وقت ما تكون لعبة مفتوحة. المزوّدات الأونلاين تستلم اللي على شاشتك لما تشوف، وحقول كلمات المرور ما تنقرأ أبداً. الموديل المحلي يبقي كل شي على جوالك."))

        val n = section(t("NOTIFICATIONS AND MUSIC", "الإشعارات والموسيقى"))
        val ns = body(n, "", 14f, cyan)
        refreshers.add {
            val on = (Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: "").contains(packageName)
            ns.text = t("Notification access: ", "صلاحية الإشعارات: ") + if (on) t("on", "شغّالة") else t("off", "مطفية")
        }
        toggle(n, t("She mentions new messages and calls (who from, not the text)", "تخبرك بالرسائل والمكالمات الجديدة (من مين وليس النص)"), "readNotifs", false)
        toggle(n, t("Include a short bit of the message text", "تضيف جزءاً قصيراً من نص الرسالة"), "readNotifContent", false)
        btn(n, t("OPEN NOTIFICATION ACCESS", "افتح صلاحية الإشعارات")) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }

        val x = section(t("EXTRAS", "إضافات"))
        note(x, t("Select any text in any app and choose \"Ask Capri\" (or use Share). Add the Capri tile to your quick settings to start and stop her. Hold her to talk, tap her to type, tap the menu button for games, dictation and pranks.",
            "حدّد أي نص في أي تطبيق واختر «Ask Capri» (أو من قائمة المشاركة) عشان تشرحه أو تترجمه. أضف مربع Capri للإعدادات السريعة عشان تشغلها وتوقفها. اضغط عليها مطوّلاً للكلام، واضغط عليها للكتابة، وزر القائمة فيه الألعاب والإملاء والمقالب. وامسح على راسها وشوف وش يصير."))
    }

    // ---- MORE ----

    private fun buildMore() {
        val m = section(t("HER NOTES ABOUT YOU", "ملاحظاتها عنك"))
        edit(m, t("Nothing yet. She adds things as you chat.", "ما فيه شي للحين. تضيف أشياء وانت تسولف معها."), prefs.getS("notes", ""), multiline = true) { prefs.putS("notes", it) }

        val p = section(t("HER PERSONALITY", "شخصيتها"))
        val custom = prefs.getS("personality", "")
        val pe = edit(p, "personality", if (custom.isBlank()) Persona.default(this) else custom, multiline = true) {
            prefs.putS("personality", if (it.trim().isEmpty() || it.trim() == Persona.default(this).trim()) "" else it.trim())
        }
        note(p, t("Her safety rules always stay in place after whatever you write here.", "قواعد الأمان تبقى دايماً مهما كتبت هنا."))
        btn(p, t("RESET PERSONALITY", "أعد الشخصية للأصل")) { pe.setText(Persona.default(this)) }

        val mods = section(t("MODS", "المودات"))
        note(mods, t("A mod is a small .json file: {\"name\", \"accent\": \"#RRGGBB\", \"personality\": \"extra lines\", \"nickname\"}. Data only, no code.",
            "المود ملف .json صغير: {\"name\", \"accent\": \"#RRGGBB\", \"personality\": \"سطور إضافية\", \"nickname\"}. بيانات فقط وبدون كود."))
        if (CustomLook.active(this)) note(mods, t("Look in use: ", "الشكل المستخدم: ") + CustomLook.name(this))
        btn(mods, t("IMPORT A MOD OR LOOK (.json / .capri)", "استيراد مود أو شكل (.json / .capri)")) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 7)
        }
        btn(mods, t("REMOVE MY MOD", "حذف المود")) {
            prefs.putS("modAccent", ""); prefs.putS("modPersona", "")
            if (CustomLook.active(this)) { CustomLook.remove(this); toast(t("Look removed. Restart her.", "انحذف الشكل. أعد تشغيلها.")) }
            toast(t("Mod removed.", "انحذف المود."))
            showTab(5)
        }

        val d = section(t("DATA", "البيانات"))
        btn(d, t("COPY MY SETTINGS (no keys)", "انسخ إعداداتي (بدون المفاتيح)")) { clip(prefs.dump(false)); toast(t("Copied.", "تم النسخ.")) }
        btn(d, t("COPY MY SETTINGS WITH KEYS", "انسخ إعداداتي مع المفاتيح")) { clip(prefs.dump(true)); toast(t("Copied. Keep it private.", "تم النسخ. لا تشاركه مع أحد.")) }
        btn(d, t("PASTE SETTINGS FROM CLIPBOARD", "الصق الإعدادات من الحافظة")) {
            try {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                prefs.restore(cm.primaryClip?.getItemAt(0)?.text?.toString() ?: "")
                toast(t("Restored.", "تم الاسترجاع."))
                recreate()
            } catch (e: Exception) { toast(t("The clipboard doesn't hold saved settings.", "الحافظة ما فيها إعدادات محفوظة.")) }
        }
        btn(d, t("CLEAR OUR CHAT HISTORY", "امسح سجل المحادثة")) { prefs.putS("history", "[]"); toast(t("Cleared. Restart her to apply.", "انمسح. أعد تشغيلها عشان يتطبّق.")) }
        btn(d, t("RESET THE BOND TO ZERO", "صفّر العلاقة")) { prefs.putI("charm", 0); refresh() }

        val dg = section(t("SOMETHING WRONG?", "في مشكلة؟"))
        note(dg, t("Copy a setup report (no keys, no chats) and paste it to whoever is helping you.", "انسخ تقرير الإعدادات (بدون مفاتيح وبدون محادثات) والصقه لمن يساعدك."))
        btn(dg, t("COPY SETUP REPORT", "انسخ تقرير الإعدادات"), true) { clip(Diag.report(this, prefs)); toast(t("Copied.", "تم النسخ.")) }

        val lastCrash = CrashLog.last(this)
        if (lastCrash != null) {
            note(dg, t("The last error Capri caught is saved here. Copy it and send it to the developer.", "آخر خطأ سجّله كابري محفوظ هنا. انسخه وارسله للمطوّر."))
            btn(dg, t("COPY LAST ERROR", "انسخ آخر خطأ")) { clip(lastCrash); toast(t("Copied.", "تم النسخ.")) }
            btn(dg, t("SHARE LAST ERROR", "شارك آخر خطأ")) {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, lastCrash), "Capri"))
            }
            btn(dg, t("CLEAR LAST ERROR", "امسح آخر خطأ")) { CrashLog.clear(this); toast(t("Cleared.", "انمسح.")); showTab(tab) }
        }
        btn(dg, t("CHECK FOR UPDATES", "شوف في تحديث")) {
            toast(t("Checking...", "أتحقق..."))
            bg("update", this) {
                val info = Updater.check(this)
                runOnUiThread {
                    if (info == null) toast(t("You have the latest version.", "عندك آخر نسخة."))
                    else android.app.AlertDialog.Builder(this).setTitle(t("New version ", "نسخة جديدة ") + info.version)
                        .setMessage(t("Download it and install over the old one. Your settings stay.", "نزّلها وثبّتها فوق القديمة. إعداداتك تبقى."))
                        .setPositiveButton(t("Download", "تنزيل")) { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apk ?: info.page))) }
                        .setNegativeButton(t("Later", "لاحقاً"), null).show()
                }
            }
        }

        val news = section(t("WHAT'S NEW", "الجديد"))
        note(news, t("\u2022 Fixed the crash on start\n\u2022 She can watch your screen and tell you when something finishes (\"tell me when the download is done\")\n\u2022 She can circle things on your screen, and spawn glitchy clones\n\u2022 Share pictures, PDFs and text files to her\n\u2022 Error reports you can copy, and an update checker\n\u2022 Dances, pats, doomscroll guard, no-AI mode, setup report",
            "\u2022 إصلاح الكراش عند التشغيل\n\u2022 تراقب شاشتك وتقول لك إذا خلص شي (\"قولي إذا خلص التنزيل\")\n\u2022 تأشّر على أشياء في الشاشة، وتسوّي نسخ مشوّشة منها\n\u2022 شارك لها صور وPDF وملفات نصية\n\u2022 تقارير أخطاء تنسخها، ومدقّق تحديثات\n\u2022 رقص، مسح على الراس، حارس التصفّح، وضع بدون ذكاء اصطناعي، تقرير الإعدادات"))

        val c = section(t("CREDITS", "الشكر"))
        val tv = body(c, "Unofficial fan project, free for non-commercial use only. Based on Capri by Lachy: https://github.com/lachydotmcg/capri-desktop " +
            "(PolyForm Noncommercial 1.0.0). Her art, animations and the Mainframe look are from the desktop app. Fonts: Share Tech Mono, Barlow and Tajawal (SIL OFL). " +
            "Not approved or endorsed by CD PROJEKT RED, the anime's rights holders or its voice actors.", 13f, muted)
        Linkify.addLinks(tv, Linkify.WEB_URLS)
        tv.setLinkTextColor(accent)
    }

    private fun clip(s: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Capri settings", s))
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
        page.postDelayed({ refresh() }, 800)
    }

    private fun refresh() { for (r in refreshers) r() }

    override fun onResume() {
        super.onResume()
        refresh()
        val crash = CrashLog.unseen(this)
        if (crash != null) {
            CrashLog.markSeen(this)
            android.app.AlertDialog.Builder(this)
                .setTitle(t("Capri hit a problem last time", "كابري واجه مشكلة المرة الماضية"))
                .setMessage(crash.take(1500))
                .setPositiveButton(t("Copy", "نسخ")) { _, _ -> clip(crash); toast(t("Copied.", "تم النسخ.")) }
                .setNegativeButton(t("Close", "إغلاق"), null).show()
        }
    }

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
            val head = contentResolver.openInputStream(uri)?.use { val b = ByteArray(2); if (it.read(b) == 2) String(b, Charsets.ISO_8859_1) else "" } ?: ""
            if (head == "PK") {
                // a .capri look made in the desktop editor
                val n = CustomLook.install(this, uri)
                toast(t("Look loaded: ", "انحمّل الشكل: ") + n + t(" (restart her to see it)", " (أعد تشغيلها عشان يبان)"))
                if (CapriService.running) { stopService(Intent(this, CapriService::class.java)); page.postDelayed({ startForegroundService(Intent(this, CapriService::class.java)) }, 700) }
                showTab(tab)
                return
            }
            val txt = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
            val j = JSONObject(txt)
            if (j.has("accent")) prefs.putS("modAccent", j.getString("accent"))
            if (j.has("personality")) prefs.putS("modPersona", j.getString("personality").take(2000))
            if (j.has("nickname")) prefs.putS("nick", j.getString("nickname"))
            // Only harmless look-and-feel settings can come from a mod file (never keys or providers).
            val ok = setOf("size", "theme", "quiet", "sounds", "lang")
            j.optJSONObject("settings")?.let { st ->
                for (k in st.keys()) if (k in ok) when (val v = st.get(k)) {
                    is Boolean -> prefs.putB(k, v)
                    is Int -> prefs.putI(k, v)
                    is String -> prefs.putS(k, v.take(200))
                }
            }
            toast(t("Mod loaded: ", "انحمّل المود: ") + j.optString("name", "?"))
            showTab(tab)
        } catch (e: Exception) {
            toast(t("That isn't a valid mod file.", "هذا مو ملف مود صحيح."))
        }
    }
}
