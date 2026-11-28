package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class ChessLevelsTest {
    @Test fun oldSavedLevelsMoveUpOnceAndKeepTheirMeaning() {
        assertEquals(1, Engine.migrateLevel(1, false)); assertEquals(2, Engine.migrateLevel(2, false))
        assertEquals(4, Engine.migrateLevel(3, false)); assertEquals(5, Engine.migrateLevel(4, false))
        assertEquals(3, Engine.migrateLevel(3, true)); assertEquals(5, Engine.migrateLevel(9, true))
    }

    @Test fun everyLevelAnswersWithALegalMove() {
        Engine.useStockfish = false
        val p = Chess.start()
        val legal = Chess.legal(p).map { it.toString() }
        for (lv in 1..3) { val a = Engine.forLevel(p, lv)!!; assertTrue("level $lv", a.move.toString() in legal) }
    }

    @Test fun lowerSkillLooksLessFarAhead() {
        Engine.useStockfish = false
        val p = Chess.start()
        assertTrue(Engine.analyse(p, 400, 12, emptyList(), false, 0)!!.depth <= 2)
        assertTrue(Engine.analyse(p, 400, 12, emptyList(), false, 20)!!.depth >= 3)
    }
}
