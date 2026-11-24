package app.capri.mobile

import android.content.Context
import android.net.Uri
import java.util.zip.ZipInputStream

// Plain text out of the files you give her: text, markdown, csv, json, .html pages and Word (.docx) documents.
object DocText {
    fun read(ctx: Context, uri: Uri, name: String): String? {
        val n = name.lowercase()
        return try {
            if (n.endsWith(".docx")) {
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    val z = ZipInputStream(ins)
                    while (true) {
                        val e = z.nextEntry ?: break
                        if (e.name == "word/document.xml") { val b = z.readBytes(); return docx(String(b, 0, minOf(3_000_000, b.size), Charsets.UTF_8)) }
                    }
                    null
                }
            } else {
                val raw = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(400_000) } ?: return null
                if (n.endsWith(".html") || n.endsWith(".htm")) Tools.readable(raw) else raw.take(200_000)
            }
        } catch (e: Exception) { null }
    }

    /** The words of word/document.xml: paragraphs become line breaks, every other tag disappears. */
    fun docx(xml: String): String =
        xml.replace(Regex("</w:p>"), "\n\n").replace(Regex("<w:tab/>"), " ").replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").trim()
}
