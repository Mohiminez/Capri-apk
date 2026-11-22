package app.capri.mobile

import android.content.Context
import java.io.File

// Her own notes library: text files you add. When you ask something, the few best matching passages go along with it.
// Everything stays on the phone (the passages that match are sent to your model only when they are relevant to a question).
object Knowledge {
    class Chunk(val doc: String, val text: String)

    fun chunk(doc: String, text: String, size: Int = 600): List<Chunk> {
        val out = ArrayList<Chunk>()
        val sb = StringBuilder()
        for (para in text.replace("\r", "").split(Regex("\n\\s*\n"))) {
            val p = para.trim().replace(Regex("\\s+"), " ")
            if (p.isEmpty()) continue
            if (sb.isNotEmpty() && sb.length + p.length > size) { out.add(Chunk(doc, sb.toString())); sb.setLength(0) }
            if (p.length > size) { p.chunked(size).forEach { out.add(Chunk(doc, it)) } } else { if (sb.isNotEmpty()) sb.append(' '); sb.append(p) }
        }
        if (sb.isNotEmpty()) out.add(Chunk(doc, sb.toString()))
        return out
    }

    private val stop = setOf("the", "a", "an", "is", "are", "of", "to", "in", "and", "or", "what", "my", "me", "it", "for", "on", "do", "i", "you", "في", "من", "على", "الى", "إلى", "هل", "ما", "وش", "شنو", "عن", "انا", "أنا")

    // rough stem so "opens"/"open" and "الاجتماع"/"اجتماع" meet: drop the Arabic "ال", keep the first 4 letters of long words
    private fun stem(w: String): String { val x = if (w.startsWith("ال") && w.length > 4) w.substring(2) else w; return if (x.length >= 5) x.take(4) else x }

    private fun words(s: String) = Regex("[\\p{L}\\p{N}]{2,}").findAll(s.lowercase()).map { it.value }.filter { it !in stop }.map { stem(it) }.toList()

    // Best passages for a question, by how many of its words they share. Nothing relevant means nothing returned.
    fun best(chunks: List<Chunk>, query: String, k: Int = 3): List<Chunk> {
        val q = words(query).toSet()
        if (q.isEmpty()) return emptyList()
        return chunks.map { c -> Pair(c, words(c.text).toSet().count { it in q }) }
            .filter { it.second >= minOf(2, q.size) }
            .sortedByDescending { it.second }.take(k).map { it.first }
    }

    // ---- storage: one text file, passages separated by a marker, encrypted with the Android Keystore ----
    private const val SEP = "\n\u0001\n"
    private fun file(ctx: Context) = File(ctx.filesDir, "knowledge.txt")

    // pure, so it can be tested without a phone
    fun pack(all: List<Chunk>): String = all.joinToString(SEP) { it.doc.replace('\u0002', ' ') + "\u0002" + it.text.replace('\u0001', ' ') }
    fun unpack(text: String): List<Chunk> = text.split(SEP).filter { it.contains('\u0002') }.map { Chunk(it.substringBefore('\u0002'), it.substringAfter('\u0002')) }

    /** Swap these in tests; on the phone they are the Keystore. */
    internal var seal: (String) -> String = { Secret.enc(it) }
    internal var unseal: (String) -> String = { Secret.dec(it) }

    private fun write(ctx: Context, all: List<Chunk>): Boolean {
        if (all.isEmpty()) { file(ctx).delete(); return true }
        val sealed = seal(pack(all))
        if (sealed.isEmpty()) return false // could not encrypt: keep what is there, never write her library as plain text
        file(ctx).writeText(sealed)
        return true
    }

    fun encrypted(ctx: Context): Boolean = try { file(ctx).takeIf { it.exists() }?.useLines { it.firstOrNull() }?.startsWith("enc:") ?: true } catch (e: Exception) { false }

    fun load(ctx: Context): List<Chunk> = try {
        val f = file(ctx)
        if (!f.exists()) emptyList() else {
            val raw = f.readText()
            val legacy = !raw.startsWith("enc:")
            val chunks = unpack(if (legacy) raw else unseal(raw))
            if (legacy && chunks.isNotEmpty()) write(ctx, chunks) // older versions kept it as plain text: lock it on first read
            chunks
        }
    } catch (e: Exception) { emptyList() }

    fun add(ctx: Context, doc: String, text: String): Int {
        val cs = chunk(doc, text.take(200_000))
        if (cs.isEmpty()) return 0
        return if (write(ctx, load(ctx).filter { it.doc != doc } + cs)) cs.size else 0
    }

    fun docs(ctx: Context): List<String> = load(ctx).map { it.doc }.distinct()
    fun remove(ctx: Context, doc: String) { write(ctx, load(ctx).filter { it.doc != doc }) }
    fun clear(ctx: Context) { file(ctx).delete() }

    fun context(ctx: Context, query: String): String {
        val hits = best(load(ctx), query)
        if (hits.isEmpty()) return ""
        return "\nFrom the user's own notes (use only if relevant, never read them out word for word):\n" + hits.joinToString("\n") { "- [${it.doc}] ${it.text.take(600)}" }
    }
}
