package app.capri.mobile

import android.content.Context
import org.json.JSONObject
import java.io.File

// A mod is a small .json file of look-and-feel settings: colours, a few extra personality lines, a nickname, a tone and her voice effects.
// It is data only. It can never carry code, keys, providers, tools or permissions.
class Mod(
    val id: String, val name: String, val nameAr: String, val author: String, val desc: String, val descAr: String,
    val accent: String, val colors: Map<String, String>, val persona: String, val nickname: String, val tone: Int?,
    val voice: Map<String, Int>, val settings: Map<String, Any>, val builtin: Boolean = false
) {
    fun title(ar: Boolean) = if (ar && nameAr.isNotBlank()) nameAr else name
    fun about(ar: Boolean) = if (ar && descAr.isNotBlank()) descAr else desc
}

object Mods {
    private val hex = Regex("^#[0-9A-Fa-f]{6}$")
    val colorSlots = listOf("hair", "accent", "eyes", "edges", "body")
    private val voiceRanges = mapOf("speed" to (60..160), "pitch" to (60..160), "robot" to (0..100), "glitch" to (0..100), "layers" to (0..100), "arPitch" to (80..160))
    private val voiceKeys = mapOf("speed" to "ttsSpeed", "pitch" to "ttsPitch", "robot" to "voiceRobot", "glitch" to "voiceGlitch", "layers" to "voiceLayers", "arPitch" to "arPitch")
    // The only plain settings a mod may touch.
    private val allowedSettings = setOf("size", "theme", "quiet", "sounds", "lang")

    fun slug(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)

    private fun clean(s: String, max: Int) = s.replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "").trim().take(max)

    internal fun readObject(text: String): Map<String, Any?>? = ModJson.readObject(text)

    private fun str(m: Map<String, Any?>?, k: String): String = (m?.get(k) as? String) ?: ""
    private fun int(m: Map<String, Any?>?, k: String): Int? = (m?.get(k) as? Number)?.toInt()
    @Suppress("UNCHECKED_CAST")
    private fun obj(m: Map<String, Any?>?, k: String): Map<String, Any?>? = m?.get(k) as? Map<String, Any?>

    // Reads and checks a mod. Anything out of range is clamped, anything unknown is ignored. Returns null when it is not a mod at all.
    fun parse(text: String, builtin: Boolean = false): Mod? {
        val j = readObject(text) ?: return null
        val name = clean(str(j, "name"), 40)
        if (name.isEmpty()) return null
        val id = slug(str(j, "id")).ifEmpty { slug(name) }.ifEmpty { "mod" + (System.nanoTime() % 100000) }
        val accent = str(j, "accent").let { if (hex.matches(it)) it.uppercase() else "" }
        val colors = LinkedHashMap<String, String>()
        obj(j, "colors")?.let { c ->
            for (slot in colorSlots) { val v = str(c, slot); colors[slot] = if (hex.matches(v)) v.uppercase() else "" }
        }
        val voice = LinkedHashMap<String, Int>()
        obj(j, "voice")?.let { v ->
            for ((k, r) in voiceRanges) int(v, k)?.let { voice[k] = it.coerceIn(r.first, r.last) }
        }
        val settings = LinkedHashMap<String, Any>()
        obj(j, "settings")?.let { st ->
            for ((k, v) in st) if (k in allowedSettings) when (v) {
                is Boolean -> settings[k] = v
                is Number -> settings[k] = v.toInt()
                is String -> settings[k] = clean(v, 200)
            }
        }
        val tone = int(j, "tone")?.coerceIn(0, 3)
        return Mod(id, name, clean(str(j, "name_ar"), 40), clean(str(j, "author"), 40),
            clean(str(j, "description"), 300), clean(str(j, "description_ar"), 300),
            accent, colors, clean(str(j, "personality"), 2000), clean(str(j, "nickname"), 30), tone, voice, settings, builtin)
    }

    // What turning the mod on writes into her settings: setting name -> new value (String, Int or Boolean).
    fun changes(m: Mod): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        if (m.accent.isNotEmpty()) out["modAccent"] = m.accent
        if (m.colors.isNotEmpty()) for (slot in colorSlots) out["col_$slot"] = m.colors[slot] ?: ""
        if (m.persona.isNotEmpty()) out["modPersona"] = m.persona
        if (m.nickname.isNotEmpty()) out["nick"] = m.nickname
        if (m.tone != null) out["tone"] = m.tone
        for ((k, v) in m.voice) out[voiceKeys[k] ?: continue] = v
        for ((k, v) in m.settings) out[k] = v
        return out
    }

    fun template(): String = """{
  "capriMod": 2,
  "name": "My Mod",
  "name_ar": "مودي",
  "author": "me",
  "description": "What it does, in one line.",
  "accent": "#FF7AC6",
  "colors": { "hair": "#FF7AC6", "accent": "#7DF9FF", "eyes": "#B14CFF", "edges": "#FFFFFF", "body": "" },
  "personality": "She is extra dramatic about everything.",
  "nickname": "boss",
  "tone": 0,
  "voice": { "speed": 105, "pitch": 100, "robot": 0, "glitch": 6, "layers": 20, "arPitch": 130 }
}"""

    // ---- built in ----

    private fun b(name: String, nameAr: String, desc: String, descAr: String, accent: String = "", colors: Map<String, String> = emptyMap(),
                  persona: String = "", tone: Int? = null, voice: Map<String, Int> = emptyMap()) =
        Mod(slug(name), name, nameAr, "Capri", desc, descAr, accent, colors, persona, "", tone, voice, emptyMap(), true)

    private fun cs(h: String, a: String, e: String, ed: String, bo: String) = mapOf("hair" to h, "accent" to a, "eyes" to e, "edges" to ed, "body" to bo)

    val builtins: List<Mod> = listOf(
        b("Bubblegum", "علكة", "Pink hair, cyan accents, purple eyes. Sweet to look at.", "شعر وردي وتفاصيل سيان وعيون بنفسجية.", "#FF7AC6", cs("#FF7AC6", "#7DF9FF", "#B14CFF", "#FFFFFF", "#3A1B3F")),
        b("Cyber Cyan", "سايبر سيان", "Cyan hair, magenta accents, gold eyes.", "شعر سيان وتفاصيل ماجنتا وعيون ذهبية.", "#00D8FF", cs("#00D8FF", "#FF2BD6", "#FFD000", "#7A5CFF", "")),
        b("Royal", "ملكي", "Violet hair, gold accents, dark body.", "شعر بنفسجي وتفاصيل ذهبية وجسم داكن.", "#8A2BE2", cs("#8A2BE2", "#FFD54A", "#00C2FF", "#FF4FA3", "#120A26")),
        b("Toxic", "سام", "Acid green hair, hot red accents.", "شعر أخضر حامض وتفاصيل حمراء.", "#7CFF00", cs("#7CFF00", "#FF2E63", "#00E5FF", "#00A86B", "#0B1F12")),
        b("Robo Capri", "كابري الروبوت", "A glitchier, metallic voice with layered echoes.", "صوت معدني فيه قلتش وأصداء.", "#00E5FF", persona = "Your signal is unstable today: glitch a little more than usual, and now and then a short [SIGNAL CORRUPTED] fits.",
            voice = mapOf("robot" to 35, "glitch" to 18, "layers" to 35)),
        b("Gulf Gremlin", "شقاوة خليجية", "Teases in Gulf Arabic when you write Arabic. Cheeky, warm, never mean.", "تسولف معك بلهجة خليجية فيها شقاوة ودفا.", "#FF2A6D",
            persona = "When the user writes Arabic, answer in relaxed Gulf Arabic (Iraqi and Gulf words are welcome) with cheeky teasing, short sentences and feminine forms for yourself. Never rude, never mean.", tone = 3),
        b("Study Buddy", "رفيقة الدراسة", "Calm focus coach: short answers, gentle nudges to take breaks.", "مدرّبة تركيز هادية: ردود قصيرة وتذكير بالراحة.", "#3DFFC0",
            persona = "Right now you are a focus buddy. Keep replies very short. When they say they are studying or working, encourage them once, then stay quiet. Suggest a short break about once an hour if they are still around.", tone = 1),
        b("Business Mode", "وضع العمل", "Direct and practical, almost no slang.", "مباشرة وعملية وبدون سلانق تقريباً.", "#1870F0",
            persona = "Be direct and practical. Confirm actions briefly. No slang unless they use it first.", tone = 2)
    )

    // ---- library on disk ----

    private fun dir(ctx: Context): File = File(ctx.filesDir, "mods").also { it.mkdirs() }

    fun installed(ctx: Context): List<Mod> =
        (dir(ctx).listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()).sortedBy { it.name }.mapNotNull { f ->
            try { parse(f.readText()) } catch (e: Exception) { null }
        }

    fun all(ctx: Context): List<Mod> = builtins + installed(ctx).filter { m -> builtins.none { it.id == m.id } }

    fun save(ctx: Context, m: Mod, text: String): Boolean {
        if (text.length > 20_000) return false
        File(dir(ctx), m.id + ".json").writeText(text)
        return true
    }

    fun delete(ctx: Context, id: String) { File(dir(ctx), slug(id) + ".json").delete() }

    // ---- turning one on and off ----

    fun active(p: Prefs): String = p.getS("mod_active", "")

    fun enable(p: Prefs, m: Mod) {
        if (active(p).isNotEmpty()) disable(p)
        val ch = changes(m)
        val backup = JSONObject()
        for (k in ch.keys) {
            val cur = p.raw(k)
            backup.put(k, when (cur) {
                null -> JSONObject().put("t", "n")
                is Boolean -> JSONObject().put("t", "b").put("v", cur)
                is Int -> JSONObject().put("t", "i").put("v", cur)
                else -> JSONObject().put("t", "s").put("v", cur.toString())
            })
        }
        p.putS("mod_backup", backup.toString())
        for ((k, v) in ch) when (v) { is Boolean -> p.putB(k, v); is Int -> p.putI(k, v); else -> p.putS(k, v.toString()) }
        p.putS("mod_active", m.id)
    }

    fun disable(p: Prefs) {
        try {
            val b = JSONObject(p.getS("mod_backup", "{}"))
            for (k in b.keys()) {
                val o = b.getJSONObject(k)
                when (o.optString("t")) {
                    "b" -> p.putB(k, o.getBoolean("v"))
                    "i" -> p.putI(k, o.getInt("v"))
                    "s" -> p.putS(k, o.getString("v"))
                    else -> p.remove(k)
                }
            }
        } catch (e: Exception) { }
        p.putS("mod_backup", "")
        p.putS("mod_active", "")
    }
}
