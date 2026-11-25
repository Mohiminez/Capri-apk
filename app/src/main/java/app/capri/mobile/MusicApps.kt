package app.capri.mobile

import java.net.URLEncoder

// "play Blinding Lights on Spotify", "شغل لي اغنية على انغامي": the song AND the app it should play in.
// Pure logic (no Android), so it is unit tested. Actions.playMusic turns the result into an Intent.
// capri-desktop 0.9.7 added Deezer and YouTube Music next to Spotify, Apple Music and YouTube; Anghami is the big one in the Arab world.
class MusicApp(val key: String, val name: String, val packages: List<String>, private val web: String, val names: List<String>) {
    fun searchUrl(query: String): String = web.replace("%s", URLEncoder.encode(query, "UTF-8").replace("+", "%20"))
}

class MusicAsk(val app: MusicApp, val query: String)

object MusicApps {
    val all: List<MusicApp> = listOf(
        MusicApp("spotify", "Spotify", listOf("com.spotify.music"), "https://open.spotify.com/search/%s",
            listOf("spotify", "سبوتيفاي", "سبوتفاي", "سبوتيفي", "سبوتفي")),
        MusicApp("anghami", "Anghami", listOf("com.anghami"), "https://play.anghami.com/search/%s",
            listOf("anghami", "انغامي", "انجامي", "انغامى", "أنغامي", "أنجامي")),
        MusicApp("deezer", "Deezer", listOf("deezer.android.app"), "https://www.deezer.com/search/%s",
            listOf("deezer", "ديزر", "ديزير")),
        MusicApp("ytmusic", "YouTube Music", listOf("com.google.android.apps.youtube.music"), "https://music.youtube.com/search?q=%s",
            listOf("youtube music", "yt music", "ytmusic", "يوتيوب ميوزك", "يوتيوب ميوزيك", "يوتيوب موزك", "يوتيوب موسيقى", "يوتيوب ميوزك")),
        MusicApp("applemusic", "Apple Music", listOf("com.apple.android.music"), "https://music.apple.com/search?term=%s",
            listOf("apple music", "ابل ميوزك", "أبل ميوزك", "ابل ميوزيك")),
        MusicApp("soundcloud", "SoundCloud", listOf("com.soundcloud.android"), "https://soundcloud.com/search?q=%s",
            listOf("soundcloud", "ساوند كلاود", "ساوندكلاود")),
        MusicApp("youtube", "YouTube", listOf("com.google.android.youtube"), "https://www.youtube.com/results?search_query=%s",
            listOf("youtube", "you tube", "يوتيوب", "اليوتيوب"))
    )

    fun byKey(key: String): MusicApp? = all.firstOrNull { it.key == key.trim().lowercase() }

    private fun norm(s: String): String = s.lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي').replace('ة', 'ه')
        .replace(Regex("[^a-z0-9 ؀-ۿ]"), " ").replace(Regex("\\s+"), " ").trim()

    // alias (normalised) -> app, longest first so "youtube music" wins over "youtube"
    private val aliasList: List<Pair<String, MusicApp>> =
        all.flatMap { a -> a.names.map { norm(it) to a } }.distinctBy { it.first }.sortedByDescending { it.first.length }

    fun byName(name: String): MusicApp? { val n = norm(name); return aliasList.firstOrNull { it.first == n }?.second }

    private val aliasRx = aliasList.joinToString("|") { Regex.escape(it.first) }
    private val en = Regex("^(?:hey capri |ok capri )?(?:please |can you |could you )*(?:play|put on|start|stream) (.+?) (?:on|in|using|with|from|through|via) ($aliasRx)(?: please)?$")
    private val ar = Regex("^(?:يا كابري )?(?:شغل|شغلي|شغللي|شغل لي|شغلي لي|دز|حط|حطلي|حط لي|اسمعني|سمعني|ابي اسمع|ابغى اسمع) (.+?) (?:على|علي|من|في|عبر|عن طريق|ب) ?($aliasRx)(?: لو سمحت)?$")
    // "play on spotify Blinding Lights" / "شغل من سبوتيفاي اغنية كذا"
    private val enFront = Regex("^(?:hey capri |ok capri )?(?:please )?(?:play|put on) (?:on|in|from) ($aliasRx) (.+)$")
    private val arFront = Regex("^(?:يا كابري )?(?:شغل|شغلي|شغللي|دز|حط) (?:من|على|علي|في|عبر|ب) ?($aliasRx) (.+)$")

    fun parse(raw: String): MusicAsk? {
        val t = norm(raw)
        if (t.length < 5) return null
        en.find(t)?.let { return make(it.groupValues[2], it.groupValues[1]) }
        ar.find(t)?.let { return make(it.groupValues[2], it.groupValues[1]) }
        enFront.find(t)?.let { return make(it.groupValues[1], it.groupValues[2]) }
        arFront.find(t)?.let { return make(it.groupValues[1], it.groupValues[2]) }
        return null
    }

    private fun make(alias: String, q: String): MusicAsk? {
        val app = byName(alias) ?: return null
        var query = q.trim()
        // "شغل لي اغنية X": the verb alternatives match "شغل" first, so the leftovers are dropped here
        for (i in 0 until 3) query = query.removePrefix("لي ").removePrefix("the ").removePrefix("song ").removePrefix("اغنيه ").removePrefix("اغنية ").trim()
        if (query.isEmpty() || Regex("^(?:something|some music|music|اي شي|شي|اغنيه|اغاني|موسيقي|موسيقى)$").matches(query)) return MusicAsk(app, "")
        return MusicAsk(app, query)
    }

    // The tag Capri emits and Actions understands: [[music:song|appkey]]
    fun tag(a: MusicAsk): String = "[[music:" + a.query.replace("]", ")").replace("|", "/") + "|" + a.app.key + "]]"

    // Splits a music tag argument back into (song, app). A plain "song name" has no app.
    fun split(arg: String): Pair<String, MusicApp?> {
        val i = arg.lastIndexOf('|')
        if (i >= 0) byKey(arg.substring(i + 1))?.let { return arg.substring(0, i).trim() to it }
        return arg.trim() to null
    }
}
