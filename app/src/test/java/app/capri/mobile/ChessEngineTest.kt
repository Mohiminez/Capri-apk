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

class OneVoiceTest {
    @org.junit.Test fun picksOneCloudVoiceForEveryLanguage() {
        org.junit.Assert.assertEquals(Voice.ELEVEN, Voice.oneVoiceEngine(Voice.ELEVEN, true, true, true))
        org.junit.Assert.assertEquals(Voice.GEMINI, Voice.oneVoiceEngine(Voice.KOKORO, true, true, true))
        org.junit.Assert.assertEquals(Voice.ELEVEN, Voice.oneVoiceEngine(Voice.ARABIC, false, true, true))
        org.junit.Assert.assertEquals(Voice.FISH, Voice.oneVoiceEngine(Voice.KOKORO, false, false, false, true))
        org.junit.Assert.assertEquals(Voice.OPENAI, Voice.oneVoiceEngine(Voice.PHONE, false, false, true))
        org.junit.Assert.assertNull(Voice.oneVoiceEngine(Voice.KOKORO, false, false, false))
    }
}

class ModesTest {
    @org.junit.Test fun phrasesMapToModes() {
        val cases = mapOf("study mode" to "study", "switch to game mode" to "game", "party mode" to "party", "battery saver mode" to "battery",
            "وضع الدراسة" to "study", "شغلي وضع اللعب" to "game", "وضع هادئ" to "quiet", "فعلي وضع توفير البطارية" to "battery", "وضع الرفيقة" to "companion", "وضع الحفلة" to "party", "وضع عادي" to "normal")
        for ((p, id) in cases) org.junit.Assert.assertEquals(p, id, Modes.fromPhrase(p))
        for (p in listOf("hello", "open study", "game", "شغل اغنية")) org.junit.Assert.assertNull(p, Modes.fromPhrase(p))
        org.junit.Assert.assertTrue(QuickAsks.match("وضع الدراسة")?.contains("[[mode:study]]") == true)
    }
    @org.junit.Test fun everyModeCoversTheSameKeysSoSwitchingNeverLeavesLeftovers() {
        val keys = Modes.all.first().set.keys
        for (m in Modes.all) org.junit.Assert.assertTrue(m.id, m.set.keys.containsAll(keys))
        org.junit.Assert.assertEquals(Modes.all.size, Modes.all.map { it.id }.toSet().size)
        org.junit.Assert.assertEquals(true, Modes.find("quiet")!!.set["quiet"])
        org.junit.Assert.assertEquals(false, Modes.find("normal")!!.set["quiet"])
    }
}

class SpecialistsTest {
    @org.junit.Test fun picksTheRightSpecialist() {
        val c = mapOf("fix this python function that crashes" to Specialist.CODER, "ترجم هذا النص للانجليزي" to Specialist.TRANSLATOR, "translate this to french please" to Specialist.TRANSLATOR,
            "solve the equation 3x + 5 = 20" to Specialist.MATHS, "احسب 15 * 12" to Specialist.MATHS, "write an email to my landlord about the heater" to Specialist.WRITER,
            "اكتب لي رسالة اعتذار للمدير" to Specialist.WRITER, "explain how vaccines work" to Specialist.TUTOR, "اشرح لي الجاذبية" to Specialist.TUTOR, "make a plan to learn guitar" to Specialist.PLANNER)
        for ((s, a) in c) org.junit.Assert.assertEquals(s, a, Specialists.pick(s))
        for (s in listOf("hello", "how are you doing today", "شلونك اليوم", "play some music")) org.junit.Assert.assertEquals(s, Specialist.NONE, Specialists.pick(s))
        org.junit.Assert.assertEquals("", Specialists.context("fix this python function", false, false))
        org.junit.Assert.assertTrue(Specialists.context("fix this python function", false, true).contains("coder"))
    }
}

class RoomRulesTest {
    @org.junit.Test fun roomGrowsWithTheBond() {
        org.junit.Assert.assertTrue(RoomRules.decor(0).isEmpty())
        org.junit.Assert.assertEquals(setOf("rug"), RoomRules.decor(1))
        org.junit.Assert.assertTrue(RoomRules.decor(4).containsAll(setOf("rug", "plant", "lamp", "shelf", "lights", "poster")))
        for (l in 0..3) org.junit.Assert.assertTrue(RoomRules.decor(l + 1).containsAll(RoomRules.decor(l)))
    }
    @org.junit.Test fun skyFollowsTheClock() {
        org.junit.Assert.assertEquals(0, RoomRules.sky(2)); org.junit.Assert.assertEquals(1, RoomRules.sky(6)); org.junit.Assert.assertEquals(2, RoomRules.sky(12)); org.junit.Assert.assertEquals(1, RoomRules.sky(19)); org.junit.Assert.assertEquals(0, RoomRules.sky(23))
    }
    @org.junit.Test fun cooldownsCountDown() {
        org.junit.Assert.assertEquals(0L, RoomRules.wait(100_000, 0L, RoomRules.PET_GAP))
        org.junit.Assert.assertEquals(5L, RoomRules.wait(1_000, 0L, 6_000))
        org.junit.Assert.assertTrue(QuickAsks.match("her room")?.contains("[[room:open]]") == true)
        org.junit.Assert.assertTrue(QuickAsks.match("غرفتك")?.contains("[[room:open]]") == true)
    }
}

class ModesCycleTest {
    @org.junit.Test fun nextWrapsAround() {
        org.junit.Assert.assertEquals("quiet", Modes.next(Modes.find("normal")!!).id)
        org.junit.Assert.assertEquals("normal", Modes.next(Modes.all.last()).id)
    }
}

class MemoryTest {
    @org.junit.Test fun oldNotesStillParseAndPinSurvivesTrimming() {
        val old = "- likes tea\n- sister is Noor"
        org.junit.Assert.assertEquals(listOf("likes tea", "sister is Noor"), Memory.parse(old).map { it.text })
        var raw = Memory.togglePin(old, 1)
        org.junit.Assert.assertTrue(Memory.parse(raw)[1].pinned)
        for (i in 1..60) raw = Memory.add(raw, "fact $i")
        val facts = Memory.parse(raw)
        org.junit.Assert.assertEquals(Memory.MAX, facts.size)
        org.junit.Assert.assertTrue(facts.any { it.text == "sister is Noor" && it.pinned })
        org.junit.Assert.assertTrue(facts.any { it.text == "fact 60" })
        org.junit.Assert.assertFalse(facts.any { it.text == "likes tea" })
    }
    @org.junit.Test fun editRemoveSearchAndNoDuplicates() {
        var raw = Memory.add("", "Birthday is in May"); raw = Memory.add(raw, "birthday is in may"); raw = Memory.add(raw, "Dog named Rex")
        org.junit.Assert.assertEquals(2, Memory.parse(raw).size)
        org.junit.Assert.assertEquals(listOf(1), Memory.search(raw, "rex"))
        org.junit.Assert.assertEquals(2, Memory.search(raw, "").size)
        raw = Memory.edit(raw, 0, "Birthday is in June")
        org.junit.Assert.assertEquals("Birthday is in June", Memory.parse(raw)[0].text)
        org.junit.Assert.assertEquals(1, Memory.parse(Memory.edit(raw, 0, "  ")).size)
        org.junit.Assert.assertEquals(1, Memory.parse(Memory.remove(raw, 1)).size)
    }
}

class StreakTest {
    @org.junit.Test fun countsDaysInARow() {
        org.junit.Assert.assertEquals(1, Streak.next(0, 100, 0))
        org.junit.Assert.assertEquals(3, Streak.next(100, 101, 2))
        org.junit.Assert.assertEquals(3, Streak.next(100, 100, 3))
        org.junit.Assert.assertEquals(1, Streak.next(100, 103, 9))
    }
    @org.junit.Test fun greetingFitsTheHour() {
        org.junit.Assert.assertTrue(Streak.greeting(8, 1, false).startsWith("Good morning"))
        org.junit.Assert.assertTrue(Streak.greeting(2, 1, true).contains("صاحي"))
        org.junit.Assert.assertTrue(Streak.greeting(19, 7, false).contains("week"))
    }
}

class ChessInsteadOfMePhraseTest {
    @org.junit.Test fun gulfPhrasesStartThePilot() {
        for (q in listOf("العب مكاني", "العبي مكاني", "العب بمكاني", "العب مكاني شطرنج", "يا كابري العبي مكاني", "العب مكاني اللعبة", "play chess instead of me"))
            org.junit.Assert.assertEquals(q, "auto", QuickAsks.chessAuto(q))
        org.junit.Assert.assertNull(QuickAsks.chessAuto("العب مع اخوي"))
    }
}
