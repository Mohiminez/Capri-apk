package app.capri.mobile

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class VoiceOpt(val id: String, val label: String)

// Better voices than the stock phone one: Gemini (uses a Gemini key, free tier), ElevenLabs (what the Windows version uses),
// or any OpenAI-style /audio/speech server (OpenAI itself, or your own Kokoro-FastAPI).
object Voice {
    const val PHONE = 0
    const val GEMINI = 1
    const val ELEVEN = 2
    const val OPENAI = 3

    val engines get() = listOf(
        L.t("Phone voice (basic)", "صوت الجوال (عادي)"),
        L.t("Gemini voice (free key)", "صوت Gemini (مفتاح مجاني)"),
        L.t("ElevenLabs (like the Windows version)", "ElevenLabs (مثل نسخة الويندوز)"),
        L.t("Groq / OpenAI / Kokoro server", "Groq أو OpenAI أو سيرفر Kokoro")
    )

    val groqVoices get() = if (L.ar) listOf("noura", "lulwa", "aisha", "fahad", "sultan", "abdullah") else listOf("hannah", "diana", "autumn", "austin", "daniel", "troy")

    val geminiVoices = listOf("Kore", "Zephyr", "Leda", "Aoede", "Puck", "Charon", "Fenrir", "Callirrhoe", "Autonoe", "Despina")

    // How loud her voice is right now (0 to 1); -1 when unknown, then her mouth just flaps.
    @Volatile var level = -1f
    private var viz: android.media.audiofx.Visualizer? = null

    private fun startViz(session: Int) {
        stopViz()
        try {
            val v = android.media.audiofx.Visualizer(session)
            v.captureSize = android.media.audiofx.Visualizer.getCaptureSizeRange()[0]
            v.setDataCaptureListener(object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vz: android.media.audiofx.Visualizer?, wf: ByteArray?, rate: Int) {
                    if (wf == null || wf.isEmpty()) return
                    var sum = 0.0
                    for (b in wf) { val d = (b.toInt() and 0xFF) - 128; sum += d * d }
                    val rms = Math.sqrt(sum / wf.size) / 128.0
                    level = (level.coerceAtLeast(0f) * 0.35f + (rms * 6).toFloat().coerceIn(0f, 1f) * 0.65f)
                }
                override fun onFftDataCapture(vz: android.media.audiofx.Visualizer?, fft: ByteArray?, rate: Int) {}
            }, android.media.audiofx.Visualizer.getMaxCaptureRate() / 2, true, false)
            v.enabled = true
            level = 0f
            viz = v
        } catch (e: Throwable) {
            level = -1f
        }
    }

    private fun stopViz() {
        try { viz?.enabled = false } catch (e: Throwable) { }
        try { viz?.release() } catch (e: Throwable) { }
        viz = null
        level = -1f
    }

    @Volatile private var gen = 0
    @Volatile private var prefsRef: Prefs? = null
    private var player: MediaPlayer? = null
    private var track: AudioTrack? = null

    fun stop() {
        gen++
        stopViz()
        try { player?.stop() } catch (e: Exception) { }
        try { player?.release() } catch (e: Exception) { }
        player = null
        try { track?.stop() } catch (e: Exception) { }
        try { track?.release() } catch (e: Exception) { }
        track = null
    }

    // Blocks until she has finished talking. Returns null on success or an error text.
    // onStart runs right before the sound starts, so her mouth can move with it.
    fun speak(ctx: Context, prefs: Prefs, text: String, onStart: () -> Unit): String? {
        stop()
        prefsRef = prefs
        val my = gen
        return try {
            when (prefs.getI("ttsEngine", PHONE)) {
                GEMINI -> playPcm(gemini(prefs, text), 24000, my, onStart)
                ELEVEN -> playMp3(ctx, eleven(prefs, text), my, onStart)
                OPENAI -> playMp3(ctx, openai(prefs, text), my, onStart)
                else -> return "phone"
            }
            null
        } catch (e: Throwable) {
            e.message ?: "voice failed"
        }
    }

    // Your voice key if you set one, else the key you saved for the provider at that address, else the brain key.
    private fun voiceKey(prefs: Prefs, url: String = ""): String {
        val own = prefs.getSecret("voiceKey")
        if (own.isNotEmpty()) return own
        if (url.isNotEmpty()) prefs.keyForUrl(url).let { if (it.isNotEmpty()) return it }
        return prefs.key(prefs.cur)
    }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): ByteArray {
        Net.checkKey(url, if (headers.isEmpty()) "" else "x")
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 20000
        c.readTimeout = 60000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        for ((k, v) in headers) c.setRequestProperty(k, v)
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        if (code !in 200..299) {
            val err = c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            var d = err.take(120)
            try { d = JSONObject(err).getJSONObject("error").getString("message").take(120) } catch (e: Exception) { }
            try { d = JSONObject(err).getJSONObject("detail").getString("message").take(120) } catch (e: Exception) { }
            throw Exception("voice HTTP $code: $d")
        }
        return c.inputStream.use { it.readBytes() }
    }

    private fun gemini(prefs: Prefs, text: String): ByteArray {
        val key = voiceKey(prefs, "https://generativelanguage.googleapis.com/")
        var model = prefs.getS("voiceModel", "").trim()
        if (model.isEmpty() || !model.contains("tts", true)) {
            model = geminiTtsModel(key)
            prefs.putS("voiceModel", model)
        }
        val name = prefs.getS("voiceName", "Kore").ifBlank { "Kore" }
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", JSONObject()
                    .put("prebuiltVoiceConfig", JSONObject().put("voiceName", name)))))
        val raw = post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("x-goog-api-key" to key), body)
        val data = JSONObject(String(raw, Charsets.UTF_8)).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getJSONObject("inlineData").getString("data")
        return Base64.decode(data, Base64.DEFAULT)
    }

    // The preview names change, so ask Google which speech model this key can use.
    private fun geminiTtsModel(key: String): String {
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200").openConnection() as HttpURLConnection
        c.setRequestProperty("x-goog-api-key", key)
        c.connectTimeout = 15000
        val txt = c.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONObject(txt).getJSONArray("models")
        val names = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val n = arr.getJSONObject(i).optString("name").removePrefix("models/")
            if (n.contains("tts", true)) names.add(n)
        }
        return names.firstOrNull { it.contains("flash") } ?: names.firstOrNull() ?: "gemini-2.5-flash-preview-tts"
    }

    private fun eleven(prefs: Prefs, text: String): ByteArray {
        val id = prefs.getS("voiceId", "").trim()
        if (id.isEmpty()) throw Exception("pick an ElevenLabs voice first")
        val model = prefs.getS("voiceModel", "").ifBlank { "eleven_multilingual_v2" }
            .let { if (it.startsWith("eleven")) it else "eleven_multilingual_v2" }
        val body = JSONObject().put("text", text).put("model_id", model)
        // ElevenLabs is its own service: only a key you pasted for it will work.
        val key = prefs.getSecret("voiceKey")
        if (key.isEmpty()) throw Exception("paste your ElevenLabs key in the Voice tab")
        return post("https://api.elevenlabs.io/v1/text-to-speech/$id?output_format=mp3_44100_128",
            mapOf("xi-api-key" to key), body)
    }

    private fun openai(prefs: Prefs, text: String): ByteArray {
        val base = prefs.getS("voiceUrl", "").trim().trimEnd('/').ifBlank { "https://api.openai.com/v1" }
        val groq = base.contains("groq.com")
        // Groq's Orpheus voices: one model for English, one for Saudi Arabic.
        val model = prefs.getS("voiceModel", "").ifBlank {
            if (groq) (if (L.ar) "canopylabs/orpheus-arabic-saudi" else "canopylabs/orpheus-v1-english") else "tts-1"
        }
        val voice = prefs.getS("voiceName", "").ifBlank {
            if (groq) (if (L.ar) "noura" else "hannah") else "nova"
        }
        val body = JSONObject()
            .put("model", model)
            .put("input", text)
            .put("voice", voice)
            .put("response_format", if (groq) "wav" else "mp3")
        val key = voiceKey(prefs, base)
        return post("$base/audio/speech", if (key.isEmpty()) emptyMap() else mapOf("Authorization" to "Bearer $key"), body)
    }

    // ElevenLabs voices the key can use, for the picker.
    fun elevenVoices(key: String): List<VoiceOpt> {
        val c = URL("https://api.elevenlabs.io/v1/voices").openConnection() as HttpURLConnection
        c.setRequestProperty("xi-api-key", key.trim())
        c.connectTimeout = 15000
        if (c.responseCode !in 200..299) throw Exception("ElevenLabs refused the key")
        val arr = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONArray("voices")
        val out = ArrayList<VoiceOpt>()
        for (i in 0 until arr.length()) {
            val v = arr.getJSONObject(i)
            out.add(VoiceOpt(v.getString("voice_id"), v.optString("name")))
        }
        return out
    }

    private fun playMp3(ctx: Context, bytes: ByteArray, my: Int, onStart: () -> Unit) {
        if (my != gen) return
        val f = File(ctx.cacheDir, "capri_voice.mp3")
        f.writeBytes(bytes)
        val done = CountDownLatch(1)
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        mp.setDataSource(f.path)
        val vol = (prefsRef?.getI("ttsVolume", 100) ?: 100) / 100f
        mp.setVolume(vol, vol)
        mp.setOnCompletionListener { done.countDown() }
        mp.setOnErrorListener { _, _, _ -> done.countDown(); true }
        mp.prepare()
        if (my != gen) { mp.release(); return }
        onStart()
        startViz(mp.audioSessionId)
        mp.start()
        // Her voice sliders (50 to 200 percent). Set after start(): some versions begin playing when params are set.
        try {
            val speed = (prefsRef?.getI("ttsSpeed", 100) ?: 100) / 100f
            val pitch = (prefsRef?.getI("ttsPitch", 100) ?: 100) / 100f
            if (speed != 1f || pitch != 1f) mp.playbackParams = android.media.PlaybackParams().setSpeed(speed).setPitch(pitch)
        } catch (e: Exception) { }
        done.await(90, TimeUnit.SECONDS)
        stopViz()
        try { mp.release() } catch (e: Exception) { }
        if (player === mp) player = null
    }

    private fun playPcm(pcm: ByteArray, rate: Int, my: Int, onStart: () -> Unit) {
        if (my != gen) return
        val vol2 = (prefsRef?.getI("ttsVolume", 100) ?: 100) / 100f
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track = t
        try { t.setVolume(vol2) } catch (e: Exception) { }
        t.write(pcm, 0, pcm.size)
        onStart()
        startViz(t.audioSessionId)
        t.play()
        val ms = pcm.size / 2L * 1000L / rate
        var waited = 0L
        while (waited < ms + 300 && my == gen) { Thread.sleep(50); waited += 50 }
        stopViz()
        try { t.release() } catch (e: Exception) { }
        if (track === t) track = null
    }
}
