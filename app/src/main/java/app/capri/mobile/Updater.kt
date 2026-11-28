package app.capri.mobile

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Asks GitHub whether a newer release exists. Only reads public data; nothing about you is sent.
object Updater {
    const val REPO = "Mohiminez/Capri-apk"

    class Info(val version: String, val page: String, val apk: String?, val notes: String = "")

    fun current(ctx: Context): String =
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0" } catch (e: Exception) { "0" }

    // Is a newer than b? Compares dotted numbers (0.5.12 > 0.5.9).
    fun newer(a: String, b: String): Boolean {
        val x = a.trimStart('v', 'V').split('.', '-').map { it.toIntOrNull() ?: 0 }
        val y = b.trimStart('v', 'V').split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    // Call from a background thread. Returns null if there is no newer release or the network failed.
    fun check(ctx: Context): Info? {
        return try {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) return null
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = j.optString("tag_name", "")
            if (tag.isEmpty() || !newer(tag, current(ctx))) return null
            var apk: String? = null
            val assets = j.optJSONArray("assets")
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val nm = a.optString("name")
                if (!nm.endsWith(".apk")) continue
                // two files per release: arm64 (almost every phone) and arm32 (old phones); take the one that fits this phone
                val want64 = android.os.Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"
                if (apk == null || (want64 && nm.contains("arm64")) || (!want64 && nm.contains("arm32"))) apk = a.optString("browser_download_url")
            }
            Info(tag.trimStart('v'), j.optString("html_url", "https://github.com/$REPO/releases"), apk, j.optString("body", "").trim().take(1500))
        } catch (e: Exception) { null }
    }
}
