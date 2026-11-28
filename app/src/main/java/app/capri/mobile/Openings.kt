package app.capri.mobile

/** The name of the opening a game is in, from its moves in SAN (no move numbers, no check marks). The longest matching line wins. Pure. */
object Openings {
    private val lines = listOf(
        "e4 e5" to "King's Pawn Game", "e4 e5 Nf3" to "King's Knight Opening", "e4 e5 Nf3 Nc6" to "King's Knight Opening",
        "e4 e5 Nf3 Nc6 Bb5" to "Ruy Lopez", "e4 e5 Nf3 Nc6 Bb5 a6" to "Ruy Lopez: Morphy Defense", "e4 e5 Nf3 Nc6 Bb5 Nf6" to "Ruy Lopez: Berlin Defense",
        "e4 e5 Nf3 Nc6 Bc4" to "Italian Game", "e4 e5 Nf3 Nc6 Bc4 Bc5" to "Italian Game: Giuoco Piano", "e4 e5 Nf3 Nc6 Bc4 Nf6" to "Italian Game: Two Knights Defense",
        "e4 e5 Nf3 Nc6 d4" to "Scotch Game", "e4 e5 Nf3 Nc6 d4 exd4 Nxd4" to "Scotch Game", "e4 e5 Nf3 Nc6 Nc3 Nf6" to "Four Knights Game",
        "e4 e5 Nf3 Nf6" to "Petrov's Defense", "e4 e5 Nf3 d6" to "Philidor Defense",
        "e4 e5 f4" to "King's Gambit", "e4 e5 f4 exf4" to "King's Gambit Accepted", "e4 e5 Bc4" to "Bishop's Opening", "e4 e5 Nc3" to "Vienna Game", "e4 e5 d4 exd4" to "Center Game",
        "e4 c5" to "Sicilian Defense", "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6" to "Sicilian Defense: Najdorf Variation",
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 g6" to "Sicilian Defense: Dragon Variation", "e4 c5 Nf3 Nc6 d4 cxd4 Nxd4" to "Sicilian Defense: Open",
        "e4 c5 Nf3 d6 d4 cxd4 Nxd4" to "Sicilian Defense: Open", "e4 c5 Nf3 e6" to "Sicilian Defense: French Variation", "e4 c5 c3" to "Sicilian Defense: Alapin Variation",
        "e4 c5 Nf3 Nc6 Bb5" to "Sicilian Defense: Rossolimo Variation", "e4 c5 Nc3" to "Sicilian Defense: Closed",
        "e4 e6" to "French Defense", "e4 e6 d4 d5" to "French Defense", "e4 e6 d4 d5 Nc3" to "French Defense: Classical", "e4 e6 d4 d5 e5" to "French Defense: Advance Variation",
        "e4 c6" to "Caro-Kann Defense", "e4 c6 d4 d5 e5" to "Caro-Kann Defense: Advance Variation", "e4 c6 d4 d5 Nc3 dxe4 Nxe4" to "Caro-Kann Defense: Classical",
        "e4 d5" to "Scandinavian Defense", "e4 d5 exd5 Qxd5" to "Scandinavian Defense: Main Line", "e4 d6" to "Pirc Defense", "e4 d6 d4 Nf6 Nc3 g6" to "Pirc Defense",
        "e4 Nf6" to "Alekhine's Defense", "e4 g6" to "Modern Defense",
        "d4 d5" to "Queen's Pawn Game", "d4 d5 c4" to "Queen's Gambit", "d4 d5 c4 dxc4" to "Queen's Gambit Accepted", "d4 d5 c4 e6" to "Queen's Gambit Declined",
        "d4 d5 c4 c6" to "Slav Defense", "d4 d5 Bf4" to "London System", "d4 d5 Nf3 Nf6 Bf4" to "London System", "d4 Nf6 Nf3 e6 Bf4" to "London System",
        "d4 Nf6" to "Indian Defense", "d4 Nf6 c4 g6" to "King's Indian Defense", "d4 Nf6 c4 g6 Nc3 Bg7 e4 d6" to "King's Indian Defense: Classical",
        "d4 Nf6 c4 g6 Nc3 d5" to "Grunfeld Defense", "d4 Nf6 c4 e6 Nc3 Bb4" to "Nimzo-Indian Defense", "d4 Nf6 c4 e6 Nf3 b6" to "Queen's Indian Defense",
        "d4 Nf6 c4 e6 Nf3 Bb4" to "Bogo-Indian Defense", "d4 Nf6 Bg5" to "Trompowsky Attack", "d4 f5" to "Dutch Defense", "d4 d6" to "Old Indian Defense",
        "c4" to "English Opening", "c4 e5" to "English Opening: Reversed Sicilian", "c4 c5" to "English Opening: Symmetrical Variation", "c4 Nf6" to "English Opening",
        "Nf3" to "Reti Opening", "Nf3 d5 c4" to "Reti Opening", "Nf3 Nf6 g3" to "King's Indian Attack", "b3" to "Nimzo-Larsen Attack", "f4" to "Bird's Opening", "g3" to "King's Fianchetto Opening"
    ).map { it.first.split(' ') to it.second }

    /** [sans] = the game so far in SAN ("e4", "Nf3", "O-O"...). Null when it matches nothing (or before the first move). */
    fun name(sans: List<String>): String? {
        val clean = sans.map { it.replace(Regex("[+#!?]"), "") }
        var best: String? = null; var bestLen = 0
        for ((l, n) in lines) if (l.size > bestLen && l.size <= clean.size && l.indices.all { l[it] == clean[it] }) { best = n; bestLen = l.size }
        return best
    }
}
