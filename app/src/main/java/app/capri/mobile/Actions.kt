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
        fun chessScreen() {}
        fun playGame(name: String) {}
        fun browse(target: String) {}
        fun chessPilot(mode: String) {}
        fun screen(name: String, arg: String = "") {}
        fun mode(id: String) {}
    }

    const val LOCKDOWN = 0
    const val GUARDED = 1
    const val TRUSTED = 2
    const val ASK_ALWAYS = 3

    private val tag = Regex("\\[\\[(\\w+):([^\\]]*)\\]\\]")
    // Lookups that only read public info: allowed even in Lockdown.
    private val readOnly = setOf("weather", "wiki", "web", "read", "brief", "playing", "remember", "info", "game", "guide", "security", "mode", "room")
    // In Guarded these ask first, by showing a bubble you tap to allow.
    private val asksFirst = setOf("do", "look", "url", "dictate", "scrollforme", "watch")
    // In "ask every time" mode even your own direct orders ask first.
    private val controls = setOf("hands", "do", "look", "url", "open", "dictate", "scrollforme", "chaos", "watch", "point", "playgame", "browse")

    fun strip(s: String): String = tag.replace(s, "").replace(Regex("\\s+"), " ").trim()

    // userInitiated = the words came straight from the user (a quick ask), so Guarded mode doesn't ask again.
    fun run(ctx: Context, prefs: Prefs, reply: String, hooks: Hooks?, userInitiated: Boolean = false): String {
        var extra = ""
        val level = prefs.getI("security", GUARDED)
        for (m in tag.findAll(reply)) {
            val name = m.groupValues[1].lowercase()
            val arg = m.groupValues[2].trim()
            // she never starts flipping your videos because a reply said so: only your own words can
            if (name == "scrollforme" && !arg.lowercase().startsWith("off") && !userInitiated) continue
            if (level == LOCKDOWN && name !in readOnly) {
                extra = L.t(" (Lockdown is on, so I can't touch your phone.)", " (وضع الإغلاق الكامل شغّال، فما أقدر ألمس جوالك.)")
                continue
            }
            if (hooks != null && ((level == ASK_ALWAYS && name in controls) ||
                    (level == GUARDED && (name in asksFirst || (!userInitiated && name in setOf("playgame", "browse", "open", "remember")) || ((name == "hands" || name == "point") && !userInitiated))))) {
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
        "hands" to "control", "do" to "control", "playgame" to "control", "watch" to "screen", "point" to "screen", "scrollforme" to "control", "dictate" to "control",
        "music" to "media", "media" to "media", "volume" to "media",
        "timer" to "alerts", "alarm" to "alerts", "remind" to "alerts", "focus" to "alerts",
        "flash" to "device", "nav" to "device", "search" to "device", "copy" to "device",
        "weather" to "lookup", "wiki" to "lookup", "web" to "lookup", "read" to "lookup", "brief" to "lookup", "browse" to "apps", "playing" to "lookup")

    // Returns extra text to add to her reply when something went wrong.
    private fun execute(ctx: Context, prefs: Prefs, name: String, arg: String, hooks: Hooks?): String {
        val group = groups[name]
        if (group != null && !prefs.getB("tool_$group", true)) return L.t(" (That tool is switched off in my settings.)", " (هذي الأداة مطفية من إعداداتي.)")
        if (name !in readOnly) ActionLog.tag(prefs, name, arg)
        try {
            when (name) {
                "open" -> if (!openApp(ctx, arg)) return L.t(" Couldn't find that app. If it is installed, your phone may be hiding apps from me: Settings > Apps > Capri > Permissions > Installed apps > Allow.", " ما لقيت هذا التطبيق. إذا هو منزّل، جوالك يمكن يخفي التطبيقات عني: الإعدادات > التطبيقات > Capri > الصلاحيات > التطبيقات المثبتة > سماح.")
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
                "info" -> return " " + DeviceInfo.answer(ctx, arg.lowercase())
                "game" -> start(ctx, Intent(ctx, GamesActivity::class.java).putExtra("game", arg.lowercase()))
                "remind" -> if (!Reminders.add(ctx, arg)) return L.t(" I couldn't read that time.", " ما فهمت الوقت.")
                "remember" -> prefs.addNote(arg)
                "do" -> hooks?.startJob(arg)
                "look" -> hooks?.look(arg)
                "weather", "wiki", "web", "read", "brief", "playing" -> hooks?.tool(name, arg)
                "browse" -> hooks?.browse(arg)
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
                "guide", "security", "room" -> hooks?.screen(name, arg.trim())
                "mode" -> hooks?.mode(arg.trim())
                "playgame" -> hooks?.playGame(arg.trim())
                "chess" -> when (arg.trim()) { "screen" -> hooks?.chessScreen(); "auto", "coach", "stop", "scan", "learn" -> hooks?.chessPilot(arg.trim()); else -> hooks?.chess() }
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
    // Well-known package names, tried directly when the phone hides the list of installed apps from us.
    private val knownPackages = mapOf(
        "youtube" to listOf("com.google.android.youtube", "app.revanced.android.youtube", "com.vanced.android.youtube"),
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"), "telegram" to listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"),
        "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), "instagram" to listOf("com.instagram.android", "com.instagram.lite"),
        "snapchat" to listOf("com.snapchat.android"), "discord" to listOf("com.discord"), "chrome" to listOf("com.android.chrome"),
        "twitter" to listOf("com.twitter.android"), "x" to listOf("com.twitter.android"), "facebook" to listOf("com.facebook.katana", "com.facebook.lite"),
        "messenger" to listOf("com.facebook.orca"), "spotify" to listOf("com.spotify.music"), "netflix" to listOf("com.netflix.mediaclient"),
        "gmail" to listOf("com.google.android.gm"), "maps" to listOf("com.google.android.apps.maps"), "reddit" to listOf("com.reddit.frontpage"),
        "pinterest" to listOf("com.pinterest"), "steam" to listOf("com.valvesoftware.android.steam.community"), "pubg" to listOf("com.tencent.ig", "com.pubg.imobile"),
        "camera" to listOf("com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.sec.android.app.camera"),
        "settings" to listOf("com.android.settings"), "calculator" to listOf("com.google.android.calculator", "com.miui.calculator", "com.sec.android.app.popupcalculator", "com.android.calculator2"),
        "clock" to listOf("com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage"), "play store" to listOf("com.android.vending"),
        "phone" to listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"), "messages" to listOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms"),
        "gallery" to listOf("com.google.android.apps.photos", "com.miui.gallery", "com.sec.android.gallery3d"), "photos" to listOf("com.google.android.apps.photos")
    )

    private fun norm(s: String): String = s.lowercase().replace(Regex("[\u064B-\u065F\u0640]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي').replace(Regex("[^a-z0-9\u0600-\u06FF ]"), "").replace(Regex("\\s+"), " ").trim()

    /** A rough sound skeleton so "فورت نايت", "ببجي" or "ماين كرافت" still find "Fortnite", "PUBG" or "Minecraft": vowels dropped, look-alike consonants merged. */
    fun skeletonOf(raw: String) = skeleton(raw)
    internal fun skeleton(raw: String): String {
        val ar = mapOf('ب' to "b", 'پ' to "b", 'ت' to "t", 'ث' to "s", 'ج' to "j", 'چ' to "j", 'ح' to "h", 'خ' to "k", 'د' to "d", 'ذ' to "s", 'ر' to "r", 'ز' to "s", 'س' to "s",
            'ش' to "sh", 'ص' to "s", 'ض' to "d", 'ط' to "t", 'ظ' to "s", 'غ' to "j", 'ف' to "f", 'ڤ' to "f", 'ق' to "k", 'ك' to "k", 'ک' to "k", 'ل' to "l", 'م' to "m", 'ن' to "n", 'ه' to "h", 'ة' to "h")
        val sb = StringBuilder()
        for (c in raw.lowercase()) {
            val r = ar[c]
            if (r != null) { sb.append(r); continue }
            when (c) {
                'p' -> sb.append('b'); 'g' -> sb.append('j'); 'c', 'q' -> sb.append('k'); 'v' -> sb.append('f'); 'z' -> sb.append('s'); 'x' -> sb.append("ks")
                'a', 'e', 'i', 'o', 'u', 'y', 'w' -> {}
                in 'a'..'z' -> sb.append(c)
            }
        }
        // collapse doubled letters ("Minecraft" has none, "Candy Crush"/"Call" do)
        return sb.toString().replace(Regex("(.)\\1+"), "$1")
    }

    fun findApp(ctx: Context, name: String): Pair<String, String>? {
        val pm = ctx.packageManager
        val apps = try { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0) } catch (e: Throwable) { emptyList() }
        var q = norm(name).removePrefix("تطبيق ").removePrefix("application ").removePrefix("app ").trim()
        val aliasKey = aliases.keys.firstOrNull { norm(it) == q || norm(it) == q.removePrefix("ال") }
        q = if (aliasKey != null) aliases[aliasKey]!! else q
        if (q.isEmpty()) return null
        // Some phones (Xiaomi, Samsung, Oppo) hide the installed-apps list; known apps are still found by their package name.
        fun viaKnown(): Pair<String, String>? {
            val list = knownPackages[q] ?: knownPackages.entries.firstOrNull { q.contains(it.key) || it.key.contains(q) && q.length > 3 }?.value ?: return null
            for (p in list) if (try { pm.getLaunchIntentForPackage(p) != null } catch (e: Throwable) { false }) return Pair(q, p)
            return null
        }
        if (apps.isEmpty()) return viaKnown()
        val sk = skeleton(q)
        fun label(i: Int) = norm(apps[i].loadLabel(pm).toString())
        fun pkg(i: Int) = apps[i].activityInfo.packageName.lowercase()
        val idx = apps.indices.firstOrNull { label(it) == q }
            ?: apps.indices.firstOrNull { label(it).startsWith(q) }
            ?: apps.indices.firstOrNull { label(it).contains(q) }
            ?: apps.indices.firstOrNull { pkg(it).contains(q.replace(" ", "")) }
            ?: viaKnown()?.let { return it }
            ?: (if (sk.length >= 3) apps.indices.firstOrNull { skeleton(apps[it].loadLabel(pm).toString()) == sk } else null)
            ?: (if (sk.length >= 4) apps.indices.firstOrNull { val l = skeleton(apps[it].loadLabel(pm).toString()); l.length >= 4 && (l.startsWith(sk) || sk.startsWith(l)) } else null)
            ?: return null
        return Pair(apps[idx].loadLabel(pm).toString(), apps[idx].activityInfo.packageName)
    }

    fun openApp(ctx: Context, name: String): Boolean {
        val found = findApp(ctx, name) ?: return false
        val launch = ctx.packageManager.getLaunchIntentForPackage(found.second) ?: return false
        start(ctx, launch)
        return true
    }

    private fun installed(ctx: Context, pkg: String): Boolean =
        try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (e: Exception) { false }

    // [[music:song]] plays in whatever the phone picks; [[music:song|anghami]] plays in that app (or on its website when it isn't installed).
    // With no app named, the one chosen in Settings (pref "musicApp") is used when there is one.
    private fun playMusic(ctx: Context, arg: String) {
        val (query, named) = MusicApps.split(arg)
        val chosen = named ?: MusicApps.byKey(Prefs(ctx).getS("musicApp", ""))
        if (chosen != null) {
            val pkg = chosen.packages.firstOrNull { installed(ctx, it) }
            if (pkg != null) {
                try {
                    if (query.isBlank()) {
                        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)
                        if (launch != null) { start(ctx, launch); return }
                    } else {
                        start(ctx, Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                            .setPackage(pkg)
                            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                            .putExtra(SearchManager.QUERY, query))
                        return
                    }
                } catch (e: Exception) { }
            }
            try { start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(chosen.searchUrl(query)))); return } catch (e: Exception) { }
        }
        playMusicAnywhere(ctx, query)
    }

    private fun playMusicAnywhere(ctx: Context, query: String) {
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

    private val knownGames = "fortnite|pubg|minecraft|roblox|genshin(?: impact)?|free ?fire|clash royale|clash of clans|brawl stars|among us|subway surfers|temple run|candy crush|call of duty|cod mobile|mobile legends|8 ball pool|ludo(?: king)?|geometry dash|flappy bird|2048|stumble guys|hay day|cookie run|honkai|wuthering waves|asphalt|hill climb racing|plants vs zombies|zombie|tower defense|فورت ?نايت|ببجي|ماين ?كرافت|روبلوكس|فري ?فاير|كلاش|براول|امونج|أمونج|سب ?واي|كاندي|كول اوف ديوتي|لودو|ليدو"

    /** "العبلي فورت نايت", "العب لي ببجي", "play fortnite for me", "play the game subway surfers": opens that game and plays it. Chess has its own route. */
    internal fun gameAsk(raw: String): String? {
        val t = raw.lowercase().trim().replace(Regex("[^a-z0-9 ؀-ۿ]"), " ").replace(Regex("\\s+"), " ").replace('أ', 'ا').replace('إ', 'ا')
        if (Regex("شطرنج|chess").containsMatchIn(t)) return null
        Regex("^(?:play|launch and play|start playing|open and play) (?:the game |game )?(.+?)(?: for me| game)?$").find(t)?.let { m ->
            val g = m.groupValues[1].trim()
            if (Regex("^(?:this|my|the|that|a|it|some)\\b.*").matches(g) && !Regex("^(?:$knownGames)$").matches(g)) return null
            val forMe = t.endsWith("for me") || t.contains("the game") || t.endsWith(" game") || t.startsWith("launch and play") || t.startsWith("open and play")
            if (g.isNotEmpty() && (forMe || Regex("^(?:$knownGames)$").matches(g))) return g
        }
        Regex("^(?:ال?عب(?:ي)?(?: ?لي| لي| عني| بدالي| مكاني)?|شغل(?:ي)?(?: ?لي)? و?ال?عب(?:ي)?) (?:لعبه |لعبة )?(.+?)(?: عني| بدالي| مكاني| لي)?$").find(t)?.let { m ->
            val g = m.groupValues[1].trim()
            if (g.isNotEmpty() && g !in setOf("عني", "لي", "مكاني", "بدالي", "بمكاني", "محلي") && !g.startsWith("شطرنج") && !g.startsWith("موسيق") && !g.startsWith("اغنيه") && !g.startsWith("اغنية")) return g
        }
        return null
    }

    /** "العب عني شطرنج", "العب شطرنج عني", "كملي لي اللعبة", "play the chess game for me": short and loose, so the word order never decides. */
    internal fun chessAuto(raw: String): String? {
        var t = raw.lowercase().replace(Regex("[\\u064B-\\u0652\\u0640]"), "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي')
            .replace(Regex("[.,!?؟،]"), " ")
        t = t.replace(Regex("\\b(?:يا |hey |ok |okay )?capri\\b|يا كابري|كابري|لو سمحتي|لو سمحت|رجاءا?|please|pls|الحين|هسه|الان|هلا|ممكن|تقدرين|تقدر|يلا|خلاص")," ")
            .trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty() || t.split(' ').size > 9) return null
        val chessWord = Regex("شطرنج|chess").containsMatchIn(t)
        val forMe = Regex("(^| )(?:عني|عنّي|بدالي|بدلي|بمكاني|مكاني|مكانی|محلي|لي|for me|by myself|instead of me)( |$)").containsMatchIn(t) ||
            Regex("^(?:play|finish|continue|auto ?play)\\b.*\\b(?:game|chess)\\b").containsMatchIn(t) && t.contains("for me")
        val verb = Regex("(^| )(?:العبي?|لعب|كملي?|خلصي?|شغلي?|اكملي?|play|finish|continue|autoplay|auto)( |$)").containsMatchIn(t)
        if (chessWord && forMe && verb) return "auto"
        if (Regex("^(?:العبي?|لعب|كملي?|خلصي?|اكملي?) (?:عني|بدالي|بدلي|بمكاني|مكاني|محلي|لي)(?: (?:ال)?(?:لعبه|اللعبه|لعبه الشطرنج|شطرنج))?$").matches(t)) return "auto"
        return null
    }

    fun match(raw: String): String? {
        val r = raw.trim()
        ReminderAsk.match(r)?.let { return it }
        LocalAsks.match(r)?.let { return it }
        // "play X on Anghami / Spotify / Deezer / YouTube Music", "شغل X على انغامي"
        MusicApps.parse(r)?.let { return L.t("On it. ", "ابشر. ") + MusicApps.tag(it) }
        // "is this a scam: <message>", "هل هذي رسالة نصب: ..." is checked on the phone, nothing is sent anywhere
        ScamCheck.ask(r)?.let { text ->
            return if (text.isBlank()) L.t("Paste or share the message to me, like: is this a scam: <the message>. Or share it from WhatsApp or SMS to Capri.",
                "الصق لي الرسالة أو شاركها معي، مثل: هل هذي نصب: <الرسالة>. أو شاركها من واتساب أو الرسائل إلى كابري.")
            else ScamCheck.say(ScamCheck.analyze(text), L.ar)
        }
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
        if (Regex("^(?:my )?(?:daily )?(?:briefing|brief me|brief|what(?:s| is) my day(?: like)?|catch me up|ملخص (?:اليوم|يومي)|شنو (?:عندي|اخبار) اليوم|وش (?:عندي|اخبار) اليوم|لخصي يومي)$").matches(t)) return L.t("Let me put your day together. [[brief:today]]", "خلني أجمع لك يومك. [[brief:today]]")
        Regex("^(?:open|launch|start|افتح(?:ي)?(?: لي)?|افتحلي|شغل(?:ي)?(?: لي)?) (.+?) (?:and |then |و|ثم )(?:search(?: for)?|look up|ابحث(?:ي)?(?: لي)?(?: عن)?|ابحثلي(?: عن)?|دور(?:ي)?(?: لي)?(?: على)?) (.+)$").find(t)?.let { m ->
            val app = m.groupValues[1].trim(); var q = m.groupValues[2].trim()
            var then = ""
            Regex("^(.+?) (?:and |then |و|ثم )(play|open|watch|شغل(?:ي)?(?: لي)?|افتح(?:ي)?|شوف(?:ي)?)(?: it| اول نتيجه| اول نتيجة| the first result| first result)?$").find(q)?.let { q2 -> q = q2.groupValues[1].trim(); then = q2.groupValues[2] }
            val goal = "Open the app \"$app\", search for \"$q\" in it" + (if (then.isNotEmpty()) ", then " + (if (Regex("play|شغل").containsMatchIn(then)) "play" else "open") + " the first result" else "") + "."
            return L.t("On it. [[do:$goal]]", "أبشر. [[do:$goal]]")
        }
        if (Regex("^(?:open|launch|افتح(?:ي)?|شغل(?:ي)?) (?:your |the |ال)?(?:browser|متصفح(?:ك)?|المتصفح)$").matches(t)) return L.t("Opening my browser. [[browse:https://duckduckgo.com]]", "أفتح متصفحي. [[browse:https://duckduckgo.com]]")
        Regex("^(?:open (?:the )?(?:site|website|page)|go to|visit|افتح(?:ي)?(?: لي)? (?:موقع|الموقع|صفحه|صفحة)) (\\S+)$").find(raw.trim().lowercase().replace(Regex("^(hey |ok |please )+"), ""))?.let { m -> return L.t("Opening it. [[browse:${m.groupValues[1]}]]", "أفتحه. [[browse:${m.groupValues[1]}]]") }
        Regex("^(?:search(?: the web)?(?: for)?|google|look up|ابحث(?:ي)?(?: لي)?(?: عن)?|ابحثلي(?: عن)?|دور(?:ي)?(?: لي)?(?: على)?|دورلي(?: على)?) (.{2,})$").find(t)?.let { m -> return L.t("Let me look that up. [[web:${m.groupValues[1]}]]", "خلني أبحث. [[web:${m.groupValues[1]}]]") }
        if (Regex("^(?:help|guide|help me|open (?:the )?(?:help|guide)|show (?:me )?(?:the )?(?:help|guide)|how does this app work|what can you do|how do i use (?:this|you|capri)|مساعده|مساعدة|الدليل|دليل|افتحي? (?:ال)?(?:مساعده|مساعدة|دليل|الدليل)|وريني الدليل|كيف استخدمك|شلون استخدمك|شنو تقدرين تسوين|شنو تسوين|وش تسوين)$").matches(t))
            return L.t("Here's my guide. [[guide:open]]", "هذا دليلي. [[guide:open]]")
        if (Regex("^(?:security(?: center| centre)?|open (?:the )?security(?: center| centre)?|is (?:this app|capri|my phone) safe|مركز الامان|مركز الأمان|الامان|الأمان|افتحي? (?:مركز )?(?:ال)?(?:امان|أمان)|هل التطبيق امن|هل التطبيق آمن)$").matches(t))
            return L.t("Let's check. [[security:open]]", "خلنا نشوف. [[security:open]]")
        if (Regex("^(?:her room|your room|show (?:me )?(?:your|her) room|open (?:your|her|the) room|go to your room|غرفتك|غرفتها|وريني غرفتك|افتحي? (?:ال)?غرفة|غرفة كابري|روحي لغرفتك)$").matches(t))
            return L.t("Come on in. [[room:open]]", "تفضل. [[room:open]]")
        Modes.fromPhrase(t)?.let { return L.t("On it. [[mode:$it]]", "تمام. [[mode:$it]]") }
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
        if (Regex("^(?:what(?:s| is) (?:claude|claude code|codex) doing|how(?:s| is) (?:claude|claude code) (?:doing|going)|claude status|وش يسوي كلود|شنو يسوي كلود|وش وصل كلود كود)$").matches(t)) return "[[set:huskstatus|1]]"
        if (Regex("^(?:clip that|save that clip|save the clip|clip it|سجلي (?:المقطع|هاي|هذي|اللقطه|اللقطة)|احفظي (?:المقطع|اللقطه|اللقطة)|سجلي اللي صار)$").matches(t)) return L.t("On it. [[set:clip|save]]", "أبشر. [[set:clip|save]]")
        if (Regex("^(?:clip my games|record my games|start clipping|سجلي ألعابي|سجلي العابي|سجلي لعبي)$").matches(t)) return L.t("Okay. [[set:clip|on]]", "تمام. [[set:clip|on]]")
        if (Regex("^(?:stop clipping|stop recording my games|وقفي التسجيل|وقفي المقاطع)$").matches(t)) return L.t("Stopped. [[set:clip|off]]", "وقفت. [[set:clip|off]]")
        gameAsk(t)?.let { g -> return L.t("On it. I'll open $g and play it, tap my chip to stop. [[playgame:$g]]", "أبشر. بفتح $g وألعبها، اضغط على الشريط عشان توقفني. [[playgame:$g]]") }
        Regex("^play (?!(?:a |some |this |my |the )?(?:game of )?chess\\b|(?:this|my|the) game\\b)(.+)$").find(t)?.let { return "On it. [[music:${it.groupValues[1]}]]" }
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
        if (Regex("^(explore while i'm away|explore while im away|look around while i'm away|i'm away|im away|تجولي وأنا بعيد|تجولي|أنا بعيد|انا بعيد)$").matches(t)) return "[[set:explore|on]]"
        if (Regex("^(stop exploring|i'm back|im back|وقفي التجوال|رجعت)$").matches(t)) return "[[set:explore|off]]"
        if (Regex("^(chair|sit on (?:your|the) chair|take a seat|show me your chair|كرسي|اجلسي على الكرسي|وريني الكرسي)$").matches(t)) return "[[emote:chair]]"
        if (Regex("^(stop scrolling|stop scrolling for me|stop the scrolling|وقف التصفح|وقفي التصفح|بس تصفح)$").matches(t)) return "[[scrollforme:off]]"
        chessAuto(t)?.let { return L.t("On it. Open your chess game and keep the board visible, I'll show you what I'm doing on top of the screen. [[chess:auto]]", "أبشر. افتح لعبة الشطرنج وخلي الرقعة ظاهرة، وبوريك فوق الشاشة وش قاعدة أسوي. [[chess:auto]]") }
        if (Regex("^(?:learn my (?:chess )?pieces|learn (?:the |my )?(?:chess )?(?:pieces|theme)|scan (?:the |my )?(?:chess )?board|look at (?:the |my )?chess board|can you read my chess board)$|^(?:تعلمي|تعلم) (?:قطعي|قطع الشطرنج|شكل القطع)$|^(?:شوفي|اقرأي|افحصي) (?:الرقعة|رقعة الشطرنج)$").matches(t))
            return L.t("Let me look. [[chess:scan]]", "خليني أشوف. [[chess:scan]]")
        if (Regex("^(?:play|finish|continue) (?:this |my |the )?(?:chess )?(?:game|chess)(?: for me| on (?:my )?screen)(?: for me)?$|^play chess for me(?: on (?:my )?screen)?$|^العبي? (?:لي |عني )?(?:الشطرنج|لعبة الشطرنج|اللعبة)(?: على الشاشة)?$|^العبي? عني$").matches(t))
            return L.t("Okay, hands off the board. [[chess:auto]]", "تمام، لا تلمس الرقعة. [[chess:auto]]")
        if (Regex("^(?:coach me|watch my (?:chess )?game(?: and help me)?|help me play(?: chess)?|be my chess coach)$|^(?:راقبي?|دربني|ساعدني) (?:في )?(?:لعبتي|الشطرنج|لعبة الشطرنج)$").matches(t))
            return L.t("I'll watch and point. [[chess:coach]]", "بشوف وأشرلك. [[chess:coach]]")
        if (Regex("^(?:stop (?:playing )?chess|stop the chess|stop playing for me|وقفي? (?:الشطرنج|اللعب)|بس لعب)$").matches(t))
            return L.t("Okay, stopping. [[chess:stop]]", "تمام، وقفت. [[chess:stop]]")
        if (Regex("^(?:what(?:s| is) the best move(?: on (?:my )?screen)?|what should i play(?: here)?|help me (?:with|in) (?:this|my) chess(?: game)?|play chess on (?:my )?screen|analy[sz]e (?:this|my) chess (?:board|game|position))$").matches(t) ||
            Regex("^(?:شنو|وش|ايش) (?:افضل|أفضل) نقلة(?: على الشاشة| هنا)?$|^ساعدني (?:في|ب|بـ)?\\s*(?:هذي |هذه )?(?:لعبة )?الشطرنج$|^حلل (?:لي )?(?:الرقعة|الشطرنج)$").matches(t))
            return L.t("Let me look at the board. [[chess:screen]]", "خلني أشوف الرقعة. [[chess:screen]]")
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
