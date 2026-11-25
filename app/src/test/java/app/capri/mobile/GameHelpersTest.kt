package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class GameHelpersTest {
    private fun blank() = Reflex.Frame(IntArray(3600) { 0xFFFFFFFF.toInt() }, 60, 60)

    @Test fun unstickSidestepsJumpsAndNeverTouchesFire() {
        val layout = mapOf("stick" to doubleArrayOf(0.15, 0.78), "look" to doubleArrayOf(0.7, 0.5), "jump" to doubleArrayOf(0.9, 0.8), "fire" to doubleArrayOf(0.88, 0.6))
        val r = GamePilot.unstickPolicy(layout, 10_000)
        val a = r.step(blank(), null, 10_000)
        assertTrue(a.isNotEmpty())
        // the first push starts on the stick and goes sideways, not forward
        assertEquals(0.15f, a[0].x1, 0.01f)
        assertTrue(Math.abs(a[0].x2 - a[0].x1) > 0.05f)
        assertTrue(a.none { Math.abs(it.x1 - 0.88f) < 0.02f && Math.abs(it.y1 - 0.6f) < 0.02f })
        // it runs for a few seconds only
        assertTrue(r.untilMs - 10_000 <= 5000)
    }

    @Test fun sidestepAlternatesDirection() {
        val l = mapOf("stick" to doubleArrayOf(0.15, 0.78))
        val x1 = GamePilot.unstickPolicy(l, 10_000).step(blank(), null, 10_000)[0]
        val x2 = GamePilot.unstickPolicy(l, 11_000).step(blank(), null, 11_000)[0]
        assertTrue((x1.x2 - x1.x1) * (x2.x2 - x2.x1) < 0)
    }

    @Test fun theWholeFortniteSkillReachesThePrompt() {
        val t = java.io.File(java.io.File("src/main/assets").takeIf { it.exists() } ?: java.io.File("app/src/main/assets"), "gameskills/fortnite.capri-skill").readText()
        val skill = GameSkills.parse(t)!!
        val shown = GameSkills.forGame(listOf(skill), "Fortnite")
        assertTrue("the skill is " + skill.body.length + " chars", shown.contains("STORM") && shown.contains("DEAD OR WON") && shown.contains("LAYOUT AND ROAM"))
    }
}
