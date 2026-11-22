package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class ChessEngineTest {
    @Test fun fenRoundTrip() {
        assertEquals(Fen.START, Fen.write(Fen.parse(Fen.START)!!))
        val p = Fen.parse("r3k2r/pppq1ppp/2n5/3pP3/8/5N2/PPP2PPP/R2QK2R w KQkq d6 0 1")!!
        assertEquals("r3k2r/pppq1ppp/2n5/3pP3/8/5N2/PPP2PPP/R2QK2R w KQkq d6 0 1", Fen.write(p))
        assertNull(Fen.parse("nonsense"))
        assertNull(Fen.parse("8/8/8/8/8/8/8/8 w - - 0 1"))          // no kings
    }
    @Test fun findsMateInOne() {
        val p = Fen.parse("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")!!
        val a = Engine.analyse(p, 1500)!!
        assertEquals("a1a8", a.move.toString())
        assertEquals(1, a.mateIn)
    }
    @Test fun winsAHangingQueen() {
        val p = Fen.parse("rnb1kbnr/pppp1ppp/8/4p3/4P2q/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1")!!
        val a = Engine.analyse(p, 1500)!!
        assertEquals("f3h4", a.move.toString())
    }
    @Test fun rejectsAnIllegalPosition() {
        assertNull(Fen.parse("4k3/8/8/3p4/4Q3/8/8/4K3 w - - 0 1"))   // the side not to move is in check
    }
    @Test fun playsALegalMoveFromTheStart() {
        val a = Engine.analyse(Chess.start(), 600)!!
        assertTrue(Chess.legal(Chess.start()).any { it.toString() == a.move.toString() })
    }
    @Test fun sanNames() {
        val s = Chess.start()
        assertEquals("e4", San.of(s, Chess.legal(s).first { it.toString() == "e2e4" }))
        assertEquals("Nf3", San.of(s, Chess.legal(s).first { it.toString() == "g1f3" }))
        val c = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")!!
        assertEquals("O-O", San.of(c, Chess.legal(c).first { it.toString() == "e1g1" }))
        assertEquals("O-O-O", San.of(c, Chess.legal(c).first { it.toString() == "e1c1" }))
        val pr = Fen.parse("8/P6k/8/8/8/8/8/K7 w - - 0 1")!!
        assertEquals("a8=Q", San.of(pr, Chess.legal(pr).first { it.toString() == "a7a8q" }))
        val dis = Fen.parse("4k3/8/8/8/8/8/4K3/R6R w - - 0 1")!!
        assertEquals("Rad1", San.of(dis, Chess.legal(dis).first { it.toString() == "a1d1" }))
        val mate = Fen.parse("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1")!!
        assertEquals("Ra8#", San.of(mate, Chess.legal(mate).first { it.toString() == "a1a8" }))
    }
    @Test fun strongerThanTheQuickSearchOnATactic() {
        // knight fork wins the queen: Nc7+ forks king and rook
        val p = Fen.parse("r3k3/8/8/3N4/8/8/8/4K3 w - - 0 1")!!
        val a = Engine.analyse(p, 1500)!!
        assertEquals("d5c7", a.move.toString())
    }
}

class ChessScreenAskTest {
    @Test fun phrases() {
        assertTrue(QuickAsks.match("what's the best move")!!.contains("[[chess:screen]]") || QuickAsks.match("what is the best move")!!.contains("[[chess:screen]]"))
        assertTrue(QuickAsks.match("help me with this chess game")!!.contains("[[chess:screen]]"))
        assertTrue(QuickAsks.match("play chess")!!.contains("[[chess:open]]"))
        assertTrue(QuickAsks.match("learn my pieces")!!.contains("[[chess:scan]]"))
        assertTrue(QuickAsks.match("scan the chess board")!!.contains("[[chess:scan]]"))
        assertTrue(QuickAsks.match("تعلمي قطعي")!!.contains("[[chess:scan]]"))
    }
}

class ChessAutoPhraseTest {
    @org.junit.Test fun looseWordOrderStartsAuto() {
        for (s in listOf("العب عني شطرنج", "العب شطرنج عني", "كابري العب عني الشطرنج", "كملي لي لعبة الشطرنج", "لو سمحتي العبي عني", "play chess for me", "play the chess game for me", "العب عني"))
            org.junit.Assert.assertEquals(s, "auto", QuickAsks.chessAuto(s))
    }
    @org.junit.Test fun otherSentencesAreLeftAlone() {
        for (s in listOf("نلعب شطرنج", "شنو افضل نقلة", "العب اغنية", "علميني الشطرنج", "play chess with me"))
            org.junit.Assert.assertNull(s, QuickAsks.chessAuto(s))
    }
}

class GuideAndProtectTest {
    @org.junit.Test fun guideHasEveryTopicInBothLanguages() {
        val ids = Guide.topics.map { it.id }
        org.junit.Assert.assertEquals(ids.size, ids.toSet().size)
        for (t in Guide.topics) {
            org.junit.Assert.assertTrue(t.id, t.en.isNotBlank() && t.ar.isNotBlank() && t.bodyEn.length > 40 && t.bodyAr.length > 40)
            org.junit.Assert.assertEquals(t.id, t.tryEn.isEmpty(), t.tryAr.isEmpty())
        }
        for (id in listOf("start", "map", "chess", "privacy", "fix")) org.junit.Assert.assertNotNull(id, Guide.find(id))
    }
    @org.junit.Test fun guideSearchFindsByEitherLanguage() {
        org.junit.Assert.assertEquals("chess", Guide.search("stockfish", false).first().id)
        org.junit.Assert.assertEquals("chess", Guide.search("الشطرنج", true).first().id)
        org.junit.Assert.assertTrue(Guide.search("accessibility restricted", false).any { it.id == "fix" })
        org.junit.Assert.assertTrue(Guide.search("zzzzqq", false).isEmpty())
        org.junit.Assert.assertEquals(Guide.topics.size, Guide.search("", false).size)
    }
    @org.junit.Test fun helpAndSecurityPhrases() {
        for (s in listOf("help", "الدليل", "افتح الدليل", "what can you do", "شنو تقدرين تسوين")) org.junit.Assert.assertTrue(s, QuickAsks.match(s)?.contains("[[guide:open]]") == true)
        for (s in listOf("security center", "مركز الأمان", "is capri safe")) org.junit.Assert.assertTrue(s, QuickAsks.match(s)?.contains("[[security:open]]") == true)
    }
    @org.junit.Test fun actionLogHidesPersonalData() {
        val a = ActionLog.scrub("open https://bank.example.com/login?user=ali&token=abc123 and mail ali@x.com card 4111 1111 1111 1111 call +964 770 123 4567")
        org.junit.Assert.assertFalse(a, a.contains("login") || a.contains("ali@x.com") || a.contains("4111") || a.contains("770 123"))
        org.junit.Assert.assertTrue(a, a.contains("https://bank.example.com/..."))
        org.junit.Assert.assertEquals("remind", ActionLog.describe("remind", "call mum at 5"))
    }
    @org.junit.Test fun publicKeyIsRecognised() {
        org.junit.Assert.assertTrue(Protect.isPublicKey("7F:C8:B6:5F:EE:DC:8F:48:A5:34:6A:4F:FA:0D:F8:EE:A4:13:A0:42:5C:F9:F7:0B:7C:38:BB:FB:CC:6D:A8:45"))
        org.junit.Assert.assertFalse(Protect.isPublicKey("00"))
    }
    @org.junit.Test fun plainHttpOnlyToPrivateAddresses() {
        org.junit.Assert.assertThrows(Exception::class.java) { Net.checkKey("http://example.com/v1", "") }
        org.junit.Assert.assertThrows(Exception::class.java) { Net.checkKey("http://8.8.8.8/v1", "k") }
        Net.checkKey("http://192.168.1.5:11434", ""); Net.checkKey("http://localhost:11434", "k"); Net.checkKey("https://api.example.com", "k")
    }
    @org.junit.Test fun libraryPacksAndUnpacks() {
        val cs = Knowledge.chunk("notes.txt", "Alpha beta.\n\nGamma delta.")
        val back = Knowledge.unpack(Knowledge.pack(cs))
        org.junit.Assert.assertEquals(cs.map { it.doc + it.text }, back.map { it.doc + it.text })
    }
}

class GuideContextTest {
    @org.junit.Test fun appQuestionsGetTheRightTopic() {
        val c = Guide.context("how do I make Capri play chess for me in another app")
        org.junit.Assert.assertTrue(c, c.contains("Chess"))
        org.junit.Assert.assertEquals("", Guide.context("what is the capital of France"))
        org.junit.Assert.assertEquals("", Guide.context("hello"))
    }
}
