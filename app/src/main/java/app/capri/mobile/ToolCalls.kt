package app.capri.mobile

// Native tool calls (experimental, off by default): instead of asking the model to write [[open:spotify]] inside its words, the request carries
// one function, act(name, arg), and the provider's own tool-calling returns it as structured data. Each call is turned back into the same
// [[name:arg]] tag, so everything after this (permissions, confirmations, the 6-per-reply cap) is exactly the same code. Pure, unit tested.
object ToolCalls {
    // What a model may ask for. Left out on purpose: security, set, chaos, dictate (settings and system-level things are never a model's to change).
    val names = listOf("open", "url", "music", "do", "look", "weather", "wiki", "web", "read", "brief", "playing", "remember", "timer", "alarm", "remind",
        "info", "search", "nav", "copy", "media", "volume", "flash", "browse", "hands", "point", "playgame", "scrollforme", "watch", "mode", "chess",
        "clones", "emote", "focus", "health", "wol", "room", "guide", "game")

    /** The "tools" array of the request, as JSON text. */
    fun schema(): String {
        val enum = names.joinToString(",") { "\"$it\"" }
        return "[{\"type\":\"function\",\"function\":{\"name\":\"act\",\"description\":\"Do something on the user's phone or look something up. " +
            "Use it only when the user asked for it. Also write one short sentence of normal text for the user.\"," +
            "\"parameters\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"enum\":[$enum]},\"arg\":{\"type\":\"string\",\"description\":\"The argument, e.g. an app name, a song, a place. Empty if none.\"}},\"required\":[\"name\"]}}}]"
    }

    private fun str(json: String, key: String): String? {
        val m = Regex("\"" + key + "\"\\s*:\\s*\"").find(json) ?: return null
        var i = m.range.last + 1
        val sb = StringBuilder()
        while (i < json.length) {
            val c = json[i]
            if (c == '"') return sb.toString()
            if (c == '\\' && i + 1 < json.length) {
                when (val n = json[i + 1]) {
                    'n' -> sb.append(' '); 't' -> sb.append(' ')
                    'u' -> { if (i + 5 < json.length) { json.substring(i + 2, i + 6).toIntOrNull(16)?.let { sb.append(it.toChar()) }; i += 4 } }
                    else -> sb.append(n)
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return null
    }

    /** One call's arguments text, e.g. {"name":"open","arg":"spotify"}, as a tag. Null when the name isn't one a model may use. */
    fun toTag(arguments: String): String? {
        val name = str(arguments, "name")?.trim()?.lowercase() ?: return null
        if (name !in names) return null
        val arg = (str(arguments, "arg") ?: "").replace("]]", "] ]").trim()
        return "[[$name:$arg]]"
    }
}
