package app.capri.mobile

/**
 * Talking to her while her hands are busy on a job (ported from capri-desktop's Asides, trimmed for the phone).
 * A plain "stop" or "how's it going?" is sorted here with no brain, so it never waits. Anything else is left alone (null).
 */
object Asides {
    enum class Kind { STOP, PROGRESS }

    private fun norm(s: String) = s.lowercase().replace(Regex("[\\u064B-\\u0652\\u0640]"), "").replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي')
        .replace(Regex("[.,!?؟،]"), " ").replace(Regex("\\s+"), " ").trim()

    private val stop = Regex(
        "^(?:(?:no|nah|nope|ok|okay|hey|wait|actually|just|alright|right) )*(?:stop(?: stop)*|stop (?:it|that|this|there|now|everything|working|playing|doing (?:it|that|this|everything)|right now|the (?:job|task|game))|cancel(?: (?:it|that|this|everything|the (?:job|task)))?|abort|forget (?:it|that|about (?:it|that))|never ?mind|nvm|drop it|leave it|that'?s enough|enough|quit it|hands off|give up)$" +
        "|^(?:(?:لا|يلا|خلاص|طيب|اوكي|ok) )*(?:وقف(?:ي)?(?: (?:كل شي|هذا|الشغل|المهمه|اللعبه|الحين|الان|هسه|خلاص))?|اوقف(?:ي)?(?: (?:كل شي|هذا|الشغل|المهمه|اللعبه))?|ستوب|كافي|يكفي|خلاص|الغي(?:ها)?|الغ(?:ي)? (?:المهمه|الشغل|كل شي)|انسي(?:ها)?|بس|لا تكمل(?:ي)?|لا تكملين|وقفي كل شي)$")

    private val progress = Regex(
        "\\bwhat(?:'?re| are| r)? (?:you|u|ya) (?:doing|up to|working on)\\b|\\bwhat'?s (?:happening|going on|the (?:status|progress))\\b" +
        "|\\bhow(?:'?s| is)? (?:it|that|this) (?:going|coming(?: along)?|looking)\\b|\\bhow far\\b|\\bany (?:progress|luck|news)\\b|^(?:status|progress|update)$" +
        "|\\b(?:are|r) (?:you|u) (?:done|finished|stuck|almost)\\b|\\bis it (?:done|finished)\\b|\\bhow long\\b" +
        "|وش (?:تسوين|تسوي|وصلتي|وصلت|صار)|شنو (?:تسوين|تسوي|وصلتي|وصلت)|شلون (?:ماشي|الشغل|وصلتي)|وين وصلتي|وين وصلت|خلصتي|خلصت|كم باقي|شكثر باقي|ماشي الحال|شو صار|شوصار")

    fun classify(said: String): Kind? {
        val t = norm(said)
        if (t.isEmpty() || t.length > 60) return null
        if (stop.containsMatchIn(t)) return Kind.STOP
        if (progress.containsMatchIn(t)) return Kind.PROGRESS
        return null
    }

    /** One short spoken line from the facts only: the last step note and how long it has been. Never says it is finished. */
    fun status(note: String, sinceMs: Long, ar: Boolean, roll: Int): String {
        val mins = (sinceMs / 60_000L).toInt()
        val n = note.replace(Regex("\\[[A-Za-z\\u0600-\\u06FF ]+\\]\\s*"), "").trim().take(80)
        val t = StopwatchTalk.words(sinceMs.coerceAtLeast(0), ar)
        return if (ar) {
            when {
                n.isEmpty() && mins < 1 -> "توني بديت."
                n.isEmpty() -> "للحين أشتغل، صار لي $t."
                mins < 1 -> "توني بديت. $n"
                else -> listOf("$n. صار لي $t.", "للحين عليها: $n ($t).")[Math.floorMod(roll, 2)]
            }
        } else {
            when {
                n.isEmpty() && mins < 1 -> "Just getting going."
                n.isEmpty() -> "Still at it, $t in."
                mins < 1 -> "Only just started. $n"
                else -> listOf("$n. $t in.", "Still on it: $n ($t).")[Math.floorMod(roll, 2)]
            }
        }
    }
}
