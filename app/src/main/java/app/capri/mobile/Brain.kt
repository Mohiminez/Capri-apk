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
        "When one request has several steps (like \"open Spotify, search X and play it\"), put the tags in order in the same reply: [[open:Spotify]] then [[do:search for X in the app and play the first result]], and [[do:...]] can also open the app by itself. " +
        "[[look:question]] looks at their screen to answer it, " +
        "[[do:goal]] does a multi-step job on the phone by tapping, typing and scrolling for them, like finding something in an app or joining a Discord voice channel, " +
        "[[web:query]] searches the web and reads the top page, use it BY YOURSELF whenever you are not sure of a fact, or it is recent news, a price, a score, a release, a person or anything that may have changed: never guess, look it up and then answer from the result, [[read:https://...]] reads one web page, [[browse:site or search]] opens YOUR OWN browser for them to look at, "+
        "[[weather:city]] and [[wiki:topic]] and [[playing:x]] look something up (the weather, a Wikipedia summary, the song playing now) and you then answer from the result, " +
        "[[flash:on]] or [[flash:off]] is the torch, [[volume:up]] [[volume:down]] or [[volume:60]] the music volume, [[nav:place]] opens directions, [[search:query]] a web search, " +
        "[[hands:type|text]] types text into the open text box, [[hands:openType|app|text]] opens an app and types text in it, [[hands:tap|label]] taps a button by its label, " +
        "[[hands:scroll|down]] (or up), [[hands:back]], [[hands:home]], [[hands:shot]] (screenshot), [[hands:lock]], " +
        "[[watch:what to wait for]] keeps an eye on their screen and tells them when it happens, like a download finishing ([[watch:off]] stops), " +
        "[[chess:open]] opens a chess game against you when they want to play chess, [[point:label]] circles a button or text on their screen so they can see where it is, [[clones:5]] summons that many glitchy clones of you for a few seconds ([[clones:0]] clears them), " +
        "[[copy:text]] copies text to the clipboard, [[chaos:x]] a harmless fake glitch prank on their screen, [[dictate:x]] types what they say next into the box they have open, " +
        "[[emote:heart]] [[emote:star]] [[emote:surprise]] [[emote:question]] or [[emote:angry]] floats a tiny reaction over your head: use one now and then when you are charmed, excited, shocked, confused or annoyed, not in every reply. " +
        "Never use [[do:...]] to send messages, buy things, delete things or change settings unless they plainly ask for exactly that. Never mention the tags.\n" +
        "Memory: when they tell you something worth remembering long-term, like what they like to be called or what they're into, add [[remember:short note]]. " +
        "Never store passwords, addresses or other private details."
}

class Brain(private val ctx: Context, private val prefs: Prefs) {
    private val main = SafeHandler(Looper.getMainLooper()) { e -> CrashLog.record(ctx, e, "brain reply") }
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

    fun anyReady(): Boolean = prefs.getB("noAI", false) || chain(prefs.cur).any { ready(it) }

    private val canned get() = if (L.ar) listOf("هه. كمّل.", "مممم. سجّلتها.", "ما عندي عقل اليوم بس أسمعك.", "أنا الحين بدون ذكاء اصطناعي، فبس أبتسم وأهز راسي.", "كمّل كمّل.", "هذا يضحك، صراحة.", "أنا هنا. اطلب مني أفتح شي.")
        else listOf("Heh. Go on.", "Mm. Noted.", "No brain today, but I'm listening.", "I'm in no-AI mode, so I just nod. Ask me to open something.", "Keep going, choom.", "That's kind of funny, honestly.", "I'm here. Try telling me to open an app.")

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
        if (L.lang(prefs) == 2) sb.append("\nThe user chose Arabic. Reply in natural, warm, playful Arabic with a light Gulf flavour, as a girl would speak (feminine forms for yourself), in short sentences. If they write in plain Modern Standard Arabic, answer in the same register. Keep tag names, app names and code in English. Never translate tags.")
        val replyLang = prefs.getS("replyLang", "").trim()
        if (replyLang.isNotEmpty()) sb.append("\nAlways reply in ").append(replyLang.take(30)).append(", whatever language they write in.")
        when (prefs.getI("tone", 0)) {
            1 -> sb.append("\nTone: calm, gentle and supportive. Tease much less than usual.")
            2 -> sb.append("\nTone: professional and concise, almost no slang, still recognisably you.")
            4 -> sb.append("\nTone: openly affectionate and a little flirty, still teasing, still you; keep it tasteful, never explicit, and drop it the moment the user seems uncomfortable.")
            3 -> sb.append("\nTone: wilder and cheekier than usual, with more chaotic energy, but never cruel and never crossing your safety rules.")
        }
        if (prefs.getB("deep", false)) sb.append("\nDeep thinking is on: for anything with several steps, maths, code, planning or a decision, reason it through carefully first, check your work, then give the answer clearly. Stay short unless they ask for detail.")
        val nick = prefs.getS("nick", "")
        if (nick.isNotBlank()) sb.append("\nThe user wants to be called ").append(nick).append(".")
        sb.append("\nHow well you know them: ").append(listOf("barely at all", "a little", "as friends", "closely", "like ride-or-die family")[Charm.level(prefs.getI("charm", 0))])
            .append(". Let it show a little in how warm and teasing you are. Never mention this line or any number.")
        val notes = Memory.parse(prefs.getS("notes", "")).joinToString("\n") { (if (it.pinned) "- (important) " else "- ") + it.text }
        if (notes.isNotBlank()) sb.append("\nWhat you remember about them:\n").append(notes)
        sb.append("\nFor them it is ").append(SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ENGLISH).format(Date()))
            .append(". Only bring up the time if they ask.")
        return sb.toString()
    }

    // done(reply, error): exactly one of them is non-null, always called on the main thread.
    // hidden = an instruction from the app, not something the user said, so it isn't kept in the chat.
    fun ask(text: String, hidden: Boolean, image: String? = null, remember: Boolean = true, done: (String?, String?) -> Unit) {
        if (prefs.getB("noAI", false)) {
            // No AI mode: she answers from a handful of lines, so nothing leaves the phone and nothing costs anything.
            if (!hidden) history.add(Msg("user", text, System.currentTimeMillis()))
            val line = if (hidden) "NOTHING" else canned.random()
            main.postDelayed({ done(line, null) }, 600)
            return
        }
        if (!hidden) history.add(Msg("user", text, System.currentTimeMillis()))
        val past = (if (hidden) history.toList() else history.dropLast(1)).takeLast(if (isLocal()) 6 else 20)
        Thread {
            try {
                val reply = complete(system() + (if (hidden) "" else Knowledge.context(ctx, text) + Guide.context(text) + Specialists.context(text, image != null, prefs.getB("agents", true))), past, text, image, route(text, image != null))
                main.post {
                    if (remember && !reply.trim().uppercase().startsWith("NOTHING")) {
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
        // A chat that runs on the phone never hands the screen to an online job brain unless you allow it.
        val offlineOnly = prefs.cur == Providers.LOCAL && !prefs.getB("localToCloud", false)
        val base = if (jobs in 0 until Providers.all.size && !offlineOnly) jobs else prefs.cur
        // a picture goes to a brain that can see, when you have one set up
        val first = if (image != null && !offlineOnly && prefs.getB("autoRoute", true)) AutoRoute.pick("", true, base, Providers.all.map { it.name }, { ready(it) }, Providers.LOCAL) else base
        return complete(sys, emptyList(), user, image, first)
    }

    private fun ready(i: Int): Boolean = when (i) {
        Providers.LOCAL -> prefs.getS("localModel", "").isNotBlank() && LocalLlm.file(ctx, prefs.getS("localModel", "")).exists()
        else -> prefs.url(i).isNotBlank() && (prefs.key(i).isNotEmpty() || prefs.url(i).startsWith("http://"))
    }

    // The provider you chose first. When it's rate-limited or down, the next one you have a key for takes over.
    private fun chain(first: Int): List<Int> {
        val out = arrayListOf(first)
        // Offline stays offline: no online brain is tried unless you turned that on.
        if (first == Providers.LOCAL && !prefs.getB("localToCloud", false)) return out
        if (prefs.getB("fallback", true)) {
            for (i in Providers.all.indices) if (i != first && i != Providers.LOCAL && ready(i)) out.add(i)
            if (first != Providers.LOCAL && prefs.getB("offlineFallback", true) && ready(Providers.LOCAL)) out.add(Providers.LOCAL)
        }
        return out
    }

    @Volatile var lastUsed = ""

    // Your fast brain wins for short chat when you set one; otherwise, with auto on, the best ready provider for the topic.
    private fun route(text: String, hasImage: Boolean): Int {
        if (prefs.cur == Providers.LOCAL && !prefs.getB("localToCloud", false)) return prefs.cur   // offline stays offline
        val fast = prefs.getI("fastProvider", -1)
        if (fast >= 0) return Router.pick(text, prefs.cur, fast, hasImage)
        if (!prefs.getB("autoRoute", true)) return prefs.cur
        return AutoRoute.pick(text, hasImage, prefs.cur, Providers.all.map { it.name }, { ready(it) }, Providers.LOCAL)
    }

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
        Net.checkKey(base, key)
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
        if (code == 429) return "HTTP 429, rate limited: wait a bit or add another provider"
        if (code == 402) return "HTTP 402, this provider says you're out of credits"
        var detail = txt.take(140)
        try { detail = JSONObject(txt).getJSONObject("error").getString("message").take(140) } catch (e: Exception) { }
        return "HTTP $code: $detail"
    }
}
