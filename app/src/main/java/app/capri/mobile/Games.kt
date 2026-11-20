package app.capri.mobile

// Small game engines (no Android here, so they can be unit tested).

object TicTacToe {
    // cells: 0 empty, 1 = you (X), 2 = Capri (O)
    private val lines = arrayOf(intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8), intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8), intArrayOf(0, 4, 8), intArrayOf(2, 4, 6))

    fun winner(b: IntArray): Int {
        for (l in lines) if (b[l[0]] != 0 && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return b[l[0]]
        return 0
    }

    fun full(b: IntArray) = b.none { it == 0 }

    private fun score(b: IntArray, turn: Int, depth: Int): Int {          // from Capri's (2) point of view
        val w = winner(b)
        if (w == 2) return 10 - depth
        if (w == 1) return depth - 10
        if (full(b)) return 0
        var best = if (turn == 2) -100 else 100
        for (i in 0..8) if (b[i] == 0) {
            b[i] = turn
            val s = score(b, 3 - turn, depth + 1)
            b[i] = 0
            best = if (turn == 2) maxOf(best, s) else minOf(best, s)
        }
        return best
    }

    // level 1 easy (mostly random), 2 medium, 3 never loses
    fun aiMove(b: IntArray, level: Int, rnd: java.util.Random = java.util.Random()): Int {
        val free = (0..8).filter { b[it] == 0 }
        if (free.isEmpty()) return -1
        val randomChance = when (level) { 1 -> 70; 2 -> 30; else -> 0 }
        if (rnd.nextInt(100) < randomChance) return free[rnd.nextInt(free.size)]
        var best = free[0]; var bestS = -1000
        for (i in free) {
            b[i] = 2
            val s = score(b, 1, 1)
            b[i] = 0
            if (s > bestS) { bestS = s; best = i }
        }
        return best
    }
}

object ConnectFour {
    const val ROWS = 6
    const val COLS = 7
    // cells r*7+c with r = 0 at the top. 0 empty, 1 = you, 2 = Capri

    fun dropRow(b: IntArray, col: Int): Int {
        if (col !in 0 until COLS) return -1
        for (r in ROWS - 1 downTo 0) if (b[r * COLS + col] == 0) return r
        return -1
    }

    fun winner(b: IntArray): Int {
        val dirs = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1))
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val p = b[r * COLS + c]
            if (p == 0) continue
            for (d in dirs) {
                var k = 1
                while (k < 4) {
                    val rr = r + d[0] * k; val cc = c + d[1] * k
                    if (rr !in 0 until ROWS || cc !in 0 until COLS || b[rr * COLS + cc] != p) break
                    k++
                }
                if (k == 4) return p
            }
        }
        return 0
    }

    fun winningCells(b: IntArray): List<Int> {
        val dirs = arrayOf(intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1))
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            val p = b[r * COLS + c]
            if (p == 0) continue
            for (d in dirs) {
                val cells = ArrayList<Int>()
                for (k in 0 until 4) {
                    val rr = r + d[0] * k; val cc = c + d[1] * k
                    if (rr !in 0 until ROWS || cc !in 0 until COLS || b[rr * COLS + cc] != p) break
                    cells.add(rr * COLS + cc)
                }
                if (cells.size == 4) return cells
            }
        }
        return emptyList()
    }

    fun full(b: IntArray) = (0 until COLS).all { b[it] != 0 }

    private val order = intArrayOf(3, 2, 4, 1, 5, 0, 6)

    private fun windowScore(w: IntArray): Int {
        val me = w.count { it == 2 }; val you = w.count { it == 1 }; val empty = w.count { it == 0 }
        return when {
            me == 4 -> 1000
            me == 3 && empty == 1 -> 8
            me == 2 && empty == 2 -> 3
            you == 3 && empty == 1 -> -9
            you == 2 && empty == 2 -> -2
            else -> 0
        }
    }

    private fun eval(b: IntArray): Int {
        var s = 0
        for (r in 0 until ROWS) if (b[r * COLS + 3] == 2) s += 3
        for (r in 0 until ROWS) for (c in 0 until COLS) {
            if (c + 3 < COLS) s += windowScore(IntArray(4) { b[r * COLS + c + it] })
            if (r + 3 < ROWS) s += windowScore(IntArray(4) { b[(r + it) * COLS + c] })
            if (r + 3 < ROWS && c + 3 < COLS) s += windowScore(IntArray(4) { b[(r + it) * COLS + c + it] })
            if (r + 3 < ROWS && c - 3 >= 0) s += windowScore(IntArray(4) { b[(r + it) * COLS + c - it] })
        }
        return s
    }

    private fun negamax(b: IntArray, depth: Int, alphaIn: Int, betaIn: Int, turn: Int): Int {   // score for Capri (2)
        val w = winner(b)
        if (w == 2) return 100000 + depth
        if (w == 1) return -100000 - depth
        if (full(b) || depth == 0) return eval(b)
        var alpha = alphaIn; var beta = betaIn
        var best = if (turn == 2) Int.MIN_VALUE else Int.MAX_VALUE
        for (c in order) {
            val r = dropRow(b, c)
            if (r < 0) continue
            b[r * COLS + c] = turn
            val s = negamax(b, depth - 1, alpha, beta, 3 - turn)
            b[r * COLS + c] = 0
            if (turn == 2) { best = maxOf(best, s); alpha = maxOf(alpha, best) } else { best = minOf(best, s); beta = minOf(beta, best) }
            if (alpha >= beta) break
        }
        return best
    }

    // level 1 easy, 2 medium, 3 hard. Always takes a win and blocks yours when it can (except on easy, where it sometimes misses).
    fun aiMove(b: IntArray, level: Int, rnd: java.util.Random = java.util.Random()): Int {
        val cols = order.filter { dropRow(b, it) >= 0 }
        if (cols.isEmpty()) return -1
        if (level == 1 && rnd.nextInt(100) < 45) return cols[rnd.nextInt(cols.size)]
        val depth = when (level) { 1 -> 2; 2 -> 4; else -> 7 }
        var best = cols[0]; var bestS = Int.MIN_VALUE
        for (c in cols) {
            val r = dropRow(b, c)
            b[r * COLS + c] = 2
            val s = negamax(b, depth - 1, Int.MIN_VALUE, Int.MAX_VALUE, 1)
            b[r * COLS + c] = 0
            if (s > bestS) { bestS = s; best = c }
        }
        return best
    }
}

object Rps {
    // 0 rock, 1 paper, 2 scissors. returns 1 if a wins, -1 if b wins, 0 draw
    fun beats(a: Int, b: Int): Int = if (a == b) 0 else if ((a + 1) % 3 == b) -1 else 1

    // Capri tries to read you a little: she is more likely to beat what you played last.
    fun aiPick(last: Int, rnd: java.util.Random = java.util.Random()): Int =
        if (last >= 0 && rnd.nextInt(100) < 40) (last + 1) % 3 else rnd.nextInt(3)
}
