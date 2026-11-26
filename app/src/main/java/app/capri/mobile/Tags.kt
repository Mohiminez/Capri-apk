package app.capri.mobile

// The actions inside a reply: [[name:argument]]. Models are sloppy, so this reads what they really write: spaces around the colon,
// a full-width colon, an argument that contains a single "]" (a URL, a list), and several tags in a row. At most MAX_PER_REPLY are
// ever acted on, so a page or a message that tricks her into printing fifty tags still does almost nothing. Pure and unit tested.
object Tags {
    const val MAX_PER_REPLY = 6
    class Tag(val name: String, val arg: String, val range: IntRange)

    val regex = Regex("\\[\\[\\s*(\\w+)\\s*[:\\uFF1A]\\s*((?:[^\\]]|\\](?!\\]))*?)\\s*\\]\\]")

    fun parse(s: String): List<Tag> = regex.findAll(s).map { Tag(it.groupValues[1].lowercase(), it.groupValues[2].trim(), it.range) }.toList()

    /** What gets acted on: the first MAX_PER_REPLY, and the same tag with the same argument only once. */
    fun actable(s: String): List<Tag> {
        val seen = HashSet<String>()
        return parse(s).filter { seen.add(it.name + "\u0000" + it.arg) }.take(MAX_PER_REPLY)
    }

    fun strip(s: String): String = regex.replace(s, "").replace(Regex("\\s+"), " ").trim()
}
