package app.capri.mobile

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

// Home-screen widget: whether she is awake, how many reminders wait, and a tap to open her.
class CapriWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val prefs = Prefs(ctx)
        L.init(prefs)
        val v = RemoteViews(ctx.packageName, R.layout.widget)
        v.setTextViewText(R.id.w_status, if (CapriService.running) L.t("CAPRI  ● ONLINE", "كابري  ● شغّالة") else L.t("CAPRI  ○ SLEEPING", "كابري  ○ نايمة"))
        val n = try { Reminders.count(ctx) } catch (e: Throwable) { 0 }
        v.setTextViewText(R.id.w_line, if (n > 0) L.t("$n reminder(s) waiting", "$n تذكير ينتظر") else L.t("Tap to open", "اضغط للفتح"))
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        for (id in ids) mgr.updateAppWidget(id, v)
    }
}

// Plugin API: other apps (Tasker, MacroDroid...) can make her say or answer something. Off until you switch it on in her settings.
//   adb shell am broadcast -a app.capri.mobile.PLUGIN --es cmd say --es text "Hello" -n app.capri.mobile/.PluginReceiver
class PluginReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val prefs = Prefs(ctx)
        if (!prefs.getB("pluginApi", false)) return
        val text = (i.getStringExtra("text") ?: "").trim().take(500)
        if (text.isEmpty()) return
        val cmd = if (i.getStringExtra("cmd") == "say") "say" else "ask"
        try {
            ctx.startForegroundService(Intent(ctx, CapriService::class.java).setAction(CapriService.ACT_PLUGIN).putExtra("cmd", cmd).putExtra("text", text))
        } catch (e: Exception) { CrashLog.record(ctx, e, "plugin api") }
    }
}
