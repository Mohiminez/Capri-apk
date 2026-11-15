package app.capri.mobile

import android.content.Context
import org.json.JSONObject

// Does a multi-step job on the phone: look at the screen, pick one action, do it, repeat. Tapping Capri stops it.
class Agent(
    private val ctx: Context,
    private val brain: Brain,
    private val prefs: Prefs,
    private val progress: (String) -> Unit,
    private val finish: (String) -> Unit
) {
    @Volatile var running = false
        private set
    @Volatile private var cancelled = false

    private val system =
        "You are the hands of Capri, controlling an Android phone to do what the user asked. Each turn you get the goal, your earlier steps and a list of what is on screen as " +
        "[index] kind \"label\" @(x,y). Reply with ONE JSON object and nothing else: {\"say\":\"five words about what you're doing\",\"act\":\"...\"}. " +
        "Acts: tap (\"id\": index), tap_text (\"text\"), type (\"text\"), submit, scroll_down, scroll_up, back, home, open (\"app\": app name), wait, " +
        "done (\"say\": short friendly final line), fail (\"say\": why). " +
        "Rules: finish with done as soon as the goal is reached. Never enter passwords or payment details. Never send messages, buy, delete, uninstall or change settings " +
        "unless the goal says exactly that. If you are stuck after a few tries, use fail. The say text must be in the same language as the goal."

    private val risky = Regex("pay|buy|purchase|order|delete|remove|uninstall|erase|reset|send|confirm|subscribe|transfer", RegexOption.IGNORE_CASE)

    fun cancel() { cancelled = true }

    fun start(goal: String) {
        if (running) return
        if (CapriAccess.instance == null) {
            finish(L.t("I need my accessibility access first. Turn it on in the Capri app.", "أحتاج صلاحية الوصول أول. شغّلها من تطبيق كابري."))
            return
        }
        running = true
        cancelled = false
        Thread {
            var last = L.t("Done.", "خلصت.")
            try { last = loop(goal) } catch (e: Throwable) { last = L.t("Something broke: ", "صار خلل: ") + (e.message ?: "") }
            running = false
            finish(last)
        }.start()
    }

    private fun loop(goal: String): String {
        val trail = ArrayList<String>()
        var bad = 0
        for (step in 1..16) {
            if (cancelled) return L.t("Stopped.", "وقفت.")
            val acc = CapriAccess.instance ?: return L.t("Lost my hands. Is accessibility still on?", "فقدت التحكم. هل الوصول لسا شغال؟")
            val screen = acc.dump(if (brain.isLocal()) 2200 else 3500)
            val image = if (prefs.getB("vision", false) && !brain.isLocal()) acc.shotBlocking() else null
            val user = "Goal: $goal\nSteps so far:\n" + (if (trail.isEmpty()) "(none)" else trail.takeLast(8).joinToString("\n")) + "\n\nCurrent screen:\n$screen"
            val out = brain.completeBlocking(system, user, image)
            val j = parse(out)
            if (j == null) {
                if (++bad >= 3) return L.t("I got confused, sorry.", "تلخبطت، آسفة.")
                trail.add("(your last reply wasn't valid JSON)")
                continue
            }
            val act = j.optString("act").lowercase()
            val say = j.optString("say")
            if (say.isNotBlank() && act != "done" && act != "fail") progress(say)
            var note = act
            when (act) {
                "done" -> return say.ifBlank { L.t("Done.", "خلصت.") }
                "fail" -> return say.ifBlank { L.t("I couldn't do that.", "ما قدرت أسويها.") }
                "tap" -> {
                    val id = j.optInt("id", -1)
                    note = "tap [$id]" + if (acc.clickIndex(id)) "" else " (failed)"
                }
                "tap_text" -> {
                    val t = j.optString("text")
                    if (risky.containsMatchIn(t) && !risky.containsMatchIn(goal)) note = "tap_text \"$t\" refused, it looks risky and the goal didn't ask for it"
                    else note = "tap_text \"$t\"" + if (acc.clickText(t)) "" else " (not found)"
                }
                "type" -> note = "type" + if (acc.typeText(j.optString("text"))) "" else " (no input field found)"
                "submit" -> { acc.submit(); note = "submit" }
                "scroll_down" -> acc.scroll(true)
                "scroll_up" -> acc.scroll(false)
                "back" -> acc.back()
                "home" -> acc.home()
                "open" -> note = "open \"${j.optString("app")}\"" + if (Actions.openApp(ctx, j.optString("app"))) "" else " (no such app)"
                "wait" -> {}
                else -> note = "unknown act $act"
            }
            trail.add("${trail.size + 1}. $note")
            try { Thread.sleep(1300) } catch (e: InterruptedException) { }
        }
        return L.t("That's taking too long, I'll stop here.", "طوّلت، بوقف هنا.")
    }

    private fun parse(s: String): JSONObject? {
        val a = s.indexOf('{')
        val b = s.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return try { JSONObject(s.substring(a, b + 1)) } catch (e: Exception) { null }
    }
}
