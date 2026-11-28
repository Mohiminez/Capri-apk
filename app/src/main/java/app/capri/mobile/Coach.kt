package app.capri.mobile

/** Small pure pieces of her chess coaching: the gentle first hint, whether she accepts a draw, and the lines for training on your own mistakes. */
object Coach {
    fun squareName(sq: Int): String = "" + ('a' + (sq and 7)) + ('1' + (sq shr 3))

    /** The first hint: only which piece to look at, not where it goes. [code] is the piece on the move's from-square. */
    fun nudge(code: Int, sq: Int, ar: Boolean): String {
        val name = ChessEvents.pieceName(code, ar)
        return if (ar) "فكّر في $name اللي على ${squareName(sq)}. اضغط تلميح مرة ثانية إذا تبي النقلة."
        else "Think about your $name on ${squareName(sq)}. Press Hint again for the move."
    }

    /** She takes a draw when she is not better: [herCp] is her score in centipawns from her side. */
    fun acceptsDraw(herCp: Int, plyCount: Int): Boolean = plyCount >= 20 && herCp <= 30

    fun drawReply(accept: Boolean, ar: Boolean): String = if (accept) (if (ar) "تمام، نتعادل." else "Okay, a draw.")
        else (if (ar) "لا، أحس إني أحسن. نكمل." else "No, I like my position. Let's keep playing.")

    fun puzzleIntro(i: Int, n: Int, played: String, ar: Boolean): String =
        if (ar) "تمرين $i من $n: هني لعبت $played وكلّفتك. دور نقلة أحسن."
        else "Puzzle $i of $n: here you played $played and it cost you. Find something better."

    fun puzzleRight(ar: Boolean) = if (ar) "أيوه! هذي النقلة." else "Yes! That's the move."
    fun puzzleWrong(ar: Boolean) = if (ar) "مو بالضبط. جرّب مرة ثانية." else "Not quite. Try again."
    fun puzzleShow(best: String, ar: Boolean) = if (ar) "النقلة الأحسن كانت $best." else "The better move was $best."
    fun puzzleDone(solved: Int, n: Int, ar: Boolean) = if (ar) "خلصت التمارين: حليت $solved من $n من أول أو ثاني محاولة." else "Training done: $solved of $n found."

    /** Eval graph input: the score from White's side at each position, from the score for the side to move. Mates count as +-1000. */
    fun whiteView(scores: IntArray, whiteToMove: BooleanArray): IntArray =
        IntArray(scores.size) { val c = Review.clamp(scores[it]); if (whiteToMove[it]) c else -c }
}
