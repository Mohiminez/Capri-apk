package app.capri.mobile

/** Plays a chime from Chimes.kt on the notification-sounds stream. The desktop's gaps: any two chimes 300 ms apart at least, the same one 700 ms (a preview skips the second). */
object ChimePlayer {
    private var last = 0L
    private val lastOf = LongArray(3)
    private var track: android.media.AudioTrack? = null

    @Synchronized fun play(which: Int, set: Int, volume: Int, preview: Boolean = false) {
        if (volume <= 0) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - last < 300 || (!preview && now - lastOf[which] < 700)) return
        last = now; lastOf[which] = now
        Thread {
            try {
                val pcm = Chimes.render(set, which, volume)
                val t = android.media.AudioTrack.Builder()
                    .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(android.media.AudioFormat.Builder().setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Chimes.RATE).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2).setTransferMode(android.media.AudioTrack.MODE_STATIC).build()
                t.write(pcm, 0, pcm.size)
                synchronized(this) { track?.let { try { it.release() } catch (e: Throwable) { } }; track = t }
                t.play()
            } catch (e: Throwable) { }
        }.start()
    }
}
