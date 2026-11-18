package app.capri.mobile

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class LocalModel(val label: String, val file: String, val url: String, val needMb: Int = 2500)

// Runs a .litertlm model on the phone with Google's LiteRT-LM, and downloads models from Hugging Face.
object LocalLlm {
    val catalog = listOf(
        LocalModel("Qwen2.5 1.5B, best balance (about 1.6 GB)", "qwen2.5-1.5b.litertlm",
            "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm", 2600),
        LocalModel("Qwen3 0.6B, light and quick (about 0.6 GB)", "qwen3-0.6b.litertlm",
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm", 1400)
    )

    fun dir(ctx: Context): File = File(ctx.filesDir, "models").also { it.mkdirs() }
    fun file(ctx: Context, name: String): File = File(dir(ctx), name)

    // Returns null on success, or a short error.
    fun download(ctx: Context, url: String, name: String, token: String, progress: (Int) -> Unit): String? {
        val part = File(dir(ctx), "$name.part")
        try {
            var u = url
            var hops = 0
            var conn: HttpURLConnection
            while (true) {
                conn = URL(u).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 20000
                conn.readTimeout = 60000
                // Only the Hugging Face host gets the token, never the file CDN it redirects to.
                val host = URL(u).host.lowercase()
                if (token.isNotBlank() && URL(u).protocol == "https" && (host == "huggingface.co" || host.endsWith(".huggingface.co"))) {
                    conn.setRequestProperty("Authorization", "Bearer " + token.trim())
                }
                val code = conn.responseCode
                if (code in 301..308 && hops < 6) {
                    u = URL(URL(u), conn.getHeaderField("Location")).toString()
                    hops++
                    conn.disconnect()
                    continue
                }
                if (code == 401 || code == 403) return "HTTP $code: this model needs a Hugging Face token (accept its licence on huggingface.co first)"
                if (code == 404) return "HTTP 404: that file name wasn't found, check the URL"
                if (code != 200) return "HTTP $code"
                break
            }
            val total = conn.contentLengthLong
            var done = 0L
            var last = -1
            conn.inputStream.use { ins ->
                part.outputStream().use { os ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done * 100 / total).toInt()
                            if (p != last) { last = p; progress(p) }
                        }
                    }
                }
            }
            if (total > 0 && done != total) { part.delete(); return "the download was cut short, try again" }
            if (done < 5_000_000L) { part.delete(); return "that file is too small to be a model" }
            val out = file(ctx, name)
            if (out.exists()) out.delete()
            if (!part.renameTo(out)) return "couldn't save the file"
            close()
            return null
        } catch (e: Exception) {
            return e.message ?: "download failed"
        }
    }

    private var engine: Engine? = null
    private var loaded = ""
    private val idle = android.os.Handler(android.os.Looper.getMainLooper())
    private val unload = Runnable { close() }

    @Synchronized
    private fun engineFor(ctx: Context, path: String, gpu: Boolean): Engine {
        val key = "$path|$gpu"
        engine?.let { if (loaded == key) return it }
        try { engine?.close() } catch (e: Exception) { }
        engine = null
        val cfg = EngineConfig(
            modelPath = path,
            backend = if (gpu) Backend.GPU() else Backend.CPU(),
            cacheDir = ctx.cacheDir.path
        )
        val e = Engine(cfg)
        e.initialize()
        engine = e
        loaded = key
        return e
    }

    @Synchronized
    fun close() {
        try { engine?.close() } catch (e: Exception) { }
        engine = null
        loaded = ""
    }

    // turns: alternating ("user"|"assistant", text) pairs that end on an assistant turn; user is the new message.
    @Synchronized
    fun chat(ctx: Context, path: String, gpu: Boolean, system: String, turns: List<Pair<String, String>>, user: String): String {
        idle.removeCallbacks(unload)
        val e = engineFor(ctx, path, gpu)
        val initial = ArrayList<Message>()
        for (t in turns) initial.add(if (t.first == "user") Message.user(t.second) else Message.model(t.second))
        val cfg = ConversationConfig(
            systemInstruction = Contents.of(system),
            initialMessages = initial,
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.9)
        )
        val answer = e.createConversation(cfg).use { c ->
            // Message.toString() is the reply text in every release (the .text property only exists in newer ones).
            c.sendMessage(user, extraContext = mapOf("enable_thinking" to false)).toString().trim()
        }
        // Frees about a gigabyte of RAM when she hasn't needed the model for two minutes.
        idle.postDelayed(unload, 120_000L)
        return answer
    }
}
