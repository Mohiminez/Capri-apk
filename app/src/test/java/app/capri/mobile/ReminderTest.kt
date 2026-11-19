package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class ReminderTest {
    private val now = java.util.Calendar.getInstance().apply { set(2026, 10, 20, 10, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis

    @Test fun englishIn() {
        assertTrue(ReminderAsk.match("remind me in 10 minutes to drink water", now)!!.contains("[[remind:+600|drink water]]"))
        assertTrue(ReminderAsk.match("Remind me in 2 hours to call mum", now)!!.contains("+7200|call mum"))
    }

    @Test fun arabicIn() {
        assertTrue(ReminderAsk.match("ذكرني بعد 10 دقايق اشرب موية", now)!!.contains("[[remind:+600|اشرب موية]]"))
        assertTrue(ReminderAsk.match("ذكرني بعد ٥ دقائق أن أصلي", now)!!.contains("+300|"))
        assertTrue(ReminderAsk.match("ذكرني بعد نص ساعة اكل", now)!!.contains("+1800|اكل"))
        assertTrue(ReminderAsk.match("ذكرني بعد ساعتين اتصل", now)!!.contains("+7200|"))
    }

    @Test fun clockTimes() {
        assertTrue(ReminderAsk.match("remind me at 5pm to leave", now)!!.contains("2026-11-20 17:00|leave"))
        assertTrue(ReminderAsk.match("ذكرني الساعة 9 مساء اتصل", now)!!.contains("2026-11-20 21:00|"))
        // no am/pm: 5 means the next 5 o'clock, which from 10:00 is 17:00 today
        assertTrue(ReminderAsk.match("ذكرني الساعة 5 اكل", now)!!.contains("2026-11-20 17:00|"))
        // 9 from 10:00 is 21:00 today, not 09:00 tomorrow
        assertTrue(ReminderAsk.match("ذكرني الساعة 9 اتصل", now)!!.contains("2026-11-20 21:00|"))
        // am that already passed goes to tomorrow
        assertTrue(ReminderAsk.match("remind me at 8am to run", now)!!.contains("2026-11-21 08:00|"))
    }

    @Test fun notAReminder() { assertNull(ReminderAsk.match("what time is it", now)) }
}

class DspTest {
    @Test fun pitchShiftKeepsLength() {
        val x = FloatArray(16000) { Math.sin(2 * Math.PI * 150 * it / 16000.0).toFloat() * 0.5f }
        val y = Dsp.pitchShift(x, 1.3f)
        assertTrue(Math.abs(y.size - x.size) < 1200)
        // pitch went up: more zero crossings per sample than the original
        fun zc(a: FloatArray): Int { var c = 0; for (i in 1 until a.size) if (a[i - 1] <= 0 && a[i] > 0) c++; return c }
        assertTrue(zc(y) > zc(x) * 1.15)
        assertFalse(y.any { it.isNaN() })
    }
}
