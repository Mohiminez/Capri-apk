package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class MusicAppsTest {
    private fun p(s: String) = MusicApps.parse(s)

    @Test fun englishOnApp() {
        p("play Blinding Lights on Spotify")!!.let { assertEquals("spotify", it.app.key); assertEquals("blinding lights", it.query) }
        p("Hey Capri, please play Mirrors by Justin Timberlake on Deezer")!!.let { assertEquals("deezer", it.app.key); assertEquals("mirrors by justin timberlake", it.query) }
        p("play lofi beats on youtube music")!!.let { assertEquals("ytmusic", it.app.key); assertEquals("lofi beats", it.query) }
        p("play some jazz from Anghami")!!.let { assertEquals("anghami", it.app.key); assertEquals("some jazz", it.query) }
    }

    @Test fun titleContainingOn() {
        p("play dancing on my own on spotify")!!.let { assertEquals("spotify", it.app.key); assertEquals("dancing on my own", it.query) }
    }

    @Test fun youtubeMusicBeatsYoutube() {
        assertEquals("ytmusic", p("play x on youtube music")!!.app.key)
        assertEquals("youtube", p("play x on youtube")!!.app.key)
    }

    @Test fun arabic() {
        p("شغل لي اغنية بحبك وحشتيني على انغامي")!!.let { assertEquals("anghami", it.app.key); assertEquals("بحبك وحشتيني", it.query) }
        p("شغل كاظم الساهر من سبوتيفاي")!!.let { assertEquals("spotify", it.app.key); assertEquals("كاظم الساهر", it.query) }
        p("شغلي حسين الجسمي بديزر")!!.let { assertEquals("deezer", it.app.key); assertEquals("حسين الجسمي", it.query) }
        p("يا كابري شغل اغاني هادية على يوتيوب ميوزك")!!.let { assertEquals("ytmusic", it.app.key) }
    }

    @Test fun appFirst() {
        p("play on spotify blinding lights")!!.let { assertEquals("spotify", it.app.key); assertEquals("blinding lights", it.query) }
        p("شغل من انغامي ماجد المهندس")!!.let { assertEquals("anghami", it.app.key); assertEquals("ماجد المهندس", it.query) }
    }

    @Test fun plainAsksAreLeftAlone() {
        assertNull(p("play blinding lights"))
        assertNull(p("شغل كاظم الساهر"))
        assertNull(p("play chess on the phone"))
        assertNull(p("open spotify"))
    }

    @Test fun emptyQuery() {
        assertEquals("", p("play music on spotify")!!.query)
        assertEquals("", p("شغل موسيقى على انغامي")!!.query)
    }

    @Test fun tagRoundTrip() {
        val a = p("play Blinding Lights on Spotify")!!
        val tag = MusicApps.tag(a)
        assertEquals("[[music:blinding lights|spotify]]", tag)
        val (q, app) = MusicApps.split("blinding lights|spotify")
        assertEquals("blinding lights", q); assertEquals("spotify", app!!.key)
        // a song with a bar in it and no app stays whole
        val (q2, app2) = MusicApps.split("a | b")
        assertEquals("a | b", q2); assertNull(app2)
    }

    @Test fun appOnlyTag() {
        val (q, app) = MusicApps.split("|anghami")
        assertEquals("", q); assertEquals("anghami", app!!.key)
        assertEquals("[[music:|spotify]]", MusicApps.tag(MusicApps.parse("play music on spotify")!!))
    }

    @Test fun webFallbackEncodes() {
        assertEquals("https://open.spotify.com/search/blinding%20lights", MusicApps.byKey("spotify")!!.searchUrl("blinding lights"))
        assertEquals("https://music.youtube.com/search?q=a%26b", MusicApps.byKey("ytmusic")!!.searchUrl("a&b"))
    }

    @Test fun everyAppHasAPackageAndWebUrl() {
        for (a in MusicApps.all) { assertTrue(a.name, a.packages.isNotEmpty()); assertTrue(a.searchUrl("x").startsWith("https://")) }
    }
}
