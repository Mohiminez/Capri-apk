package app.capri.mobile

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HttpErr(val code: Int, msg: String) : Exception(msg)

// Asks the provider which models it has, so nobody has to type an exact model name.
object Models {
    private val skip = Regex("embed|whisper|tts|image|imagen|veo|moderation|dall-e|audio|realtime|transcribe|rerank|guard|vision-preview|aqa|learnlm", RegexOption.IGNORE_CASE)
    private val favourites = listOf(
        "gemini-2\\.5-flash$", "gemini-flash-latest$", "gemini-[0-9.]+-flash$", "gemini.*flash",
        "llama-3\\.3-70b", "llama-3\\.1-70b", "llama-3\\.1-405b",
        "gpt-4o-mini$", "gpt-4\\.1-mini$", "gpt-[0-9.]+-mini",
        "deepseek-chat", "claude.*sonnet", "claude.*haiku", "qwen.*instruct", "mistral"
    )

    // Chat models the key can use, best guesses first. Throws on network or key trouble.
    fun fetch(base: String, key: String): List<String> {
        val c = URL(base.trim().trimEnd('/') + "/models").openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        if (key.isNotBlank()) {
            c.setRequestProperty("Authorization", "Bearer " + key.trim())
            c.setRequestProperty("x-api-key", key.trim())
            c.setRequestProperty("anthropic-version", "2023-06-01")
        }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw HttpErr(code, if (code == 401 || code == 403) "the key was refused" else "HTTP $code")
        val data = JSONObject(txt).getJSONArray("data")
        val ids = ArrayList<String>()
        for (i in 0 until data.length()) {
            val id = data.getJSONObject(i).optString("id").removePrefix("models/")
            if (id.isNotEmpty() && !skip.containsMatchIn(id)) ids.add(id)
        }
        return ids.sortedBy { rank(it) }
    }

    private fun rank(id: String): Int {
        val i = favourites.indexOfFirst { Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(id) }
        return (if (i < 0) 100 else i) * 1000 + id.length
    }

    // What to use when the saved name is empty or wrong: the closest name the provider really has.
    fun choose(list: List<String>, wanted: String): String? {
        if (list.isEmpty()) return null
        val w = wanted.trim().removePrefix("models/")
        if (w.isNotEmpty()) {
            list.firstOrNull { it.equals(w, true) }?.let { return it }
            list.firstOrNull { it.contains(w, true) || w.contains(it, true) }?.let { return it }
        }
        return list[0]
    }
}
