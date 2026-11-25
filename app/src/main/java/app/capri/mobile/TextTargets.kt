package app.capri.mobile

// What to do with the words a game's screen shows. The game surface has no accessibility buttons, so a model that has to guess a grid
// cell for "PLAY" is often a cell off. OCR gives the real position of every word, and this file decides which word is a safe button
// to press without asking the model at all (Continue, Play again, Skip, Ready...), finds a button the model asked for by name
// ({"t":"text","text":"READY"}), and writes the word list the model reads. Pure logic (no Android), so it is unit tested.
// All positions are fractions 0..1 of the screenshot.
class Word(val text: String, val x: Float, val y: Float, val w: Float, val h: Float) {
    val cx get() = x + w / 2
    val cy get() = y + h / 2
}

object TextTargets {
    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    // Never press anything that looks like money, a store, a subscription or an ad you must watch.
    private val risky = Regex("\\b(buy|purchase|pay|subscribe|subscription|store|shop|top ?up|gift|vip|premium|offer|bundle|v ?bucks|vbucks|robux|gems?|coins? pack|watch|ad|ads|video|sign ?in|log ?in|login|password|install|update|download|delete|reset|uninstall)\\b|[$€£]|\\d+\\.\\d{2}")

    // The buttons that are safe to press by themselves, most useful first. "leave match" is not here: the model decides when a game is over.
    private val menuButtons = listOf(
        "tap to continue", "press any button", "touch to continue", "tap anywhere to continue", "continue", "play again", "try again", "next",
        "ok", "okay", "got it", "skip", "claim", "collect", "resume", "ready", "start", "play", "close", "retry", "confirm"
    )

    /** Words of one line of text, joined: OCR gives words or lines, either works. */
    private fun lines(words: List<Word>): List<Pair<String, List<Word>>> {
        val sorted = words.sortedWith(compareBy({ Math.round(it.cy * 40) }, { it.x }))
        val out = ArrayList<Pair<String, List<Word>>>()
        var cur = ArrayList<Word>(); var row = Int.MIN_VALUE
        for (w in sorted) {
            val r = Math.round(w.cy * 40)
            if (cur.isNotEmpty() && Math.abs(r - row) > 1) { out.add(cur.joinToString(" ") { it.text } to cur); cur = ArrayList() }
            cur.add(w); row = r
        }
        if (cur.isNotEmpty()) out.add(cur.joinToString(" ") { it.text } to cur)
        return out
    }

    private fun box(ws: List<Word>): Word {
        val x1 = ws.minOf { it.x }; val y1 = ws.minOf { it.y }; val x2 = ws.maxOf { it.x + it.w }; val y2 = ws.maxOf { it.y + it.h }
        return Word(ws.joinToString(" ") { it.text }, x1, y1, x2 - x1, y2 - y1)
    }

    private fun close(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 5 || Math.abs(a.length - b.length) > 1) return false
        // one wrong, missing or extra letter ("C0NTINUE", "PLAY AGAN")
        var i = 0; var j = 0; var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            if (a.length > b.length) i++ else if (a.length < b.length) j++ else { i++; j++ }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }

    /** The spot of a label the model named, e.g. "READY" or "play again". Looks at single words and at runs of words on one line. */
    fun find(words: List<Word>, wanted: String): Word? {
        val w = norm(wanted)
        if (w.isEmpty()) return null
        val n = w.split(' ').size
        var best: Word? = null
        for ((_, ws) in lines(words)) {
            val toks = ws.map { norm(it.text) }
            for (i in 0..(ws.size - n).coerceAtLeast(-1)) {
                if (i + n > ws.size) break
                val joined = toks.subList(i, i + n).joinToString(" ").trim()
                if (close(joined, w)) {
                    val b = box(ws.subList(i, i + n))
                    // prefer the largest (buttons are bigger than body text that happens to say the same word)
                    if (best == null || b.w * b.h > best.w * best.h) best = b
                }
            }
        }
        return best
    }

    /** Is there anything on this line that says money, a store, an ad or a login? */
    fun lineIsRisky(words: List<Word>, at: Word): Boolean {
        for ((text, ws) in lines(words)) if (ws.any { Math.abs(it.cy - at.cy) < 0.03f && it.x < at.x + at.w + 0.25f && it.x + it.w > at.x - 0.25f }) {
            if (risky.containsMatchIn(norm(text)) || Regex("[$€£]|\\d+\\.\\d{2}").containsMatchIn(text)) return true
        }
        return false
    }

    /** The one safe menu button worth pressing right now, or null. recent = labels already pressed lately (a label pressed 3 times in a row is not working). */
    fun autoTap(words: List<Word>, recent: List<String>): Word? {
        if (words.isEmpty()) return null
        // the screen must not look like a store, a login or an ad
        val all = norm(words.joinToString(" ") { it.text })
        if (Regex("\\b(buy now|purchase|subscribe|sign in|log in|password|watch ad|watch video|v ?bucks|item shop|install)\\b").containsMatchIn(all)) return null
        for (label in menuButtons) {
            if (recent.count { it == label } >= 3) continue
            val hit = find(words, label) ?: continue
            if (lineIsRisky(words, hit)) continue
            // a button sits in the lower 85% of the screen or is big; a one-letter "ok" in the status bar is not one
            if (hit.cy < 0.08f) continue
            return Word(label, hit.x, hit.y, hit.w, hit.h)
        }
        return null
    }

    /** What the model reads: the biggest and most useful words with their pixel centres, top to bottom. */
    fun describe(words: List<Word>, pxW: Int, pxH: Int, max: Int = 28): String {
        if (words.isEmpty()) return "(none read)"
        return words.filter { it.text.isNotBlank() && it.text.trim().length >= 2 }
            .sortedByDescending { it.w * it.h }.take(max).sortedWith(compareBy({ it.cy }, { it.x }))
            .joinToString("; ") { "\"" + it.text.trim().take(24) + "\" @" + Math.round(it.cx * pxW) + "," + Math.round(it.cy * pxH) }
    }

    /** Share of the frame that is storm purple, 0..1 (Fortnite's storm, also poison or darkness effects). Used as a hint, not a rule. */
    fun purpleShare(f: Reflex.Frame): Float {
        if (f.w * f.h == 0) return 0f
        var n = 0
        for (p in f.px) {
            val r = (p shr 16) and 255; val g = (p shr 8) and 255; val b = p and 255
            val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
            // blue-violet to magenta, clearly coloured, not dark and not washed out; green is the lowest channel
            if (mx > 70 && mx - mn > 55 && g == mn && b * 10 >= r * 7 && b > 90) n++
        }
        return n.toFloat() / (f.w * f.h)
    }
}
