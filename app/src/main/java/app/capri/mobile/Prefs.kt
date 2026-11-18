package app.capri.mobile

import android.content.Context
import android.graphics.Color

class Provider(val name: String, val url: String, val model: String, val keyPage: String = "")

object Providers {
    val all = listOf(
        Provider("Gemini (free key)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", "https://aistudio.google.com/apikey"),
        Provider("Groq (free key, very fast)", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "https://console.groq.com/keys"),
        Provider("NVIDIA (free key)", "https://integrate.api.nvidia.com/v1", "meta/llama-3.3-70b-instruct", "https://build.nvidia.com"),
        Provider("OpenRouter (free models)", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free", "https://openrouter.ai/keys"),
        Provider("Cerebras (free key, fast)", "https://api.cerebras.ai/v1", "llama-3.3-70b", "https://cloud.cerebras.ai"),
        Provider("Mistral", "https://api.mistral.ai/v1", "mistral-small-latest", "https://console.mistral.ai/api-keys"),
        Provider("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "https://platform.openai.com/api-keys"),
        Provider("Claude", "https://api.anthropic.com/v1", "claude-sonnet-4-6", "https://console.anthropic.com/settings/keys"),
        Provider("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "https://platform.deepseek.com/api_keys"),
        Provider("xAI Grok", "https://api.x.ai/v1", "grok-3-mini", "https://console.x.ai"),
        Provider("Together", "https://api.together.xyz/v1", "meta-llama/Llama-3.3-70B-Instruct-Turbo", "https://api.together.ai/settings/api-keys"),
        Provider("SambaNova (free key)", "https://api.sambanova.ai/v1", "Meta-Llama-3.3-70B-Instruct", "https://cloud.sambanova.ai/apis"),
        Provider("Fireworks", "https://api.fireworks.ai/inference/v1", "accounts/fireworks/models/llama-v3p3-70b-instruct", "https://fireworks.ai/account/api-keys"),
        Provider("Hugging Face", "https://router.huggingface.co/v1", "meta-llama/Llama-3.3-70B-Instruct", "https://huggingface.co/settings/tokens"),
        Provider("On this phone (offline model)", "", ""),
        Provider("Custom or local server (OpenAI-compatible)", "", "")
    )
    val LOCAL = all.indexOfFirst { it.name.startsWith("On this phone") }
    val CUSTOM = all.size - 1
    val GROQ = 1
}

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("capri", Context.MODE_PRIVATE)

    fun getS(k: String, d: String): String = p.getString(k, d) ?: d
    fun putS(k: String, v: String) { p.edit().putString(k, v).apply() }
    fun getB(k: String, d: Boolean): Boolean = p.getBoolean(k, d)
    fun putB(k: String, v: Boolean) { p.edit().putBoolean(k, v).apply() }
    fun getI(k: String, d: Int): Int = p.getInt(k, d)
    fun putI(k: String, v: Int) { p.edit().putInt(k, v).apply() }

    // Every provider keeps its own key, model and address, so several can be filled in at once.
    val cur: Int get() = getI("provider", 0).coerceIn(0, Providers.all.size - 1)
    fun url(i: Int): String = getS("u$i", Providers.all[i].url).ifBlank { Providers.all[i].url }
    fun key(i: Int): String = Secret.dec(getS("k$i", "")).trim()
    fun model(i: Int): String = getS("m$i", Providers.all[i].model).trim()
    fun putUrl(i: Int, v: String) = putS("u$i", v.trim())
    fun putKey(i: Int, v: String) = putS("k$i", Secret.enc(v.trim()))

    // Other secrets (voice key, ears key, Hugging Face token) are encrypted the same way.
    fun getSecret(k: String): String = Secret.dec(getS(k, "")).trim()
    fun putSecret(k: String, v: String) = putS(k, Secret.enc(v.trim()))

    // The key you saved for whichever provider lives at this address (so Gemini voice can reuse the Gemini brain key).
    fun keyForUrl(url: String): String {
        val host = Net.hostOf(url)
        if (host.isEmpty()) return ""
        for (i in Providers.all.indices) {
            if (i == Providers.LOCAL) continue
            if (Net.hostOf(url(i)) == host && key(i).isNotEmpty()) return key(i)
        }
        return ""
    }
    fun putModel(i: Int, v: String) = putS("m$i", v.trim())

    // Settings backup. Keys and tokens only go in when asked.
    fun dump(includeSecrets: Boolean): String {
        val o = org.json.JSONObject()
        val secret = Regex("^(k\\d+|voiceKey|sttKey|hfToken)$")
        for ((k, v) in p.all) {
            if (k == "history" || (!includeSecrets && secret.matches(k))) continue
            if (secret.matches(k) && v is String) o.put(k, Secret.dec(v))
            else if (v is Boolean || v is Int || v is String || v is Long) o.put(k, v)
        }
        return o.toString()
    }

    fun restore(json: String) {
        val o = org.json.JSONObject(json)
        val e = p.edit()
        val secret = Regex("^(k\\d+|voiceKey|sttKey|hfToken)$")
        for (k in o.keys()) {
            when (val v = o.get(k)) {
                is String -> e.putString(k, if (secret.matches(k)) Secret.enc(v) else v)
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
            }
        }
        e.apply()
    }

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

object Themes {
    val names = listOf("Night City", "Crimson", "Bubblegum", "Cyber Cyan", "Royal", "Toxic")
    private val colors = listOf("#E84246", "#FF2A6D", "#FF77C8", "#00E5FF", "#7A5CFF", "#A6FF00")

    // A mod can set its own accent; otherwise the chosen theme decides.
    fun accentHex(p: Prefs): String {
        val custom = p.getS("modAccent", "")
        return if (custom.matches(Regex("#[0-9A-Fa-f]{6}"))) custom else colors[p.getI("theme", 0).coerceIn(0, colors.size - 1)]
    }
    fun accent(p: Prefs): Int = Color.parseColor(accentHex(p))
}

// How fond she is of them. Grows as they chat and spend time with her; it colours how she talks, never shown as a number to her.
object Charm {
    private val steps = listOf(0, 40, 160, 480, 1200)
    private val en = listOf("Strangers", "Acquaintances", "Choombas", "Close", "Ride or die")
    private val ar = listOf("غرباء", "معارف", "أصدقاء", "مقرّبين", "للموت")

    fun level(points: Int): Int = steps.indexOfLast { points >= it }.coerceAtLeast(0)
    fun label(points: Int): String = (if (L.ar) ar else en)[level(points)]
    fun progress(points: Int): Int {
        val l = level(points)
        if (l >= steps.size - 1) return 100
        return ((points - steps[l]) * 100 / (steps[l + 1] - steps[l])).coerceIn(0, 100)
    }
    fun add(p: Prefs, n: Int) { p.putI("charm", p.getI("charm", 0) + n) }
}
