package app.capri.mobile

// Server-sent events of an OpenAI-compatible chat stream ("data: {...}" lines). Pure parsing, unit tested.
object Sse {
    /** The text piece in one line of the stream, "" for a line with no text (role, usage, finish), null when it isn't a data line. */
    fun delta(line: String): String? {
        val t = line.trim()
        if (!t.startsWith("data:")) return null
        val d = t.removePrefix("data:").trim()
        if (d.isEmpty() || d == "[DONE]") return null
        if (!d.startsWith("{") || !d.endsWith("}")) return null
        val dm = Regex("\"delta\"\\s*:\\s*\\{").find(d) ?: return ""
        val cm = Regex("\"content\"\\s*:\\s*").find(d, dm.range.last) ?: return ""
        var i = cm.range.last + 1
        if (i >= d.length || d[i] != '"') return ""          // null, or no text in this piece
        i++
        val sb = StringBuilder()
        while (i < d.length) {
            val c = d[i]
            if (c == '"') return sb.toString()
            if (c == '\\' && i + 1 < d.length) {
                val n = d[i + 1]
                when (n) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                    'u' -> { if (i + 5 < d.length) { val h = d.substring(i + 2, i + 6).toIntOrNull(16); if (h != null) sb.append(h.toChar()); i += 4 } }
                    else -> sb.append(n)                      // \" \\ \/
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()                                  // cut off mid-string: what we have
    }

    fun done(line: String) = line.trim().removePrefix("data:").trim() == "[DONE]"

    /** What the bubble shows while the answer is still arriving: finished [[tags]] removed and a half-written one hidden. */
    fun preview(partial: String): String =
        partial.replace(Regex("\\[\\[[^\\]]*\\]\\]"), "").replace(Regex("\\[\\[[^\\]]*$"), "").replace(Regex("\\[$"), "").replace(Regex("(?s)<think>.*?(</think>|$)"), "").replace(Regex("\\s+"), " ").trim()
}
