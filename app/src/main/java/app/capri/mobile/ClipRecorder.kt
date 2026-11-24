package app.capri.mobile

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer

// "Clip my games", from the desktop: she keeps recording the last half minute of your screen, and "clip that" saves it to Movies/Capri.
// It records in short pieces (about 10 s each, the last three are kept), so nothing big stays on the phone, then joins the pieces on save.
class ClipService : Service() {
    companion object {
        @Volatile var instance: ClipService? = null
        const val ACT_SAVE = "app.capri.mobile.CLIP_SAVE"
        const val ACT_STOP = "app.capri.mobile.CLIP_STOP"
        const val PIECE_MS = 10_000L
        const val KEEP = 3
    }

    private val h = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var current: File? = null
    private val pieces = ArrayList<File>()
    private var w = 720
    private var hh = 1280
    private var dpi = 320
    @Volatile var recording = false
        private set

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACT_SAVE -> { if (recording) save() else stopSelf(); return START_NOT_STICKY }
            ACT_STOP -> { if (recording) shutdown() else stopSelf(); return START_NOT_STICKY }
        }
        if (recording && intent?.hasExtra("code") == true) return START_NOT_STICKY
        val code = intent?.getIntExtra("code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("data", Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra("data")
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("capri_clip", "Capri clips", NotificationManager.IMPORTANCE_LOW))
        val n = notif()
        // Android 14 wants the foreground type declared before the projection is created
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(77, n)
        try {
            val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = pm.getMediaProjection(code, data)
            p.registerCallback(object : MediaProjection.Callback() { override fun onStop() { h.post { shutdown() } } }, h)
            projection = p
            val m = resources.displayMetrics
            val scale = 720f / minOf(m.widthPixels, m.heightPixels).coerceAtLeast(1)
            w = (m.widthPixels * scale).toInt() / 2 * 2; hh = (m.heightPixels * scale).toInt() / 2 * 2; dpi = m.densityDpi
            instance = this
            startPiece()
            recording = true
            h.postDelayed(rotate, PIECE_MS)
        } catch (e: Throwable) { CrashLog.record(this, e, "clip start"); shutdown() }
        return START_NOT_STICKY
    }

    private fun notif(): Notification {
        val save = PendingIntent.getService(this, 1, Intent(this, ClipService::class.java).setAction(ACT_SAVE), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2, Intent(this, ClipService::class.java).setAction(ACT_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "capri_clip").setSmallIcon(R.drawable.ic_stat).setContentTitle(L.t("Capri is keeping your last 30 seconds", "كابري تحتفظ بآخر ٣٠ ثانية"))
            .setContentText(L.t("Say \"clip that\" or tap Save", "قل \"سجلي المقطع\" أو اضغط حفظ")).setOngoing(true)
            .addAction(Notification.Action.Builder(null, L.t("Save clip", "حفظ المقطع"), save).build())
            .addAction(Notification.Action.Builder(null, L.t("Stop", "إيقاف"), stop).build()).build()
    }

    private fun startPiece() {
        val f = File(cacheDir, "clip_" + System.currentTimeMillis() + ".mp4")
        val r = MediaRecorder()
        r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        r.setVideoSize(w, hh); r.setVideoFrameRate(30); r.setVideoEncodingBitRate(4_000_000)
        r.setOutputFile(f.path)
        r.prepare()
        val old = recorder
        if (display == null) display = projection!!.createVirtualDisplay("capri-clip", w, hh, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null)
        else display!!.surface = r.surface
        r.start()
        recorder = r; current = f
        old?.let { try { it.stop() } catch (e: Throwable) { }; try { it.release() } catch (e: Throwable) { } }
    }

    private fun finishPiece() {
        val f = current ?: return
        synchronized(pieces) { pieces.add(f); current = null; while (pieces.size > KEEP) pieces.removeAt(0).delete() }
    }

    private val rotate = object : Runnable {
        override fun run() {
            if (!recording) return
            try {
                val gameOnly = Prefs(this@ClipService).getB("clipGamesOnly", true)
                finishPiece()
                if (!gameOnly || CapriAccess.instance?.foregroundIsGame() != false) startPiece() else { recorder?.let { try { it.stop() } catch (e: Throwable) { }; it.release() }; recorder = null }
            } catch (e: Throwable) { CrashLog.record(this@ClipService, e, "clip rotate") }
            h.postDelayed(this, PIECE_MS)
        }
    }

    /** Joins the pieces kept so far (plus the one being recorded) into one video in Movies/Capri. */
    fun save(onDone: ((String?) -> Unit)? = null) {
        Thread {
            var name: String? = null
            try {
                h.post { try { finishPiece(); if (recording) startPiece() } catch (e: Throwable) { } }
                Thread.sleep(900)
                val list = synchronized(pieces) { pieces.toList() }.filter { it.exists() && it.length() > 2000 }
                if (list.isNotEmpty()) {
                    val out = File(cacheDir, "clip_out_" + System.nanoTime() + ".mp4")
                    join(list, out)
                    name = "capri-clip-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date()) + ".mp4"
                    val v = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, name); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Capri")
                    }
                    val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)
                    if (uri != null) contentResolver.openOutputStream(uri)?.use { o -> out.inputStream().use { it.copyTo(o) } } else name = null
                    out.delete()
                }
            } catch (e: Throwable) { CrashLog.record(this, e, "clip save"); name = null }
            val r = name
            h.post { CapriService.clipSaved?.invoke(r); onDone?.invoke(r) }
        }.start()
    }

    private fun join(files: List<File>, out: File) {
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1; var offset = 0L
        for (f in files) {
            val ex = MediaExtractor()
            try {
                ex.setDataSource(f.path)
                var vt = -1
                for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { vt = i; break }
                if (vt < 0) continue
                ex.selectTrack(vt)
                if (track < 0) { track = muxer.addTrack(ex.getTrackFormat(vt)); muxer.start() }
                val buf = ByteBuffer.allocate(1 shl 20); val info = MediaCodec.BufferInfo()
                var lastUs = 0L
                while (true) {
                    val n = ex.readSampleData(buf, 0); if (n < 0) break
                    info.set(0, n, ex.sampleTime + offset, ex.sampleFlags)
                    muxer.writeSampleData(track, buf, info)
                    lastUs = ex.sampleTime
                    ex.advance()
                }
                offset += lastUs + 33_000
            } catch (e: Throwable) { } finally { ex.release() }
        }
        if (track >= 0) muxer.stop()
        muxer.release()
    }

    private fun shutdown() {
        recording = false
        h.removeCallbacks(rotate)
        try { recorder?.stop() } catch (e: Throwable) { }
        try { recorder?.release() } catch (e: Throwable) { }
        recorder = null
        try { display?.release() } catch (e: Throwable) { }
        display = null
        try { projection?.stop() } catch (e: Throwable) { }
        projection = null
        for (f in cacheDir.listFiles() ?: emptyArray()) if (f.name.startsWith("clip_")) f.delete()
        pieces.clear()
        instance = null
        CapriService.clipStopped?.invoke()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { if (instance === this) shutdown(); super.onDestroy() }
}

// Asks Android's one-time "start recording?" question, then hands the answer to the service.
class ClipActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try {
            val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(pm.createScreenCaptureIntent(), 91)
        } catch (e: Throwable) { finish() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 91 && resultCode == RESULT_OK && data != null)
            startForegroundService(Intent(this, ClipService::class.java).putExtra("code", resultCode).putExtra("data", data))
        finish()
    }
}
