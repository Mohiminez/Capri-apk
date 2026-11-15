package app.capri.mobile

import android.content.Context

class Provider(val name: String, val url: String, val model: String)

object Providers {
    val all = listOf(
        Provider("Gemini (free key)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash"),
        Provider("NVIDIA (free key)", "https://integrate.api.nvidia.com/v1", "meta/llama-3.3-70b-instruct"),
        Provider("OpenRouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free"),
        Provider("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        Provider("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
        Provider("Claude", "https://api.anthropic.com/v1", "claude-sonnet-4-6"),
        Provider("Custom (OpenAI-compatible)", "", "")
    )
}

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("capri", Context.MODE_PRIVATE)

    fun getS(k: String, d: String): String = p.getString(k, d) ?: d
    fun putS(k: String, v: String) { p.edit().putString(k, v).apply() }
    fun getB(k: String, d: Boolean): Boolean = p.getBoolean(k, d)
    fun putB(k: String, v: Boolean) { p.edit().putBoolean(k, v).apply() }
    fun getI(k: String, d: Int): Int = p.getInt(k, d)
    fun putI(k: String, v: Int) { p.edit().putInt(k, v).apply() }

    fun addNote(note: String) {
        val clean = note.trim().take(200)
        if (clean.isEmpty()) return
        val lines = getS("notes", "").split("\n").filter { it.isNotBlank() }.toMutableList()
        val line = "- $clean"
        if (lines.contains(line)) return
        lines.add(line)
        putS("notes", lines.takeLast(40).joinToString("\n"))
    }
}
