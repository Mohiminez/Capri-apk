package app.capri.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.Rect
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

// Stacking ".capri-skin" files from capri-desktop. Data only: skin.json and PNG strips are read, nothing else, no code.
// The strips are drawn onto her own sheets once, when a sheet is loaded (SpriteBank), so animation costs nothing extra.
object Skins {
    private const val MAX_STRIP = 8_000_000L
    private const val MAX_TOTAL = 48_000_000L
    private const val MAX_FILES = 80

    private fun root(ctx: Context) = File(ctx.filesDir, "skins")

    fun installed(ctx: Context): List<SkinStack.Meta> =
        (root(ctx).listFiles() ?: emptyArray()).filter { it.isDirectory }.mapNotNull { d ->
            try { SkinStack.meta(File(d, "skin.json").readText()) } catch (e: Exception) { null }
        }.sortedBy { it.name.lowercase() }

    fun on(prefs: Prefs): List<String> = prefs.getS("skinsOn", "").split(',').filter { it.isNotBlank() }

    fun toggle(ctx: Context, prefs: Prefs, id: String) {
        prefs.putS("skinsOn", SkinStack.toggle(on(prefs), id).joinToString(","))
        SpriteBank.clear()
    }

    fun remove(ctx: Context, prefs: Prefs, id: String) {
        File(root(ctx), id).deleteRecursively()
        prefs.putS("skinsOn", on(prefs).filter { it != id }.joinToString(","))
        SpriteBank.clear()
    }

    /** Installs a .capri-skin zip. Returns the skin's name, or null when the zip has no skin.json (it may be a .capri look instead). */
    fun install(ctx: Context, prefs: Prefs, uri: Uri): String? {
        val tmp = File(ctx.cacheDir, "skin_new"); tmp.deleteRecursively(); tmp.mkdirs()
        var json: String? = null; var total = 0L; var files = 0
        ctx.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val isJson = e.name == "skin.json"
                    val strip = SkinStack.stripName(e.name)
                    if (!isJson && strip == null) continue
                    if (++files > MAX_FILES) throw IllegalStateException("too many files")
                    val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(32 * 1024); var left = MAX_STRIP + 1
                    while (left > 0) { val n = z.read(buf, 0, minOf(buf.size.toLong(), left).toInt()); if (n < 0) break; out.write(buf, 0, n); left -= n }
                    val bytes = out.toByteArray()
                    total += bytes.size
                    if (bytes.size > MAX_STRIP || total > MAX_TOTAL) throw IllegalStateException("too big")
                    if (isJson) json = String(bytes, Charsets.UTF_8) else File(tmp, "$strip.png").writeBytes(bytes)
                }
            }
        }
        val meta = SkinStack.meta(json ?: return null) ?: throw IllegalStateException("bad skin.json")
        File(tmp, "skin.json").writeText(json!!)
        val d = File(root(ctx), meta.id); d.deleteRecursively(); root(ctx).mkdirs()
        if (!tmp.renameTo(d)) { tmp.copyRecursively(d, true); tmp.deleteRecursively() }
        // installing one turns it on, on top
        prefs.putS("skinsOn", SkinStack.toggle(on(prefs).filter { it != meta.id }, meta.id).joinToString(","))
        SpriteBank.clear()
        return meta.name
    }

    /** Draws the skins that are on over her sheet [name] (frame size w x h, [frames] frames in [cols] columns). Whole skins replace, accessories add. */
    fun bake(ctx: Context, prefs: Prefs, name: String, bmp: Bitmap, frames: Int, w: Int, h: Int, cols: Int) {
        val onList = on(prefs)
        if (onList.isEmpty() || !bmp.isMutable) return
        val metas = installed(ctx).associateBy { it.id }
        for (id in SkinStack.usable(onList, metas.keys)) {
            val f = File(root(ctx), "$id/$name.png")
            if (!f.exists() || f.length() > MAX_STRIP) continue
            val s = try { BitmapFactory.decodeFile(f.path) } catch (e: Throwable) { null } ?: continue
            try {
                if (SkinStack.frames(s.width, s.height, w, h) != frames) continue
                val c = Canvas(bmp)
                if (metas[id]?.accessory != true) c.drawColor(0, PorterDuff.Mode.CLEAR)
                val src = Rect(); val dst = Rect()
                for (i in 0 until frames) {
                    src.set(i * w, 0, (i + 1) * w, h)
                    dst.set((i % cols) * w, (i / cols) * h, (i % cols + 1) * w, (i / cols + 1) * h)
                    c.drawBitmap(s, src, dst, null)
                }
            } finally { s.recycle() }
        }
    }
}
