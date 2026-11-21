package app.capri.mobile

// Custom commands you write yourself, one per line:   phrase => what she does or says
// A {x} in the phrase catches the rest of what you said, and {x} in the answer puts it back. The answer may hold
// action tags, so  "good morning => Morning! [[weather:Baghdad]]"  or  "work mode => On it. [[open:Slack]] [[music:lofi]]"  just works.
object Skills {
    class Skill(val re: Regex, val answer: String)

    fun parse(text: String): List<Skill> {
        val out = ArrayList<Skill>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val i = line.indexOf("=>")
            if (i <= 0) continue
            val phrase = norm(line.substring(0, i))
            val answer = line.substring(i + 2).trim()
            if (phrase.isEmpty() || answer.isEmpty()) continue
            val pattern = StringBuilder("^")
            var last = 0
            val ph = Regex("\\{x\\}")
            for (m in ph.findAll(phrase)) { pattern.append(Regex.escape(phrase.substring(last, m.range.first))).append("(.+)"); last = m.range.last + 1 }
            pattern.append(Regex.escape(phrase.substring(last))).append("$")
            try { out.add(Skill(Regex(pattern.toString()), answer)) } catch (e: Exception) { }
        }
        return out
    }

    private fun norm(s: String) = s.trim().lowercase().replace(Regex("[.!?؟،,]+$"), "").replace(Regex("\\s+"), " ")

    // The answer to run for what you said, or null when none of your commands fit.
    fun match(skills: String, said: String): String? {
        if (skills.isBlank()) return null
        val t = norm(said)
        for (s in parse(skills)) {
            val m = s.re.find(t) ?: continue
            val x = if (m.groupValues.size > 1) m.groupValues[1].trim().replace("]", ")").replace("|", "/") else ""
            return s.answer.replace("{x}", x)
        }
        return null
    }
}
