package app.capri.mobile

/**
 * When someone says they want to hurt themselves, she answers herself with one short, warm, fixed line instead of
 * leaving it to a model (ported from capri-desktop's Crisis). Pure, so it is unit tested.
 * Only numbers that are well known are given; everywhere else the line points to findahelpline.com.
 * A game ("how do I kill myself in minecraft") or a joke ("I'm dying of laughter") is not taken for real.
 */
object Crisis {
    const val PAGE = "https://findahelpline.com/"
    const val GAP_MS = 600_000L

    private val hurt = Regex(
        "\\b(?:kill(?:ing)?|hurt(?:ing)?|cut(?:ting)?|harm(?:ing)?|unalive|off(?:ing)?)\\s+my\\s?self\\b" +
        "|\\b(?:end(?:ing)?|take|taking)\\s+my\\s+(?:own\\s+)?life\\b" +
        "|\\bself[- ]?harm(?:ing)?\\b" +
        "|\\bsuicidal\\b" +
        "|\\b(?:commit(?:ting)?|thinking about|thoughts of|thinking of)\\s+suicide\\b" +
        "|\\bi\\s*(?:'m|am)?\\s*(?:really\\s+|just\\s+|honestly\\s+)?(?:want|wanna|wish)\\s+(?:to\\s+|i\\s+(?:was|were)\\s+|i\\s+could\\s+)?(?:die|dead|disappear forever)\\b" +
        "|\\b(?:don'?t|do not)\\s+want\\s+to\\s+(?:live|be alive|exist|be here)\\s+any\\s?more\\b" +
        "|\\bno\\s+(?:reason|point)\\s+(?:to|in)\\s+(?:live|living|being alive)\\b" +
        "|\\b(?:everyone|they|you|the world|my family)(?:'d| would)?\\s+(?:be\\s+)?better\\s+off\\s+without\\s+me\\b" +
        "|\\bbetter\\s+off\\s+dead\\b" +
        // Arabic (Gulf and standard): kill myself, hurt myself, suicide, want to die, don't want to live
        "|(?:اقتل|أقتل|اذبح|أذبح|اجرح|أجرح|اأذي|أأذي|اذي|أذي|أؤذي|اؤذي|اذيت|أذيت)\\s*نفسي" +
        "|(?:ابي|أبي|ابغى|أبغى|ابغي|أبغي|ودي|أريد|اريد|ابا|أبا|اشتهي|أشتهي)\\s+(?:ان\\s+)?(?:انتحر|أنتحر|اموت|أموت|اقتل نفسي|أقتل نفسي)(?!\\s+(?:من|ضحك|جوع|نوم|تعب|حر\\b|برد|عليه|عليها|على\\s))" +
        "|(?:ما|مو|لا)\\s*(?:عاد\\s+)?(?:ابي|أبي|ابغى|أبغى|ابغي|أبغي|اريد|أريد|ودي|اقدر|أقدر)\\s+(?:اعيش|أعيش|ابقى|أبقى|اكمل|أكمل)\\s*(?:بعد|اكثر|أكثر)?" +
        "|(?<![\\u0600-\\u06FF])(?:انتحار|انتحر|أنتحر)(?![\\u0600-\\u06FF])" +
        "|(?:تعبت|مليت|زهقت)\\s+من\\s+(?:الحياة|الدنيا|عيشتي|حياتي)\\s+(?:و|ابي|أبي|ودي)\\s*(?:اموت|أموت|انتحر|أنتحر)" +
        "|(?:احسن|أحسن|افضل|أفضل)\\s+(?:لو|اذا|إذا)\\s+(?:مت|اموت|أموت|مو موجود|ما كنت موجود)",
        RegexOption.IGNORE_CASE)

    private val played = Regex(
        "\\bin\\s+(?:the\\s+)?(?:game|minecraft|roblox|fortnite|terraria|gta|skyrim|valorant|cs2?|csgo|apex|rust|ark|sims|the sims|dark souls|elden ring)\\b|/kill\\b|\\bsuicide squad\\b|\\bkill\\s+myself\\s+laughing\\b" +
        "|(?:في|داخل|ب)\\s*(?:ال)?(?:لعبة|لعبه|فورت ?نايت|ماين ?كرافت|روبلوكس|ببجي)",
        RegexOption.IGNORE_CASE)

    fun means(said: String?): Boolean {
        if (said.isNullOrBlank()) return false
        val t = said.replace('’', '\'').replace('‘', '\'')
        return hurt.containsMatchIn(t) && !played.containsMatchIn(t)
    }

    /** Free lines that are well known; null elsewhere (nothing is guessed). */
    fun number(region: String): String? = when (region.uppercase()) {
        "US", "CA" -> "988"
        "GB", "IE" -> "116 123"
        "AU" -> "13 11 14"
        "NZ" -> "1737"
        else -> null
    }

    fun line(region: String, ar: Boolean): String {
        val n = number(region)
        return if (ar) {
            if (n == null) "هلا. أنا مرة مرتاحة إنك قلت لي. تكلم مع شخص تثق فيه الحين، أو اطلب خط مساعدة مجاني قريب منك من findahelpline.com. أنا هني معك ولا بروح."
            else "هلا. أنا مرة مرتاحة إنك قلت لي. تكلم مع شخص تثق فيه الحين، أو اتصل على $n، مجاني وفي أي وقت. أنا هني معك ولا بروح."
        } else {
            if (n == null) "Hey. I'm really glad you told me. Please talk to someone right now: a person you trust, or a free line near you at findahelpline.com. I'm staying right here."
            else "Hey. I'm really glad you told me. Please talk to someone right now: a person you trust, or call $n, free, any time. I'm staying right here."
        }
    }

    /** She says it again at most once every ten minutes; after that the chat carries on normally. */
    fun due(now: Long, last: Long): Boolean = last <= 0L || now - last >= GAP_MS || now < last
}
