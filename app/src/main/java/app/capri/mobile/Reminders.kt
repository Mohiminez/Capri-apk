package app.capri.mobile

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

object Reminders {
    // arg looks like "2026-12-05 09:00|call mum" (local time)
    // or "+600|text" (in 600 seconds). Returns false when the time could not be understood or is in the past.
    fun add(ctx: Context, arg: String): Boolean {
        val parts = arg.split("|", limit = 2)
        val head = parts[0].trim()
        val at = if (head.startsWith("+")) System.currentTimeMillis() + (head.substring(1).toLongOrNull() ?: return false) * 1000L
                 else (try { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(head)?.time } catch (e: Exception) { null } ?: return false)
        if (at < System.currentTimeMillis() - 5000) return false
        val text = parts.getOrElse(1) { "Reminder" }.trim()
        val prefs = Prefs(ctx)
        val list = load(prefs)
        list.put(JSONObject().put("at", at).put("text", text))
        prefs.putS("reminders", list.toString())
        schedule(ctx, at, text)
        return true
    }

    /** Reminders that are due before midnight tonight, soonest first, as "HH:mm text". */
    fun today(ctx: Context): List<String> {
        val list = load(Prefs(ctx)); val now = System.currentTimeMillis()
        val end = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59) }.timeInMillis
        val out = ArrayList<Pair<Long, String>>()
        for (i in 0 until list.length()) { val o = list.getJSONObject(i); val at = o.getLong("at"); if (at in now..end) out.add(at to SimpleDateFormat("HH:mm", Locale.US).format(java.util.Date(at)) + " " + o.getString("text")) }
        return out.sortedBy { it.first }.map { it.second }
    }

    fun count(ctx: Context): Int = load(Prefs(ctx)).length()

    private fun load(prefs: Prefs): JSONArray = try { JSONArray(prefs.getS("reminders", "[]")) } catch (e: Exception) { JSONArray() }

    private fun schedule(ctx: Context, at: Long, text: String) {
        val pi = PendingIntent.getBroadcast(ctx, (at / 1000).toInt(),
            Intent(ctx, ReminderReceiver::class.java).putExtra("text", text).putExtra("at", at),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // setAlarmClock is the one call phones never delay (it works like the clock app's alarm), and it needs no special permission.
        try {
            val show = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } catch (e: Throwable) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) } catch (e2: Throwable) { }
        }
    }

    // Alarms are lost when the phone restarts, so they are put back from the saved list.
    fun rescheduleAll(ctx: Context) {
        val prefs = Prefs(ctx)
        val now = System.currentTimeMillis()
        val keep = JSONArray()
        val list = load(prefs)
        for (i in 0 until list.length()) {
            val o = list.getJSONObject(i)
            if (o.getLong("at") > now) { keep.put(o); schedule(ctx, o.getLong("at"), o.getString("text")) }
        }
        prefs.putS("reminders", keep.toString())
    }

    fun done(ctx: Context, at: Long) {
        val prefs = Prefs(ctx)
        val keep = JSONArray()
        val list = load(prefs)
        for (i in 0 until list.length()) if (list.getJSONObject(i).getLong("at") != at) keep.put(list.getJSONObject(i))
        prefs.putS("reminders", keep.toString())
    }
}

// Understands "remind me in 10 minutes to ..." and the Arabic versions, with no AI needed. Returns the action tag text or null.
object ReminderAsk {
    private fun digits(s: String): String {
        val sb = StringBuilder()
        for (c in s) sb.append(when (c) { in '\u0660'..'\u0669' -> '0' + (c - '\u0660'); in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0'); '\u066B' -> '.'; else -> c })
        return sb.toString()
    }

    private fun secs(n: Double, unit: String): Long = when {
        unit.startsWith("h") || unit.startsWith("س") -> (n * 3600).toLong()
        unit.startsWith("m") || unit.startsWith("د") -> (n * 60).toLong()
        else -> n.toLong()
    }

    private val wordNums = mapOf("نص" to 0.5, "نصف" to 0.5, "ربع" to 0.25, "دقيقة" to 1.0, "ساعة" to 1.0, "ساعه" to 1.0, "دقيقتين" to 2.0, "ساعتين" to 2.0, "ثلث" to 0.33)

    private val enIn = Regex("^(?:hey capri[, ]*)?(?:please )?remind me in (\\d+(?:\\.\\d+)?) ?(seconds?|secs?|minutes?|mins?|hours?|hrs?) (?:to |about |that )?(.+)$", RegexOption.IGNORE_CASE)
    private val enAt = Regex("^(?:hey capri[, ]*)?(?:please )?remind me at (\\d{1,2})(?::(\\d{2}))? ?(am|pm)? (?:to |about |that )?(.+)$", RegexOption.IGNORE_CASE)
    private val arIn = Regex("^(?:يا كابري[، ]*)?(?:ذكرني|ذكريني|نبهني|نبهيني|ذكّرني|ذكّريني) (?:بعد|خلال) (\\d+(?:\\.\\d+)?) ?(ثواني|ثانية|ثانيه|دقيقة|دقيقه|دقايق|دقائق|دقيقتين|ساعة|ساعه|ساعات|ساعتين) ?(?:ان|أن|إن|اني|أني|إني|ب|عشان|علشان|لـ|ل)? ?(.*)$")
    private val arInWord = Regex("^(?:يا كابري[، ]*)?(?:ذكرني|ذكريني|نبهني|نبهيني|ذكّرني|ذكّريني) (?:بعد|خلال) (نص|نصف|ربع|دقيقتين|ساعتين|ساعة|ساعه|دقيقة|دقيقه) ?(ساعة|ساعه)? ?(?:ان|أن|إن|اني|أني|إني|ب|عشان|علشان|لـ|ل)? ?(.*)$")
    private val arAt = Regex("^(?:يا كابري[، ]*)?(?:ذكرني|ذكريني|نبهني|نبهيني|ذكّرني|ذكّريني) (?:الساعة|الساعه|عند الساعة|ساعة) (\\d{1,2})(?:[:.](\\d{2}))? ?(صباحا|صباحاً|الصبح|ص|مساء|مساءً|العصر|الظهر|الليل|م|بالليل)? ?(?:ان|أن|إن|اني|أني|إني|ب|عشان|علشان|لـ|ل)? ?(.*)$")

    private fun clean(s: String) = s.trim().replace("]", ")").replace("|", "/").ifBlank { "Reminder" }

    fun match(raw: String, now: Long = System.currentTimeMillis()): String? {
        val r = digits(raw.trim().trimEnd('.', '!', '؟', '?'))
        enIn.find(r)?.let { m ->
            val s = secs(m.groupValues[1].toDouble(), m.groupValues[2].lowercase())
            return L.t("Got it. I'll remind you.", "تمام، بذكّرك.") + " [[remind:+$s|${clean(m.groupValues[3])}]]"
        }
        arIn.find(r)?.let { m ->
            val s = secs(m.groupValues[1].toDouble(), m.groupValues[2])
            val unitSecs = if (m.groupValues[2] == "دقيقتين") 120L else if (m.groupValues[2] == "ساعتين") 7200L else s
            return "تمام، بذكّرك. [[remind:+$unitSecs|${clean(m.groupValues[3])}]]"
        }
        arInWord.find(r)?.let { m ->
            val w = m.groupValues[1]
            val big = m.groupValues[2]
            val total = when {
                big.isNotEmpty() -> ((wordNums[w] ?: return null) * 3600).toLong()          // "نص ساعة"
                w == "دقيقتين" -> 120L
                w == "ساعتين" -> 7200L
                w.startsWith("دقيق") -> 60L
                w.startsWith("ساع") -> 3600L
                else -> return null
            }
            return "تمام، بذكّرك. [[remind:+$total|${clean(m.groupValues[3])}]]"
        }
        val at = enAt.find(r)?.let { listOf(it.groupValues[1], it.groupValues[2], it.groupValues[3].lowercase(), it.groupValues[4]) }
            ?: arAt.find(r)?.let { m ->
                val ap = m.groupValues[3]
                val mark = when { ap in setOf("م", "مساء", "مساءً", "العصر", "الليل", "بالليل", "الظهر") -> "pm"; ap.isNotEmpty() -> "am"; else -> "" }
                listOf(m.groupValues[1], m.groupValues[2], mark, m.groupValues[4])
            }
        if (at != null) {
            var h = at[0].toInt(); val mi = at[1].toIntOrNull() ?: 0
            if (h > 23 || mi > 59) return null
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = now; set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
            if (at[2] == "pm" && h < 12) h += 12
            if (at[2] == "am" && h == 12) h = 0
            cal.set(java.util.Calendar.HOUR_OF_DAY, h); cal.set(java.util.Calendar.MINUTE, mi)
            if (at[2].isEmpty() && h in 1..12) {                          // no am/pm said: take whichever comes next
                val pmCal = cal.clone() as java.util.Calendar
                if (h < 12) pmCal.set(java.util.Calendar.HOUR_OF_DAY, h + 12)
                if (cal.timeInMillis <= now) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                if (pmCal.timeInMillis > now && (pmCal.timeInMillis < cal.timeInMillis || cal.timeInMillis <= now)) cal.timeInMillis = pmCal.timeInMillis
            } else if (cal.timeInMillis <= now) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(cal.time)
            return L.t("Got it. I'll remind you.", "تمام، بذكّرك.") + " [[remind:$f|${clean(at[3])}]]"
        }
        return null
    }
}

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        @Volatile var speaker: ((String) -> Unit)? = null
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: "Reminder"
        Reminders.done(ctx, intent.getLongExtra("at", 0L))
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("capri_alerts", "Capri reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(text.hashCode(), Notification.Builder(ctx, "capri_alerts")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Capri reminder")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
        speaker?.invoke(L.t("Reminder: $text", "تذكير: $text"))
    }
}
