package app.capri.mobile

// Modes: one tap changes a whole bundle of settings (how much she talks, wanders, pops in, how light she is on the battery).
// A mode only writes the keys listed in it; everything else you set yourself stays as it is.
class Mode(
    val id: String, val icon: String,
    val en: String, val ar: String,
    val descEn: String, val descAr: String,
    val set: Map<String, Any>,
    val sayEn: String, val sayAr: String
)

object Modes {
    // the keys every mode controls: Normal puts them all back to the app's defaults
    private val base: Map<String, Any> = mapOf(
        "quiet" to false, "gameMode" to false, "voice" to true, "wanderPace" to 1, "popMin" to 30, "sleepMin" to 3,
        "lowPower" to false, "chimes" to false, "dance" to true, "events" to true, "mischief" to false,
        "perchBias" to 0, "wanderMix" to 0, "careWater" to false, "careStretch" to false, "checkin" to false, "convo" to false, "intro" to true, "miniPlayer" to true
    )

    private fun mode(id: String, icon: String, en: String, ar: String, dEn: String, dAr: String, sayEn: String, sayAr: String, vararg over: Pair<String, Any>) =
        Mode(id, icon, en, ar, dEn, dAr, base + over, sayEn, sayAr)

    val all: List<Mode> = listOf(
        mode("normal", "◉", "NORMAL", "عادي", "She talks, wanders, reacts", "تتكلم وتتمشى وتتفاعل", "Back to normal!", "رجعت عادي!"),
        mode("quiet", "◌", "QUIET", "هادئ", "She only types, never speaks aloud, stays calm", "تكتب بس وما تتكلم بصوت، وتبقى هادية",
            "Quiet mode. I'll only type.", "وضع هادئ. بكتب لك بس.", "quiet" to true, "wanderPace" to 0, "popMin" to 90, "chimes" to false, "mischief" to false, "events" to false, "dance" to false),
        mode("game", "▶", "GAME", "لعب", "Stays out of the way, light on the phone, comments while a game is open", "تبعد عن طريقك وخفيفة على الجوال وتعلّق وقت اللعب",
            "Game mode. I'll stay out of your way.", "وضع اللعب. بخلي عنك الطريق.", "gameMode" to true, "quiet" to true, "wanderPace" to 0, "popMin" to 120, "lowPower" to true, "miniPlayer" to false, "intro" to false, "events" to false, "dance" to false),
        mode("study", "≣", "STUDY", "دراسة", "Silent and still, reminds you to drink water and stretch", "ساكتة وثابتة، وتذكّرك بالموية والحركة",
            "Study mode. Focus, I'll remind you to drink and stretch.", "وضع الدراسة. ركّز، وبذكّرك بالموية والحركة.", "quiet" to true, "wanderPace" to 0, "popMin" to 120, "careWater" to true, "careStretch" to true, "events" to false, "dance" to false, "sleepMin" to 2),
        mode("battery", "▮", "BATTERY SAVER", "توفير بطارية", "Fewer frames, no glow or floating, rare pop-ins", "إطارات أقل وبدون هالة وطفو، وتطل نادراً",
            "Battery saver. I'll go easy on the phone.", "توفير بطارية. بخفف على الجوال.", "lowPower" to true, "wanderPace" to 0, "popMin" to 120, "intro" to false, "dance" to false, "chimes" to false, "checkin" to false, "sleepMin" to 1),
        mode("companion", "♡", "COMPANION", "رفيقة", "Hands-free chat: wake word and conversation mode, checks on you, wanders around", "محادثة بدون يدين: كلمة النداء ووضع المحادثة، وتطمّن عليك وتتمشى",
            "Companion mode. Just talk to me.", "وضع الرفيقة. بس حچي وياي.", "convo" to true, "wakeWord" to true, "wanderPace" to 2, "popMin" to 15, "careWater" to true, "checkin" to true),
        mode("party", "✦", "PARTY", "حفلة", "Max energy: wanders a lot, pops in often, mischief and blips", "أقصى طاقة: تتمشى هواي وتطل كثير وتشاغب",
            "Party time!", "وقت الحفلة!", "wanderPace" to 3, "popMin" to 10, "mischief" to true, "chimes" to true, "dance" to true, "sleepMin" to 0, "wanderMix" to 2),
        mode("chill", "~", "LEDGE", "جلسة", "She sits on the bottom edge, lies back, naps and sways to your music", "تجلس على حافة الشاشة وتستلقي وتنام وتتمايل مع موسيقاك",
            "Ledge mode. I'll sit this one out.", "وضع الجلسة. بجلس وأريّح.", "perchBias" to 1, "wanderPace" to 1, "popMin" to 60, "sleepMin" to 6, "dance" to true),
        mode("night", "☾", "NIGHT", "ليل", "Dim and quiet: she sits on the ledge, naps early, no chimes, no pop-ins", "هادية وخفيفة: تجلس على الحافة وتنام بدري وبدون أصوات وإطلالات",
            "Night mode. Sleep soon, choom.", "وضع الليل. نام بدري يا چوم.", "quiet" to true, "perchBias" to 1, "wanderPace" to 1, "popMin" to 120, "sleepMin" to 1, "chimes" to false, "intro" to false, "dance" to false, "events" to false, "lowPower" to true)
    )

    fun find(id: String): Mode? = all.firstOrNull { it.id == id.lowercase().trim() }

    /** Which mode the saved settings match best (for the highlight when the app opens). */
    fun current(prefs: Prefs): Mode {
        // the saved mode counts only while the switches it set are still as it set them (you may have toggled Quiet by hand since)
        prefs.getS("mode", "").let { id -> find(id)?.let { m ->
            if (listOf("quiet", "gameMode", "lowPower").all { k -> (m.set[k] as? Boolean) == prefs.getB(k, false) }) return m } }
        return all[if (prefs.getB("quiet", false)) 1 else if (prefs.getB("gameMode", false)) 2 else 0]
    }

    /** The mode after [m] in the list, for the "next mode" menu item. */
    fun next(m: Mode): Mode = all[(all.indexOfFirst { it.id == m.id } + 1) % all.size]

    fun apply(prefs: Prefs, m: Mode) {
        for ((k, v) in m.set) when (v) { is Boolean -> prefs.putB(k, v); is Int -> prefs.putI(k, v) }
        prefs.putS("mode", m.id)
    }

    // "study mode", "وضع الدراسة", "game mode on" ... -> mode id
    private val names = mapOf(
        "normal" to listOf("normal", "default", "عادي", "طبيعي"), "quiet" to listOf("quiet", "silent", "هادئ", "هادي", "صامت"),
        "game" to listOf("game", "gaming", "لعب", "العاب", "قيم"), "study" to listOf("study", "focus", "دراسه", "دراسة", "مذاكره", "مذاكرة", "تركيز"),
        "battery" to listOf("battery", "battery saver", "power saver", "بطاريه", "بطارية", "توفير"), "companion" to listOf("companion", "رفيقه", "رفيقة", "صديقه", "صديقة"),
        "party" to listOf("party", "حفله", "حفلة", "بارتي"),
        "chill" to listOf("ledge", "chill", "perch", "جلسه", "جلسة", "حافه", "حافة", "استرخاء"), "night" to listOf("night", "bedtime", "ليل", "الليل", "نوم")
    )

    fun fromPhrase(raw: String): String? {
        val t = raw.lowercase().trim().replace(Regex("[^a-z0-9 ؀-ۿ]"), " ").replace(Regex("\\s+"), " ").replace('أ', 'ا').replace('إ', 'ا')
        val m = Regex("^(?:switch to |go to |turn on |enable |start |put me in |فعلي |شغلي |شغل |خلي |حطي |حط |غيري ل|روحي ل)?(.+?)(?: mode)?(?: on| please)?$").find(t) ?: return null
        val rest = m.groupValues[1].trim()
        if (!(raw.lowercase().contains("mode") || rest.startsWith("وضع") || t.contains("وضع"))) return null
        val core = rest.removePrefix("وضع").removePrefix("ال").trim().removePrefix("ال")
        val tokens = core.split(" ").map { it.removePrefix("ال") }
        for ((id, words) in names) if (words.any { w -> core == w || tokens.contains(w.removePrefix("ال")) }) return id
        return null
    }
}


// Days in a row you came to see her. Pure maths, so it can be tested: [last] and [today] are day numbers (epoch days).
object Streak {
    fun next(last: Long, today: Long, streak: Int): Int = when {
        last <= 0L -> 1
        today == last -> maxOf(1, streak)
        today == last + 1 -> streak + 1
        else -> 1
    }

    fun touch(prefs: Prefs, nowMs: Long = System.currentTimeMillis()): Int {
        val today = (nowMs + java.util.TimeZone.getDefault().getOffset(nowMs)) / 86_400_000L
        val n = next(prefs.getL("streakLast", 0L), today, prefs.getI("streak", 0))
        prefs.putL("streakLast", today); prefs.putI("streak", n)
        return n
    }

    /** What she says when you open the app: time of day, and a nudge on long streaks. */
    fun greeting(hour: Int, streak: Int, ar: Boolean): String {
        val base = when (hour) {
            in 5..11 -> if (ar) "صباح الخير" else "Morning, choom"
            in 12..16 -> if (ar) "هلا والله" else "Hey, choom"
            in 17..21 -> if (ar) "مساء الخير" else "Evening, choom"
            else -> if (ar) "للحين صاحي؟" else "Still awake, choom?"
        }
        val tail = when {
            streak >= 30 -> if (ar) " شهر كامل وانت وياي." else " A whole month. Respect."
            streak >= 7 -> if (ar) " أسبوع ورا بعض." else " A week straight."
            streak >= 2 -> if (ar) " يوم $streak ورا بعض." else " Day $streak."
            else -> ""
        }
        return base + tail
    }
}
