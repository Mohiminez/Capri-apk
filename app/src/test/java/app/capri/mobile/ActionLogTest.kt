package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class ActionLogTest {
    @Test fun keepsOnlyTheLastOnes() {
        var s = ""
        for (i in 1..100) s = ActionLog.push(s, "line $i", 80)
        val l = s.split("\n")
        assertEquals(80, l.size)
        assertEquals("line 100", l.last())
        assertEquals("line 21", l.first())
    }
    @Test fun neverLogsWhatWasTyped() {
        assertFalse(ActionLog.describe("hands", "type|my secret").contains("secret"))
        assertFalse(ActionLog.describe("copy", "hunter2").contains("hunter2"))
        assertTrue(ActionLog.describe("open", "WhatsApp").contains("WhatsApp"))
    }
    @Test fun blankAndMultilineAreHandled() {
        assertEquals("a", ActionLog.push("a", "  "))
        assertEquals("x y", ActionLog.push("", "x\ny"))
    }
}
