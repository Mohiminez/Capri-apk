package app.capri.mobile

// Which screen of a shooter / battle royale (Fortnite first) is showing, judged from the words OCR read. Pure logic, unit tested.
// It lets the phone wait out a loading screen, press "Return to lobby" after the match by itself, and tell the model what screen it is on.
enum class GameScreen { LOBBY, QUEUE, BUS, MATCH, ELIMINATED, VICTORY, UNKNOWN }

object GameScreens {
    private fun norm(words: List<Word>) = " " + words.joinToString(" ") { it.text }.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim() + " "
    private fun has(t: String, vararg k: String) = k.any { t.contains(" $it ") || t.contains(" $it") && it.length >= 6 }

    fun classify(words: List<Word>): GameScreen {
        if (words.size < 2) return GameScreen.UNKNOWN
        val t = norm(words)
        if (has(t, "victory royale", "victory", "you won")) return GameScreen.VICTORY
        if (has(t, "eliminated", "you were eliminated", "better luck next time") || (has(t, "spectate") && has(t, "leave", "return to lobby"))) return GameScreen.ELIMINATED
        if (has(t, "searching", "matchmaking", "finding match", "joining", "waiting for players", "connecting", "loading", "preparing", "starting in", "match starting")) return GameScreen.QUEUE
        if (has(t, "battle bus") || (has(t, "jump") && has(t, "bus"))) return GameScreen.BUS
        if (has(t, "players left", "remaining", "eliminations", "storm", "inventory") || (has(t, "players") && has(t, "elims"))) return GameScreen.MATCH
        if ((has(t, "locker", "quests", "battle pass", "item shop") && has(t, "play")) || has(t, "zero build", "battle royale")) return GameScreen.LOBBY
        return GameScreen.UNKNOWN
    }

    /** After a match: the safe way out. Never "spectate". */
    fun leaveButton(words: List<Word>): Word? {
        for (label in listOf("return to lobby", "leave match", "leave game", "leave", "back to lobby", "lobby")) {
            val hit = TextTargets.find(words, label) ?: continue
            if (hit.cy < 0.08f || TextTargets.lineIsRisky(words, hit)) continue
            return Word(label, hit.x, hit.y, hit.w, hit.h)
        }
        return null
    }

    fun describe(s: GameScreen, ar: Boolean): String = when (s) {
        GameScreen.LOBBY -> if (ar) "اللوبي" else "lobby"
        GameScreen.QUEUE -> if (ar) "انتظار/تحميل" else "searching or loading"
        GameScreen.BUS -> if (ar) "الباص" else "battle bus"
        GameScreen.MATCH -> if (ar) "داخل المباراة" else "in the match"
        GameScreen.ELIMINATED -> if (ar) "انتهت (انقتلت)" else "eliminated"
        GameScreen.VICTORY -> if (ar) "فوز" else "victory"
        GameScreen.UNKNOWN -> "unknown"
    }
}
