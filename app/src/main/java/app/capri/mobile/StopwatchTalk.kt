package app.capri.mobile

/** One stopwatch with no laps, ported from capri-desktop. Pure: the caller keeps the start time (0 = not running). */
object StopwatchTalk {
    class Out(val say: String, val startedAt: Long)

    /** "start the stopwatch", "stop the stopwatch", "how long has it been" (only means this while it runs), Arabic too. Returns start/stop/check or null. */
    fun request(raw: String, running: Boolean): String? {
        val t = raw.lowercase().replace(Regex("[\\u064B-\\u0652\\u0640]"), "").replace('أ', 'ا').replace('إ', 'ا').replace('ة', 'ه')
            .replace(Regex("[.,!?؟،]"), " ").replace(Regex("\\s+"), " ").trim()
        val word = Regex("\\bstop ?watch\\b|ستوب ?واتش|ساعه ?(?:ال)?توقيت|كرونو|كرونومتر")
        if (word.containsMatchIn(t)) {
            return when {
                Regex("\\b(?:start|begin|run)\\b|ابدا|شغل|ابدي|دور").containsMatchIn(t) -> "start"
                Regex("\\b(?:stop|end|finish)\\b|وقف|اوقف|انهي|خلص").containsMatchIn(t) -> "stop"
                Regex("how long|what|check|وين وصل|كم|شكثر|وصل").containsMatchIn(t) -> "check"
                else -> null
            }
        }
        if (running && Regex("^(?:how long has it been|how long was that|how much time has passed|كم مر|كم صار|شكثر صار|كم مضى)$").matches(t)) return "check"
        return null
    }

    fun words(ms: Long, ar: Boolean): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600; val m = total % 3600 / 60; val s = total % 60
        val parts = ArrayList<String>()
        if (ar) {
            if (h > 0) parts.add(if (h == 1L) "ساعة" else if (h == 2L) "ساعتين" else "$h ساعات")
            if (m > 0) parts.add(if (m == 1L) "دقيقة" else if (m == 2L) "دقيقتين" else if (m <= 10) "$m دقايق" else "$m دقيقة")
            if (s > 0 || parts.isEmpty()) parts.add(if (s == 1L) "ثانية" else if (s == 2L) "ثانيتين" else if (s in 3..10) "$s ثواني" else "$s ثانية")
            return parts.joinToString(" و")
        }
        if (h > 0) parts.add("$h hour" + if (h == 1L) "" else "s")
        if (m > 0) parts.add("$m minute" + if (m == 1L) "" else "s")
        if (s > 0 || parts.isEmpty()) parts.add("$s second" + if (s == 1L) "" else "s")
        return parts.joinToString(" ")
    }

    fun run(action: String, startedAt: Long, now: Long, ar: Boolean): Out {
        val running = startedAt > 0
        val t = if (running) words(now - startedAt, ar) else ""
        return when (action) {
            "start" -> if (running) Out(if (ar) "ساعة التوقيت شغالة من قبل: $t." else "Your stopwatch is already running: $t.", startedAt)
                       else Out(if (ar) "بدأت ساعة التوقيت." else "Started your stopwatch.", now)
            "stop" -> if (!running) Out(if (ar) "ساعة التوقيت مو شغالة." else "Your stopwatch isn't running.", 0L)
                      else Out(if (ar) "وقفت ساعة التوقيت على $t." else "Stopped your stopwatch at $t.", 0L)
            else -> if (!running) Out(if (ar) "ساعة التوقيت مو شغالة." else "Your stopwatch isn't running.", 0L)
                    else Out(if (ar) "ساعة التوقيت على $t." else "Your stopwatch is at $t.", startedAt)
        }
    }
}
