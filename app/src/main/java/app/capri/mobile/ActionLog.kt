package app.capri.mobile

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// A plain record of what she did on the phone, so nothing she does is invisible. Kept short, stored as lines.
// What she TYPED or COPIED is never written here, only that she did it.
object ActionLog {
    private const val KEY = "actlog"
    private const val MAX = 80
    private val quiet = setOf("hands", "copy", "remember", "dictate")

    // pure, so it can be tested
    fun push(existing: String, line: String, max: Int = MAX): String {
        val one = line.replace('\n', ' ').trim().take(140)
        if (one.isEmpty()) return existing
        val lines = existing.split("\n").filter { it.isNotBlank() } + one
        return lines.takeLast(max).joinToString("\n")
    }

    fun describe(name: String, arg: String): String =
        if (name in quiet) name + (if (name == "hands") ":" + arg.substringBefore('|').take(12) else "") else if (arg.isBlank()) name else "$name: ${arg.take(60)}"

    fun add(prefs: Prefs, source: String, text: String) {
        val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date())
        prefs.putS(KEY, push(prefs.getS(KEY, ""), "$stamp  $source  $text"))
    }

    fun tag(prefs: Prefs, name: String, arg: String) = add(prefs, "tag", describe(name, arg))
    fun text(prefs: Prefs): String = prefs.getS(KEY, "")
    fun clear(prefs: Prefs) = prefs.remove(KEY)
}
