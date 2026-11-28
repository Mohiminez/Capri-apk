package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class ReviewTest {
    @Test fun gradesByLoss() {
        assertEquals(Review.Grade.BEST, Review.grade(0)); assertEquals(Review.Grade.GOOD, Review.grade(30)); assertEquals(Review.Grade.INACCURACY, Review.grade(60))
        assertEquals(Review.Grade.MISTAKE, Review.grade(150)); assertEquals(Review.Grade.BLUNDER, Review.grade(600))
    }

    @Test fun winChanceIsAnSCurve() {
        assertEquals(50.0, Review.winPct(0), 0.001)
        assertTrue(Review.winPct(300) > 70 && Review.winPct(300) < 80); assertTrue(Review.winPct(-300) < 30)
        assertEquals(Review.winPct(1000), Review.winPct(99999), 0.0001)   // a mate counts as +-1000
    }

    @Test fun theBestMoveIsFullAccuracyAndABlunderIsNot() {
        val best = Review.row(0, true, "e4", "e4", 30, 30, false)
        assertEquals(Review.Grade.BEST, best.grade); assertEquals(100.0, best.accuracy, 0.01); assertNull(best.best)
        val bl = Review.row(1, false, "Qh5", "Nf6", 20, -400, false)
        assertEquals(Review.Grade.BLUNDER, bl.grade); assertEquals("Nf6", bl.best); assertEquals(420, bl.loss); assertTrue(bl.accuracy < 30)
    }

    @Test fun aMoveCannotBeBetterThanTheBest() {
        val r = Review.row(2, true, "d4", "Nf3", 10, 90, false)   // a noisy search said the played move was better: no negative loss
        assertEquals(0, r.loss)
    }

    @Test fun forcedMovesAreLeftOut() {
        val rows = listOf(Review.row(0, true, "Kxa1", null, 0, 0, true), Review.row(2, true, "e4", "e4", 0, 0, false))
        val s = Review.side(rows, true)
        assertEquals(1, s.moves); assertEquals(100.0, s.accuracy, 0.01)
    }

    @Test fun summaryAndKeyMoments() {
        val rows = listOf(
            Review.row(0, true, "e4", "e4", 20, 20, false), Review.row(1, false, "a6", "e5", 20, -150, false),
            Review.row(2, true, "Qh5", "Nf3", 150, -100, false), Review.row(3, false, "Nf6", "Nf6", 100, 100, false))
        val w = Review.side(rows, true); val b = Review.side(rows, false)
        assertEquals(2, w.moves); assertEquals(1, w.best); assertEquals(1, w.blunders); assertTrue(w.accuracy < 100)
        assertEquals(1, b.mistakes + b.blunders)
        assertEquals(listOf(1, 2), Review.keyMoments(rows).map { it.ply })
        assertEquals("1... a6", Review.moveText(rows[1])); assertEquals("2. Qh5", Review.moveText(rows[2]))
    }
}
