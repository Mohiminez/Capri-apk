package app.capri.mobile

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.view.KeyEvent

// Capri ends a reply with tags like [[open:Spotify]] when they ask for something on the phone. This runs them and strips them.
object Actions {
    interface Hooks {
        fun startJob(goal: String)
        fun look(question: String)
        fun tool(name: String, arg: String)
        fun confirm(text: String, yes: () -> Unit)
        fun chaos()
        fun dictate()
        fun hands(spec: String)
        fun emote(name: String)
        fun setting(key: String, value: String)
        fun watchAlong(on: Boolean)
        fun keepWatch(goal: String)
        fun point(label: String)
        fun clones(count: Int)
        fun chess()
    }

    const val LOCKDOWN = 0
    const val GUARDED = 1
    const val TRUSTED = 2
    const val ASK_ALWAYS = 3

    private val tag = Regex("\\[\\[(\\w+):([^\\]]*)\\]\\]")
    // Lookups that only read public info: allowed even in Lockdown.
    private val readOnly = setOf("weather", "wiki", "playing", "remember")
    // In Guarded these ask first, by showing a bubble you tap to allow.
    private val asksFirst = setOf("do", "look", "url", "dictate", "scrollforme", "watch")
    // In "ask every time" mode even your own direct orders ask first.
    private val controls = setOf("hands", "do", "look", "url", "open", "dictate", "scrollforme", "chaos", "watch", "point")

    fun strip(s: String): String = tag.replace(s, "").replace(Regex("\\s+"), " ").trim()

    // userInitiated = the words came straight from the user (a quick ask), so Guarded mode doesn't ask again.
    fun run(ctx: Context, prefs: Prefs, reply: String, hooks: Hooks?, userInitiated: Boolean = false): String {
        var extra = ""
        val level = prefs.getI("security", GUARDED)
        for (m in tag.findAll(reply)) {
            val name = m.groupValues[1].lowercase()
            val arg = m.groupValues[2].trim()
            if (level == LOCKDOWN && name !in readOnly) {
                extra = L.t(" (Lockdown is on, so I can't touch your phone.)", " (وضع الإغلاق الكامل شغّال، فما أقدر ألمس جوالك.)")
                continue
            }
            if (hooks != null && ((level == ASK_ALWAYS && name in controls) ||
                    (level == GUARDED && (name in asksFirst || ((name == "hands" || name == "point") && !userInitiated))))) {
                hooks.confirm(describe(name, arg)) { execute(ctx, prefs, name, arg, hooks) }
                continue
            }
            extra += execute(ctx, prefs, name, arg, hooks)
        }
        return strip(reply) + extra
    }

    private fun describe(name: String, arg: String): String = when (name) {
        "do" -> L.t("I'll do this on your phone: $arg", "بسوّي هذا على جوالك: $arg")
        "hands" -> L.t("Let me use your phone: $arg", "خلني أستخدم جوالك: $arg")
        "scrollforme" -> L.t("Want me to scroll your videos and react?", "تبيني أتصفّح لك الفيديوهات وأعلّق عليها؟")
        "open" -> L.t("Open $arg?", "أفتح $arg؟")
        "look" -> L.t("Can I look at your screen?", "أقدر أشوف شاشتك؟")
        "watch" -> L.t("Can I keep an eye on your screen until: $arg?", "أقدر أراقب شاشتك لين: $arg؟")
        "point" -> L.t("Let me look for \"$arg\" on your screen.", "خلني أدور على \"$arg\" في شاشتك.")
        "url" -> L.t("Open $arg?", "أفتح $arg؟")
        else -> L.t("Type what you say next into the box you have open?", "أكتب اللي تقوله بعد شوي في الخانة المفتوحة؟")
    }

    // Tools you can switch off one by one (Hands tab), like the desktop's explicit permissions.
    private val groups = mapOf(
        "open" to "apps", "url" to "apps", "look" to "screen",
        "hands" to "control", "do" to "control", "watch" to "screen", "point" to "screen", "scrollforme" to "control", "dictate" to "control",
        "music" to "media", "media" to "media", "volume" to "media",
        "timer" to "alerts", "alarm" to "alerts", "remind" to "alerts", "focus" to "alerts",
        "flash" to "device", "nav" to "device", "search" to "device", "copy" to "device",
        "weather" to "lookup", "wiki" to "lookup", "playing" to "lookup")

    // Returns extra text to add to her reply when something went wrong.
    private fun execute(ctx: Context, prefs: Prefs, name: String, arg: String, hooks: Hooks?): String {
        val group = groups[name]
        if (group != null && !prefs.getB("tool_$group", true)) return L.t(" (That tool is switched off in my settings.)", " (هذي الأداة مطفية من إعداداتي.)")
        try {
            when (name) {
                "open" -> if (!openApp(ctx, arg)) return L.t(" Couldn't find that app.", " ما لقيت هذا التطبيق.")
                "url" -> if (arg.startsWith("http://") || arg.startsWith("https://")) start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(arg)))
                "music" -> playMusic(ctx, arg)
                "media" -> media(ctx, arg)
                "timer" -> {
                    // our own alarm (always rings), instead of asking the clock app, which many phones ignore
                    val parts = arg.split("|", limit = 2)
                    val secs = parts[0].trim().toIntOrNull()?.coerceIn(1, 86400) ?: 0
                    val label = parts.getOrElse(1) { "" }.trim().takeIf { it.isNotEmpty() && it != "Capri" }
                    if (secs > 0) Reminders.add(ctx, "+$secs|" + L.t("Timer done", "انتهى المؤقت") + (label?.let { ": $it" } ?: ""))
                }
                "alarm" -> {
                    val parts = arg.split("|", limit = 2)
                    val hm = parts[0].trim().split(":")
                    start(ctx, Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, hm[0].trim().toInt())
                        .putExtra(AlarmClock.EXTRA_MINUTES, hm.getOrElse(1) { "0" }.trim().toInt())
                        .putExtra(AlarmClock.EXTRA_MESSAGE, parts.getOrElse(1) { "Capri" }.trim())
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                }
                "remind" -> if (!Reminders.add(ctx, arg)) return L.t(" I couldn't read that time.", " ما فهمت الوقت.")
                "remember" -> prefs.addNote(arg)
                "do" -> hooks?.startJob(arg)
                "look" -> hooks?.look(arg)
                "weather", "wiki", "playing" -> hooks?.tool(name, arg)
                "flash" -> Tools.flashlight(ctx, !arg.lowercase().startsWith("off"))
                "volume" -> Tools.volume(ctx, arg)
                "nav" -> Tools.navigate(ctx, arg)
                "search" -> Tools.webSearch(ctx, arg)
                "copy" -> Tools.copy(ctx, arg)
                "chaos" -> hooks?.chaos()
                "dictate" -> hooks?.dictate()
                "hands" -> hooks?.hands(arg)
                "emote" -> hooks?.emote(arg)
                "focus" -> hooks?.setting("focus", arg)
                "set" -> hooks?.setting(arg.substringBefore('|'), arg.substringAfter('|', ""))
                "scrollforme" -> hooks?.watchAlong(!arg.lowercase().startsWith("off"))
                "watch" -> hooks?.keepWatch(arg)
                "point" -> hooks?.point(arg)
                "chess" -> hooks?.chess()
                "clones" -> hooks?.clones(arg.trim().toIntOrNull() ?: if (arg.trim().lowercase() in setOf("off", "clear", "stop")) 0 else 4)
            }
        } catch (e: Exception) {
            return L.t(" That didn't work on this phone.", " ما زبطت على هذا الجوال.")
        }
        return ""
    }

    // Launches go through the accessibility service when it's on: phones like POCO and Xiaomi block other apps from opening things in the background.
    private fun start(ctx: Context, i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        (CapriAccess.instance ?: ctx).startActivity(i)
    }

    private val aliases = mapOf(
        "واتساب" to "whatsapp", "واتس اب" to "whatsapp", "واتس" to "whatsapp", "تيليجرام" to "telegram", "تلقرام" to "telegram", "تليجرام" to "telegram",
        "يوتيوب" to "youtube", "كروم" to "chrome", "ديسكورد" to "discord", "انستغرام" to "instagram", "انستقرام" to "instagram", "انستا" to "instagram",
        "تويتر" to "twitter", "اكس" to "x", "سناب" to "snapchat", "سناب شات" to "snapchat", "تيك توك" to "tiktok", "كاميرا" to "camera", "الكاميرا" to "camera",
        "الاعدادات" to "settings", "الإعدادات" to "settings", "اعدادات" to "settings", "الحاسبة" to "calculator", "اله حاسبة" to "calculator", "الساعة" to "clock",
        "خرائط" to "maps", "الخرائط" to "maps", "جيميل" to "gmail", "سبوتيفاي" to "spotify", "نتفلكس" to "netflix", "نتفليكس" to "netflix", "ماسنجر" to "messenger",
        "فيسبوك" to "facebook", "الهاتف" to "phone", "الرسائل" to "messages", "المعرض" to "gallery", "الصور" to "photos", "الملاحظات" to "notes",
        "المتصفح" to "browser", "بلاي ستور" to "play store", "ستيم" to "steam", "ببجي" to "pubg", "فورتنايت" to "fortnite", "بنترست" to "pinterest", "ريديت" to "reddit"
    )

    // Finds an installed app by its name in English or Arabic, or by part of its package name. Returns (label, package).
    fun findApp(ctx: Context, name: String): Pair<String, String>? {
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        var q = name.lowercase().trim().removePrefix("تطبيق ").removePrefix("application ").removePrefix("app ").trim()
        q = aliases[q] ?: aliases[q.removePrefix("ال")] ?: q
        if (q.isEmpty()) return null
        fun label(i: Int) = apps[i].loadLabel(pm).toString().lowercase()
        fun pkg(i: Int) = apps[i].activityInfo.packageName.lowercase()
        val idx = apps.indices.firstOrNull { label(it) == q }
            ?: apps.indices.firstOrNull { label(it).startsWith(q) }
            ?: apps.indices.firstOrNull { label(it).contains(q) }
            ?: apps.indices.firstOrNull { pkg(it).contains(q.replace(" ", "")) }
            ?: return null
        return Pair(apps[idx].loadLabel(pm).toString(), apps[idx].activityInfo.packageName)
    }

    fun openApp(ctx: Context, name: String): Boolean {
        val found = findApp(ctx, name) ?: return false
        val launch = ctx.packageManager.getLaunchIntentForPackage(found.second) ?: return false
        start(ctx, launch)
        return true
    }

    private fun playMusic(ctx: Context, query: String) {
        try {
            start(ctx, Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                .putExtra(SearchManager.QUERY, query))
        } catch (e: Exception) {
            start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))))
        }
    }

    private fun media(ctx: Context, what: String) {
        val code = when (what.lowercase()) {
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}

// Plain asks that happen straight away, even with a small offline brain: "open discord", "pause", "next", "play <song>", "timer for 5 minutes", "check my screen".
object QuickAsks {
    private val enOpenType = Regex("^(?:hey capri[, ]*)?(?:please )?(?:open|launch|start)\\s+(.+?)\\s+(?:and|then|&)\\s+(?:type|write|put|enter|text)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val enType = Regex("^(?:hey capri[, ]*)?(?:please )?(?:type|enter)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val enWrite = Regex("^(?:please )?write\\s+(?!me\\b|us\\b|a\\b|an\\b|the\\b|some\\b|something\\b|about\\b|my\\b|our\\b)(.+)$", RegexOption.IGNORE_CASE)
    private val arOpenType = Regex("^(?:يا كابري[، ]*)?(?:افتح|فتح|شغل)\\s+(.+?)\\s+(?:ثم\\s+|وبعدين\\s+|بعدين\\s+)?و?(?:اكتب لي|اكتبلي|اكتبي|اكتب)\\s+(.+)$")
    private val arType = Regex("^(?:اكتب لي|اكتبلي|اكتبي|اكتب)\\s+(.+)$")

    private fun clean(s: String) = s.trim().replace("]", ")").replace("|", "/")

    fun match(raw: String): String? {
        val r = raw.trim()
        ReminderAsk.match(r)?.let { return it }
        enOpenType.find(r)?.let { return "On it. [[hands:openType|${clean(it.groupValues[1])}|${clean(it.groupValues[2])}]]" }
        arOpenType.find(r)?.let { return "ابشر. [[hands:openType|${clean(it.groupValues[1])}|${clean(it.groupValues[2])}]]" }
        enType.find(r)?.let { return "Typing. [[hands:type|${clean(it.groupValues[1])}]]" }
        enWrite.find(r)?.let { return "Typing. [[hands:type|${clean(it.groupValues[1])}]]" }
        arType.find(r)?.let { return "تمام. [[hands:type|${clean(it.groupValues[1])}]]" }
        return matchSimple(raw)
    }

    private fun matchSimple(raw: String): String? {
        var t = raw.lowercase().replace("\u2019", "'").replace("'", " ").replace(Regex("[^a-z0-9 \u0600-\u06FF]"), " ").replace(Regex("\\s+"), " ").trim()
        t = t.replace(Regex("^(hey |hi |ok |okay )?(capri )?(please |can you |could you )*"), "").replace(Regex(" please$"), "").trim()
        if (t.isEmpty()) return null
        Regex("^(?:tap|click|press|touch) (?:on )?(.+)$").find(t)?.let { return "[[hands:tap|${it.groupValues[1]}]]" }
        Regex("^(?:اضغط|دوس|كبس|اكبس) (?:على )?(.+)$").find(t)?.let { return "[[hands:tap|${it.groupValues[1]}]]" }
        if (Regex("^(scroll down|swipe up|انزل|نزل|اسحب لتحت|سكرول لتحت)$").matches(t)) return "[[hands:scroll|down]]"
        if (Regex("^(scroll up|swipe down|اطلع|اسحب لفوق|سكرول لفوق)$").matches(t)) return "[[hands:scroll|up]]"
        if (Regex("^(go back|back|ارجع|رجوع|ارجعي)$").matches(t)) return "[[hands:back]]"
        if (Regex("^(go home|home|home screen|الرئيسية|روح للرئيسية|الصفحة الرئيسية)$").matches(t)) return "[[hands:home]]"
        if (Regex("^(recent apps|recents|التطبيقات الاخيرة|التطبيقات الأخيرة)$").matches(t)) return "[[hands:recents]]"
        if (Regex("^(take a screenshot|screenshot|take screenshot|لقطة شاشة|خذ لقطة شاشة|صور الشاشة)$").matches(t)) return "[[hands:shot]]"
        if (Regex("^(lock the screen|lock screen|lock the phone|lock|اقفل الشاشة|قفل الشاشة|اقفل الجوال)$").matches(t)) return "[[hands:lock]]"
        if (Regex("^(open )?(the )?notifications$|^(افتح )?الاشعارات$|^(افتح )?الإشعارات$").matches(t)) return "[[hands:notifs]]"
        if (Regex("^(open )?(the )?quick settings$|^الاعدادات السريعة$").matches(t)) return "[[hands:settings]]"
        Regex("^(افتح|شغل تطبيق|فتح) (.+)$").find(t)?.let { return "تمام. [[open:${it.groupValues[2]}]]" }
        if (Regex("^(ايقاف مؤقت|وقف الموسيقى|وقف الاغنية|وقف الأغنية|بوز)$").matches(t)) return "تمام. [[media:pause]]"
        if (Regex("^(كمل|كمّل|شغل)$").matches(t)) return "تمام. [[media:play]]"
        if (Regex("^(التالي|الاغنية الجاية|الأغنية الجاية|غير الاغنية|غير الأغنية)$").matches(t)) return "تمام. [[media:next]]"
        if (Regex("^(السابق|الاغنية اللي قبل|الأغنية اللي قبل)$").matches(t)) return "تمام. [[media:previous]]"
        Regex("^شغل (.+)$").find(t)?.let { return "ابشر. [[music:${it.groupValues[1]}]]" }
        if (Regex("^(شوف شاشتي|شوف الشاشة|وش على شاشتي|وش في الشاشة)$").matches(t)) return "لحظة. [[look:What is on their screen right now?]]"
        Regex("^open (.+)$").find(t)?.let { return "Opening ${it.groupValues[1]}. [[open:${it.groupValues[1]}]]" }
        if (Regex("^(pause|pause music|pause the music|stop the music)$").matches(t)) return "Paused. [[media:pause]]"
        if (Regex("^(resume|play|unpause)$").matches(t)) return "Playing. [[media:play]]"
        if (Regex("^(next|skip|next song|skip song|skip this|next track)$").matches(t)) return "Skipping. [[media:next]]"
        if (Regex("^(previous|prev|previous song|last song|previous track)$").matches(t)) return "Going back. [[media:previous]]"
        Regex("^play (.+)$").find(t)?.let { return "On it. [[music:${it.groupValues[1]}]]" }
        Regex("^(?:set )?(?:a )?timer (?:for )?(\\d+) ?(seconds?|secs?|minutes?|mins?|hours?)$").find(t)?.let {
            val n = it.groupValues[1].toInt()
            val unit = it.groupValues[2]
            val secs = when {
                unit.startsWith("h") -> n * 3600
                unit.startsWith("m") -> n * 60
                else -> n
            }
            return "Timer's running. [[timer:$secs|Capri]]"
        }
        Regex("^(?:focus|pomodoro|focus mode|ركزي|وضع التركيز)(?: for)? ?(\\d+)?(?: minutes| mins| min| دقيقة| دقائق)?$").find(t)?.let {
            val n = (it.groupValues[1].toIntOrNull() ?: 25).coerceIn(1, 180)
            return L.t("Focus for $n minutes. I'll stay quiet. [[focus:$n]]", "تركيز لمدة $n دقيقة. بسكت. [[focus:$n]]")
        }
        Regex("^change (?:your )?(hair|accents|eyes|edges|body|colou?rs?)(?: colou?r)? (?:to )?([a-z]+)$").find(t)?.let {
            val slot = if (it.groupValues[1].startsWith("colo")) "all" else it.groupValues[1]
            return "[[set:color|$slot=${it.groupValues[2]}]]"
        }
        Regex("^(?:غيري|بدلي) لون (شعرك|عيونك|جسمك|الوانك|ألوانك) (?:الى|إلى|ل|لـ)? ?(.+)$").find(t)?.let {
            val slot = when (it.groupValues[1]) { "شعرك" -> "hair"; "عيونك" -> "eyes"; "جسمك" -> "body"; else -> "all" }
            return "[[set:color|$slot=${it.groupValues[2].trim()}]]"
        }
        if (Regex("^(dance|dance for me|start dancing|ارقص|ارقصي|ارقص لي)$").matches(t)) return "[[emote:dance]]"
        if (Regex("^(stop dancing|enough dancing|وقف الرقص|بس رقص|بطلي رقص|وقفي الرقص)$").matches(t)) return "[[emote:stop]]"
        if (Regex("^(scroll for me|doomscroll for me|scroll tiktok for me|scroll shorts for me|react to my tiktoks|lets doomscroll|تصفح لي|تصفحي لي|سكرول لي|سكرولي)$").matches(t)) return "[[scrollforme:on]]"
        if (Regex("^(stop scrolling|stop scrolling for me|stop the scrolling|وقف التصفح|وقفي التصفح|بس تصفح)$").matches(t)) return "[[scrollforme:off]]"
        if (Regex("^(?:let s |lets |can we |i want to |want to |wanna )?(?:play|have|do)(?: a| some)?(?: game of)? chess(?: with me)?(?: now)?$|^(?:play|chess) (?:with me|please)$|^chess$").matches(t) ||
            Regex("^(?:خلنا|يلا|تعالي|تعال|ابي|ابغى|أبغى|أبي|ممكن|هيا)?\\s*(?:نلعب|العب|لعب|العبي|نلعبو)\\s*(?:معي\\s*)?(?:لعبه |لعبة )?(?:شطرنج|الشطرنج)(?:\\s*معي)?$|^شطرنج$").matches(t))
            return L.t("Sure, let's play. [[chess:open]]", "يلا نلعب. [[chess:open]]")
        // keeps an eye out, points at things, clones (ported from the desktop version)
        if (Regex("^(stop watching|stop watching my screen|cancel the watch|dont watch anymore|وقف المراقبة|وقفي المراقبة|بس مراقبة)$").matches(t)) return "[[watch:off]]"
        Regex("^(?:let me know|notify me|alert me|ping me|warn me) (?:when|once|as soon as|if) (.+)$").find(t)?.let {
            return L.t("Okay, I'll watch for it. [[watch:${it.groupValues[1]}]]", "تمام، براقب. [[watch:${it.groupValues[1]}]]")
        }
        Regex("^tell me (?:when|once|as soon as) ((?:the|my|it|this|that|i|you|he|she|they|we) .+)$").find(t)?.let {
            return L.t("Okay, I'll watch for it. [[watch:${it.groupValues[1]}]]", "تمام، براقب. [[watch:${it.groupValues[1]}]]")
        }
        Regex("^(?:نبهني|نبّهني|ابلغني|بلغني|خبرني|قل لي|قولي) (?:اذا|إذا|لما|لمّا|عندما|اول ما|أول ما|لو) (.+)$").find(t)?.let {
            return L.t("Okay, I'll watch for it. [[watch:${it.groupValues[1]}]]", "تمام، براقب. [[watch:${it.groupValues[1]}]]")
        }
        Regex("^(?:point (?:at|to)|circle|highlight|show me where) (?:the )?(.+?)(?: is)?(?: button| icon)?$").find(t)?.let { return "[[point:${it.groupValues[1]}]]" }
        Regex("^where (?:is|s) (?:the )?(.+?) (?:button|icon|option|tab|menu|field|box)$").find(t)?.let { return "[[point:${it.groupValues[1]}]]" }
        Regex("^(?:دلني على|دليني على|حددي|حدد لي|اشري على|أشري على|وين زر|وين ايقونة|وين أيقونة) (?:زر )?(.+)$").find(t)?.let { return "[[point:${it.groupValues[1]}]]" }
        if (Regex("^(?:clear|remove|get rid of|kill|stop)(?: the| all| your)? clones?$").matches(t) || Regex("^(?:امسحي|شيلي|ازيلي|خلصي على) (?:الكلونز|النسخ|الكلون)$").matches(t)) return "[[clones:0]]"
        Regex("^(?:make|summon|spawn|create|give me)(?: me)?(?: (\\d+|a few|some|three|five|ten))?(?: more)? (?:clones?|copies of you)$").find(t)?.let {
            val n = when (val w = it.groupValues[1]) { "", "a few", "some" -> 4; "three" -> 3; "five" -> 5; "ten" -> 8; else -> w.toIntOrNull() ?: 4 }
            return L.t("Hold on. [[clones:$n]]", "لحظة. [[clones:$n]]")
        }
        Regex("^(?:سوي|سوّي|اعملي|اصنعي|سوي لي) (?:لي )?(\\d+)? ?(?:نسخ|نسخة|كلونز|كلون)(?: منك)?$").find(t)?.let {
            return L.t("Hold on. [[clones:${(it.groupValues[1].toIntOrNull() ?: 4)}]]", "لحظة. [[clones:${(it.groupValues[1].toIntOrNull() ?: 4)}]]")
        }
        if (Regex("^(استنسخي نفسك|استنسخي)$").matches(t)) return "[[clones:4]]"
        if (Regex("^(make you bigger|be bigger|bigger|get bigger|اكبري|كبري نفسك|اكبر)$").matches(t)) return "[[set:size|bigger]]"
        if (Regex("^(make you smaller|be smaller|smaller|get smaller|صغري نفسك|اصغري|اصغر)$").matches(t)) return "[[set:size|smaller]]"
        if (Regex("^(stop wandering|dont wander|do not wander|stay put|اثبتي|لا تتمشين|وقفي التمشي)$").matches(t)) return "[[set:wander|off]]"
        if (Regex("^(wander around|wander|you can wander|تمشي|تمشين)$").matches(t)) return "[[set:wander|1]]"
        if (Regex("^(quiet mode|be quiet|go quiet|وضع الصمت|اسكتي)$").matches(t)) return "[[set:quiet|on]]"
        if (Regex("^(talk again|voice on|you can talk|تكلمي|ارجعي تتكلمين)$").matches(t)) return "[[set:quiet|off]]"
        if (Regex("^(talk faster|speak faster|اسرعي)$").matches(t)) return "[[set:speed|faster]]"
        if (Regex("^(talk slower|speak slower|ابطئي)$").matches(t)) return "[[set:speed|slower]]"
        if (Regex("^(louder|speak louder|you are too quiet|ارفعي صوتك)$").matches(t)) return "[[set:volume|up]]"
        if (Regex("^(quieter|speak quieter|you are too loud|اخفضي صوتك)$").matches(t)) return "[[set:volume|down]]"
        if (Regex("^(what can you do|what do you do|your features|your abilities|وش تقدر تسوي|وش تسوين|ايش تقدرين تسوين)$").matches(t))
            return L.t("I chat, talk, remember you, wander, dance, and use your phone: open apps and type, tap, scroll, look at your screen, set timers and reminders, check weather, play music, and scroll your videos for you. Pat my head. Say hands for help.",
                "أسولف معك وأتكلم وأتذكّرك وأتمشّى وأرقص وأستخدم جوالك: أفتح التطبيقات وأكتب وأضغط وأسحب وأشوف شاشتك وأضبط المؤقتات والتذكيرات وأقول لك الطقس وأشغّل الموسيقى وأتصفّح لك الفيديوهات. وامسح على راسي إذا تبي.")
        if (Regex("^(flashlight|torch|turn on the flashlight|turn on the torch)( on)?$").matches(t)) return "On. [[flash:on]]"
        if (Regex("^(flashlight off|torch off|turn off the flashlight|turn off the torch)$").matches(t)) return "Off. [[flash:off]]"
        if (Regex("^(volume up|louder)$").matches(t)) return "Louder. [[volume:up]]"
        if (Regex("^(volume down|quieter|lower the volume)$").matches(t)) return "Quieter. [[volume:down]]"
        if (Regex("^(what s playing|what is playing|what song is this|what s this song)$").matches(t)) return "[[playing:x]]"
        Regex("^(?:what s|what is) the weather (?:in|at) (.+)$").find(t)?.let { return "[[weather:${it.groupValues[1]}]]" }
        Regex("^weather (?:in )?(.+)$").find(t)?.let { return "[[weather:${it.groupValues[1]}]]" }
        Regex("^(?:who is|who was|what is|what s|tell me about) (.+)$").find(t)?.takeIf { it.groupValues[1].split(" ").size <= 4 && !Regex("^(the weather|playing|your|you|my|the time|up|going|this|that|it|wrong|happening)\\b").containsMatchIn(it.groupValues[1]) }
            ?.let { return "[[wiki:${it.groupValues[1]}]]" }
        if (Regex("^(look at|check|watch|see) (my )?screen$|^what s on my screen$|^what is on my screen$").matches(t))
            return "Let me look. [[look:What is on their screen right now?]]"
        return null
    }
}
