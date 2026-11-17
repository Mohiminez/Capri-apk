package app.capri.mobile

import android.app.Activity
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

// "Ask Capri" in the text-selection menu and in the Share sheet: select anything, anywhere, and she explains or translates it.
class AskActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrBlank()) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, L.t("Open Capri and allow floating over apps first.", "افتح كابري واسمح بالظهور فوق التطبيقات أول."), Toast.LENGTH_LONG).show()
            } else {
                startForegroundService(Intent(this, CapriService::class.java)
                    .setAction(CapriService.ACT_ASK).putExtra("text", text.take(3000)))
            }
        }
        finish()
    }
}

// Quick Settings tile: start or stop her from the pull-down.
class CapriTile : TileService() {
    override fun onStartListening() { refresh() }

    private fun refresh() {
        val t = qsTile ?: return
        t.state = if (CapriService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Capri"
        t.updateTile()
    }

    override fun onClick() {
        if (CapriService.running) {
            stopService(Intent(this, CapriService::class.java))
        } else if (Settings.canDrawOverlays(this)) {
            startForegroundService(Intent(this, CapriService::class.java))
        } else {
            try {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) { }
        }
        refresh()
    }
}

// Optional: she mentions new messages, and can tell what song is playing. Needs "notification access" turned on for Capri.
class CapriNotifListener : NotificationListenerService() {
    private var lastKey = ""
    private var lastAt = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = Prefs(this)
        if (!prefs.getB("readNotifs", false) || !CapriService.running) return
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val cat = n.category
        if (cat != Notification.CATEGORY_MESSAGE && cat != Notification.CATEGORY_EMAIL && cat != Notification.CATEGORY_CALL && cat != Notification.CATEGORY_SOCIAL) return
        val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() } catch (e: Exception) { sbn.packageName }
        val who = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val key = label + who
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastAt < 4000) return
        lastKey = key; lastAt = now
        var line = if (who.isNotEmpty()) L.t("$label: $who", "$label: $who") else label
        if (prefs.getB("readNotifContent", false)) {
            val body = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            if (body.isNotEmpty()) line += " - " + body.take(80)
        }
        CapriService.notifier?.invoke(line)
    }
}

// Starts her when the phone boots, if you turned that on.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try { Reminders.rescheduleAll(ctx) } catch (e: Throwable) { }
        if (!Prefs(ctx).getB("autostart", false) || !Settings.canDrawOverlays(ctx)) return
        try { ctx.startForegroundService(Intent(ctx, CapriService::class.java)) } catch (e: Throwable) { }
    }
}
