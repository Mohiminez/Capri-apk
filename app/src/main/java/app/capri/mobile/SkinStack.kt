package app.capri.mobile

// Stacking skins, the rules (pure, unit tested). A ".capri-skin" from capri-desktop is a zip with skin.json and PNG strips named like her
// own animations (idle.png ...). The bottom skin is drawn whole; an "accessory" skin (cat ears, a hat) is see-through except what it adds
// and goes on top of whatever is under it. A strip only counts when it is exactly as tall as her frame and a whole number of frames wide.
object SkinStack {
    class Meta(val id: String, val name: String, val accessory: Boolean)

    fun slug(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)

    private fun str(json: String, key: String): String? =
        Regex("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\\\", "\\")

    /** skin.json -> Meta, or null when it has no usable id. */
    fun meta(json: String): Meta? {
        val name = (str(json, "name") ?: "").trim().take(60)
        val id = slug(str(json, "id") ?: name)
        if (id.isEmpty()) return null
        val acc = Regex("\"accessory\"\\s*:\\s*true").containsMatchIn(json)
        return Meta(id, name.ifEmpty { id }, acc)
    }

    /** A zip entry that may be read: a top-level lowercase strip name. Folders, "..", other extensions and odd names are refused. */
    fun stripName(entry: String): String? {
        val m = Regex("^([a-z0-9_]{1,40})\\.png$").matchEntire(entry) ?: return null
        return m.groupValues[1]
    }

    /** Turn a skin on, or off if it is already on. Turning on puts it on top ("newest on top"). */
    fun toggle(on: List<String>, id: String): List<String> = if (id in on) on.filter { it != id } else on + id

    /** The stack bottom to top, without skins that are no longer installed or repeated. */
    fun usable(on: List<String>, installed: Set<String>): List<String> = on.filter { it in installed }.distinct()

    /** Number of frames in a strip that fits her frame size (frame w x h), or 0 when it doesn't fit. */
    fun frames(sheetW: Int, sheetH: Int, w: Int, h: Int): Int =
        if (w <= 0 || h <= 0 || sheetH != h || sheetW < w || sheetW % w != 0 || sheetW / w > 240) 0 else sheetW / w
}
