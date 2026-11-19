package app.capri.mobile

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import java.io.File
import java.nio.IntBuffer
import java.nio.LongBuffer

// An Arabic voice that runs on the phone with no key and no internet once downloaded: Meta's MMS-TTS Arabic (a VITS model, single voice).
// It reads Standard Arabic letter by letter, so dialect words come out in a "book" pronunciation, but it is a real neural voice.
object ArabicTts {
    private const val BASE = "https://huggingface.co/Xenova/mms-tts-ara/resolve/main/"

    fun modelName(hd: Boolean) = if (hd) "model.onnx" else "model_quantized.onnx"
    fun dir(ctx: Context): File = File(ctx.filesDir, "arabic_voice").also { it.mkdirs() }

    fun ready(ctx: Context, hd: Boolean): Boolean {
        val d = dir(ctx)
        return File(d, modelName(hd)).length() > 5_000_000L && File(d, "vocab.json").length() > 100L
    }

    fun download(ctx: Context, hd: Boolean, progress: (Int, String) -> Unit): String? {
        val d = dir(ctx)
        val files = listOf("vocab.json" to 1L, modelName(hd) to (if (hd) 114L else 39L))
        val total = files.sumOf { (n, mb) -> if (File(d, n).length() > 100) 0L else mb }.coerceAtLeast(1L)
        var done = 0L
        for ((name, mb) in files) {
            val f = File(d, name)
            if (f.length() > 100) continue
            val before = done
            val sub = if (name == "vocab.json") "vocab.json" else "onnx/$name"
            val err = Kokoro.fetch(BASE + sub, f) { b -> progress(((before + b / 1_000_000L) * 100 / total).toInt().coerceIn(0, 99), name) }
            if (err != null) return name + ": " + err
            done += mb
        }
        synchronized(this) { session = null; sessionKey = ""; vocab = null }
        return null
    }

    fun delete(ctx: Context) {
        synchronized(this) { try { session?.close() } catch (e: Throwable) { }; session = null; sessionKey = ""; vocab = null }
        dir(ctx).deleteRecursively()
    }

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var sessionKey = ""
    private var vocab: HashMap<String, Int>? = null

    @Synchronized
    private fun piece(ctx: Context, text: String, hd: Boolean): FloatArray? {
        val d = dir(ctx)
        if (vocab == null) vocab = KokoroText.parseFlatVocab(File(d, "vocab.json").readText())
        val key = modelName(hd)
        if (session == null || sessionKey != key) {
            try { session?.close() } catch (e: Throwable) { }
            val e = env ?: OrtEnvironment.getEnvironment().also { env = it }
            val o = OrtSession.SessionOptions()
            o.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
            session = e.createSession(File(d, key).path, o)
            sessionKey = key
        }
        val ids = KokoroText.arabicIds(vocab ?: return null, text)
        if (ids.size < 3) return null
        val sess = session ?: return null
        val e = env ?: return null
        val made = ArrayList<OnnxTensor>()
        try {
            val inputs = HashMap<String, OnnxTensor>()
            for (name in sess.inputNames) {
                val ln = name.lowercase()
                val type = (sess.inputInfo[name]?.info as? TensorInfo)?.type
                val values = if (ln.contains("mask")) LongArray(ids.size) { 1L } else ids
                val t = if (type == OnnxJavaType.INT32) OnnxTensor.createTensor(e, IntBuffer.wrap(IntArray(values.size) { values[it].toInt() }), longArrayOf(1, values.size.toLong()))
                        else OnnxTensor.createTensor(e, LongBuffer.wrap(values), longArrayOf(1, values.size.toLong()))
                made.add(t)
                inputs[name] = t
            }
            sess.run(inputs).use { r ->
                val out = r[0].value
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

    // The voice's own pace is fixed, so the pitch slider also changes the playback rate (higher = a little faster and brighter).
    fun chunk(ctx: Context, prefs: Prefs, text: String): Kokoro.Chunk? {
        val hd = prefs.getB("arabicHd", false)
        val raw = piece(ctx, text, hd) ?: return null
        val pitch = (prefs.getI("ttsPitch", 100) / 100f).coerceIn(0.6f, 1.6f)
        val speed = (prefs.getI("ttsSpeed", 100) / 100f).coerceIn(0.6f, 1.6f)
        val fx = Kokoro.effects(raw, prefs.getI("voiceRobot", 0), prefs.getI("voiceGlitch", 0))
        return Kokoro.Chunk(Kokoro.toPcm(fx), (16000 * pitch * speed).toInt())
    }
}
