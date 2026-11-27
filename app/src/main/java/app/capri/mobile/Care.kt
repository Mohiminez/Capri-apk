package app.capri.mobile

enum class CareKind { WATER, STRETCH, EYES, LATE, MEAL, GAME, BORED }

/**
 * Her check-ins, ported from capri-desktop's Care: water, stretch, rest your eyes, a bedtime nudge, meals, a game
 * break and being bored. Pure: the caller hands in the clock and what is happening, and gets back what to say (or null).
 * Timers start when you start using the phone, and start over after you were away, so she never nags the moment you pick it up.
 */
class Care {
    class Cfg(val on: (CareKind) -> Boolean, val mins: (CareKind) -> Int)

    private val next = LongArray(CareKind.values().size)
    private val pending = BooleanArray(CareKind.values().size)
    private var started = false
    private var startedAt = 0L
    private var night = Int.MIN_VALUE
    private var meal = Int.MIN_VALUE
    private var game: String? = null
    private var turn = 0

    companion object {
        const val AWAY_MS = 600_000L
        fun defaultMins(k: CareKind) = when (k) {
            CareKind.WATER -> 90; CareKind.STRETCH -> 60; CareKind.EYES -> 45
            CareKind.LATE -> 1      // bedtime hour: from this hour until 6 am
            CareKind.MEAL -> 240    // gap between meal windows starting at noon
            CareKind.GAME -> 60; CareKind.BORED -> 45
        }
    }

    private fun idx(k: CareKind) = k.ordinal

    /**
     * @param awayMs how long the screen has been off or nothing has happened (0 while in use)
     * @param sinceTalkMs how long since they last talked to her
     * @param day a number that changes at midnight (days since 1970 in local time)
     * @param free false while she is busy or hidden: nothing is said, and timers keep their due state
     */
    fun check(now: Long, hour: Int, minute: Int, day: Int, awayMs: Long, sinceTalkMs: Long, playing: String?, cfg: Cfg, free: Boolean): CareKind? {
        if (awayMs > AWAY_MS) { started = false; pending.fill(false); return null }
        if (!started) {
            started = true; startedAt = now; game = playing
            for (k in CareKind.values()) next[idx(k)] = now + cfg.mins(k) * 60_000L
            return null
        }
        for (k in listOf(CareKind.WATER, CareKind.STRETCH, CareKind.EYES)) {
            if (cfg.on(k) && now >= next[idx(k)]) pending[idx(k)] = true
        }
        if (game != playing) { game = playing; next[idx(CareKind.GAME)] = now + cfg.mins(CareKind.GAME) * 60_000L; pending[idx(CareKind.GAME)] = false }
        if (cfg.on(CareKind.GAME) && playing != null && now >= next[idx(CareKind.GAME)]) pending[idx(CareKind.GAME)] = true
        if (cfg.on(CareKind.BORED) && sinceTalkMs >= cfg.mins(CareKind.BORED) * 60_000L && now >= next[idx(CareKind.BORED)]) pending[idx(CareKind.BORED)] = true

        // bedtime: once per evening, from the chosen hour until 6 am
        val bed = cfg.mins(CareKind.LATE).coerceIn(0, 23)
        val late = if (bed < 12) hour in bed until 6 else (hour >= bed || hour < 6)
        val evening = if (hour >= 12) day else day - 1
        if (cfg.on(CareKind.LATE) && late && night != evening) { night = evening; pending[idx(CareKind.LATE)] = true }

        // meals: windows at noon and then every N minutes; she speaks once per window, within its first hour, never late in the evening
        val gap = cfg.mins(CareKind.MEAL).coerceAtLeast(60)
        val sinceNoon = hour * 60 + minute - 720
        if (cfg.on(CareKind.MEAL) && sinceNoon >= 0 && hour < 21 && now - startedAt >= 30 * 60_000L) {
            val w = sinceNoon / gap
            if (sinceNoon - w * gap < 60 && meal != day * 100 + w) { meal = day * 100 + w; pending[idx(CareKind.MEAL)] = true }
        }

        if (!free) return null
        var said: CareKind? = null
        val n = pending.size
        for (c in 0 until n) {
            val i = (turn + c) % n
            if (pending[i]) { said = CareKind.values()[i]; turn = (i + 1) % n; break }
        }
        if (said != null) {
            // everything that was due is re-armed, so one nudge at a time and none piles up
            for (k in CareKind.values()) if (pending[idx(k)]) next[idx(k)] = now + cfg.mins(k) * 60_000L
            pending.fill(false)
        }
        return said
    }

    fun reset() { started = false; pending.fill(false) }
}

/** Is this foreground app a game? Known publishers and titles, by package name. */
object GamePkgs {
    private val parts = listOf("com.epicgames", "com.tencent.ig", "com.pubg", "com.mojang", "com.roblox", "com.dts.freefire", "com.supercell",
        "com.miHoYo", "com.mihoyo", "com.mobile.legends", "com.activision", "com.kiloo", "com.imangi", "com.king.", "com.innersloth", "com.moonton",
        "com.garena", "com.netease", "com.ea.game", "com.gameloft", "com.rovio", "com.halfbrick", "com.ludo", "com.zynga", "com.playrix", "com.unity3d.player")
    fun isGame(pkg: String): Boolean { val p = pkg.lowercase(); return p.isNotEmpty() && parts.any { p.contains(it.lowercase()) } }
}

/** Her short lines for each check-in, in both languages. */
object CareLines {
    private val en = mapOf(
        CareKind.WATER to listOf("Have a sip of water. I'll keep your spot.", "Water break. Even you need a refill."),
        CareKind.STRETCH to listOf("Unfold yourself a little. Those legs still work.", "Tiny stretch break. I'll be right here."),
        CareKind.EYES to listOf("Give your eyes 20 seconds looking far away from the screen.", "Look at something far away for 20 seconds. Yes, even farther than me."),
        CareKind.LATE to listOf("It's late. Your bed misses you.", "It's very late. Sleep exists, you know."),
        CareKind.MEAL to listOf("Been at it a while. Go grab something to eat.", "Food break. Can't run that brain on empty."),
        CareKind.GAME to listOf("Long session. Take a little break when you've got a good spot.", "You've been at that game a while. Rest those thumbs when you can."),
        CareKind.BORED to listOf("So boreddd.", "I've counted your home screen icons twice now.", "Tiny gremlin. Big boredom."))
    private val ar = mapOf(
        CareKind.WATER to listOf("اشرب شوية موية، أنا أحفظ لك مكانك.", "وقت الموية. حتى انت تحتاج تعبّي."),
        CareKind.STRETCH to listOf("فك نفسك شوي، رجولك للحين تشتغل.", "استراحة تمطّط صغيرة. أنا هني."),
        CareKind.EYES to listOf("طالع شي بعيد عشرين ثانية وريّح عيونك من الشاشة.", "ريّح عيونك، شوف شي بعيد حتى أبعد مني."),
        CareKind.LATE to listOf("الوقت متأخر، سريرك مشتاق لك.", "الوقت متأخر مرة. النوم موجود تدري."),
        CareKind.MEAL to listOf("صار لك مدة، قم كل شي.", "وقت أكل. المخ ما يشتغل على فاضي."),
        CareKind.GAME to listOf("جلسة طويلة. خذ استراحة لما توصل لمكان مناسب.", "صار لك مدة تلعب، ريّح إبهامك شوي."),
        CareKind.BORED to listOf("زهقانة مرة.", "عديت أيقونات شاشتك مرتين.", "وحش صغير، ملل كبير."))

    fun pick(k: CareKind, arabic: Boolean, roll: Int): String {
        val l = (if (arabic) ar else en)[k] ?: return ""
        return l[Math.floorMod(roll, l.size)]
    }

    fun label(k: CareKind, arabic: Boolean): String = when (k) {
        CareKind.WATER -> if (arabic) "الموية" else "Water"
        CareKind.STRETCH -> if (arabic) "التمطّط" else "Stretch"
        CareKind.EYES -> if (arabic) "راحة العيون" else "Rest your eyes"
        CareKind.LATE -> if (arabic) "النوم" else "Bedtime"
        CareKind.MEAL -> if (arabic) "الأكل" else "Meals"
        CareKind.GAME -> if (arabic) "استراحة اللعب" else "Game break"
        CareKind.BORED -> if (arabic) "الملل" else "Bored"
    }
}
