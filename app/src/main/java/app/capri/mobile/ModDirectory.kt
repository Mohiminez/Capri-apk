package app.capri.mobile

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The mod directory, the same one capri-desktop's Mods window browses: the capri-mods repository on GitHub.
 * Its index.json lists every mod. A phone can use skins (.capri-skin) and settings mods (.capri-mod); code mods, tools and
 * companions run C# or MCP on a PC, so they are counted but not offered. Nothing is installed without the person pressing Install.
 * Parsing and filtering are pure (unit tested); the download is the only part that touches the network.
 */
object ModDirectory {
    const val PAGE = "https://github.com/lachydotmcg/capri-mods"
    const val RAW = "https://raw.githubusercontent.com/lachydotmcg/capri-mods/HEAD/"
    const val INDEX = RAW + "index.json"
    const val MAX_INDEX = 2_000_000L
    const val MAX_FILE = 96L * 1024 * 1024
    private val safeName = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")

    class Listing(val folder: String, val file: String, val name: String, val kind: String, val author: String, val about: String) {
        val usable get() = kind == "skin" || kind == "settings"
        val url get() = RAW + "mods/" + folder + "/" + file
        val preview get() = RAW + "mods/" + folder + "/preview.png"
    }

    class Parsed(val usable: List<Listing>, val hidden: Int)

    /** The file's extension decides what it is: the index's own "kind" is not reliable (colour mods are listed as skins). */
    fun kindOf(file: String): String = when (file.substringAfterLast('.', "").lowercase()) {
        "capri-skin" -> "skin"; "capri-mod" -> "settings"; else -> "other"
    }

    /** Reads index.json. Entries with odd folder or file names are dropped (they become part of a URL and a file name). */
    fun parse(json: String): Parsed {
        val root = ModJson.readObject(json) ?: return Parsed(emptyList(), 0)
        val list = root["mods"] as? List<*> ?: return Parsed(emptyList(), 0)
        val ok = ArrayList<Listing>(); var hidden = 0
        for (e in list) {
            val m = e as? Map<*, *> ?: continue
            fun s(k: String, max: Int) = ((m[k] as? String) ?: "").replace(Regex("[\\u0000-\\u001F]"), " ").trim().take(max)
            val folder = s("folder", 64); val file = s("file", 64)
            if (!safeName.matches(folder) || !safeName.matches(file)) continue
            val l = Listing(folder, file, s("name", 60).ifEmpty { folder }, kindOf(file), s("author", 40), s("about", 300))
            if (l.usable) ok.add(l) else hidden++
        }
        return Parsed(ok.sortedBy { it.name.lowercase() }, hidden)
    }

    /** kind: "" = all, "skin", "settings". */
    fun filter(all: List<Listing>, search: String, kind: String): List<Listing> {
        val q = search.trim().lowercase()
        return all.filter { (kind.isEmpty() || it.kind == kind) && (q.isEmpty() || (it.name + " " + it.author + " " + it.about).lowercase().contains(q)) }
    }

    fun kindLabel(kind: String, ar: Boolean) = when (kind) {
        "skin" -> if (ar) "سكن" else "Skin"
        "settings" -> if (ar) "مود إعدادات" else "Settings mod"
        else -> kind
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Capri-Android")
        if (c.responseCode != 200) throw java.io.IOException("HTTP " + c.responseCode)
        return c
    }

    fun fetchIndex(): Parsed {
        val c = open(INDEX)
        val text = c.inputStream.use { ins ->
            val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16 * 1024); var total = 0L
            while (true) { val n = ins.read(buf); if (n < 0) break; total += n; if (total > MAX_INDEX) throw java.io.IOException("too big"); out.write(buf, 0, n) }
            out.toString("UTF-8")
        }
        return parse(text)
    }

    /** Downloads one mod's file into [dest]; [progress] gets 0..100 when the size is known. */
    fun download(l: Listing, dest: File, progress: (Int) -> Unit) {
        val c = open(l.url)
        val size = c.contentLengthLong
        if (size > MAX_FILE) throw java.io.IOException("too big")
        dest.parentFile?.mkdirs()
        c.inputStream.use { ins -> dest.outputStream().use { out ->
            val buf = ByteArray(64 * 1024); var done = 0L; var last = -1
            while (true) {
                val n = ins.read(buf); if (n < 0) break
                done += n; if (done > MAX_FILE) throw java.io.IOException("too big")
                out.write(buf, 0, n)
                if (size > 0) { val p = (done * 100 / size).toInt(); if (p != last) { last = p; progress(p) } }
            }
        } }
    }
}
