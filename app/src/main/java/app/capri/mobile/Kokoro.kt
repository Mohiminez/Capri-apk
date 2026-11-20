package app.capri.mobile

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// The Windows version speaks with Kokoro-82M. This runs the same model on the phone with ONNX Runtime, fully offline once downloaded.
// Files: the model (88 MB, or 310 MB for the full one), the voices, the symbol table and the English word list (about 15 MB).
object Kokoro {
    private const val REL = "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0/"
    private val files = listOf(
        "voices.bin" to REL + "voices-v1.0.bin",
        "config.json" to "https://huggingface.co/hexgrad/Kokoro-82M/resolve/main/config.json",
        "us_gold.json" to "https://raw.githubusercontent.com/hexgrad/misaki/main/misaki/data/us_gold.json",
        "us_silver.json" to "https://raw.githubusercontent.com/hexgrad/misaki/main/misaki/data/us_silver.json"
    )

    fun modelName(hd: Boolean) = if (hd) "kokoro-v1.0.onnx" else "kokoro-v1.0.int8.onnx"
    fun dir(ctx: Context): File = File(ctx.filesDir, "kokoro").also { it.mkdirs() }

    fun ready(ctx: Context, hd: Boolean): Boolean {
        val d = dir(ctx)
        return File(d, modelName(hd)).length() > 10_000_000L && File(d, "voices.bin").length() > 1_000_000L &&
            File(d, "config.json").length() > 500L && File(d, "us_gold.json").length() > 100_000L
    }

    fun voices(ctx: Context): List<String> {
        val f = File(dir(ctx), "voices.bin")
        return if (f.exists()) KokoroText.voiceNames(f.path) else emptyList()
    }

    internal fun fetch(url: String, out: File, progress: (Long) -> Unit): String? {
        val part = File(out.path + ".part")
        try {
            var u = url
            var hops = 0
            var c: HttpURLConnection
            while (true) {
                c = URL(u).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 20000
                c.readTimeout = 60000
                val code = c.responseCode
                if (code in 301..308 && hops < 6) { u = URL(URL(u), c.getHeaderField("Location")).toString(); hops++; c.disconnect(); continue }
                if (code != 200) return "HTTP $code for ${URL(url).host}"
                break
            }
            val total = c.contentLengthLong
            if (total > 800_000_000L) { c.disconnect(); return "unexpectedly large file, stopped" }
            var done = 0L
            c.inputStream.use { ins -> part.outputStream().use { os ->
                val buf = ByteArray(256 * 1024)
                while (true) { val n = ins.read(buf); if (n < 0) break; os.write(buf, 0, n); done += n; progress(done) }
            } }
            if (total > 0 && done != total) { part.delete(); return "the download was cut short, try again" }
            if (out.exists()) out.delete()
            return if (part.renameTo(out)) null else "couldn't save the file"
        } catch (e: Exception) {
            return e.message ?: "download failed"
        }
    }

    // Downloads everything missing. Returns null on success or a short error. progress gets a 0..100 number and a label.
    fun download(ctx: Context, hd: Boolean, progress: (Int, String) -> Unit): String? {
        val d = dir(ctx)
        val all = listOf(modelName(hd) to (REL + modelName(hd))) + files
        val sizes = mapOf(modelName(hd) to (if (hd) 310L else 88L), "voices.bin" to 28L, "config.json" to 1L, "us_gold.json" to 4L, "us_silver.json" to 4L)
        val totalMb = all.sumOf { (n, _) -> if (File(d, n).length() > 100) 0L else sizes[n] ?: 1L }.coerceAtLeast(1L)
        var doneMb = 0L
        for ((name, url) in all) {
            val f = File(d, name)
            if (f.length() > 100) continue
            val before = doneMb
            val err = fetch(url, f) { bytes -> progress(((before + bytes / 1_000_000L) * 100 / totalMb).toInt().coerceIn(0, 99), name) }
            // the second word list is a bonus: the voice still works without it
            if (err != null) { if (name == "us_silver.json") continue; return name + ": " + err }
            doneMb += sizes[name] ?: 1L
        }
        synchronized(this) { session = null; sessionKey = "" ; lex = null; vocab = null }
        return null
    }

    fun delete(ctx: Context) {
        synchronized(this) { try { session?.close() } catch (e: Throwable) { }; session = null; sessionKey = ""; lex = null; vocab = null }
        dir(ctx).deleteRecursively()
    }

    // ---- running the model ----

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var sessionKey = ""
    private var lex: HashMap<String, String>? = null
    private var vocab: HashMap<String, Int>? = null

    @Synchronized
    private fun load(ctx: Context, hd: Boolean) {
        val d = dir(ctx)
        if (vocab == null) vocab = KokoroText.parseVocab(File(d, "config.json").readText())
        if (lex == null) {
            val m = HashMap<String, String>(300_000)
            File(d, "us_silver.json").takeIf { it.exists() }?.let { try { KokoroText.parseLexicon(it.readText(), m) } catch (e: Exception) { } }
            KokoroText.parseLexicon(File(d, "us_gold.json").readText(), m)   // the better list wins
            lex = m
        }
        val key = modelName(hd)
        if (session == null || sessionKey != key) {
            try { session?.close() } catch (e: Throwable) { }
            val e = env ?: OrtEnvironment.getEnvironment().also { env = it }
            val o = OrtSession.SessionOptions()
            o.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
            session = e.createSession(File(d, key).path, o)
            sessionKey = key
        }
    }

    // One piece of text -> 16-bit mono PCM at sampleRate (24000, or shifted when her pitch is changed). Null if the piece has nothing to say.
    @Synchronized
    private fun piece(ctx: Context, text: String, voice: String, speed: Float, hd: Boolean): FloatArray? {
        load(ctx, hd)
        val v = vocab ?: return null
        val ph = KokoroText.phonemes(lex ?: return null, text)
        val ids = ArrayList<Long>()
        var i = 0
        while (i < ph.length) {
            val cp = ph.codePointAt(i)
            val s = String(Character.toChars(cp))
            v[s]?.let { ids.add(it.toLong()) }
            i += Character.charCount(cp)
        }
        if (ids.isEmpty()) return null
        val used = if (ids.size > 500) ids.subList(0, 500) else ids
        val style = KokoroText.style(File(dir(ctx), "voices.bin").path, voice, used.size) ?: throw IllegalStateException("voice \"$voice\" not found")
        val tokens = LongArray(used.size + 2)
        for (k in used.indices) tokens[k + 1] = used[k]
        val sess = session ?: return null
        val e = env ?: return null
        val inputs = HashMap<String, OnnxTensor>()
        val made = ArrayList<OnnxTensor>()
        try {
            for (name in sess.inputNames) {
                val ln = name.lowercase()
                val type = (sess.inputInfo[name]?.info as? TensorInfo)?.type
                val t: OnnxTensor = when {
                    ln.contains("token") || ln.contains("input_ids") ->
                        if (type == OnnxJavaType.INT32) OnnxTensor.createTensor(e, IntBuffer.wrap(IntArray(tokens.size) { tokens[it].toInt() }), longArrayOf(1, tokens.size.toLong()))
                        else OnnxTensor.createTensor(e, LongBuffer.wrap(tokens), longArrayOf(1, tokens.size.toLong()))
                    ln.contains("style") || ln.contains("ref_s") -> OnnxTensor.createTensor(e, FloatBuffer.wrap(style), longArrayOf(1, 256))
                    ln.contains("speed") ->
                        if (type == OnnxJavaType.INT32) OnnxTensor.createTensor(e, IntBuffer.wrap(intArrayOf(speed.toInt().coerceAtLeast(1))), longArrayOf(1))
                        else OnnxTensor.createTensor(e, FloatBuffer.wrap(floatArrayOf(speed)), longArrayOf(1))
                    else -> throw IllegalStateException("unknown model input: $name")
                }
                made.add(t)
                inputs[name] = t
            }
            sess.run(inputs).use { r ->
                val out = r[0].value
                @Suppress("UNCHECKED_CAST")
                return when (out) {
                    is FloatArray -> out
                    is Array<*> -> (out.firstOrNull() as? FloatArray)
                    else -> null
                }
            }
        } finally {
            for (t in made) try { t.close() } catch (x: Throwable) { }
        }
    }

    // Her voice sliders (the desktop's pitch / speed / robot / glitch). Pitch resamples, so the model speed is adjusted to keep the pace.
    internal fun effects(x: FloatArray, robot: Int, glitch: Int): FloatArray {
        var y = x
        if (robot > 0) {
            val m = robot / 100f * 0.85f
            y = FloatArray(x.size) { k -> val s = sin(2 * PI * 72.0 * k / 24000.0).toFloat(); x[k] * (1 - m) + x[k] * s * m }
        }
        if (glitch > 0) {
            val rnd = java.util.Random(x.size.toLong())
            val seg = 1500
            val out = ArrayList<Float>(y.size + 8000)
            var k = 0
            while (k < y.size) {
                val end = minOf(y.size, k + seg)
                for (q in k until end) out.add(y[q])
                if (rnd.nextInt(100) < glitch / 3 && end - k == seg) for (rep in 0 until 2) for (q in k until end) out.add(y[q])
                k = end
            }
            y = FloatArray(out.size) { out[it] }
        }
        return y
    }

    internal fun toPcm(x: FloatArray): ByteArray {
        var n = x.size
        while (n > 2400 && abs(x[n - 1]) < 0.004f) n--         // trim the silent tail
        val b = ByteArray(n * 2)
        for (k in 0 until n) {
            var f = x[k]
            if (k > n - 240) f *= (n - k) / 240f                    // short fade so it never clicks
            val s = (f.coerceIn(-1f, 1f) * 32767f).toInt()
            b[2 * k] = (s and 255).toByte()
            b[2 * k + 1] = ((s shr 8) and 255).toByte()
        }
        return b
    }

    class Chunk(val pcm: ByteArray, val rate: Int)

    // Splits the text into pieces; calls next(i) lazily so the caller can play one piece while the next is being made.
    fun pieces(text: String): List<String> = KokoroText.pieces(text)

    fun chunk(ctx: Context, prefs: Prefs, text: String): Chunk? {
        val hd = prefs.getB("kokoroHd", false)
        val voice = prefs.getS("kokoroVoice", "af_heart").ifBlank { "af_heart" }
        val speed = prefs.getI("ttsSpeed", 100) / 100f
        val pitch = (prefs.getI("ttsPitch", 100) / 100f).coerceIn(0.6f, 1.6f)
        val raw = piece(ctx, text, voice, (speed / pitch).coerceIn(0.4f, 2.5f), hd) ?: return null
        val fx = effects(Dsp.layers(raw, prefs.getI("voiceLayers", 0)), prefs.getI("voiceRobot", 0), prefs.getI("voiceGlitch", 0))
        return Chunk(toPcm(fx), (24000 * pitch).toInt())
    }
}
