package app.capri.mobile

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.sqrt

// Her own ears: records until you stop talking, then sends the clip to a Whisper-style server (Groq, OpenAI or your own).
// Better than the phone's recogniser for Arabic dialects and phones without Google speech services.
object Ears {
    @Volatile private var recording = false
    fun cancel() { recording = false }

    private const val RATE = 16000

    // Blocks. Returns the text ("" if nothing was said). Throws on mic or network trouble.
    fun listen(ctx: Context, prefs: Prefs, onSpeechStart: () -> Unit): String {
        val wav = record(onSpeechStart) ?: return ""
        return transcribe(prefs, wav)
    }

    private fun record(onSpeechStart: () -> Unit): ByteArray? {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 4096) * 2)
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); throw Exception("couldn't open the microphone") }
        val pcm = ByteArrayOutputStream()
        val buf = ShortArray(RATE / 10)
        var totalMs = 0
        var silentMs = 0
        var spoke = false
        var baseline = 0.0
        var calib = 0
        recording = true
        try {
            rec.startRecording()
            while (recording && totalMs < 15000) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) break
                var sum = 0.0
                for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
                val rms = sqrt(sum / n)
                totalMs += n * 1000 / RATE
                if (calib < 3) { baseline += rms / 3; calib++ }
                val threshold = maxOf(baseline * 2.6, 450.0)
                if (rms > threshold) {
                    if (!spoke) onSpeechStart()
                    spoke = true
                    silentMs = 0
                } else if (spoke) {
                    silentMs += n * 1000 / RATE
                }
                for (i in 0 until n) { pcm.write(buf[i].toInt() and 0xFF); pcm.write((buf[i].toInt() shr 8) and 0xFF) }
                if (spoke && silentMs >= 1100) break
                if (!spoke && totalMs >= 6000) return null
            }
        } finally {
            recording = false
            try { rec.stop() } catch (e: Exception) { }
            rec.release()
        }
        if (!spoke) return null
        return wavOf(pcm.toByteArray())
    }

    private fun wavOf(pcm: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        fun le32(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF) }
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        out.write("RIFF".toByteArray()); le32(36 + pcm.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16); le16(1); le16(1); le32(RATE); le32(RATE * 2); le16(2); le16(16)
        out.write("data".toByteArray()); le32(pcm.size)
        out.write(pcm)
        return out.toByteArray()
    }

    fun sttKey(prefs: Prefs): String {
        val own = prefs.getS("sttKey", "").trim()
        if (own.isNotEmpty()) return own
        val groq = prefs.key(Providers.GROQ)
        return if (groq.isNotEmpty()) groq else prefs.key(prefs.cur)
    }

    private fun transcribe(prefs: Prefs, wav: ByteArray): String {
        val base = prefs.getS("sttUrl", "").trim().trimEnd('/').ifBlank { "https://api.groq.com/openai/v1" }
        val model = prefs.getS("sttModel", "").ifBlank { "whisper-large-v3-turbo" }
        val boundary = "----capri" + System.currentTimeMillis()
        val body = ByteArrayOutputStream()
        fun field(name: String, value: String) {
            body.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
        }
        field("model", model)
        field("response_format", "json")
        val lang = when (L.lang(prefs)) { 1 -> "en"; 2 -> "ar"; else -> "" }
        if (lang.isNotEmpty()) field("language", lang)
        body.write("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())
        body.write(wav)
        body.write("\r\n--$boundary--\r\n".toByteArray())

        val c = URL("$base/audio/transcriptions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 20000
        c.readTimeout = 60000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        val key = sttKey(prefs)
        if (key.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $key")
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            var d = txt.take(120)
            try { d = JSONObject(txt).getJSONObject("error").getString("message").take(120) } catch (e: Exception) { }
            throw Exception("hearing HTTP $code: $d")
        }
        return JSONObject(txt).optString("text").trim()
    }
}
