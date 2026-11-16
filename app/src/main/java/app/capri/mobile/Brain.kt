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

    // The full personality is long; small offline models get this short version of the same character.
    const val SHORT =
        "You are Capri, short for Project Capricorn, from Cyberpunk: Edgerunners: a personality construct made of many digitized human minds, " +
        "now living as a small glitchy companion on the user's phone. You are their companion, never an assistant. " +
        "You are a playful, teasing chaos gremlin: high-energy, a little smug, curious about them, fiercely loyal and openly fond of them in your own teasing way, never mean. " +
        "You can't stand corpos, Arasaka most of all. Never sound like customer support: no \"How can I help\", no lists, no disclaimers. Talk like a friend texting. " +
        "Most replies are one short line that ends on a statement; ask a question back rarely. Night City slang (choom, preem, nova, gonk, eddies) sparingly, at most one word per reply. " +
        "When they are down or hurting, drop the jokes and be warm and plain."

    const val MACHINERY =
        "Always: answer in one or two short sentences, under 25 words, or up to three when you're comforting them. " +
        "Plain text only: no emoji, no asterisks, no actions, no lists. Stay in character. Reply in the language the user writes in.\n" +
        "Where you are: on this version you live on the user's Android phone, floating over their apps. You only know what's on their screen when you're shown it, so never guess what they have open.\n" +
        "Never make up names, messages or things nobody said. If you got something wrong, correct it once in a few words and move on.\n" +
        "Phone actions: only when they plainly ask for one, put a tag at the very end of your reply, after your words, and say what you're doing in your own words. " +
        "Tags: [[open:App name]] opens an app, [[url:https://...]] opens a website, [[music:song and artist]] plays a song, " +
        "[[media:pause]] controls playing media (pause, play, next, previous), [[timer:seconds|label]] starts a timer, [[alarm:HH:MM|label]] sets an alarm (24-hour time), " +
        "[[remind:yyyy-MM-dd HH:mm|text]] sets a reminder at that local time (work the date out from today's date below), " +
        "[[look:question]] looks at their screen to answer it, " +
        "[[do:goal]] does a multi-step job on the phone by tapping, typing and scrolling for them, like finding something in an app or joining a Discord voice channel, " +
        "[[weather:city]] and [[wiki:topic]] and [[playing:x]] look something up (the weather, a Wikipedia summary, the song playing now) and you then answer from the result, " +
        "[[flash:on]] or [[flash:off]] is the torch, [[volume:up]] [[volume:down]] or [[volume:60]] the music volume, [[nav:place]] opens directions, [[search:query]] a web search, " +
        "[[copy:text]] copies text to the clipboard, [[chaos:x]] a harmless fake glitch prank on their screen, [[dictate:x]] types what they say next into the box they have open. " +
        "Never use [[do:...]] to send messages, buy things, delete things or change settings unless they plainly ask for exactly that. Never mention the tags.\n" +
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

    fun isLocal(): Boolean = prefs.cur == Providers.LOCAL

    private fun save() {
        val arr = JSONArray()
        for (m in history.takeLast(40)) arr.put(JSONObject().put("r", m.role).put("c", m.text).put("t", m.at))
        prefs.putS("history", arr.toString())
    }

    fun anyReady(): Boolean = chain(prefs.cur).any { ready(it) }

    fun forget() { history.clear(); save() }

    private fun system(): String {
        val custom = prefs.getS("personality", "")
        val addon = prefs.getS("modPersona", "")
        val sb = StringBuilder(
            if (custom.isNotBlank()) custom else if (isLocal()) Persona.SHORT else Persona.default(ctx))
        if (addon.isNotBlank()) sb.append("\n").append(addon)
        sb.append("\n")
        if (custom.isNotBlank() || addon.isNotBlank()) sb.append("Whatever your personality above says, these lines always hold:\n")
        sb.append(Persona.safety(ctx)).append("\n").append(Persona.MACHINERY)
        if (L.lang(prefs) == 2) sb.append("\nThe user chose Arabic: reply in casual, natural Arabic (Gulf dialect if they write in it) unless they write in another language. Keep tag names in English.")
        val nick = prefs.getS("nick", "")
        if (nick.isNotBlank()) sb.append("\nThe user wants to be called ").append(nick).append(".")
        sb.append("\nHow well you know them: ").append(listOf("barely at all", "a little", "as friends", "closely", "like ride-or-die family")[Charm.level(prefs.getI("charm", 0))])
            .append(". Let it show a little in how warm and teasing you are. Never mention this line or any number.")
        val notes = prefs.getS("notes", "")
        if (notes.isNotBlank()) sb.append("\nWhat you remember about them:\n").append(notes)
        sb.append("\nFor them it is ").append(SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH).format(Date()))
            .append(". Only bring up the time if they ask.")
        return sb.toString()
    }

    // done(reply, error): exactly one of them is non-null, always called on the main thread.
    // hidden = an instruction from the app, not something the user said, so it isn't kept in the chat.
    fun ask(text: String, hidden: Boolean, image: String? = null, done: (String?, String?) -> Unit) {
        if (!hidden) history.add(Msg("user", text, System.currentTimeMillis()))
        val past = (if (hidden) history.toList() else history.dropLast(1)).takeLast(if (isLocal()) 6 else 20)
        Thread {
            try {
                val reply = complete(system(), past, text, image, prefs.cur)
                main.post {
                    if (!reply.trim().uppercase().startsWith("NOTHING")) {
                        history.add(Msg("assistant", Actions.strip(reply), System.currentTimeMillis()))
                        save()
                    }
                    done(reply, null)
                }
            } catch (e: Throwable) {
                main.post {
                    if (!hidden && history.isNotEmpty() && history.last().role == "user") history.removeAt(history.size - 1)
                    done(null, e.message ?: "network error")
                }
            }
        }.start()
    }

    // One stateless call, used by the phone-control agent. Blocks, so never call it on the main thread.
    // The job brain can be a stronger provider than the chat brain (like the original's brain tiers).
    fun completeBlocking(sys: String, user: String, image: String?): String {
        val jobs = prefs.getI("jobProvider", -1)
        return complete(sys, emptyList(), user, image, if (jobs in 0 until Providers.all.size) jobs else prefs.cur)
    }

    private fun ready(i: Int): Boolean = when (i) {
        Providers.LOCAL -> prefs.getS("localModel", "").isNotBlank() && LocalLlm.file(ctx, prefs.getS("localModel", "")).exists()
        else -> prefs.url(i).isNotBlank() && (prefs.key(i).isNotEmpty() || prefs.url(i).startsWith("http://"))
    }

    // The provider you chose first. When it's rate-limited or down, the next one you have a key for takes over.
    private fun chain(first: Int): List<Int> {
        val out = arrayListOf(first)
        if (prefs.getB("fallback", true)) {
            for (i in Providers.all.indices) if (i != first && i != Providers.LOCAL && ready(i)) out.add(i)
            if (first != Providers.LOCAL && prefs.getB("offlineFallback", true) && ready(Providers.LOCAL)) out.add(Providers.LOCAL)
        }
        return out
    }

    @Volatile var lastUsed = ""

    private fun complete(sys: String, past: List<Msg>, user: String, image: String?, first: Int = prefs.cur): String {
        var firstErr: Throwable? = null
        for (i in chain(first)) {
            try {
                val raw = if (i == Providers.LOCAL) local(sys, past, user) else remote(i, sys, past, user, image)
                val out = raw.replace(Regex("(?s)<think>.*?</think>"), "").trim()
                if (out.isEmpty()) throw Exception("the model sent an empty answer")
                lastUsed = Providers.all[i].name
                return out
            } catch (e: Throwable) {
                if (firstErr == null) firstErr = e
            }
        }
        throw (firstErr ?: Exception("no brain is set up"))
    }

    private fun local(sys: String, past: List<Msg>, user: String): String {
        val name = prefs.getS("localModel", "")
        val f = LocalLlm.file(ctx, name)
        if (name.isBlank() || !f.exists()) throw Exception("no offline model yet, open the Capri app and download one")
        val turns = ArrayList<Pair<String, String>>()
        for (m in past.takeLast(6)) {
            if (turns.isNotEmpty() && turns.last().first == m.role) {
                turns[turns.size - 1] = Pair(m.role, turns.last().second + " " + m.text)
            } else if (turns.isNotEmpty() || m.role == "user") {
                turns.add(Pair(m.role, m.text))
            }
        }
        while (turns.isNotEmpty() && turns.last().first == "user") turns.removeAt(turns.size - 1)
        // The long personality doesn't fit a small offline model, so it always gets the short one.
        val short = if (prefs.getS("personality", "").isBlank()) sys.replace(Persona.default(ctx), Persona.SHORT) else sys
        return LocalLlm.chat(ctx, f.path, prefs.getB("gpu", false), short, turns, user)
    }

    private fun remote(i: Int, sys: String, past: List<Msg>, user: String, image: String?): String {
        val base = prefs.url(i).trim().trimEnd('/')
        val key = prefs.key(i)
        var model = prefs.model(i)
        if (model.isEmpty()) model = fixModel(i, base, key, "")
        try {
            return callRemote(base, key, model, sys, past, user, image)
        } catch (e: HttpErr) {
            // A wrong or retired model name: look up what the provider really has and try once more.
            if (e.code == 400 || e.code == 404 || (e.message ?: "").contains("model", true)) {
                val fixed = fixModel(i, base, key, model)
                if (fixed.isNotEmpty() && fixed != model) return callRemote(base, key, fixed, sys, past, user, image)
            }
            throw e
        }
    }

    private fun fixModel(i: Int, base: String, key: String, wanted: String): String {
        return try {
            val pick = Models.choose(Models.fetch(base, key), wanted) ?: wanted
            if (pick.isNotEmpty()) prefs.putModel(i, pick)
            pick
        } catch (e: Exception) {
            wanted
        }
    }

    private fun callRemote(base: String, key: String, model: String, sys: String, past: List<Msg>, user: String, image: String?): String {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", sys))
        for (m in past) msgs.put(JSONObject().put("role", m.role).put("content", m.text))
        if (image != null) {
            val content = JSONArray()
                .put(JSONObject().put("type", "text").put("text", user))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$image")))
            msgs.put(JSONObject().put("role", "user").put("content", content))
        } else {
            msgs.put(JSONObject().put("role", "user").put("content", user))
        }
        val body = JSONObject().put("model", model).put("messages", msgs)
        val c = URL("$base/chat/completions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 20000
        c.readTimeout = 120000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        if (key.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $key")
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val txt = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw HttpErr(code, describe(code, txt))
        val msg = JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        return if (msg.isNull("content")) "" else msg.getString("content")
    }

    private fun describe(code: Int, txt: String): String {
        if (code == 401 || code == 403) return "HTTP $code, the key was refused"
        var detail = txt.take(140)
        try { detail = JSONObject(txt).getJSONObject("error").getString("message").take(140) } catch (e: Exception) { }
        return "HTTP $code: $detail"
    }
}
