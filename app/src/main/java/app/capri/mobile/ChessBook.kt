package app.capri.mobile

// A small opening book: well-known lines, so she opens like a player and spends her thinking time where it counts.
// Lines are written as coordinate moves; any line that stops being legal is cut there, so a typo can't make her play something illegal.
object Book {
    private val LINES = listOf(
        "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7 f1e1 b7b5 a4b3 d7d6 c2c3 e8g8 h2h3",
        "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6 d2d3 d7d6 e1g1 e8g8",
        "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 d2d3 f8c5 c2c3 d7d6 b2b4 c5b6 a2a4 a7a6",
        "e2e4 e7e5 g1f3 b8c6 d2d4 e5d4 f3d4 g8f6 d4c6 b7c6 e4e5 d8e7 d1e2 f6d5 c2c4",
        "e2e4 e7e5 g1f3 g8f6 f3e5 d7d6 e5f3 f6e4 d2d4 d6d5 f1d3 f8e7 e1g1 b8c6",
        "e2e4 e7e5 g1f3 b8c6 f1b5 g8f6 e1g1 f6e4 d2d4 e4d6 b5c6 d7c6 d4e5 d6f5 d1d8 e8d8",
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6 c1e3 e7e5 d4b3 c8e6 f2f3 f8e7 d1d2 e8g8 e1c1",
        "e2e4 c7c5 g1f3 b8c6 d2d4 c5d4 f3d4 g8f6 b1c3 d7d6 f1e2 e7e6 e1g1 f8e7",
        "e2e4 c7c5 g1f3 e7e6 d2d4 c5d4 f3d4 a7a6 f1d3 g8f6 e1g1 d8c7",
        "e2e4 c7c5 b1c3 b8c6 g2g3 g7g6 f1g2 f8g7 d2d3 d7d6",
        "e2e4 e7e6 d2d4 d7d5 b1c3 g8f6 c1g5 f8e7 e4e5 f6d7 g5e7 d8e7 f2f4 e8g8 g1f3 c7c5",
        "e2e4 e7e6 d2d4 d7d5 b1d2 g8f6 e4e5 f6d7 f1d3 c7c5 c2c3 b8c6 g1e2",
        "e2e4 c7c6 d2d4 d7d5 b1c3 d5e4 c3e4 c8f5 e4g3 f5g6 h2h4 h7h6 g1f3 b8d7 h4h5 g6h7 f1d3 h7d3 d1d3",
        "e2e4 c7c6 d2d4 d7d5 e4e5 c8f5 g1f3 e7e6 f1e2 c6c5 e1g1 b8c6",
        "e2e4 d7d5 e4d5 d8d5 b1c3 d5a5 d2d4 g8f6 g1f3 c7c6 f1c4 c8f5",
        "e2e4 d7d6 d2d4 g8f6 b1c3 g7g6 f2f4 f8g7 g1f3 e8g8 f1d3 b8a6",
        "e2e4 g7g6 d2d4 f8g7 b1c3 d7d6 g1f3 g8f6 f1e2 e8g8 e1g1 c7c6",
        "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6 c1g5 f8e7 e2e3 e8g8 g1f3 h7h6 g5h4 b7b6",
        "d2d4 d7d5 c2c4 c7c6 g1f3 g8f6 b1c3 d5c4 a2a4 c8f5 e2e3 e7e6 f1c4 f8b4",
        "d2d4 d7d5 c2c4 e7e6 b1c3 c7c6 e2e3 g8f6 g1f3 b8d7 f1d3 d5c4 d3c4 b7b5",
        "d2d4 d7d5 c2c4 d5c4 g1f3 g8f6 e2e3 e7e6 f1c4 c7c5 e1g1 a7a6",
        "d2d4 g8f6 c2c4 e7e6 b1c3 f8b4 e2e3 e8g8 f1d3 d7d5 g1f3 c7c5 e1g1 b8c6",
        "d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4 d7d6 g1f3 e8g8 f1e2 e7e5 e1g1 b8c6 d4d5 c6e7",
        "d2d4 g8f6 c2c4 e7e6 g1f3 b7b6 g2g3 c8b7 f1g2 f8e7 e1g1 e8g8 b1c3 f6e4",
        "d2d4 g8f6 g1f3 e7e6 c1f4 d7d5 e2e3 f8d6 f4g3 e8g8 b1d2 c7c5 c2c3 b8c6",
        "d2d4 d7d5 g1f3 g8f6 c1f4 e7e6 e2e3 c7c5 c2c3 b8c6 b1d2 f8d6 f4g3 e8g8",
        "c2c4 e7e5 b1c3 g8f6 g1f3 b8c6 g2g3 d7d5 c4d5 f6d5 f1g2 d5b6 e1g1 f8e7",
        "c2c4 g8f6 b1c3 e7e6 g1f3 d7d5 d2d4 f8e7 c1g5 e8g8 e2e3 h7h6 g5h4 b7b6",
        "c2c4 c7c5 g1f3 g8f6 b1c3 b8c6 g2g3 g7g6 f1g2 f8g7 e1g1 e8g8 d2d4 c5d4 f3d4",
        "g1f3 d7d5 g2g3 g8f6 f1g2 e7e6 e1g1 f8e7 d2d3 e8g8 b1d2 c7c5 e2e4 b8c6",
        "g1f3 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4 d7d6 d2d4 e8g8 f1e2 e7e5 e1g1 b8c6"
    )

    private val table: Map<Long, List<Move>> by lazy {
        val t = HashMap<Long, ArrayList<Move>>()
        for (line in LINES) {
            var p = Chess.start()
            for (tok in line.split(" ")) {
                if (tok.length < 4) break
                val from = (8 - (tok[1] - '0')) * 8 + (tok[0] - 'a'); val to = (8 - (tok[3] - '0')) * 8 + (tok[2] - 'a')
                val m = Chess.legal(p).firstOrNull { it.from == from && it.to == to } ?: break
                val list = t.getOrPut(Engine.key(p)) { ArrayList() }
                if (list.none { it.from == m.from && it.to == m.to }) list.add(m)
                p = Chess.make(p, m)
            }
        }
        t
    }

    val size: Int get() = table.size

    // A book move for this position, or null once the game has left the book.
    fun pick(p: Pos): Move? {
        val l = table[Engine.key(p)] ?: return null
        val m = l[Engine.rnd.nextInt(l.size)]
        return if (Chess.legal(p).any { it.from == m.from && it.to == m.to }) m else null
    }

    internal fun lines(): List<String> = LINES
}
