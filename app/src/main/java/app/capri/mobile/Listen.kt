package app.capri.mobile

// Everything about "she didn't hear me" that needs no Android: what each recogniser error means, which ones are worth
// retrying on their own, and how to cut her name out of what she heard.
object Listen {
    // SpeechRecognizer error codes
    const val NETWORK_TIMEOUT = 1; const val NETWORK = 2; const val AUDIO = 3; const val SERVER = 4; const val CLIENT = 5
    const val SPEECH_TIMEOUT = 6; const val NO_MATCH = 7; const val BUSY = 8; const val PERMISSION = 9
    const val TOO_MANY = 10; const val DISCONNECTED = 11; const val LANG_UNSUPPORTED = 12; const val LANG_UNAVAILABLE = 13

    // Errors that are just the recogniser being busy or restarting: try again by itself, quietly.
    fun retryable(code: Int) = code == CLIENT || code == BUSY || code == TOO_MANY || code == DISCONNECTED

    // Silence and "no match" mean nothing was said, not that something broke.
    fun silent(code: Int) = code == SPEECH_TIMEOUT || code == NO_MATCH

    fun hint(code: Int, ar: Boolean): String = when (code) {
        SPEECH_TIMEOUT, NO_MATCH -> if (ar) "ما وصلني صوت واضح. قرّب الجوال شوي وقلها مرة ثانية." else "I didn't get a clear sound. Hold the phone a bit closer and say it again."
        NETWORK, NETWORK_TIMEOUT, SERVER -> if (ar) "التعرف على الصوت يحتاج نت الحين. شغّل النت، أو نزّل اللغة للاستخدام بدون اتصال، أو فعّل «أذنها الخاصة»." else "Speech recognition needs internet right now. Turn it on, download the language for offline use, or switch on her own ears."
        AUDIO -> if (ar) "المايك ما اشتغل. يمكن تطبيق ثاني يستخدمه (مكالمة، تسجيل)؟" else "The microphone didn't start. Maybe another app is using it (a call or a recording)?"
        PERMISSION -> if (ar) "أحتاج صلاحية المايك. افتح تطبيق كابري وفعّلها." else "I need the microphone permission. Open the Capri app and allow it."
        LANG_UNSUPPORTED, LANG_UNAVAILABLE -> if (ar) "لغة التعرف مو منزّلة. نزّلها من إعدادات جوجل (الصوت > التعرف دون اتصال)، أو فعّل «أذنها الخاصة»." else "That recognition language isn't installed. Download it in Google's voice settings (offline speech), or switch on her own ears."
        else -> if (ar) "ما سمعتك زين. جرب مرة ثانية." else "Didn't catch that. Try again."
    }

    // Longer spellings first, so "كابريا" does not leave a stray "يا" behind.
    val wakeRe = Regex("(?<![\\p{L}\\p{N}])(capri|kapri|cabri|capry|capre|kapre|caprie|كابريه|كابريا|كابري|كابرى|كابرة|كابرا|قابري|كبري|كابر)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    private val lead = Regex("^(?:(?:hey|hi|hello|ok|okay|yo|اهلا|أهلا|هلا|مرحبا|يا|هاي|هلو)[ ,،]*)+$", RegexOption.IGNORE_CASE)

    // "hey Capri open whatsapp" -> "open whatsapp"; "كابري" -> ""; no name -> null.
    fun afterName(text: String): String? {
        val m = wakeRe.find(text) ?: return null
        // her name has to open the sentence (after a greeting at most), so "my cousin Capri is late" does not wake her
        if (!onlyGreeting(text.substring(0, m.range.first))) return null
        return text.substring(m.range.last + 1).trim().trimStart(',', '،', '.', '!', ' ', '؟', '?')
    }

    fun onlyGreeting(before: String) = before.isBlank() || lead.matches(before.trim())
}
