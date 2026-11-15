package app.capri.mobile

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Locale

object Reminders {
    // arg looks like "2026-12-05 09:00|call mum" (local time)
    fun add(ctx: Context, arg: String) {
        val parts = arg.split("|", limit = 2)
        val at = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(parts[0].trim())?.time ?: return
        if (at < System.currentTimeMillis()) return
        val text = parts.getOrElse(1) { "Reminder" }.trim()
        val pi = PendingIntent.getBroadcast(ctx, (at / 1000).toInt(),
            Intent(ctx, ReminderReceiver::class.java).putExtra("text", text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        @Volatile var speaker: ((String) -> Unit)? = null
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val text = intent.getStringExtra("text") ?: "Reminder"
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
