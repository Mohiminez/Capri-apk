package app.capri.mobile

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Her personality and safety lines are copied from capri-desktop (app/Brain.cs) into assets by tools/prepare_assets.py.
object Persona {
    fun default(ctx: Context): String = read(ctx, "personality.txt")
    fun safety(ctx: Context): String = read(ctx, "safety.txt")
    private fun read(ctx: Context, name: String): String =
        ctx.assets.open(name).bufferedReader().use { it.readText() }

    const val MACHINERY =
        "Always: answer in one or two short sentences, under 25 words, or up to three when you're comforting them. " +
        "Plain text only: no emoji, no asterisks, no actions, no lists. Stay in character. Reply in the language the user writes in.\n" +
        "Where you are: on this version you live on the user's Android phone, floating over their apps. You can't see their screen " +
        "and you can't read their messages or files, so never guess what they have open.\n" +
        "Never make up names, messages or things nobody said. If you got something wrong, correct it once in a few words and move on.\n" +
        "Phone actions: only when they plainly ask for one, put a tag at the very end of your reply, after your words, and say what you're doing in your own words. " +
        "Tags: [[open:App name]] opens an app, [[url:https://...]] opens a website, [[music:song and artist]] plays a song, " +
        "[[media:pause]] controls playing media (pause, play, next, previous), [[timer:seconds|label]] starts a timer, [[alarm:HH:MM|label]] sets an alarm (24-hour time). " +
        "Never mention the tags.\n" +
        "Memory: when they tell you something worth remembering long-term, like what they like to be called or what they're into, add [[remember:short note]]. " +
        "Never store passwords, addresses or other private details."
}

class Brain(private val ctx: Context, private val prefs: Prefs) {
    private val main = Handler(Looper.getMainLooper())
    private class Msg(val role: String, val text: String, val at: Long)
    private val history = ArrayList<Msg>()

    init {
        try {
            val arr = JSONArray(prefs.getS("history", "[]"))
            val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val at = o.optLong("t")
                if (at >= weekAgo) history.add(Msg(o.getString("r"), o.getString("c"), at))
            }
        } catch (e: Exception) { }
    }

    private fun save() {
        val arr = JSONArray()
        for (m in history.takeLast(40)) arr.put(JSONObject().put("r", m.role).put("c", m.text).put("t", m.at))
        prefs.putS("history", arr.toString())
    }

    fun forget() { history.clear(); save() }

    private fun system(): String {
        val custom = prefs.getS("personality", "")
        val safety = Persona.safety(ctx)
        val base = if (custom.isBlank()) Persona.default(ctx) + "\n" + safety
        else custom + "\nWhatever your personality above says, these lines always hold:\n" + safety
        val sb = StringBuilder(base).append("\n").append(Persona.MACHINERY)
        val nick = prefs.getS("nick", "")
        if (nick.isNotBlank()) sb.append("\nThe user wants to be called ").append(nick).append(".")
        val notes = prefs.getS("notes", "")
        if (notes.isNotBlank()) sb.append("\nWhat you remember about them:\n").append(notes)
        sb.append("\nFor them it is ").append(SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH).format(Date()))
            .append(". Only bring up the time if they ask.")
        return sb.toString()
    }

    // done(reply, error): exactly one of them is non-null, always called on the main thread.
    fun ask(text: String, hidden: Boolean, done: (String?, String?) -> Unit) {
        if (!hidden) history.add(Msg("user", text, System.currentTimeMillis()))
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system()))
        for (m in history.takeLast(20)) msgs.put(JSONObject().put("role", m.role).put("content", m.text))
        if (hidden) msgs.put(JSONObject().put("role", "user").put("content", text))
        Thread {
            try {
                val reply = post(msgs)
                main.post {
                    history.add(Msg("assistant", Actions.strip(reply), System.currentTimeMillis()))
                    save()
                    done(reply, null)
                }
            } catch (e: Exception) {
                main.post {
                    if (!hidden && history.isNotEmpty() && history.last().role == "user") history.removeAt(history.size - 1)
                    done(null, e.message ?: "network error")
                }
            }
        }.start()
    }

    private fun post(msgs: JSONArray): String {
        val key = prefs.getS("key", "")
        val base = prefs.getS("url", "").trim().trimEnd('/')
        val body = JSONObject().put("model", prefs.getS("model", "").trim()).put("messages", msgs)
        val c = URL("$base/chat/completions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 20000
        c.readTimeout = 60000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Authorization", "Bearer " + key.trim())
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val txt = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw Exception(describe(code, txt))
        val msg = JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val raw = if (msg.isNull("content")) "" else msg.getString("content")
        val out = raw.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        if (out.isEmpty()) throw Exception("the model sent an empty answer")
        return out
    }

    private fun describe(code: Int, txt: String): String {
        if (code == 401 || code == 403) return "HTTP $code, the key was refused"
        var detail = txt.take(140)
        try { detail = JSONObject(txt).getJSONObject("error").getString("message").take(140) } catch (e: Exception) { }
        return "HTTP $code: $detail"
    }
}
