package app.capri.mobile

// Which brain gets a message, and what to do with messages that could not be sent because the phone is offline.
object Router {
    // Short, simple chat goes to the fast brain (if you chose one). Anything with a picture, or longer, stays with the main brain.
    fun pick(text: String, main: Int, fast: Int, hasImage: Boolean): Int {
        if (fast < 0 || hasImage) return main
        val t = text.trim()
        if (t.length > 60 || t.contains('\n')) return main
        if (Regex("(?i)\\b(explain|write|summar|translate|code|plan|compare|why|how)\\b|اشرح|اكتب|لخص|ترجم|قارن|ليش|كيف").containsMatchIn(t)) return main
        return fast
    }
}

// Picks the best of YOUR ready providers for what you asked, by topic. Keeps the main brain when it is already a good fit.
object AutoRoute {
    enum class Topic { CODE, FAST, WRITING, LONG, VISION, GENERAL }

    fun topic(text: String, hasImage: Boolean): Topic {
        val t = text.trim()
        if (hasImage) return Topic.VISION
        if (t.length > 1500) return Topic.LONG
        if (Regex("(?i)\\b(code|function|bug|error|python|kotlin|java|javascript|regex|sql|algorithm|calculate|equation|prove|math|debug)\\b|```|كود|برمجة|دالة|خطأ برمجي|معادلة|احسب").containsMatchIn(t)) return Topic.CODE
        if (Regex("(?i)\\b(translate|essay|story|poem|email|letter|rewrite|summari[sz]e|write)\\b|ترجم|لخص|اكتب لي|قصة|قصيدة|رسالة|اعد صياغة").containsMatchIn(t)) return Topic.WRITING
        if (t.length <= 60 && !t.contains('\n')) return Topic.FAST
        return Topic.GENERAL
    }

    // provider-name keywords, best first
    private fun rank(tp: Topic): List<String> = when (tp) {
        Topic.CODE -> listOf("claude", "deepseek", "openai", "gemini", "xai", "nvidia", "groq")
        Topic.FAST -> listOf("groq", "cerebras", "sambanova", "gemini", "mistral")
        Topic.WRITING -> listOf("gemini", "claude", "openai", "mistral")
        Topic.LONG -> listOf("gemini", "claude", "openai")
        Topic.VISION -> listOf("gemini", "openai", "claude")
        Topic.GENERAL -> emptyList()
    }

    // names: provider display names by index. ready(i): has a key/address. Returns an index.
    fun pick(text: String, hasImage: Boolean, main: Int, names: List<String>, ready: (Int) -> Boolean, local: Int): Int {
        if (main == local) return main                     // offline stays offline
        val order = rank(topic(text, hasImage))
        if (order.isEmpty()) return main
        fun at(key: String) = names.indices.firstOrNull { it != local && names[it].lowercase().contains(key) && ready(it) }
        val mainName = names.getOrElse(main) { "" }.lowercase()
        if (order.take(2).any { mainName.contains(it) }) return main
        for (k in order) at(k)?.let { return it }
        return main
    }
}

object OfflineQueue {
    private const val MAX = 5

    fun isNetworkError(msg: String?): Boolean =
        msg != null && Regex("(?i)unable to resolve|unknownhost|timed out|timeout|failed to connect|no address|network|connection (?:reset|refused|closed)|software caused|ENETUNREACH|ssl|شبكة|اتصال").containsMatchIn(msg)

    fun push(existing: String, text: String): String {
        val one = text.replace('\n', ' ').trim().take(300)
        if (one.isEmpty()) return existing
        val l = existing.split("\n").filter { it.isNotBlank() }.filter { it != one } + one
        return l.takeLast(MAX).joinToString("\n")
    }

    // the oldest waiting message and what is left
    fun pop(existing: String): Pair<String, String>? {
        val l = existing.split("\n").filter { it.isNotBlank() }
        if (l.isEmpty()) return null
        return Pair(l.first(), l.drop(1).joinToString("\n"))
    }
}
