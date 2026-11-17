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
    fun add(ctx: Context, arg: String) {
        val parts = arg.split("|", limit = 2)
        val at = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(parts[0].trim())?.time ?: return
        if (at < System.currentTimeMillis()) return
        val text = parts.getOrElse(1) { "Reminder" }.trim()
        val prefs = Prefs(ctx)
        val list = load(prefs)
        list.put(JSONObject().put("at", at).put("text", text))
        prefs.putS("reminders", list.toString())
        schedule(ctx, at, text)
    }

    private fun load(prefs: Prefs): JSONArray = try { JSONArray(prefs.getS("reminders", "[]")) } catch (e: Exception) { JSONArray() }

    private fun schedule(ctx: Context, at: Long, text: String) {
        val pi = PendingIntent.getBroadcast(ctx, (at / 1000).toInt(),
            Intent(ctx, ReminderReceiver::class.java).putExtra("text", text).putExtra("at", at),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
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
        speaker?.invoke("Reminder: $text")
    }
}
