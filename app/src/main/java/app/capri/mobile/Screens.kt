package app.capri.mobile

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

private fun Activity.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

private fun Activity.pill(text: String, accent: Int, click: () -> Unit): TextView {
    val b = TextView(this)
    b.text = text; b.textSize = 13f; b.typeface = Typeface.DEFAULT_BOLD; b.setTextColor(accent)
    b.setPadding(dp(12), dp(8), dp(12), dp(8))
    b.background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Night.deep); setStroke(dp(1), Night.deepEdge) }
    b.setOnClickListener { click() }
    return b
}

private fun Activity.frame(titleText: String, onBack: () -> Unit): Pair<LinearLayout, ScrollView> {
    val root = LinearLayout(this)
    root.orientation = LinearLayout.VERTICAL
    root.setBackgroundColor(Night.ground)
    root.layoutDirection = if (L.ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    root.setPadding(dp(12), dp(28), dp(12), dp(8))
    val bar = TextView(this)
    bar.text = "‹  $titleText >"
    bar.typeface = Typeface.DEFAULT_BOLD; bar.textSize = 16f; bar.setTextColor(Night.amber)
    bar.setPadding(dp(6), 0, 0, dp(8))
    bar.setOnClickListener { onBack() }
    root.addView(bar)
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    val sv = ScrollView(this)
    sv.addView(col)
    return Pair(root, sv).also { root.tag = col }
}

// The guide: what Capri is and how the app is arranged. A table of contents, a search box, and a card per topic with "try it" phrases.
class GuideActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private val open = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        L.init(prefs)
        val (root, sv) = frame(L.t("GUIDE", "الدليل")) { finish() }
        val box = EditText(this)
        box.hint = L.t("Search the guide...", "ابحث بالدليل...")
        box.setTextColor(Night.white); box.setHintTextColor(Night.faint); box.textSize = 15f
        box.setSingleLine(true)
        box.setPadding(dp(12), dp(8), dp(12), dp(8))
        box.background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Night.field); setStroke(dp(1), Night.edge) }
        root.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        list = root.tag as LinearLayout
        setContentView(root)
        intent.getStringExtra("topic")?.let { open.add(it) }
        render("")
        box.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { open.clear(); render(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    private fun render(q: String) {
        list.removeAllViews()
        val topics = Guide.search(q, L.ar)
        val accent = Themes.accent(prefs)
        if (topics.isEmpty()) {
            val n = TextView(this); n.text = L.t("Nothing matches. Try another word.", "ما لقيت شي. جرّب كلمة ثانية."); n.setTextColor(Night.faint); n.setPadding(dp(8), dp(16), dp(8), dp(8))
            list.addView(n); return
        }
        if (q.isBlank()) {
            val intro = TextView(this)
            intro.text = L.t("Tap a topic to open it.", "اضغط على أي موضوع يفتح.")
            intro.setTextColor(Night.faint); intro.textSize = 13f; intro.setPadding(dp(6), 0, dp(6), dp(6))
            list.addView(intro)
        }
        for (t in topics) {
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(dp(12), dp(10), dp(12), dp(10))
            card.background = NeonPanel(resources.displayMetrics.density)
            val isOpen = t.id in open || topics.size == 1 && q.isNotBlank()
            val head = TextView(this)
            head.text = t.icon + "  " + t.title(L.ar) + if (isOpen) "" else "  +"
            head.typeface = Typeface.DEFAULT_BOLD; head.textSize = 16f; head.setTextColor(Night.amber)
            head.setOnClickListener { if (!open.add(t.id)) open.remove(t.id); render(q) }
            card.addView(head)
            if (isOpen) {
                val body = TextView(this)
                body.text = t.body(L.ar)
                body.setTextColor(Night.white); body.textSize = 14f; body.setLineSpacing(0f, 1.15f)
                body.setPadding(0, dp(8), 0, dp(4))
                card.addView(body)
                val tries = t.tries(L.ar)
                if (tries.isNotEmpty()) {
                    val lab = TextView(this); lab.text = L.t("TRY IT", "جرّبها"); lab.setTextColor(Night.cyan); lab.textSize = 12f; lab.typeface = Typeface.DEFAULT_BOLD
                    lab.setPadding(0, dp(6), 0, dp(4)); card.addView(lab)
                    val flow = LinearLayout(this); flow.orientation = LinearLayout.VERTICAL
                    for (p in tries) flow.addView(pill("“$p”", accent) { tryIt(p) }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(6) })
                    card.addView(flow)
                }
            }
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun tryIt(phrase: String) {
        if (!CapriService.running) { Toast.makeText(this, L.t("Wake her up first (HOME tab).", "صحّيها أول (تبويب الرئيسية)."), Toast.LENGTH_SHORT).show(); return }
        try { startForegroundService(Intent(this, CapriService::class.java).setAction(CapriService.ACT_ASK).putExtra("text", phrase)); finish() }
        catch (e: Exception) { Toast.makeText(this, L.t("Couldn't send that to her.", "ما قدرت أرسلها لها."), Toast.LENGTH_SHORT).show() }
    }
}

// One honest page about how safe the app is right now, with a fix for each thing.
class Check(val ok: Boolean, val title: String, val detail: String, val fixLabel: String? = null, val fix: (() -> Unit)? = null)

object Protect {
    /** SHA-256 of the signing certificate that ships in the repo. Anyone can sign an app with it, so a release signed with it is for testing only. */
    const val PUBLIC_KEY_SHA256 = "7FC8B65FEEDC8F48A5346A4FFA0DF8EEA413A0425CF9F70B7C38BBFBCC6DA845"

    fun isPublicKey(shaHex: String) = shaHex.replace(":", "").uppercase() == PUBLIC_KEY_SHA256

    @Suppress("DEPRECATION")
    fun signerSha(ctx: android.content.Context): String? = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
        val sig = pi.signingInfo?.apkContentsSigners?.firstOrNull()
        sig?.let { java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02X".format(b) } }
    } catch (e: Throwable) { null }

    fun checks(a: Activity, prefs: Prefs, refresh: () -> Unit): List<Check> {
        val out = ArrayList<Check>()
        val sha = signerSha(a)
        out.add(if (sha != null && isPublicKey(sha))
            Check(false, L.t("Signing key", "مفتاح التوقيع"), L.t("This app is signed with the public key from the source code. Fine for you, but anyone could build an app that installs over it. Set your own key in GitHub secrets (see README, Signing).", "التطبيق موقّع بالمفتاح العام اللي بالسورس. عادي لك، بس أي أحد يقدر يسوي تطبيق ينثبت فوقه. حط مفتاحك الخاص بأسرار GitHub (README، قسم التوقيع)."))
        else Check(true, L.t("Signing key", "مفتاح التوقيع"), L.t("Signed with your own key.", "موقّع بمفتاحك الخاص.")))

        val lvl = prefs.getI("security", Actions.GUARDED)
        out.add(Check(lvl != Actions.TRUSTED, L.t("Safety level", "مستوى الأمان"),
            when (lvl) { Actions.LOCKDOWN -> L.t("Lockdown: she never touches the phone.", "إغلاق كامل: ما تلمس الجوال أبداً."); Actions.GUARDED -> L.t("Guarded: risky taps are refused unless you asked.", "حذر: ترفض الضغطات الخطرة إلا إذا طلبتها."); Actions.TRUSTED -> L.t("Trusted: she acts without asking. Use Guarded unless you need this.", "موثوق: تسوي بدون ما تسألك. استخدم حذر إلا إذا تحتاج هذا."); else -> L.t("Ask always: she asks before every action.", "اسألني دائماً: تسألك قبل كل عمل.") },
            if (lvl == Actions.TRUSTED) L.t("SET GUARDED", "خلّه حذر") else null, if (lvl == Actions.TRUSTED) ({ prefs.putI("security", Actions.GUARDED); refresh() }) else null))

        val shield = prefs.getB("shield", true)
        out.add(Check(shield, L.t("Privacy shield", "درع الخصوصية"), if (shield) L.t("On: cards, emails, phones and codes are hidden before screen text goes online.", "شغّال: البطاقات والايميلات والأرقام والأكواد تنخفي قبل ما نص الشاشة يروح أونلاين.") else L.t("Off: screen text can contain private details when sent to an online model.", "مطفي: نص الشاشة ممكن فيه بيانات خاصة لما يروح لموديل أونلاين."),
            if (!shield) L.t("TURN ON", "شغّله") else null, if (!shield) ({ prefs.putB("shield", true); refresh() }) else null))

        val docs = Knowledge.docs(a).size
        val enc = Knowledge.encrypted(a)
        out.add(Check(enc, L.t("Her library", "مكتبتها"), if (docs == 0) L.t("Empty. Anything you add is stored encrypted.", "فاضية. أي شي تضيفه ينحفظ مشفر.") else if (enc) L.t("$docs file(s), encrypted with the Android Keystore.", "$docs ملف، مشفرة بـ Android Keystore.") else L.t("Not encrypted yet; it is locked the next time she reads it.", "مو مشفرة للحين؛ تنقفل أول ما تقراها.")))

        val api = prefs.getB("pluginApi", false)
        out.add(Check(!api, L.t("Plugin API", "واجهة الإضافات"), if (api) L.t("On: other apps on this phone can make her talk or answer.", "شغّالة: تطبيقات ثانية بالجوال تقدر تخليها تحچي أو تجاوب.") else L.t("Off.", "مطفية."),
            if (api) L.t("TURN OFF", "طفّيها") else null, if (api) ({ prefs.putB("pluginApi", false); refresh() }) else null))

        val acc = CapriAccess.instance != null
        out.add(Check(true, L.t("Accessibility", "صلاحية الوصول"), if (acc) L.t("On: she can see and use the screen (within the safety level). Password boxes are never read.", "شغّالة: تشوف الشاشة وتتحكم بيها (ضمن مستوى الأمان). خانات كلمات المرور ما تنقرا أبداً.") else L.t("Off: she cannot see or touch other apps.", "مطفية: ما تشوف ولا تلمس التطبيقات الثانية."),
            L.t("OPEN SETTINGS", "افتح الإعدادات"), { try { a.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (e: Exception) { } }))

        val log = ActionLog.text(prefs).lines().count { it.isNotBlank() }
        out.add(Check(true, L.t("Action log", "سجل الأعمال"), L.t("$log action(s) kept. Typed text, addresses' paths, and personal numbers are never stored in it.", "$log عمل محفوظ. النص المكتوب ومسارات العناوين والأرقام الشخصية ما تنحفظ بيه."),
            if (log > 0) L.t("CLEAR", "امسح") else null, if (log > 0) ({ ActionLog.clear(prefs); refresh() }) else null))

        out.add(Check(true, L.t("Network", "الشبكة"), L.t("Keys are encrypted with the Android Keystore. Plain http is only used for this phone or your own network; public addresses need https.", "المفاتيح مشفرة بـ Android Keystore. http العادي بس للجوال أو شبكتك الخاصة؛ العناوين العامة لازم https.")))
        return out
    }
}

class SecurityActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        L.init(prefs)
        val (root, sv) = frame(L.t("SECURITY CENTER", "مركز الأمان")) { finish() }
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        list = root.tag as LinearLayout
        setContentView(root)
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        list.removeAllViews()
        val items = Protect.checks(this, prefs) { render() }
        val bad = items.count { !it.ok }
        val head = TextView(this)
        head.text = if (bad == 0) L.t("✓  All good", "✓  كلشي تمام") else L.t("⚠  $bad thing(s) to look at", "⚠  $bad شي يحتاج انتباه")
        head.textSize = 18f; head.typeface = Typeface.DEFAULT_BOLD; head.setTextColor(if (bad == 0) Night.cyan else Night.amber); head.setPadding(dp(6), dp(4), dp(6), dp(10))
        list.addView(head)
        val accent = Themes.accent(prefs)
        for (c in items) {
            val card = LinearLayout(this); card.orientation = LinearLayout.VERTICAL; card.setPadding(dp(12), dp(10), dp(12), dp(10)); card.background = NeonPanel(resources.displayMetrics.density)
            val t = TextView(this); t.text = (if (c.ok) "✓  " else "⚠  ") + c.title; t.typeface = Typeface.DEFAULT_BOLD; t.textSize = 15f; t.setTextColor(if (c.ok) Night.cyan else Night.amber)
            card.addView(t)
            val d = TextView(this); d.text = c.detail; d.setTextColor(Night.white); d.textSize = 13f; d.setPadding(0, dp(4), 0, 0); card.addView(d)
            if (c.fix != null && c.fixLabel != null) card.addView(pill(c.fixLabel, accent) { c.fix.invoke() }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }
}
