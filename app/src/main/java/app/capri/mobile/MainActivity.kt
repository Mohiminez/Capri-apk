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
    private lateinit var statusDot: TextView
    private lateinit var scroller: ScrollView
    private lateinit var searchBar: EditText
    private var searchIdx: List<SearchHit>? = null
    private var pendingJump: String? = null

    class SearchHit(val tab: Int, val tabName: String, val raw: String, val title: String, val text: String)

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
    private val yellow = Night.amber
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
        window.statusBarColor = Night.glow
        window.navigationBarColor = Night.ground

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
        title.textSize = 28f
        title.letterSpacing = 0.12f
        title.setTextColor(Color.WHITE)
        head.addView(title)
        val pill = TextView(this)
        pill.text = t("Settings", "الإعدادات")
        pill.typeface = tfMono
        pill.textSize = 11f
        pill.setTextColor(Night.cyan)
        pill.setPadding(dp(12), dp(4), dp(12), dp(4))
        pill.background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Night.deep); setStroke(dp(1), Night.deepEdge) }
        head.addView(pill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(12) })
        val ver = TextView(this)
        ver.text = "  v" + Updater.current(this)
        ver.typeface = tfMono
        ver.textSize = 12f
        ver.setTextColor(muted)
        head.addView(ver)
        head.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val magnifier = TextView(this)
        magnifier.text = "\u2315"
        magnifier.textSize = 22f
        magnifier.setTextColor(Night.cyan)
        magnifier.setPadding(dp(10), dp(2), dp(10), dp(2))
        magnifier.setOnClickListener {
            if (searchBar.visibility == View.VISIBLE) { closeSearch() }
            else { searchIdx = null; searchBar.visibility = View.VISIBLE; searchBar.requestFocus(); (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(searchBar, 0) }
        }
        head.addView(magnifier)
        statusDot = TextView(this)
        statusDot.typeface = tfMono
        statusDot.textSize = 12f
        head.addView(statusDot)
        rootCol.addView(head)
        searchBar = EditText(this)
        searchBar.hint = t("Search settings...", "ابحث في الإعدادات...")
        searchBar.typeface = tfMono
        searchBar.textSize = 14f
        searchBar.setTextColor(Color.WHITE)
        searchBar.setHintTextColor(muted)
        searchBar.setSingleLine(true)
        searchBar.setPadding(dp(14), dp(8), dp(14), dp(8))
        searchBar.background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Night.well); setStroke(dp(1), Night.cyan) }
        searchBar.visibility = View.GONE
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: Editable?) { if (!searchMute) showSearch(e?.toString()?.trim() ?: "") }
        })
        rootCol.addView(searchBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(16), dp(2), dp(16), dp(6)) })

        tabBar = LinearLayout(this)
        tabBar.orientation = LinearLayout.HORIZONTAL
        tabBar.setBackgroundColor(Night.band)

        val scroll = ScrollView(this)
        scroller = scroll
        page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(14), dp(8), dp(12), dp(48))
        scroll.addView(page)
        rootCol.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val line = View(this)
        line.setBackgroundColor(Night.rule)
        rootCol.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        rootCol.addView(tabBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        rootCol.setPadding(dp(4), 0, dp(4), dp(3))
        setContentView(neonWrap(this, rootCol))
        showTab(tab)
    }

    private fun showTab(i: Int) {
        tab = i
        prefs.putI("lastTab", i)
        tabBar.removeAllViews()
        val glyphs = listOf("\u2302", "\u2726", "\u266A", "\u263A", "\u2725", "\u2630")
        for ((n, name) in tabNames.withIndex()) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            col.setPadding(dp(2), dp(6), dp(2), dp(8))
            val bar = View(this)
            bar.setBackgroundColor(if (n == i) accent else Color.TRANSPARENT)
            col.addView(bar, LinearLayout.LayoutParams(dp(28), dp(3)).apply { bottomMargin = dp(4) })
            val g = TextView(this)
            g.text = glyphs[n]
            g.textSize = 19f
            g.gravity = Gravity.CENTER
            g.setTextColor(if (n == i) Night.cyan else muted)
            col.addView(g)
            val v = TextView(this)
            v.text = name
            v.typeface = if (n == i) tfBold else tfMono
            v.textSize = 10f
            v.maxLines = 1
            v.gravity = Gravity.CENTER
            v.setTextColor(if (n == i) Color.WHITE else muted)
            col.addView(v)
            col.foreground = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(Color.argb(50, 255, 255, 255)), null, null)
            col.setOnClickListener { if (n != tab) showTab(n) }
            tabBar.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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
        page.alpha = 0f
        page.translationY = dp(10).toFloat()
        page.animate().alpha(1f).translationY(0f).setDuration(180).start()
        val jump = pendingJump
        if (jump != null) {
            pendingJump = null
            page.post {
                for (k in 0 until page.childCount) {
                    val outer = page.getChildAt(k) as? LinearLayout ?: continue
                    val hd = (outer.getChildAt(0) as? LinearLayout)?.getChildAt(0)
                    if (hd?.tag == jump) { scroller.smoothScrollTo(0, (outer.top - dp(8)).coerceAtLeast(0)); break }
                }
            }
        }
    }

    // ---- settings search: builds every tab off-screen once, then matches the words on it ----

    private var searchMute = false

    private fun closeSearch() {
        searchMute = true
        searchBar.setText("")
        searchMute = false
        searchBar.visibility = View.GONE
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(searchBar.windowToken, 0)
        showTab(tab)
    }

    private fun collectText(v: View, sb: StringBuilder) {
        if (v is TextView) { sb.append(v.text).append('\n'); if (v is EditText && !v.hint.isNullOrEmpty()) sb.append(v.hint).append('\n') }
        if (v is ViewGroup) for (i in 0 until v.childCount) collectText(v.getChildAt(i), sb)
    }

    private fun buildIndex(): List<SearchHit> {
        val savedPage = page
        val savedRefreshers = ArrayList(refreshers)
        val out = ArrayList<SearchHit>()
        try {
            for (n in 0..5) {
                val tmp = LinearLayout(this)
                page = tmp
                refreshers.clear()
                when (n) { 0 -> buildHome(); 1 -> buildBrain(); 2 -> buildVoice(); 3 -> buildBehavior(); 4 -> buildHands(); else -> buildMore() }
                for (i in 0 until tmp.childCount) {
                    val outer = tmp.getChildAt(i) as? LinearLayout ?: continue
                    val col = outer.getChildAt(0) as? LinearLayout ?: continue
                    val head = col.getChildAt(0) as? TextView ?: continue
                    val raw = head.tag as? String ?: continue
                    val sb = StringBuilder()
                    collectText(col, sb)
                    out.add(SearchHit(n, tabNames[n], raw, head.text.toString().removeSuffix("  +"), sb.toString()))
                }
            }
        } catch (e: Throwable) {
            Diag.log("search", e.toString())
        } finally {
            page = savedPage
            refreshers.clear(); refreshers.addAll(savedRefreshers)
        }
        return out
    }

    private fun showSearch(q: String) {
        if (q.isEmpty()) { page.removeAllViews(); refreshers.clear(); showTab(tab); return }
        val idx = searchIdx ?: buildIndex().also { searchIdx = it }
        page.removeAllViews()
        refreshers.clear()
        val words = q.lowercase().split(" ").filter { it.isNotEmpty() }
        val hits = idx.filter { h -> val low = (h.title + "\n" + h.text).lowercase(); words.all { low.contains(it) } }.take(30)
        val box = section(t("RESULTS", "النتائج") + " (" + hits.size + ")")
        if (hits.isEmpty()) note(box, t("Nothing matches. Try another word.", "ما لقيت شي. جرّب كلمة ثانية."))
        for (h in hits) {
            val line = h.text.split('\n').firstOrNull { l -> words.any { l.lowercase().contains(it) } && l.isNotBlank() && !l.equals(h.title, true) } ?: h.text.lineSequence().firstOrNull { it.isNotBlank() && !it.equals(h.title, true) } ?: ""
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.setPadding(dp(4), dp(8), dp(4), dp(8))
            val a = TextView(this); a.text = h.tabName + "  \u203A  " + h.title; a.typeface = tfBold; a.textSize = 13f; a.setTextColor(Night.amber)
            row.addView(a)
            val b = TextView(this); b.text = line.take(110); b.typeface = tfBody; b.textSize = 13f; b.setTextColor(muted)
            row.addView(b)
            row.setOnClickListener {
                pendingJump = h.raw
                prefs.putB("fold_" + h.raw, false)
                tab = h.tab
                closeSearch()
            }
            box.addView(row)
        }
    }

    // ---- building blocks ----

    // A panel with the red edge and a yellow "TITLE >" label, like the desktop.
    private fun section(title: String?, open: Boolean = true): LinearLayout {
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.HORIZONTAL
        outer.background = RuleBar(resources.displayMetrics.density)
        val bar = View(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(6), dp(6), dp(8))
        outer.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(10), 0, dp(10))
        page.addView(outer, lp)
        if (title != null) {
            val h = heading(col, "$title >")
            h.tag = title
            val base = h.text.toString()
            val key = "fold_" + title
            fun paint(folded: Boolean) {
                h.text = base + (if (folded) "  +" else "")
                for (k in 1 until col.childCount) col.getChildAt(k).visibility = if (folded) View.GONE else View.VISIBLE
            }
            h.setOnClickListener {
                val now = !prefs.getB(key, !open)
                prefs.putB(key, now)
                paint(now)
            }
            h.setPadding(0, dp(2), 0, dp(8))
            col.post { paint(prefs.getB(key, !open)) }
        }
        return col
    }

    // A grid of big tap targets, two per row.
    private fun tiles(p: LinearLayout, items: List<Triple<String, String, () -> Unit>>) {
        var row: LinearLayout? = null
        for ((i, it) in items.withIndex()) {
            if (i % 2 == 0) {
                row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                p.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            val tile = LinearLayout(this)
            tile.orientation = LinearLayout.VERTICAL
            tile.gravity = Gravity.CENTER
            tile.setPadding(dp(8), dp(14), dp(8), dp(12))
            tile.background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Night.hover); setStroke(dp(1), Night.edge) }
            tile.foreground = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(Color.argb(60, 94, 240, 246)), null, null)
            val g = TextView(this)
            g.text = it.first; g.textSize = 26f; g.gravity = Gravity.CENTER; g.setTextColor(Night.cyan)
            tile.addView(g)
            val l = TextView(this)
            l.text = it.second; l.typeface = tfMono; l.textSize = 12f; l.gravity = Gravity.CENTER; l.setTextColor(Night.white)
            tile.addView(l)
            tile.setOnClickListener { _ -> tile.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); it.third() }
            row!!.addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
    }

    private fun heading(p: LinearLayout, s: String): TextView {
        val v = TextView(this)
        v.text = (if (L.ar) s else s.uppercase())
        v.typeface = tfBold
        v.textSize = 15f
        v.setTextColor(Night.amber)
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

    private fun btn(p: LinearLayout?, s: String, primary: Boolean = false, click: () -> Unit): TextView {
        val v = TextView(this)
        v.text = s
        v.typeface = tfMono
        v.textSize = 13f
        v.gravity = Gravity.CENTER
        v.setTextColor(if (primary) Night.cyan else Night.white)
        v.setPadding(dp(16), dp(12), dp(16), dp(12))
        v.background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(if (primary) Night.deep else Night.field)
            setStroke(dp(if (primary) 2 else 1), if (primary) Night.cyan else Night.deepEdge)
        }
        v.minHeight = dp(48)
        v.foreground = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), null, null)
        v.setOnClickListener { click() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, dp(5), 0, dp(5))
        p?.addView(v, lp)
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
            setColor(Night.well)
            setStroke(dp(1), Night.deepEdge)
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
        sw.trackTintList = ColorStateList.valueOf(Color.argb(120, 94, 240, 246))
        sw.minHeight = dp(48)
        sw.setOnCheckedChangeListener { v, on -> v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); prefs.putB(k, on); extra?.invoke(on) }
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
                names[i].setTextColor(if (i == sel) Night.cyan else text)
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
        darkDialog(AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(titleText).setItems(items.toTypedArray()) { _, i -> onPick(i) }.create())
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun bgWork(work: () -> Unit, fail: (String) -> Unit) {
        Thread {
            try { work() } catch (e: Throwable) { runOnUiThread { fail(e.message ?: "error") } }
        }.start()
    }

    private fun openUrl(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun notifOk() = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun appsVisible() = try { packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).size >= 8 } catch (e: Throwable) { false }

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

        val play = section(t("PLAY AND TRY", "العب وجرّب"))
        tiles(play, listOf(
            Triple("\u265E", t("CHESS", "شطرنج")) { startActivity(Intent(this, ChessActivity::class.java)) },
            Triple("\u25A6", t("GAMES", "ألعاب")) { startActivity(Intent(this, GamesActivity::class.java).putExtra("game", "c4")) },
            Triple("\u266B", t("TEST VOICE", "جرّب الصوت")) { testVoice() },
            Triple("\u270B", t("TEST HANDS", "جرّب اليدين")) {
                if (CapriAccess.instance == null) { toast(t("Turn on accessibility first.", "شغّل صلاحية الوصول أول.")); showTab(4) }
                else if (!Settings.canDrawOverlays(this)) toast(t("Allow floating over apps first.", "اسمح بالظهور فوق التطبيقات أول."))
                else startForegroundService(Intent(this, CapriService::class.java).setAction(CapriService.ACT_TEST))
            }
        ))

        // setup assistant, like the desktop's numbered steps
        val setup = section(t("SETUP", "التجهيز"))
        class Step(val name: () -> String, val ok: () -> Boolean, val fix: () -> Unit, val optional: Boolean = false)
        val steps = listOf(
            Step({ t("Allow floating over apps", "اسمح بالظهور فوق التطبيقات") }, { Settings.canDrawOverlays(this) }, {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }),
            Step({ t("Give her a brain", "أعطها عقل") }, { Brain(this, prefs).anyReady() }, { showTab(1) }),
            Step({ t("Microphone and notifications (reminders need them)", "المايك والإشعارات (التذكيرات تحتاجها)") }, { hasMic() && notifOk() }, { askMic() }),
            Step({ t("She can see your apps (to open them)", "تشوف تطبيقاتك (عشان تفتحها)") }, { appsVisible() },
                { toast(t("Open Permissions and allow \"Installed apps\" if it is there.", "افتح الصلاحيات وفعّل «التطبيقات المثبتة» إذا موجودة.")); startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }, true),
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

        val hands = section(t("TRY HER HANDS", "جرّب يدينها"), false)
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

        val noai = section(t("NO AI MODE", "وضع بدون ذكاء اصطناعي"), false)
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

        val r = section(t("NEVER GO SILENT", "ما تسكت أبداً"), false)
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
        // ---- 1. quick setup: one button gets both offline voices, one button gives her the Capri sound ----
        val q = section(t("QUICK SETUP", "تجهيز سريع"))
        note(q, t("Two voices on your phone, no key, no internet after the download: Kokoro (the same voice as the Windows version) for English, and an Arabic voice. One button gets both (about 190 MB).",
            "صوتين على جوالك، بدون مفتاح وبدون نت بعد التنزيل: Kokoro (نفس صوت نسخة الويندوز) للإنجليزي، وصوت عربي. زر واحد ينزّلهم الثنين (حوالي ١٩٠ ميجا)."))
        val qst = body(q, "", 14f, cyan)
        val qbtn = btn(q, t("GET BOTH OFFLINE VOICES", "نزّل الصوتين الأوفلاين"), true) { }
        fun quickState() {
            val k = Kokoro.ready(this, prefs.getB("kokoroHd", false))
            val a = ArabicTts.ready(this, prefs.getB("arabicHd", false))
            qst.text = (if (k) "\u25CF " else "\u25CB ") + t("English (Kokoro)", "الإنجليزي (Kokoro)") + "     " + (if (a) "\u25CF " else "\u25CB ") + t("Arabic", "العربي")
        }
        qbtn.setOnClickListener {
            qbtn.isEnabled = false
            bg("voices-download", this) {
                var err: String? = null
                if (!Kokoro.ready(this, prefs.getB("kokoroHd", false)))
                    err = Kokoro.download(this, prefs.getB("kokoroHd", false)) { p, name -> runOnUiThread { qst.text = t("English voice... ", "الصوت الإنجليزي... ") + p + "%  (" + name + ")" } }
                if (err == null && !ArabicTts.ready(this, prefs.getB("arabicHd", false)))
                    err = ArabicTts.download(this, prefs.getB("arabicHd", false)) { p, name -> runOnUiThread { qst.text = t("Arabic voice... ", "الصوت العربي... ") + p + "%  (" + name + ")" } }
                runOnUiThread {
                    qbtn.isEnabled = true
                    if (err == null) {
                        prefs.putI("ttsEngine", Voice.KOKORO)
                        if (prefs.getS("kokoroVoice", "").isBlank()) prefs.putS("kokoroVoice", "af_heart")
                        if (prefs.getI("arVoice", 0) == 0 && prefs.getSecret("voiceKey").isEmpty()) prefs.putI("arVoice", 4)
                        toast(t("Done. English uses Kokoro and Arabic uses the offline voice. Press Test.", "تم. الإنجليزي يستخدم Kokoro والعربي الصوت الأوفلاين. اضغط جرّب."))
                        showTab(2)
                    } else { quickState(); qst.text = t("Failed: ", "فشل: ") + err }
                }
            }
        }
        quickState()
        btn(q, t("GIVE HER THE CAPRI SOUND (young, playful, a bit glitchy)", "اعطها صوت كابري (صغيرة، مشاكسة، شوية قلتش)")) {
            prefs.putI("ttsSpeed", 106); prefs.putI("ttsPitch", 100); prefs.putI("arPitch", 130)
            prefs.putI("voiceLayers", 25); prefs.putI("voiceGlitch", 6); prefs.putI("voiceRobot", 0)
            toast(t("Done. Press Test to hear her.", "تم. اضغط جرّب تسمعها."))
            showTab(2)
        }
        toggle(q, t("She talks out loud", "تتكلم بصوت مسموع"), "voice", true)
        toggle(q, t("Start with her voice off every time (privacy)", "تبدأ وصوتها مقفل كل مرة (للخصوصية)"), "startSilent", false)
        btn(q, t("TEST THE VOICE", "جرّب الصوت"), true) { testVoice() }

        // ---- 2. which engine ----
        val o = section(t("VOICE ENGINE", "محرّك الصوت"), false)
        note(o, t("Offline = Kokoro and the Arabic voice above. Cloud = Groq, Gemini, ElevenLabs, OpenAI with your key (further down). The phone voice is the basic fallback.",
            "أوفلاين = Kokoro والصوت العربي فوق. سحابي = Groq وGemini وElevenLabs وOpenAI بمفتاحك (تحت). صوت الجوال هو الاحتياط الأساسي."))
        spin(o, Voice.engines, prefs.getI("ttsEngine", 0)) {
            prefs.putI("ttsEngine", it)
            voiceNameE?.setText(prefs.getS(voiceKeyOf(it), if (it == Voice.KOKORO) "af_heart" else ""))
        }

        // ---- 3. English ----
        val k = section(t("ENGLISH: THE WINDOWS VOICE (KOKORO)", "الإنجليزي: صوت الويندوز (Kokoro)"))
        note(k, t("The same model the Windows version speaks with. English only.", "نفس الموديل اللي تتكلم فيه نسخة الويندوز. إنجليزي فقط."))
        val kst = body(k, "", 14f, cyan)
        val kbtn = btn(k, "", true) { }
        fun kokoroState() {
            val hd = prefs.getB("kokoroHd", false)
            val ok = Kokoro.ready(this, hd)
            kst.text = if (ok) "\u25CF " + t("Downloaded. Voice: ", "منزّل. الصوت: ") + prefs.getS("kokoroVoice", "af_heart") else "\u25CB " + t("Not downloaded yet.", "ما نزّلته للحين.")
            kbtn.text = if (ok) t("DELETE THE KOKORO FILES", "احذف ملفات Kokoro") else t("DOWNLOAD THE KOKORO VOICE", "نزّل صوت Kokoro")
        }
        kbtn.setOnClickListener {
            val hd = prefs.getB("kokoroHd", false)
            if (Kokoro.ready(this, hd)) { Kokoro.delete(this); kokoroState(); return@setOnClickListener }
            kbtn.isEnabled = false
            kst.text = t("Downloading... 0%", "جاري التنزيل... 0%")
            bg("kokoro-download", this) {
                val err = Kokoro.download(this, hd) { p, name -> runOnUiThread { kst.text = t("Downloading... ", "جاري التنزيل... ") + p + "%  (" + name + ")" } }
                runOnUiThread {
                    kbtn.isEnabled = true
                    if (err == null) {
                        prefs.putI("ttsEngine", Voice.KOKORO)
                        if (prefs.getS("kokoroVoice", "").isBlank()) prefs.putS("kokoroVoice", "af_heart")
                        toast(t("Done. Kokoro is now her voice. Press Test the voice.", "تم. Kokoro صار صوتها. اضغط «جرّب الصوت»."))
                        showTab(2)
                    } else { kokoroState(); kst.text = t("Failed: ", "فشل: ") + err }
                }
            }
        }
        toggle(k, t("Full quality model (310 MB instead of 88 MB)", "الموديل الكامل (310 ميجا بدل 88)"), "kokoroHd", false) { kokoroState() }
        toggle(k, t("Full quality model (310 MB instead of 88 MB)", "الموديل الكامل (310 ميجا بدل 88)"), "kokoroHd", false) { kokoroState(); quickState() }
        btn(k, t("PICK A KOKORO VOICE", "اختر صوت Kokoro")) {
            val list = Kokoro.voices(this)
            if (list.isEmpty()) toast(t("Download the Kokoro voice first.", "نزّل صوت Kokoro أول."))
            else pickDialog(t("Pick a voice", "اختر صوتاً"), list) { prefs.putS("kokoroVoice", list[it]); kokoroState(); toast(list[it]) }
        }
        note(k, t("af_* and am_* are American women and men, bf_* and bm_* are British.", "af_* و am_* أمريكي نساء ورجال، وbf_* و bm_* بريطاني."))
        kokoroState()

        // ---- 4. Arabic ----
        val ar = section(t("ARABIC", "العربي"))
        note(ar, t("Kokoro has no Arabic, so Arabic text uses the voice chosen here. The offline one (Meta MMS, about 40 MB) reads Standard Arabic clearly, so dialect words sound like a news reader; the pitch slider makes it younger.",
            "Kokoro ما يدعم العربي، فالنص العربي يستخدم الصوت اللي تختاره هنا. الأوفلاين (Meta MMS، حوالي ٤٠ ميجا) ينطق الفصحى بوضوح، فالعامية تطلع بنطق مذيع؛ وشريط النبرة يخليه أصغر."))
        spin(ar, listOf(t("Automatic (Groq Saudi, else Gemini, else the offline voice, else phone)", "تلقائي (Groq سعودي، وإلا Gemini، وإلا الصوت الأوفلاين، وإلا الجوال)"), t("Phone voice", "صوت الجوال"), t("Gemini voice (free key)", "صوت Gemini (مفتاح مجاني)"), t("Groq Saudi voice (free key)", "صوت Groq السعودي (مفتاح مجاني)"), t("Offline Arabic voice (no key)", "الصوت العربي الأوفلاين (بدون مفتاح)")), prefs.getI("arVoice", 0)) { prefs.putI("arVoice", it) }
        val ast = body(ar, "", 14f, cyan)
        val abtn = btn(ar, "", true) { }
        fun arabicState() {
            val ok = ArabicTts.ready(this, prefs.getB("arabicHd", false))
            ast.text = if (ok) "\u25CF " + t("Downloaded.", "منزّل.") else "\u25CB " + t("Not downloaded yet.", "ما نزّلته للحين.")
            abtn.text = if (ok) t("DELETE THE ARABIC VOICE FILES", "احذف ملفات الصوت العربي") else t("DOWNLOAD THE ARABIC VOICE", "نزّل الصوت العربي")
        }
        abtn.setOnClickListener {
            val hd = prefs.getB("arabicHd", false)
            if (ArabicTts.ready(this, hd)) { ArabicTts.delete(this); arabicState(); return@setOnClickListener }
            abtn.isEnabled = false
            ast.text = t("Downloading... 0%", "جاري التنزيل... 0%")
            bg("arabic-download", this) {
                val err = ArabicTts.download(this, hd) { p, name -> runOnUiThread { ast.text = t("Downloading... ", "جاري التنزيل... ") + p + "%  (" + name + ")" } }
                runOnUiThread {
                    abtn.isEnabled = true
                    if (err == null) {
                        prefs.putI("arVoice", 4)
                        if (prefs.getI("ttsEngine", 0) == Voice.PHONE) prefs.putI("ttsEngine", Voice.ARABIC)
                        toast(t("Done. Arabic now uses the offline voice. Press Test the voice.", "تم. العربي الحين يستخدم الصوت الأوفلاين. اضغط «جرّب الصوت»."))
                        showTab(2)
                    } else { arabicState(); ast.text = t("Failed: ", "فشل: ") + err }
                }
            }
        }
        slider(ar, t("Her Arabic voice pitch (130 = young and playful)", "نبرة صوتها بالعربي (130 = صغيرة ومشاكسة)"), "arPitch", 80, 160, 130)
        toggle(ar, t("Full quality model (114 MB instead of 39 MB)", "الموديل الكامل (114 ميجا بدل 39)"), "arabicHd", false) { arabicState(); quickState() }
        arabicState()

        // ---- 5. effects (same sliders as the desktop) ----
        val fx = section(t("EFFECTS", "المؤثرات"))
        slider(fx, t("Volume %", "مستوى الصوت %"), "ttsVolume", 20, 100, 100)
        slider(fx, t("Speed %", "السرعة %"), "ttsSpeed", 60, 160, 100)
        slider(fx, t("Pitch %", "حدّة الصوت %"), "ttsPitch", 60, 160, 100)
        slider(fx, t("Voice layers % (a higher and a lower copy of her voice, like the desktop)", "طبقات الصوت % (نسخة أعلى ونسخة أوطى من صوتها، مثل الديسكتوب)"), "voiceLayers", 0, 100, 0)
        slider(fx, t("Robot %", "روبوت %"), "voiceRobot", 0, 100, 0)
        slider(fx, t("Glitch %", "قلتش %"), "voiceGlitch", 0, 100, 0)

        // ---- 6. cloud voices ----
        val c = section(t("CLOUD VOICES (OPTIONAL, NEED A KEY)", "أصوات سحابية (اختياري، تحتاج مفتاح)"), false)
        note(c, t("Groq's Orpheus is free and has a Saudi Arabic voice. Gemini is free with your Gemini key. ElevenLabs and OpenAI are paid. Groq asks you to accept the Orpheus terms once at console.groq.com.",
            "Groq Orpheus مجاني وفيه صوت سعودي. Gemini مجاني بمفتاحك. ElevenLabs وOpenAI مدفوعين. Groq يطلب منك الموافقة على شروط Orpheus مرة وحدة من console.groq.com."))
        edit(c, t("Voice key (empty = use the brain key)", "مفتاح الصوت (فاضي = يستخدم مفتاح العقل)"), prefs.getSecret("voiceKey"), password = true) { prefs.putSecret("voiceKey", it) }
        voiceNameE = edit(c, t("Voice (ElevenLabs id, Gemini name, or OpenAI/Groq voice)", "الصوت (معرّف ElevenLabs أو اسم صوت Gemini أو صوت OpenAI/Groq)"),
            prefs.getS(voiceKeyOf(prefs.getI("ttsEngine", 0)), if (prefs.getI("ttsEngine", 0) == Voice.KOKORO) "af_heart" else "")) {
            prefs.putS(voiceKeyOf(prefs.getI("ttsEngine", 0)), it.trim())
        }
        voiceModelE = edit(c, t("Voice model (optional)", "موديل الصوت (اختياري)"), prefs.getS("voiceModel", "")) { prefs.putS("voiceModel", it.trim()) }
        voiceUrlE = edit(c, t("Server URL (Groq / OpenAI / Kokoro only)", "عنوان السيرفر (فقط لـ Groq / OpenAI / Kokoro)"), prefs.getS("voiceUrl", "")) { prefs.putS("voiceUrl", it.trim()) }
        note(c, t("Other options: a Kokoro server on your PC, Groq, OpenAI. Kokoro has no Arabic; for Arabic use Groq (Saudi) or Gemini.",
            "خيارات ثانية: سيرفر Kokoro على الكمبيوتر، أو Groq، أو OpenAI. Kokoro ما يدعم العربي، للعربي استخدم Groq (سعودي) أو Gemini."))
        btn(c, t("PRESETS: GROQ, OPENAI, KOKORO", "إعدادات جاهزة: Groq وOpenAI وKokoro")) {
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
        btn(c, t("PRESETS: GROQ, OPENAI, KOKORO SERVER", "إعدادات جاهزة: Groq وOpenAI وسيرفر Kokoro")) {
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
        btn(c, t("PICK A CLOUD VOICE", "اختر صوتاً سحابياً")) { pickVoice() }

        val e = section(t("HER EARS", "أذنها"))
        note(e, t("The phone's recogniser is free but weaker for Arabic dialects. \"Her own ears\" use Whisper (Groq is free and fast).",
            "تعرّف الجوال على الصوت مجاني بس أضعف مع اللهجات العربية. «أذنها الخاصة» تستخدم Whisper (وGroq مجاني وسريع)."))
        toggle(e, t("Wake word: say \"Capri\" / \"كابري\" and then what you want (uses the microphone and some battery while the screen is on)", "كلمة النداء: قل \"كابري\" ثم طلبك (تستخدم المايك وشوية بطارية والشاشة شغّالة)"), "wakeWord", false)
        note(e, t("Examples: \"Capri, open WhatsApp\", \"hey Capri\" then wait for her \"yes?\". Works best with the phone recogniser.", "أمثلة: \"كابري افتحي واتساب\"، \"اهلا كابري\" وانتظر ترد \"نعم؟\". تشتغل أحسن مع تعرّف الجوال."))
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

    private fun voiceKeyOf(engine: Int) = when (engine) { Voice.ELEVEN -> "voiceId"; Voice.KOKORO -> "kokoroVoice"; else -> "voiceName" }

    private fun pickVoice() {
        when (prefs.getI("ttsEngine", 0)) {
            Voice.KOKORO -> {
                val list = Kokoro.voices(this)
                if (list.isEmpty()) toast(t("Download the Kokoro voice first (button above).", "نزّل صوت Kokoro أول (الزر فوق)."))
                else pickDialog(t("Pick a voice", "اختر صوتاً"), list) { voiceNameE?.setText(list[it]) }
            }
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
        val line = if (L.ar) "هلا يا خوي. هذا صوتي. مو بطّال، صح؟" else "Hi choom. This is my voice. Not bad, right?"
        toast(t("Generating...", "جاري التوليد..."))
        Thread {
            val err = Voice.speak(this, prefs, line) { }
            if (err != null) runOnUiThread {
                toast(if (err == "phone") t("The phone voice is tested from Capri herself, start her and talk.", "صوت الجوال يتجرّب من كابري نفسها: شغّلها وكلّمها.") else err)
            }
        }.start()
    }

    // ---- BEHAVIOR ----

    // ---- who she is ----

    private fun chip(p: LinearLayout, text: String, color: Int) {
        val c = TextView(this)
        c.text = text; c.typeface = tfMono; c.textSize = 10f; c.setTextColor(color)
        c.setPadding(dp(8), dp(3), dp(8), dp(3))
        c.background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color))); setStroke(dp(1), Color.argb(140, Color.red(color), Color.green(color), Color.blue(color))) }
        p.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(3), dp(6), dp(3)) })
    }

    private fun characterCard() {
        val box = section(t("WHO SHE IS", "من هي"))
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(16), dp(14), dp(16), dp(14))
        card.background = CardDrawable(resources.displayMetrics.density, Themes.accent(prefs))
        val nm = TextView(this); nm.text = "CAPRI"; nm.typeface = tfTitle; nm.textSize = 30f; nm.letterSpacing = 0.1f; nm.setTextColor(Themes.accent(prefs))
        card.addView(nm)
        val sub = TextView(this); sub.text = "PROJECT CAPRICORN  \u00B7  CYBERPUNK: EDGERUNNERS 2"; sub.typeface = tfMono; sub.textSize = 10f; sub.setTextColor(muted)
        card.addView(sub)
        val ul = View(this); ul.setBackgroundColor(Themes.accent(prefs))
        card.addView(ul, LinearLayout.LayoutParams(dp(26), dp(3)).apply { topMargin = dp(6); bottomMargin = dp(8) })
        val d = TextView(this)
        d.text = t("A personality construct: dozens of digitized human minds rolled into one. Arasaka built her, King's Crew stole her, Kyoko got her back, and she slipped out. Now she lives on your phone as a small glitchy companion. She teases you, gets bored fast, loves explosions and big plays, hates corpos, and never talks like an assistant.",
            "كيان شخصية: عشرات العقول البشرية الرقمية في عقل واحد. أراساكا بنتها، وكينغز كرو سرقتها، وكيوكو رجّعتها، وهربت. الحين تعيش في جوالك كرفيقة صغيرة فيها قلتش. تمزح معك وتمل بسرعة وتحب الانفجارات واللقطات الكبيرة، وتكره الشركات، وما تتكلم أبداً مثل المساعد.")
        d.typeface = tfBody; d.textSize = 13f; d.setTextColor(Color.WHITE); d.setLineSpacing(0f, 1.15f)
        card.addView(d)
        val chips = LinearLayout(this); chips.orientation = LinearLayout.HORIZONTAL
        val chips2 = LinearLayout(this); chips2.orientation = LinearLayout.HORIZONTAL
        chip(chips, t("PLAYFUL", "مشاكسة"), Night.pink); chip(chips, t("TEASING", "تمزح"), Night.amber); chip(chips, t("LOYAL", "وفية"), Night.cyan)
        chip(chips2, t("GLITCHY", "قلتش"), Night.sCyan); chip(chips2, t("HATES CORPOS", "تكره الشركات"), Night.hot)
        card.addView(chips, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        card.addView(chips2)
        val pts = prefs.getI("charm", 0)
        val bond = TextView(this)
        bond.text = t("BOND  ", "العلاقة  ") + Charm.label(pts) + "  " + Charm.progress(pts) + "%"
        bond.typeface = tfMono; bond.textSize = 11f; bond.setTextColor(Night.amber); bond.setPadding(0, dp(10), 0, 0)
        card.addView(bond)
        box.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, dp(6)) })
        val tone = listOf(t("Normal", "عادي"), t("Calm and gentle", "هادية ولطيفة"), t("Professional", "رسمية"), t("Wild", "مجنونة"))
        body(box, t("How she talks to you", "كيف تكلمك"), 14f, muted)
        spin(box, tone, prefs.getI("tone", 0)) { prefs.putI("tone", it) }
        edit(box, t("What she calls you (nickname)", "وش تناديك (لقب)"), prefs.getS("nick", "")) { prefs.putS("nick", it.trim().take(30)) }
        note(box, t("Her full personality text is under More > Her personality. Mods below can add lines to it.", "نص شخصيتها الكامل تحت: المزيد > شخصيتها. المودات تحت تقدر تضيف عليه."))
    }

    // ---- mods ----

    private fun modCard(box: LinearLayout, m: Mod, isActive: Boolean) {
        val edgeC = try { Color.parseColor(m.accent.ifEmpty { m.colors["hair"].orEmpty().ifEmpty { "#E84246" } }) } catch (e: Exception) { Night.hot }
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(16), dp(14), dp(16), dp(14))
        card.background = CardDrawable(resources.displayMetrics.density, edgeC)
        val head = LinearLayout(this); head.orientation = LinearLayout.HORIZONTAL; head.gravity = Gravity.CENTER_VERTICAL
        val nm = TextView(this); nm.text = m.title(L.ar).let { if (L.ar) it else it.uppercase() }; nm.typeface = if (L.ar) tfBold else tfTitle; nm.textSize = 22f; nm.setTextColor(edgeC)
        head.addView(nm, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (isActive) { val on = TextView(this); on.text = "\u25CF " + t("ACTIVE", "مفعّل"); on.typeface = tfMono; on.textSize = 11f; on.setTextColor(Night.cyan); head.addView(on) }
        card.addView(head)
        val ul = View(this); ul.setBackgroundColor(edgeC)
        card.addView(ul, LinearLayout.LayoutParams(dp(26), dp(3)).apply { topMargin = dp(4); bottomMargin = dp(6) })
        val who = TextView(this)
        who.text = (if (m.builtin) t("BUILT IN", "جاهز") else t("BY ", "من ") + m.author.ifEmpty { "?" }) + "  \u00B7  " + t("SETTINGS MOD", "مود إعدادات")
        who.typeface = tfMono; who.textSize = 10f; who.setTextColor(muted)
        card.addView(who)
        if (m.about(L.ar).isNotEmpty()) {
            val d = TextView(this); d.text = m.about(L.ar); d.typeface = tfBody; d.textSize = 13f; d.setTextColor(Color.WHITE); d.setPadding(0, dp(6), 0, dp(4))
            card.addView(d)
        }
        if (m.colors.isNotEmpty() || m.accent.isNotEmpty()) {
            val sw = LinearLayout(this); sw.orientation = LinearLayout.HORIZONTAL; sw.setPadding(0, dp(4), 0, dp(4))
            for (hx in (listOf(m.accent) + Mods.colorSlots.map { m.colors[it].orEmpty() }).distinct().filter { it.isNotEmpty() }) {
                val v = View(this); v.setBackgroundColor(try { Color.parseColor(hx) } catch (e: Exception) { Color.GRAY })
                sw.addView(v, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(6) })
            }
            card.addView(sw)
        }
        val tags = LinearLayout(this); tags.orientation = LinearLayout.HORIZONTAL
        if (m.colors.isNotEmpty() || m.accent.isNotEmpty()) chip(tags, t("COLORS", "ألوان"), Night.pink)
        if (m.persona.isNotEmpty() || m.nickname.isNotEmpty() || m.tone != null) chip(tags, t("PERSONALITY", "شخصية"), Night.amber)
        if (m.voice.isNotEmpty()) chip(tags, t("VOICE", "صوت"), Night.sCyan)
        if (m.settings.isNotEmpty()) chip(tags, t("SETTINGS", "إعدادات"), Night.dim)
        card.addView(tags)
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        row.addView(btn(null, if (isActive) t("TURN OFF", "إيقاف") else t("TURN ON", "تفعيل"), !isActive) {
            if (isActive) Mods.disable(prefs) else Mods.enable(prefs, m)
            toast(if (isActive) t("Mod off. Your old settings are back.", "انطفى المود. رجعت إعداداتك.") else t("Mod on: ", "اشتغل المود: ") + m.title(L.ar) + t(" (colours show next time she starts)", " (الألوان تبان عند التشغيل الجاي)"))
            showTab(tab)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!m.builtin) row.addView(btn(null, t("DELETE", "حذف")) {
            if (isActive) Mods.disable(prefs)
            Mods.delete(this, m.id); showTab(tab)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        box.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(6), 0, dp(6)) })
    }

    private fun modsGallery() {
        val box = section(t("MODS", "المودات"))
        note(box, t("A mod changes how she looks, sounds and talks, and turning it off puts your old settings back. Mods are data only: they can never carry code, keys or permissions. One mod is on at a time.",
            "المود يغيّر شكلها وصوتها وطريقة كلامها، وإذا طفّيته ترجع إعداداتك القديمة. المودات بيانات فقط: ما تحمل كود ولا مفاتيح ولا صلاحيات. مود واحد يشتغل بنفس الوقت."))
        val act = Mods.active(prefs)
        if (act.isNotEmpty()) note(box, t("Mod on: ", "المود المفعّل: ") + (Mods.all(this).firstOrNull { it.id == act }?.title(L.ar) ?: act))
        for (m in Mods.all(this)) modCard(box, m, m.id == act)
        btn(box, t("IMPORT A MOD OR LOOK (.json / .capri)", "استيراد مود أو شكل (.json / .capri)"), true) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 7)
        }
        btn(box, t("COPY A MOD TEMPLATE TO MAKE MY OWN", "انسخ قالب مود أسوي مودي")) { clip(Mods.template()); toast(t("Copied. Edit it, save it as a .json file, then import it.", "تم النسخ. عدّله واحفظه ملف .json وبعدين استورده.")) }
        if (CustomLook.active(this)) {
            note(box, t("Custom look in use: ", "شكل مخصص مستخدم: ") + CustomLook.name(this))
            btn(box, t("REMOVE THE CUSTOM LOOK", "احذف الشكل المخصص")) { CustomLook.remove(this); toast(t("Look removed. Restart her.", "انحذف الشكل. أعد تشغيلها.")); showTab(tab) }
        }
    }

    private fun buildBehavior() {
        characterCard()
        modsGallery()
        val look = section(t("HER LOOK", "شكلها"))
        slider(look, t("Size (applies next start)", "الحجم (يتطبّق عند التشغيل الجاي)"), "size", 80, 220, 140)
        body(look, t("Theme", "الثيم"), 14f, muted)
        spin(look, Themes.names, prefs.getI("theme", 0)) { prefs.putI("theme", it); prefs.putS("modAccent", ""); showTab(3) }
        toggle(look, t("She dances when music is playing", "ترقص لما تشتغل موسيقى"), "dance", true)
        toggle(look, t("Mini music player (needs notification access)", "مشغّل موسيقى صغير (يحتاج صلاحية الإشعارات)"), "miniPlayer", true)
        toggle(look, t("Glitch-in intro when she wakes up", "مقدمة قلتش لما تصحى"), "intro", true)
        toggle(look, t("A small blip sound when she speaks", "صوت تنبيه صغير لما تتكلم"), "chimes", false)
        toggle(look, t("Low-power mode: fewer frames, no floating, no glow", "وضع توفير الطاقة: إطارات أقل وبدون طفو وبدون هالة"), "lowPower", false)

        val cols = section(t("HER COLORS", "ألوانها"), false)
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

        val tone = section(t("HER TONE", "أسلوبها"), false)
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
        refreshers.add {
            st.text = when {
                CapriAccess.instance != null -> "\u25CF " + t("ON: she can see and use your phone", "شغّال: تقدر تشوف وتستخدم جوالك")
                CapriAccess.enabledInSettings(this) -> "\u25D0 " + t("Switched on but not connected. Press RECONNECT below.", "مشغّلة بس مو متصلة. اضغط إعادة الاتصال تحت.")
                else -> "\u25CB " + t("OFF", "مطفي")
            }
        }
        btn(a, t("RECONNECT HER HANDS", "إعادة اتصال اليدين")) {
            if (CapriAccess.instance != null) toast(t("Already connected.", "متصلة أصلاً."))
            else if (!CapriAccess.enabledInSettings(this)) toast(t("Switch her on in accessibility settings first.", "شغّلها من إعدادات إمكانية الوصول أول."))
            else {
                CapriAccess.rebind(this)
                toast(t("Reconnecting...", "أعيد الاتصال..."))
                page.postDelayed({ refresh() }, 2500)
            }
        }
        note(a, t("With it she reads your screen, taps, types and scrolls with her own cursor, and takes screenshots.\n\nTo turn it on: 1) Open app info, tap the three dots and choose Allow restricted settings (Android 13+). 2) Open accessibility settings, find Capri under Installed apps and switch it on.\n\nOn POCO or Xiaomi phones also open app info > Other permissions and allow Display pop-up windows while running in the background and Start in the background, and set battery to No restrictions.",
            "بها تقرأ شاشتك وتضغط وتكتب وتسحب بمؤشرها هي وتاخذ لقطات شاشة.\n\nلتشغيلها: ١) افتح «معلومات التطبيق» واضغط النقاط الثلاث واختر «السماح بالإعدادات المقيّدة» (أندرويد ١٣ وما فوق). ٢) افتح إعدادات «إمكانية الوصول»، وابحث عن كابري تحت «التطبيقات المثبّتة» وشغّلها.\n\nإذا كان جوالك POCO أو Xiaomi: من معلومات التطبيق > الأذونات الأخرى فعّل «عرض النوافذ المنبثقة أثناء التشغيل في الخلفية» و«البدء في الخلفية»، وخلّ البطارية «بدون قيود»."))
        btn(a, t("1. OPEN APP INFO", "١. معلومات التطبيق")) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        btn(a, t("2. OPEN ACCESSIBILITY SETTINGS", "٢. إعدادات إمكانية الوصول"), true) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        val tools = section(t("TOOLS SHE MAY USE", "الأدوات المسموحة لها"), false)
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

        val x = section(t("EXTRAS", "إضافات"), false)
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

        val d = section(t("DATA", "البيانات"), false)
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

        val dg = section(t("SOMETHING WRONG?", "في مشكلة؟"), false)
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
                    else android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(t("New version ", "نسخة جديدة ") + info.version)
                        .setMessage(t("Download it and install over the old one. Your settings stay.", "نزّلها وثبّتها فوق القديمة. إعداداتك تبقى."))
                        .setPositiveButton(t("Download", "تنزيل")) { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apk ?: info.page))) }
                        .setNegativeButton(t("Later", "لاحقاً"), null).show()
                }
            }
        }

        val news = section(t("WHAT'S NEW", "الجديد"), false)
        note(news, t("\u2022 Fixed the crash on start\n\u2022 She can watch your screen and tell you when something finishes (\"tell me when the download is done\")\n\u2022 She can circle things on your screen, and spawn glitchy clones\n\u2022 Share pictures, PDFs and text files to her\n\u2022 Error reports you can copy, and an update checker\n\u2022 Dances, pats, doomscroll guard, no-AI mode, setup report",
            "\u2022 إصلاح الكراش عند التشغيل\n\u2022 تراقب شاشتك وتقول لك إذا خلص شي (\"قولي إذا خلص التنزيل\")\n\u2022 تأشّر على أشياء في الشاشة، وتسوّي نسخ مشوّشة منها\n\u2022 شارك لها صور وPDF وملفات نصية\n\u2022 تقارير أخطاء تنسخها، ومدقّق تحديثات\n\u2022 رقص، مسح على الراس، حارس التصفّح، وضع بدون ذكاء اصطناعي، تقرير الإعدادات"))

        val c = section(t("CREDITS", "الشكر"), false)
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

    private fun refresh() {
        for (r in refreshers) r()
        val on = CapriService.running
        statusDot.text = if (on) "\u25CF " + t("AWAKE", "صاحية") else "\u25CB " + t("ASLEEP", "نايمة")
        statusDot.setTextColor(if (on) Night.cyan else muted)
    }

    private var autoRebindTried = false

    override fun onResume() {
        super.onResume()
        refresh()
        // switched on in settings but never connected (after an update, or a stopped service): reconnect by itself
        if (!autoRebindTried && CapriAccess.instance == null && CapriAccess.enabledInSettings(this)) {
            autoRebindTried = true   // once per launch, and only after giving Android a few seconds to connect it on its own
            page.postDelayed({ if (CapriAccess.instance == null) { CapriAccess.rebind(this); page.postDelayed({ refresh() }, 2500) } }, 6000)
        }
        val crash = CrashLog.unseen(this)
        if (crash != null) {
            CrashLog.markSeen(this)
            android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
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
            val txt = contentResolver.openInputStream(uri)?.bufferedReader()?.use { val buf = CharArray(20_001); val n = it.read(buf); String(buf, 0, n.coerceAtLeast(0)) } ?: return
            val mod = Mods.parse(txt)
            if (mod == null || !Mods.save(this, mod, txt)) { toast(t("That isn't a valid mod file.", "هذا مو ملف مود صحيح.")); return }
            toast(t("Mod added: ", "انضاف المود: ") + mod.name + t(". Turn it on in the list.", ". فعّله من القائمة."))
            showTab(tab)
            showTab(tab)
        } catch (e: Exception) {
            toast(t("That isn't a valid mod file.", "هذا مو ملف مود صحيح."))
        }
    }
}
