package app.capri.mobile

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.provider.OpenableColumns
import java.io.File
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
        val it0 = intent
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, L.t("Open Capri and allow floating over apps first.", "افتح كابري واسمح لها بالظهور فوق التطبيقات أول."), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val text = it0.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: it0.getStringExtra(Intent.EXTRA_TEXT)
        @Suppress("DEPRECATION")
        val stream: Uri? = try { it0.getParcelableExtra(Intent.EXTRA_STREAM) } catch (e: Exception) { null }
        if (stream == null) {
            if (!text.isNullOrBlank()) go(Intent(this, CapriService::class.java).setAction(CapriService.ACT_ASK).putExtra("text", text.take(3000)))
            finish()
            return
        }
        // Reading a shared file can be slow: do it off the main thread, then hand it to her.
        val type = it0.type ?: contentResolver.getType(stream) ?: ""
        val app = applicationContext
        bg("share-read", app) {
            val svc = Intent(app, CapriService::class.java).setAction(CapriService.ACT_ASK)
            var ok = false
            try {
                val name = nameOf(stream)
                svc.putExtra("name", name)
                if (!text.isNullOrBlank()) svc.putExtra("text", text.take(500))
                when {
                    type.startsWith("image/") -> {
                        val f = File(app.cacheDir, "shared_image.jpg")
                        val bmp = decodeSmall(stream, 1280)
                        if (bmp != null) {
                            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                            svc.putExtra("kind", "image").putExtra("imagePath", f.absolutePath)
                            ok = true
                        }
                    }
                    type == "application/pdf" -> {
                        val f = File(app.cacheDir, "shared_page.jpg")
                        val pfd = contentResolver.openFileDescriptor(stream, "r")
                        if (pfd != null) {
                            pfd.use {
                                val r = PdfRenderer(it)
                                if (r.pageCount > 0) {
                                    val pg = r.openPage(0)
                                    val w = 1100
                                    val h = (w * pg.height.toFloat() / maxOf(1, pg.width)).toInt().clampTo(100, 2400)
                                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    bmp.eraseColor(Color.WHITE)
                                    pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    pg.close()
                                    f.outputStream().use { o -> bmp.compress(Bitmap.CompressFormat.JPEG, 85, o) }
                                    svc.putExtra("kind", "image").putExtra("imagePath", f.absolutePath)
                                    svc.putExtra("text", L.t("This is the first page of a PDF. ", "هذي أول صفحة من ملف PDF. ") + (text ?: "").take(300))
                                    ok = true
                                }
                                r.close()
                            }
                        }
                    }
                    type.startsWith("text/") || type == "application/json" -> {
                        val txt = contentResolver.openInputStream(stream)?.use { ins ->
                            val buf = ByteArray(24_000)
                            val n = ins.read(buf)
                            if (n > 0) String(buf, 0, n, Charsets.UTF_8) else ""
                        } ?: ""
                        if (txt.isNotBlank()) { svc.putExtra("kind", "file").putExtra("text", txt.take(6000)); ok = true }
                    }
                }
            } catch (e: Throwable) {
                CrashLog.record(app, e, "reading a shared file")
            }
            runOnUiThread {
                if (ok) go(svc) else Toast.makeText(this, L.t("I can't read that kind of file yet. Pictures, PDFs and text files work.", "ما أقدر أقرأ هالنوع من الملفات. الصور وPDF والنصوص تشتغل."), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun go(i: Intent) {
        try { startForegroundService(i) } catch (e: Exception) { CrashLog.record(this, e, "starting her from the share sheet") }
    }

    private fun nameOf(u: Uri): String = try {
        contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) ?: "" else "" } ?: ""
    } catch (e: Exception) { "" }

    // A phone photo can be 12 megapixels: shrink while decoding so sharing one never runs out of memory.
    private fun decodeSmall(u: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = sample }
        return contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
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


// KEYS: the same one-tap actions from three places: long-press the launcher icon, the Quick Settings pull-down, the notification buttons.
class KeyActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent?.getStringExtra("key") ?: ""
        try {
            val act = when (key) {
                "talk" -> CapriService.ACT_TALK
                "type" -> CapriService.ACT_TYPE
                "hide" -> CapriService.ACT_HIDE
                "quiet" -> CapriService.ACT_QUIET
                "chess" -> CapriService.ACT_CHESS
                "stop" -> CapriService.ACT_STOP
                else -> ""
            }
            if (key == "room") startActivity(Intent(this, RoomActivity::class.java))
            else if (act.isNotEmpty()) {
                if (!Settings.canDrawOverlays(this)) startActivity(Intent(this, MainActivity::class.java))
                else startForegroundService(Intent(this, CapriService::class.java).setAction(act).putExtra("mode", "auto"))
            }
        } catch (e: Throwable) { }
        finish()
    }
}

abstract class KeyTile(private val key: String, private val label: String) : TileService() {
    override fun onStartListening() { qsTile?.let { it.state = Tile.STATE_INACTIVE; it.label = label; it.updateTile() } }
    override fun onClick() {
        try {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(Intent(this, KeyActivity::class.java).putExtra("key", key).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }
}
class TalkTile : KeyTile("talk", "Capri: talk")
class QuietTile : KeyTile("quiet", "Capri: quiet")
class ChessTile : KeyTile("chess", "Capri: chess")
