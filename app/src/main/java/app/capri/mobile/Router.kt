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
