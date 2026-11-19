package app.capri.mobile

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

// A ".capri" look: a zip made by the desktop app's look editor, with one PNG sheet per animation (sprites/idle.png, blink.png ...)
// and a look.json with colours and speeds. Only PNG pictures and the two small JSON files are ever read. No code.
object CustomLook {
    private const val DIR = "look"
    private const val MAX_SHEET = 6_000_000L
    private const val MAX_TOTAL = 40_000_000L

    // her animation names (the original art) -> the sheet in the look that replaces it. A look has no walk or mouth art, so she holds her pose.
    private val map = mapOf(
        "idle" to "idle", "idle_blink" to "idle", "glitch" to "glitch", "wake" to "wake", "dangle" to "dangle",
        "arrive" to "blink", "leave" to "blink", "walk" to "idle", "dance" to "idle", "grin" to "idle",
        "sleep" to "perch", "laydown_sleep" to "perch"
    )

    private fun dir(ctx: Context) = File(ctx.filesDir, DIR)

    fun active(ctx: Context): Boolean = Prefs(ctx).getB("lookOn", false) && File(dir(ctx), "look.json").exists()

    fun name(ctx: Context): String = try { JSONObject(File(dir(ctx), "look.json").readText()).optString("name", "") } catch (e: Exception) { "" }

    // The sheet for one of her animations, or null when this look has nothing for it (then the mouth overlay is simply skipped).
    fun file(ctx: Context, anim: String): File? {
        val s = map[anim] ?: return null
        return File(dir(ctx), "$s.png").takeIf { it.exists() }
    }

    fun info(ctx: Context, anim: String): JSONObject? {
        val f = file(ctx, anim) ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        // A look's sheet is ONE picture (it can be wider than a standing frame, like the glitch pose), never a strip of frames.
        val frames = 1
        val fps = try { JSONObject(File(dir(ctx), "look.json").readText()).optJSONObject("sheets")?.optJSONObject(map[anim])?.optInt("fps", 12) ?: 12 } catch (e: Exception) { 12 }
        return JSONObject().put("frames", frames).put("fps", fps.coerceIn(1, 60)).put("w", o.outWidth / frames).put("h", o.outHeight).put("cols", frames).put("sx", 0.0)
    }

    // Unpacks a .capri file. Returns the look's name, or throws with a readable reason.
    fun install(ctx: Context, uri: Uri): String {
        val tmp = File(ctx.cacheDir, "look_new")
        tmp.deleteRecursively()
        tmp.mkdirs()
        var total = 0L
        var json: String? = null
        var sheets = 0
        ctx.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val n = e.name
                    val isSheet = !e.isDirectory && Regex("^sprites/[a-z_]+\\.png$").matches(n)
                    val isLook = n == "look.json"
                    if (!isSheet && !isLook) continue
                    val bytes = z.readBytes()
                    total += bytes.size
                    if (bytes.size > MAX_SHEET || total > MAX_TOTAL) throw IllegalStateException("too big")
                    if (isLook) json = String(bytes, Charsets.UTF_8)
                    else { File(tmp, n.removePrefix("sprites/")).writeBytes(bytes); sheets++ }
                }
            }
        } ?: throw IllegalStateException("can't open")
        if (sheets == 0 || !File(tmp, "idle.png").exists()) throw IllegalStateException("no idle sheet")
        val look = JSONObject(json ?: "{}")
        File(tmp, "look.json").writeText(look.toString())
        val d = dir(ctx)
        d.deleteRecursively()
        if (!tmp.renameTo(d)) { tmp.copyRecursively(d, true); tmp.deleteRecursively() }
        val p = Prefs(ctx)
        p.putB("lookOn", true)
        val hair = look.optJSONObject("colors")?.optString("hair", "") ?: ""
        if (hair.matches(Regex("#[0-9A-Fa-f]{6}"))) p.putS("modAccent", hair)
        SpriteBank.clear()
        return look.optString("name", "look")
    }

    fun remove(ctx: Context) {
        Prefs(ctx).putB("lookOn", false)
        Prefs(ctx).putS("modAccent", "")
        dir(ctx).deleteRecursively()
        SpriteBank.clear()
    }
}
