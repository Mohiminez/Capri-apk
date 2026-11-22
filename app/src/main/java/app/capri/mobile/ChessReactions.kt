package app.capri.mobile

import kotlin.math.abs

// Capri's running commentary on a chess game (idea taken from CapriCore's chess reactions).
// Pure logic: no Android, so it can be unit tested. One event is picked per move, by priority.
// The pet service sets this so her comments can be spoken in her voice.
object ChessBridge {
    @Volatile var speaker: ((String) -> Unit)? = null
    // asks her brain (the one chosen in the app) a question and hands back the answer; null while the service isn't running
    @Volatile var explainer: ((String, (String?) -> Unit) -> Unit)? = null
}

object ChessEvents {
    val all = listOf("capture", "check", "castle", "promotion", "mate", "draw", "start", "judge", "aside")
    private val order = listOf("mate", "draw", "promotion", "check", "capture", "castle")

    // Which things happened with this move. `after` is the position once it is played.
    fun detect(before: Pos, m: Move, after: Pos): List<String> {
        val out = ArrayList<String>()
        val pc = abs(before.b[m.from])
        val st = Chess.status(after)
        if (st == "mate") out.add("mate")
        if (st == "stalemate" || st == "draw") out.add("draw")
        if (m.promo != 0) out.add("promotion")
        if (st != "mate" && Chess.inCheck(after, after.white)) out.add("check")
        if (before.b[m.to] != 0 || (pc == 1 && m.to == before.ep)) out.add("capture")
        if (pc == 6 && abs(m.to - m.from) == 2) out.add("castle")
        return out.sortedBy { order.indexOf(it) }
    }

    fun pieceName(code: Int, ar: Boolean): String = when (abs(code)) {
        1 -> if (ar) "البيدق" else "pawn"
        2 -> if (ar) "الحصان" else "knight"
        3 -> if (ar) "الفيل" else "bishop"
        4 -> if (ar) "القلعة" else "rook"
        5 -> if (ar) "الوزير" else "queen"
        else -> if (ar) "الملك" else "king"
    }
}

class ChessTalk(private val ar: Boolean, private val rnd: java.util.Random = java.util.Random(), var enabled: Set<String> = ChessEvents.all.toSet()) {
    private val used = HashSet<String>()
    private var plies = 0

    fun reset() { used.clear(); plies = 0 }

    private fun pick(lines: List<String>): String {
        val fresh = lines.filter { it !in used }
        val pool = if (fresh.isEmpty()) { used.removeAll(lines.toSet()); lines } else fresh
        val s = pool[rnd.nextInt(pool.size)]
        used.add(s)
        return s
    }

    fun start(playerWhite: Boolean): String? {
        if ("start" !in enabled) return null
        return pick(if (ar) listOf("يلا نلعب. لا تهوّن علي.", "ورّيني شطارتك يا ذكي.", "جاهزة. لا تبكي إذا خسرت.")
                    else listOf("Let's play. Don't go easy on me.", "Show me what you've got, choom.", "Ready. Try not to cry when you lose."))
    }

    // How good was your move? lossCp = what it cost you against the best move (only judged when she searched properly).
    fun judge(lossCp: Int, bestSan: String, mateForHer: Int?): String? {
        if ("judge" !in enabled) return null
        if (mateForHer != null && mateForHer in 1..3) return pick(if (ar) listOf("أشوف مات في $mateForHer. بس أقول.", "مات في $mateForHer... لا تقول ما حذرتك.") else listOf("I see mate in $mateForHer. Just saying.", "Mate in $mateForHer. Don't say I didn't warn you."))
        if (lossCp > 250) return pick(if (ar) listOf("آخ، غلطة كبيرة. $bestSan كانت أحسن.", "هذي غلطة قوية. كنت تقدر تلعب $bestSan.") else listOf("Ouch, that's a blunder. $bestSan was better.", "That one hurt. You could have played $bestSan."))
        if (lossCp > 120) return pick(if (ar) listOf("مو أفضل نقلة. $bestSan أقوى.", "همم، $bestSan كانت أدق.") else listOf("Not your best. $bestSan was stronger.", "Hmm, $bestSan was more accurate."))
        return null
    }

    // byHer = she just moved. Returns the line she says (or null for silence).
    fun react(before: Pos, m: Move, after: Pos, byHer: Boolean): String? {
        plies++
        val ev = ChessEvents.detect(before, m, after)
        val mover = abs(before.b[m.from])
        val victim = if (before.b[m.to] != 0) abs(before.b[m.to]) else 1
        val vn = ChessEvents.pieceName(victim, ar)
        val mn = ChessEvents.pieceName(mover, ar)
        for (e in ev) {
            if (e !in enabled) continue
            val lines = when (e) {
                "mate" -> if (byHer) (if (ar) listOf("كش مات. حظ أوفر المرة الجاية.", "كش مات. انتهت اللعبة يا بطل.") else listOf("Checkmate. Better luck next time, choom.", "Checkmate. That's the game."))
                          else (if (ar) listOf("كش مات... غلبتني. لعبة حلوة.", "كش مات. أحسنت، هالمرة لك.") else listOf("Checkmate. You got me... good game.", "Checkmate. Well played, this one's yours."))
                "draw" -> if (ar) listOf("تعادل. ماشي، أرضى فيه.", "تعادل. ما فاز أحد.") else listOf("A draw. I'll take it.", "Draw. Nobody wins this one.")
                "promotion" -> if (byHer) (if (ar) listOf("بيدقي صار وزير. أحب الترقيات.", "ترقية! شوف شنو سويت.") else listOf("Promotion. I love an upgrade.", "A new queen. Cry about it."))
                               else (if (ar) listOf("ترقية؟ ما كنت أبيها.", "بيدقك صار قوي... انتبه عليه.") else listOf("A promotion? Didn't want to see that.", "Your pawn grew up. Annoying."))
                "check" -> if (byHer) (if (ar) listOf("كش. دير بالك على الملك.", "كش! وين بتهرب؟") else listOf("Check. Watch your king.", "Check! Where will you run?"))
                           else (if (ar) listOf("كش؟ كنت متوقعتها.", "كش... مو بهالسهولة.") else listOf("Check? I saw that coming.", "Check... not so easy."))
                "capture" -> if (byHer) (if (ar) listOf("أكلت $vn. صار لي.", "$vn صار لي. آسفة... لا مب آسفة.") else listOf("Your $vn is mine now.", "Took the $vn. Sorry, not sorry."))
                             else (if (ar) listOf("آخ. أكلت $vn مني. ماشي.", "هذي لك... بس بنتقم.") else listOf("Ouch. You took my $vn. Fine.", "That one's yours. I'll get it back."))
                "castle" -> if (byHer) (if (ar) listOf("تبييت. الملك بأمان.", "ملكي بالأمان. وقلعتي جاهزة.") else listOf("Castled. King safe, rook ready.", "Tucking my king away."))
                            else (if (ar) listOf("تبيّت؟ جبان!", "ملكك هرب لزاوية. ذكي.") else listOf("Castling? Hiding already?", "King tucked away. Smart."))
                else -> emptyList()
            }
            if (lines.isNotEmpty()) return pick(lines)
        }
        // nothing special: sometimes a short remark about the stage of the game, never twice in a row
        if ("aside" in enabled && rnd.nextInt(100) < 22) {
            val pieces = after.b.count { it != 0 }
            val lines = when {
                mover == 5 && plies < 14 -> if (ar) listOf("وزير بدري؟ جريء.", "طلعت الوزير بدري... همم.") else listOf("Queen out early? Bold.", "Bringing the queen out already? Hmm.")
                plies <= 8 -> if (ar) listOf("افتتاحية حلوة.", "همم، مثير.") else listOf("Nice opening.", "Hmm, interesting.")
                pieces <= 10 -> if (ar) listOf("النهاية قربت. دقق.", "قطع قليلة... كل نقلة تفرق.") else listOf("Endgame. Every move counts.", "Few pieces left. Careful now.")
                mover == 2 || mover == 3 -> if (ar) listOf("نقلة $mn حلوة.", "$mn شغّال... مو سيئة.") else listOf("Nice $mn move.", "That $mn is busy. Not bad.")
                else -> if (ar) listOf("خلني أفكر...", "مو سيئة.") else listOf("Let me think...", "Not bad.")
            }
            return pick(lines)
        }
        return null
    }
}
