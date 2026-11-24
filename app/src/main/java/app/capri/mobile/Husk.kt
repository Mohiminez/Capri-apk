package app.capri.mobile

import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

// "Husk mode" from the desktop: she becomes the body of Claude Code while it works. A coding agent (Claude Code in Termux on this phone,
// or on a PC on your Wi-Fi) posts its hook events to this tiny server; she reacts, and can tell you what it is doing.
// She only watches: nothing here approves or controls the agent.
class Husk(private val token: String, private val port: Int, private val lan: Boolean, private val onEvent: (Event) -> Unit) {
    class Event(val kind: String, val tool: String, val detail: String, val at: Long = System.currentTimeMillis())

    @Volatile private var server: ServerSocket? = null
    @Volatile var running = false
        private set
    val log = ArrayList<Event>()

    fun start(): Boolean {
        if (running) return true
        return try {
            val s = ServerSocket(port, 8, if (lan) InetAddress.getByName("0.0.0.0") else InetAddress.getByName("127.0.0.1"))
            server = s; running = true
            Thread({
                while (running) {
                    val c = try { s.accept() } catch (e: Exception) { break }
                    Thread({ serve(c) }, "husk-conn").also { it.isDaemon = true }.start()
                }
            }, "husk").also { it.isDaemon = true }.start()
            true
        } catch (e: Exception) { false }
    }

    fun stop() { running = false; try { server?.close() } catch (e: Exception) { }; server = null }

    private fun serve(c: Socket) {
        try {
            c.soTimeout = 4000
            val inp = BufferedInputStream(c.getInputStream())
            val head = StringBuilder()
            var last4 = ""
            while (head.length < 8192) {
                val b = inp.read(); if (b < 0) break
                head.append(b.toChar()); last4 = (last4 + b.toChar()).takeLast(4)
                if (last4 == "\r\n\r\n") break
            }
            val h = head.toString()
            val len = Regex("content-length:\\s*(\\d+)", RegexOption.IGNORE_CASE).find(h)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val tok = Regex("x-capri-token:\\s*(\\S+)", RegexOption.IGNORE_CASE).find(h)?.groupValues?.get(1) ?: ""
            val body = ByteArray(len.coerceIn(0, 200_000)); var got = 0
            while (got < body.size) { val n = inp.read(body, got, body.size - got); if (n < 0) break; got += n }
            val ok = tok == token && h.startsWith("POST")
            if (ok) parse(String(body, 0, got))?.let { e -> synchronized(log) { log.add(e); while (log.size > 60) log.removeAt(0) }; onEvent(e) }
            val code = if (ok) "200 OK" else "403 Forbidden"
            c.getOutputStream().write(("HTTP/1.1 $code\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok").toByteArray())
            c.getOutputStream().flush()
        } catch (e: Exception) { } finally { try { c.close() } catch (e: Exception) { } }
    }

    companion object {
        private fun field(text: String, key: String): String =
            Regex("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(text)?.groupValues?.get(1)?.replace("\\n", " ")?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: ""

        fun parse(text: String): Event? {
            if (!text.trimStart().startsWith("{")) return null
            val kind = field(text, "hook_event_name").ifBlank { field(text, "event") }
            if (kind.isBlank()) return null
            val tool = field(text, "tool_name")
            val detail = listOf("file_path", "command", "pattern", "query", "url", "prompt", "message").map { field(text, it) }.firstOrNull { it.isNotBlank() } ?: ""
            return Event(kind, tool, detail.take(80))
        }

        /** A short line for a tool use, in her words. */
        fun describe(e: Event, ar: Boolean): String {
            val f = e.detail.substringAfterLast('/').take(40)
            return when (e.kind) {
                "UserPromptSubmit" -> if (ar) "كلود كود بدأ يشتغل على طلبك." else "Claude Code is on your request."
                "Notification" -> if (ar) "كلود كود يحتاج منك شي: " + e.detail else "Claude Code needs you: " + e.detail
                "Stop" -> if (ar) "كلود كود خلّص." else "Claude Code finished."
                "SubagentStop" -> if (ar) "واحد من مساعديه خلّص." else "One of its helpers finished."
                else -> when (e.tool) {
                    "Bash" -> if (ar) "يشغّل أمر: " + e.detail.take(40) else "Running: " + e.detail.take(40)
                    "Edit", "MultiEdit", "Write" -> if (ar) "يعدّل $f" else "Editing $f"
                    "Read" -> if (ar) "يقرأ $f" else "Reading $f"
                    "Grep", "Glob" -> if (ar) "يدوّر بالكود" else "Searching the code"
                    "WebSearch", "WebFetch" -> if (ar) "يدوّر بالنت" else "Looking it up online"
                    "Task" -> if (ar) "يوزّع شغل على مساعد" else "Handing work to a helper"
                    "" -> e.kind
                    else -> e.tool
                }
            }
        }

        /** What she says when you ask what it's doing: the last few events. */
        fun summary(events: List<Event>, ar: Boolean): String {
            if (events.isEmpty()) return if (ar) "ما وصلني شي من كلود كود للحين." else "Nothing has come from Claude Code yet."
            val last = events.takeLast(5).map { describe(it, ar) }.distinct()
            val ago = (System.currentTimeMillis() - events.last().at) / 1000
            val head = if (events.last().kind == "Stop") (if (ar) "خلّص." else "It finished.") else if (ago > 120) (if (ar) "هادي من $ago ثانية." else "Quiet for ${ago}s.") else (if (ar) "يشتغل الحين." else "Working now.")
            return head + " " + last.joinToString("; ")
        }
    }
}
