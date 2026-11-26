package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class TagsTest {
    @Test fun readsTheNormalForm() {
        val t = Tags.parse("Sure. [[open:spotify]] Done.")
        assertEquals(1, t.size); assertEquals("open", t[0].name); assertEquals("spotify", t[0].arg)
    }

    @Test fun toleratesSloppyModels() {
        assertEquals("spotify", Tags.parse("[[ OPEN : spotify ]]")[0].arg)
        assertEquals("open", Tags.parse("[[ OPEN : spotify ]]")[0].name)
        assertEquals("youtube", Tags.parse("[[open：youtube]]")[0].arg)               // full-width colon
        assertEquals("https://x.com/a[1]/b", Tags.parse("[[url:https://x.com/a[1]/b]]")[0].arg)   // a single ] inside
        assertEquals("", Tags.parse("[[room:]]")[0].arg)
    }

    @Test fun severalInARow() {
        val t = Tags.parse("[[open:a]][[music:b|spotify]] text [[wiki:c]]")
        assertEquals(listOf("open", "music", "wiki"), t.map { it.name })
        assertEquals("b|spotify", t[1].arg)
    }

    @Test fun floodIsCappedAndRepeatsDropped() {
        val flood = (1..40).joinToString("") { "[[do:tap $it]]" }
        assertEquals(Tags.MAX_PER_REPLY, Tags.actable(flood).size)
        assertEquals(1, Tags.actable("[[open:a]] [[open:a]] [[open:a]]").size)
        assertEquals(2, Tags.actable("[[open:a]] [[open:b]]").size)
    }

    @Test fun stripKeepsTheWords() {
        assertEquals("Sure. Done.", Tags.strip("Sure. [[open:spotify]]  Done. [[ wiki : x ]]"))
        assertEquals("No tags here [not one]", Tags.strip("No tags here [not one]"))
    }
}
