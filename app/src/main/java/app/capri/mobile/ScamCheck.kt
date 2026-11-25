package app.capri.mobile

// A local scam / phishing check for text you share to Capri or paste ("هل هذي رسالة نصب؟", "is this a scam?").
// capri-desktop 0.9.7 warns when an email asks for card details or a password or names a company it didn't come from;
// this is the same idea for SMS, WhatsApp and Telegram messages, in Arabic and English. Nothing leaves the phone.
// Pure logic (no Android), so it is unit tested.
class Reason(val en: String, val ar: String)

class ScamVerdict(val score: Int, val reasons: List<Reason>, val links: List<String>) {
    /** 0 = nothing suspicious found, 1 = be careful, 2 = very likely a scam */
    val level: Int get() = if (score >= 5) 2 else if (score >= 3) 1 else 0
}

object ScamCheck {
    private fun norm(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s.lowercase()) sb.append(when (c) {
            'أ', 'إ', 'آ' -> 'ا'; 'ى' -> 'ي'; 'ة' -> 'ه'
            in '٠'..'٩' -> ('0' + (c - '٠'))     // Arabic-Indic digits
            'ً', 'ٌ', 'ٍ', 'َ', 'ُ', 'ِ', 'ّ', 'ْ', 'ـ' -> ' '
            else -> c
        })
        return sb.toString().replace(Regex("\\s+"), " ")
    }

    private val shorteners = setOf("bit.ly", "tinyurl.com", "t.co", "cutt.ly", "is.gd", "rb.gy", "shorturl.at", "tiny.cc", "goo.gl", "ow.ly", "buff.ly", "rebrand.ly", "t.ly", "s.id", "shorturl.asia")
    private val badTlds = setOf("xyz", "top", "click", "icu", "buzz", "cfd", "sbs", "rest", "gq", "tk", "ml", "cf", "ga", "monster", "quest", "cyou", "lol", "work", "support", "zip", "mov")
    private val tlds = setOf("com", "net", "org", "info", "io", "me", "co", "app", "dev", "iq", "ir", "ae", "sa", "tr", "uk", "us", "ru", "cn", "in", "ly", "to", "cc", "tv", "ws", "link", "site", "online", "store", "shop", "live", "vip", "pro", "biz", "page", "life", "world", "club", "one", "fun") + badTlds

    // brand word -> the domains it really uses. A link that mentions the brand on some other domain is the classic fake.
    private val brands = mapOf(
        "paypal" to listOf("paypal.com"), "facebook" to listOf("facebook.com", "fb.com", "fb.me"), "instagram" to listOf("instagram.com"),
        "whatsapp" to listOf("whatsapp.com", "wa.me", "whatsapp.net"), "telegram" to listOf("telegram.org", "t.me", "telegram.me"),
        "google" to listOf("google.com", "goo.gl", "google.iq", "withgoogle.com", "gstatic.com", "googleapis.com"), "apple" to listOf("apple.com", "icloud.com"),
        "microsoft" to listOf("microsoft.com", "live.com", "office.com", "outlook.com"), "netflix" to listOf("netflix.com"), "amazon" to listOf("amazon.com", "amazon.ae", "amazon.sa", "amzn.to"),
        "tiktok" to listOf("tiktok.com"), "binance" to listOf("binance.com"), "snapchat" to listOf("snapchat.com"), "steam" to listOf("steampowered.com", "steamcommunity.com"),
        "zaincash" to listOf("zaincash.iq"), "asiacell" to listOf("asiacell.com"), "korek" to listOf("korektel.com"), "qicard" to listOf("qi.iq", "qicard.iq"),
        "fastpay" to listOf("fast-pay.iq", "fastpay.iq"), "dhl" to listOf("dhl.com"), "fedex" to listOf("fedex.com"), "aramex" to listOf("aramex.com")
    )

    class Link(val host: String, val full: String)

    private val urlRx = Regex("(?:(?:https?|hxxps?)://|www\\.)[^\\s<>\"')\\]]+|\\b[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\.[a-z]{2,24}(?:/[^\\s<>\"')\\]]*)?", RegexOption.IGNORE_CASE)

    fun links(text: String): List<Link> {
        val out = ArrayList<Link>()
        for (m in urlRx.findAll(text)) {
            var u = m.value.trimEnd('.', ',', ';', ':', '!', '?', '،')
            val lower = u.lowercase()
            val bare = !Regex("^(?:https?|hxxps?)://|^www\\.").containsMatchIn(lower)
            var host = lower.replace(Regex("^(?:https?|hxxps?)://"), "").substringBefore('/').substringBefore('?').substringBefore('#')
            if (host.contains('@')) host = host.substringAfterLast('@')
            host = host.substringBefore(':')
            if (host.isEmpty() || !host.contains('.')) continue
            // a bare "word.word" is only a link when it ends in a real top-level domain (avoids "e.g", "5.5", file names)
            if (bare) {
                val tld = host.substringAfterLast('.')
                if (tld !in tlds && !Regex("^\\d+(?:\\.\\d+){3}$").matches(host)) continue
            }
            out.add(Link(host, u))
        }
        return out.distinctBy { it.full }
    }

    private fun registered(host: String): String {
        val parts = host.split('.')
        if (parts.size <= 2) return host
        val twoPart = setOf("co.uk", "com.sa", "com.tr", "com.iq", "gov.iq", "edu.iq", "com.eg", "co.in", "org.uk")
        val last2 = parts.takeLast(2).joinToString(".")
        return if (last2 in twoPart && parts.size >= 3) parts.takeLast(3).joinToString(".") else last2
    }

    private val ownsLabel = { host: String, legit: String -> host == legit || host.endsWith(".$legit") }

    // things nobody should send to a message sender
    private const val SECRET_EN = "otp|one[- ]?time (?:code|pass(?:word)?)|verification code|security code|confirmation code|login code|activation code|password|passcode|pin(?: code)?|cvv|cvc|card number|card details|bank details|bank account|seed phrase|recovery phrase|private key|id card|iban"
    private const val SECRET_AR = "رمز التحقق|رمز التفعيل|رمز الامان|رمز التاكيد|رمز الدخول|رمز الوصول|كود التحقق|كود التفعيل|كود الامان|كود التاكيد|كلمه المرور|كلمه السر|الباسورد|باسورد|الرقم السري|الرقم السرى|رقم البطاقه|رقم الكارت|رقم الحساب|بيانات البطاقه|بيانات الحساب|بيانات البنك|الرمز السري|صوره الهويه|رمز ال ?otp"
    private val verbEn = "send|share|give|enter|confirm|reply|provide|verify|submit|type|forward|tell|read out|update|re-?enter"
    private val verbAr = "ارسل|ارسال|ارسلي|ادخل|دخل|ادخال|زود|زودنا|شارك|اكتب|اعطني|اعطيني|اعط|اخبرني|قل لي|تاكيد|تاكد|حدث|تحديث|ابعث|ابعثلي|دز|دزلي|اذكر|سلم"
    private val negEn = Regex("(?:do not|don't|dont|never|not to|won't ask|will never ask|nobody|no one)\\b[^.!\\n]{0,40}$")
    private val negAr = Regex("(?:لا |ما |لن |ممنوع |تحذير|احذر)[^.!\\n]{0,40}$")

    private val requestRx = Regex("(?:\\b(?:$verbEn)\\b[^.!?\\n]{0,40}\\b(?:$SECRET_EN)\\b)|(?:\\b(?:$SECRET_EN)\\b[^.!?\\n]{0,30}\\b(?:to us|to this number|below|here|now|immediately)\\b)|(?:(?:$verbAr)[^.!؟\\n]{0,40}(?:$SECRET_AR))|(?:(?:$SECRET_AR)[^.!؟\\n]{0,25}(?:هنا|الان|فورا|حالا|لنا|للرقم|لهذا الرقم|بالرد))")

    private fun asksForSecret(t: String): Boolean {
        for (m in requestRx.findAll(t)) {
            val before = t.substring(maxOf(0, m.range.first - 45), m.range.first)
            val neg = negEn.containsMatchIn(before + t.substring(m.range.first, minOf(t.length, m.range.first + 12))) || negAr.containsMatchIn(before)
            // "never share your code with anyone" has the verb inside the match itself
            val inside = Regex("(?:do not|don't|dont|never|لا ت|لا تشارك|لا تعطي|لا تخبر|لا ترسل|ممنوع)").containsMatchIn(t.substring(maxOf(0, m.range.first - 25), m.range.first + 6))
            if (!neg && !inside) return true
        }
        return false
    }

    private val prizeRx = Regex("you(?:'ve| have)? won|you are (?:a )?winner|congratulations[^.!]{0,60}(?:won|prize|selected|winner|gift)|claim your (?:prize|reward|gift)|free (?:gift|iphone|money)|lottery|مبروك[^.!؟]{0,60}(?:ربحت|فزت|جايزه|جائزه|هديه|سحب|مختار)|ربحت|فزت ب|جايزه|جائزه|هديه مجانيه|سحب على|قرعه")
    private val urgencyRx = Regex("(?:account|card|wallet|number|sim)[^.!]{0,30}(?:suspended|blocked|locked|closed|restricted|disabled|deactivated|will be (?:closed|deleted|suspended))|within (?:24|48|12|2|1) ?(?:hours?|hrs?)|final (?:warning|notice)|act now|urgent(?:ly)?|immediately|legal action|حسابك[^.!؟]{0,30}(?:موقوف|معلق|محظور|مقيد|مغلق|سيتم|سيغلق|تم ايقاف|تم تعليق|تم حظر)|(?:بطاقتك|محفظتك|رقمك|شريحتك)[^.!؟]{0,25}(?:موقوف|معلق|محظور|مغلق|ستغلق|سيتم)|خلال (?:24|48|12|2|1) ?ساع|اخر تحذير|انذار اخير|عاجل|فورا|حالا|اجراء قانوني|مذكره قبض|سيتم (?:ايقاف|حظر|الغاء|تجميد)")
    private val moneyRx = Regex("(?:send|transfer|wire|pay) (?:me |us )?(?:the )?(?:money|funds|\\$|usd|usdt|btc|fee|a fee)|gift ?cards?|western union|bitcoin|usdt|crypto(?:currency)?|processing fee|(?:ارسل|حول|حوّل|ادفع|دز|ابعث)[^.!؟]{0,25}(?:مبلغ|فلوس|رصيد|رسوم|كارت|كارتات|بطاقات? (?:شحن|ايتونز|جوجل|اسياسيل|زين)|دولار|دينار)|كارت (?:اسيا|زين|كورك|شحن)|رسوم (?:التوصيل|الشحن|التحويل|التسجيل|الاستلام)|رقم (?:كارت|بطاقه) الشحن")
    private val moneyAskExcl = Regex("(?:invoice|receipt|فاتوره|وصل)")
    private val investRx = Regex("guaranteed (?:profit|return|income)|earn \\$?\\d+[^.!]{0,20}(?:per|a|every) (?:day|hour|week)|work from home|double your (?:money|investment)|investment (?:opportunity|plan)|passive income|ربح مضمون|ارباح مضمونه|اربح[^.!؟]{0,30}(?:يوميا|بالساعه|اسبوعيا)|وظيفه (?:من|عن) (?:البيت|بعد)|ضاعف (?:اموالك|رصيدك|فلوسك)|فرصه استثماريه|دخل اضافي|عمل من المنزل|توظيف اموال")
    private val relativeRx = Regex("(?:hi|hello) (?:mum|mom|dad|it'?s me)[^.!]{0,60}(?:new number|lost my phone|broken phone)|this is my new number|my phone (?:is )?(?:broken|lost|stolen)|هذا رقمي الجديد|رقمي الجديد|ضاع (?:تلفوني|جوالي|هاتفي|موبايلي)|انكسر (?:تلفوني|جوالي|هاتفي)|تلفوني (?:انسرق|ضاع|خربان)")
    private val apkRx = Regex("\\.apk\\b|install (?:the |this )?(?:app|apk)[^.!]{0,30}(?:link|below|here)|(?:نزل|حمل|ثبت|ثبّت)[^.!؟]{0,25}(?:التطبيق|البرنامج|apk)[^.!؟]{0,30}(?:رابط|الرابط|هنا|اسفل|تحت)")
    private val shipRx = Regex("(?:package|parcel|shipment|delivery|order)[^.!]{0,50}(?:could not be delivered|failed|on hold|held|customs|pending|reschedule)|(?:شحنتك|طردك|طلبك)[^.!؟]{0,40}(?:معلق|متوقف|لم يتم|الجمارك|تعذر|اعاده)")

    fun analyze(raw: String): ScamVerdict {
        val text = raw.take(6000)
        val t = norm(text)
        var score = 0
        val reasons = ArrayList<Reason>()
        fun add(points: Int, en: String, ar: String) { score += points; reasons.add(Reason(en, ar)) }

        val ls = links(text)
        for (l in ls) {
            val host = l.host
            val reg = registered(host)
            val lowerFull = l.full.lowercase()
            if (host.startsWith("xn--") || host.contains(".xn--")) add(3, "a link uses disguised (punycode) letters: $host", "رابط بأحرف مخادعة (punycode): $host")
            else if (Regex("^\\d+(?:\\.\\d+){3}$").matches(host)) add(3, "a link goes to a bare IP address: $host", "رابط يروح لعنوان IP مباشر: $host")
            else if (l.full.substringAfter("://", "").substringBefore('/').contains('@')) add(3, "a link hides its real address behind an @", "رابط يخفي عنوانه الحقيقي ورا علامة @")
            else {
                var fake = false
                for ((brand, legit) in brands) {
                    if (host.contains(brand) && legit.none { ownsLabel(host, it) }) { add(4, "a link says \"$brand\" but isn't $brand's site: $host", "رابط يذكر \"$brand\" وهو مو موقعهم الحقيقي: $host"); fake = true; break }
                }
                if (!fake) {
                    if (reg in shorteners) add(1, "a shortened link hides where it goes: $host", "رابط مختصر يخفي وجهته: $host")
                    else if (reg.substringAfterLast('.') in badTlds) add(1, "a link ends in a cheap, often-abused domain: $host", "رابط بنهاية نطاق رخيص يستخدمه المحتالين كثير: $host")
                }
            }
            if (lowerFull.contains(".apk")) { /* counted once below */ }
        }
        if (apkRx.containsMatchIn(t) || ls.any { it.full.lowercase().substringBefore('?').endsWith(".apk") })
            add(3, "it wants you to install an app from a link (outside the store)", "يطلب منك تنزل تطبيق من رابط (مو من المتجر)")
        if (asksForSecret(t)) add(4, "it asks you to send a code, password or card/bank details", "يطلب منك ترسل رمز أو كلمة سر أو بيانات بطاقة/حساب")
        if (prizeRx.containsMatchIn(t)) add(2, "it says you won a prize or gift", "يقول إنك ربحت جائزة أو هدية")
        if (urgencyRx.containsMatchIn(t)) add(2, "it pushes you to act fast or threatens to block something", "يستعجلك أو يهددك بإيقاف شي")
        if (moneyRx.containsMatchIn(t) && !moneyAskExcl.containsMatchIn(t)) add(2, "it asks you to pay a fee, send money, a gift card or a recharge card", "يطلب منك رسوم أو فلوس أو كارت هدية أو كارت شحن")
        if (investRx.containsMatchIn(t)) add(2, "it promises easy or guaranteed money", "يعدك بربح سهل أو مضمون")
        if (relativeRx.containsMatchIn(t)) add(2, "it looks like \"it's me on a new number\", a common trick to ask for money", "شكلها \"هذا رقمي الجديد\"، حيلة معروفة عشان يطلبون فلوس")
        if (shipRx.containsMatchIn(t) && ls.isNotEmpty()) add(2, "a delivery problem plus a link, a common trick", "مشكلة بشحنة مع رابط، حيلة معروفة")
        return ScamVerdict(score, reasons, ls.map { it.full })
    }

    /** The short answer Capri says, in the language asked for. */
    fun say(v: ScamVerdict, arabic: Boolean): String {
        val head = when (v.level) {
            2 -> if (arabic) "انتبه، هذي الرسالة شكلها نصب." else "Careful, this looks like a scam."
            1 -> if (arabic) "فيها علامات مشبوهة، لا تتسرع." else "There are suspicious signs here, take it slowly."
            else -> if (arabic) "ما لقيت علامات نصب واضحة، بس خلك حذر دايماً." else "I don't see clear scam signs, but stay careful."
        }
        if (v.reasons.isEmpty()) return head
        val why = v.reasons.take(4).joinToString("؛ ".takeIf { arabic } ?: "; ") { if (arabic) it.ar else it.en }
        val tail = when (v.level) {
            2 -> if (arabic) " لا تضغط الرابط ولا ترسل أي رمز. إذا تدعي إنها من بنك أو شركة، افتح تطبيقهم الرسمي بنفسك." else " Don't tap the link or send any code. If it claims to be your bank or a company, open their official app yourself."
            1 -> if (arabic) " تأكد من المرسل بطريقة ثانية قبل ما تسوي شي." else " Check with the sender another way before doing anything."
            else -> ""
        }
        return head + (if (arabic) " السبب: " else " Why: ") + why + "." + tail
    }

    // "is this a scam: ...", "هل هذي رسالة نصب: ..." -> the text to check ("" when they asked without giving a text)
    private val askRx = Regex("^(?:hey capri |ok capri |يا كابري )?(?:is (?:this|that|it) (?:a )?(?:scam|fake|phishing|fraud)(?: message| text| link)?|check (?:this )?(?:message|text|link) for scam|scam check|(?:check|scan) (?:this )?for (?:scam|phishing)|هل (?:هذي|هذه|هاي|هذا) (?:الرساله |رساله |الرابط |رابط )?(?:نصب|احتيال|مزورة|مزوره|مزيف[هة]?|فيشنغ)(?: ?\\?)?|(?:هذي|هذه|هاي) نصب|فحص (?:الرساله|النص|الرابط)|افحص(?:ي)? (?:الرساله|النص|الرابط)|(?:شوف|شوفي) (?:اذا|لو|هل) (?:هذي|هذه|هاي) نصب|نصب ولا لا|نصب لو لا)[\\s:,،-]*(.*)$", RegexOption.DOT_MATCHES_ALL)

    fun ask(raw: String): String? {
        val t = norm(raw).trim().trimEnd('?', '؟', '.', '!')
        val m = askRx.find(t) ?: return null
        // use the original text (not the normalised one) for the part after the question, so links keep their case
        val rest = m.groupValues[1]
        val idx = if (rest.isEmpty()) -1 else raw.lowercase().lastIndexOf(rest.take(12).lowercase())
        return if (rest.isEmpty()) "" else if (idx >= 0) raw.substring(idx).trim() else rest
    }
}
