package app.capri.mobile

// What to do with a failed model call, and how to say it: busy servers (503 "high demand") are waited out and retried, and nothing
// the user sees is ever a screenful of raw JSON. Pure logic, unit tested.
object ApiErrors {
    /** Worth trying again a moment later: the server is busy or hiccuping, the request itself was fine. */
    fun retryable(code: Int) = code == 408 || code == 425 || code == 429 || code == 500 || code == 502 || code == 503 || code == 504 || code == 529

    /** Wait before try number [attempt] (0 = after the first failure): 1.5 s, 4 s, 9 s. */
    fun backoffMs(attempt: Int): Long = when (attempt) { 0 -> 1500L; 1 -> 4000L; else -> 9000L }

    fun friendly(code: Int, ar: Boolean): String = when (code) {
        401, 403 -> if (ar) "المفتاح انرفض، تأكد منه في تبويب BRAIN." else "The key was refused. Check it in the BRAIN tab."
        402 -> if (ar) "رصيد هذا المزود خلص." else "This provider is out of credits."
        404 -> if (ar) "اسم الموديل غير موجود عند المزود." else "The provider doesn't have that model."
        408, 504 -> if (ar) "المزود تأخر بالرد." else "The provider took too long to answer."
        429 -> if (ar) "المزود حاطّ حد للطلبات، انتظر شوي أو ضيف مزود ثاني." else "Rate limited: wait a bit or add another provider."
        500, 502, 503, 529 -> if (ar) "الموديل مشغول هسه (ضغط على المزود). انتظر شوي أو ضيف مزود ثاني." else "The model is busy right now (high demand). Wait a little or add a second provider."
        else -> if (ar) "المزود رجّع خطأ ($code)." else "The provider returned an error ($code)."
    }

    /** One short line for any exception, never JSON, never more than about 110 characters. */
    fun clean(e: Throwable, ar: Boolean): String {
        if (e is HttpErr) return friendly(e.code, ar)
        val m = (e.message ?: "").trim()
        val low = (e.javaClass.simpleName + " " + m).lowercase()
        if ("unknownhost" in low || "unable to resolve" in low || "no address associated" in low || "network is unreachable" in low || "failed to connect" in low)
            return if (ar) "ماكو إنترنت أو المزود ما يستجيب." else "No internet, or the provider isn't reachable."
        if ("timeout" in low || "timed out" in low) return if (ar) "المزود تأخر بالرد." else "The provider took too long to answer."
        val one = m.replace(Regex("\\s+"), " ").replace(Regex("[{}\\[\\]\"]"), "").trim()
        if (one.isEmpty()) return if (ar) "صار خطأ غير معروف." else "Something went wrong."
        return if (one.length > 110) one.take(107) + "..." else one
    }

    /** When a Gemini model stays busy, a lighter sibling usually still answers (free tier: Pro > Flash > Flash-Lite). Null = no fallback known. */
    fun fallbackModel(base: String, model: String): String? {
        if (!base.contains("generativelanguage.googleapis.com")) return null
        val m = model.lowercase().removePrefix("models/")
        return when {
            m.contains("flash-lite") -> null
            m.contains("flash") -> m.replace(Regex("flash.*$"), "flash-lite")
            m.contains("pro") -> m.replace(Regex("pro.*$"), "flash")
            else -> null
        }
    }
}
