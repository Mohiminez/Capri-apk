package app.capri.mobile

// Her notes about you, as a list you can search, pin, edit and delete. The file format is the old one (one "- fact" per line), so nothing is lost.
// A pinned fact starts with "* ": she never forgets it when the list is trimmed to the newest ones.
object Memory {
    const val MAX = 40
    const val MAX_PINNED = 15

    class Fact(val text: String, val pinned: Boolean)

    fun parse(raw: String): List<Fact> = raw.split("\n").map { it.trim() }.filter { it.isNotBlank() }.map {
        if (it.startsWith("* ")) Fact(it.removePrefix("* ").trim(), true) else Fact(it.removePrefix("-").trim(), false)
    }.filter { it.text.isNotEmpty() }

    fun write(facts: List<Fact>): String = facts.joinToString("\n") { (if (it.pinned) "* " else "- ") + it.text }

    /** Keeps every pinned fact (up to [MAX_PINNED]) and fills the rest of [MAX] with the newest others, in their original order. */
    fun trim(facts: List<Fact>): List<Fact> {
        val pinned = facts.filter { it.pinned }.takeLast(MAX_PINNED).toSet()
        val room = MAX - pinned.size
        val others = facts.filter { !it.pinned }.takeLast(maxOf(0, room)).toSet()
        return facts.filter { it in pinned || it in others }
    }

    fun add(raw: String, note: String): String {
        val clean = note.trim().take(200)
        if (clean.isEmpty()) return raw
        val facts = parse(raw)
        if (facts.any { it.text.equals(clean, true) }) return raw
        return write(trim(facts + Fact(clean, false)))
    }

    fun togglePin(raw: String, index: Int): String {
        val f = parse(raw).toMutableList()
        if (index !in f.indices) return raw
        val was = f[index]
        if (!was.pinned && f.count { it.pinned } >= MAX_PINNED) return raw
        f[index] = Fact(was.text, !was.pinned)
        return write(f)
    }

    fun edit(raw: String, index: Int, text: String): String {
        val f = parse(raw).toMutableList()
        if (index !in f.indices) return raw
        val clean = text.trim().take(200)
        if (clean.isEmpty()) f.removeAt(index) else f[index] = Fact(clean, f[index].pinned)
        return write(f)
    }

    fun remove(raw: String, index: Int): String {
        val f = parse(raw).toMutableList()
        if (index !in f.indices) return raw
        f.removeAt(index)
        return write(f)
    }

    /** Indexes of the facts that contain every word of [q]; everything when [q] is blank. */
    fun search(raw: String, q: String): List<Int> {
        val words = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return parse(raw).mapIndexedNotNull { i, f -> if (words.all { f.text.lowercase().contains(it) }) i else null }
    }
}
