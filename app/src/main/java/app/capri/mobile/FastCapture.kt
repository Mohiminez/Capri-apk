package app.capri.mobile

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder

// Fast eyes for games. Accessibility screenshots are limited to about one a second; a screen-capture stream gives a new frame
// 20-60 times a second, so her local rules (Reflex) can react to what is happening NOW. Frames stay in memory, shrunk, never saved or sent.
class FastCaptureService : Service() {
    companion object {
        @Volatile var instance: FastCaptureService? = null
        @Volatile private var frame: Reflex.Frame? = null
        @Volatile private var frameAt = 0L
        @Volatile var count = 0L
        const val ACT_STOP = "app.capri.mobile.FAST_STOP"
        /** The newest frame if it is younger than [maxAgeMs], else null. */
        fun latest(maxAgeMs: Long = 400): Reflex.Frame? = if (System.currentTimeMillis() - frameAt <= maxAgeMs) frame else null
        fun active() = instance != null
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var thread: HandlerThread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACT_STOP) { shutdown(); return START_NOT_STICKY }
        if (instance != null) return START_NOT_STICKY
        val code = intent?.getIntExtra("code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("data", Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra("data")
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("capri_fast", "Capri game eyes", NotificationManager.IMPORTANCE_LOW))
        val n = android.app.Notification.Builder(this, "capri_fast").setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(L.t("Capri is watching the game screen", "كابري تراقب شاشة اللعبة")).setContentText(L.t("Frames stay in memory only", "الصور تبقى بالذاكرة فقط")).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(78, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(78, n)
        try {
            val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = pm.getMediaProjection(code, data)
            val t = HandlerThread("capri-fast").also { it.start() }; thread = t
            val hd = Handler(t.looper)
            p.registerCallback(object : MediaProjection.Callback() { override fun onStop() { shutdown() } }, hd)
            projection = p
            val m = resources.displayMetrics
            val sc = 360f / minOf(m.widthPixels, m.heightPixels).coerceAtLeast(1)
            val w = (m.widthPixels * sc).toInt().coerceAtLeast(64); val h = (m.heightPixels * sc).toInt().coerceAtLeast(64)
            val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2); reader = r
            r.setOnImageAvailableListener({ rd ->
                val img = try { rd.acquireLatestImage() } catch (e: Throwable) { null } ?: return@setOnImageAvailableListener
                try {
                    val pl = img.planes[0]; val buf = pl.buffer; val ps = pl.pixelStride; val rs = pl.rowStride
                    val ow = 120; val oh = (img.height * ow / img.width).coerceAtLeast(2)
                    val out = IntArray(ow * oh)
                    for (y in 0 until oh) { val sy = y * img.height / oh
                        for (x in 0 until ow) { val o = sy * rs + (x * img.width / ow) * ps
                            out[y * ow + x] = (0xFF shl 24) or ((buf.get(o).toInt() and 255) shl 16) or ((buf.get(o + 1).toInt() and 255) shl 8) or (buf.get(o + 2).toInt() and 255) } }
                    frame = Reflex.Frame(out, ow, oh); frameAt = System.currentTimeMillis(); count++
                } catch (e: Throwable) { } finally { img.close() }
            }, hd)
            display = p.createVirtualDisplay("capri-fast", w, h, m.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, hd)
            instance = this
        } catch (e: Throwable) { CrashLog.record(this, e, "fast capture"); shutdown() }
        return START_NOT_STICKY
    }

    private fun shutdown() {
        instance = null; frame = null
        try { display?.release() } catch (e: Throwable) { }; display = null
        try { reader?.close() } catch (e: Throwable) { }; reader = null
        try { projection?.stop() } catch (e: Throwable) { }; projection = null
        thread?.quitSafely(); thread = null
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    override fun onDestroy() { if (instance === this) shutdown(); super.onDestroy() }
}

class FastCaptureActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try { startActivityForResult((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent(), 92) } catch (e: Throwable) { finish() }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 92 && resultCode == RESULT_OK && data != null) startForegroundService(Intent(this, FastCaptureService::class.java).putExtra("code", resultCode).putExtra("data", data))
        finish()
    }
}
