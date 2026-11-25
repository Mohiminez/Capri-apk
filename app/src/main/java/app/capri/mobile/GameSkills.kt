package app.capri.mobile

import android.content.Context
import java.io.File

// Game skill packs: short how-to-play notes for a kind of game, in the same ".capri-skill" format the desktop uses
// (front matter with id, name and games, then free text). Some ship with the app; you can import more from the library screen.
// GamePilot reads the ones that match the game it is playing.
object GameSkills {
    class Skill(val id: String, val name: String, val games: List<String>, val body: String)

    fun parse(text: String): Skill? {
        val t = text.replace("\r", "").trim()
        if (!t.startsWith("---")) return null
        val end = t.indexOf("\n---", 3)
        if (end < 0) return null
        val head = t.substring(3, end)
        fun field(k: String) = Regex("(?m)^$k:\\s*(.+)$").find(head)?.groupValues?.get(1)?.trim() ?: ""
        if (field("capri-skill").isEmpty()) return null
        val id = field("id").ifBlank { return null }.lowercase().replace(Regex("[^a-z0-9_-]"), "-").take(40)
        val body = t.substring(end + 4).trim().take(6000)
        if (body.isEmpty()) return null
        return Skill(id, field("name").ifBlank { id }, field("games").split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }, body)
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "gameskills").also { it.mkdirs() }

    fun bundled(ctx: Context): List<Skill> = try {
        (ctx.assets.list("gameskills") ?: emptyArray()).mapNotNull { n -> try { parse(ctx.assets.open("gameskills/$n").bufferedReader().use { it.readText() }) } catch (e: Exception) { null } }
    } catch (e: Exception) { emptyList() }

    fun imported(ctx: Context): List<Skill> = (dir(ctx).listFiles() ?: emptyArray()).mapNotNull { f -> try { parse(f.readText()) } catch (e: Exception) { null } }

    fun all(ctx: Context): List<Skill> = imported(ctx) + bundled(ctx).filter { b -> imported(ctx).none { it.id == b.id } }

    /** Saves an imported pack. Returns its name, or null when the text isn't a skill pack. */
    fun add(ctx: Context, text: String): String? {
        val s = parse(text) ?: return null
        File(dir(ctx), s.id + ".capri-skill").writeText(text.take(8000))
        return s.name
    }

    fun remove(ctx: Context, id: String) { File(dir(ctx), "$id.capri-skill").delete() }

    /** The tips that fit this game (matched by name, either way round), for the prompt. */
    fun forGame(all: List<Skill>, game: String, max: Int = 6000): String {
        val g = game.lowercase().trim()
        if (g.isEmpty()) return ""
        val hits = all.filter { s -> s.games.any { it == g || it.contains(g) || g.contains(it) } }
        val sb = StringBuilder()
        for (s in hits) { if (sb.length + s.body.length > max) break; sb.append("[").append(s.name).append("] ").append(s.body).append("\n") }
        return sb.toString().trim()
    }
}
