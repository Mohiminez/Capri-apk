package app.capri.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.app.SearchManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Things she can look up or do without a brain: free public APIs (no keys) and small phone controls.
object Tools {
    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "CapriMobile/0.3 (unofficial fan project)")
        if (c.responseCode !in 200..299) throw Exception("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    private fun weatherWord(code: Int): String = when (code) {
        0 -> "clear sky"
        1, 2 -> "mostly clear"
        3 -> "overcast"
        45, 48 -> "fog"
        in 51..57 -> "drizzle"
        in 61..67 -> "rain"
        in 71..77 -> "snow"
        in 80..82 -> "rain showers"
        85, 86 -> "snow showers"
        in 95..99 -> "thunderstorm"
        else -> "mixed weather"
    }

    // Open-Meteo: free, no key.
    fun weather(city: String): String {
        val g = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + Uri.encode(city.trim())))
        val r = g.optJSONArray("results")?.optJSONObject(0) ?: return "No place called \"$city\" was found."
        val lat = r.getDouble("latitude")
        val lon = r.getDouble("longitude")
        val f = JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=1"))
        val cur = f.getJSONObject("current")
        val day = f.getJSONObject("daily")
        return "${r.optString("name")}, ${r.optString("country")}: now ${cur.getDouble("temperature_2m")} C " +
            "(feels like ${cur.getDouble("apparent_temperature")} C), ${weatherWord(cur.getInt("weather_code"))}, wind ${cur.getDouble("wind_speed_10m")} km/h. " +
            "Today: high ${day.getJSONArray("temperature_2m_max").getDouble(0)} C, low ${day.getJSONArray("temperature_2m_min").getDouble(0)} C, " +
            "chance of rain ${day.getJSONArray("precipitation_probability_max").optInt(0)}%."
    }

    // Wikipedia summary in the user's language when it's Arabic.
    fun wiki(topic: String): String {
        val lang = if (L.ar) "ar" else "en"
        var title = topic.trim()
        fun summary(t: String): String? = try {
            JSONObject(get("https://$lang.wikipedia.org/api/rest_v1/page/summary/" + Uri.encode(t.replace(' ', '_')))).optString("extract").ifBlank { null }
        } catch (e: Exception) { null }
        summary(title)?.let { return it.take(900) }
        val found = JSONArray(get("https://$lang.wikipedia.org/w/api.php?action=opensearch&limit=1&format=json&search=" + Uri.encode(title)))
        title = found.optJSONArray(1)?.optString(0) ?: ""
        if (title.isEmpty()) return "Nothing found about \"$topic\"."
        return (summary(title) ?: "Nothing found about \"$topic\".").take(900)
    }

    // What's playing, read from the active media session. Needs notification access.
    fun nowPlaying(ctx: Context): String {
        return try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
            val list = msm.getActiveSessions(android.content.ComponentName(ctx, CapriNotifListener::class.java))
            val c = list.firstOrNull() ?: return "Nothing is playing right now."
            val m = c.metadata ?: return "Something is playing in ${c.packageName} but it shares no title."
            val title = m.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "unknown title"
            val artist = m.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
            "Now playing: $title" + if (artist.isNotEmpty()) " by $artist" else ""
        } catch (e: SecurityException) {
            "I can't see what's playing until notification access is on for Capri."
        }
    }

    fun flashlight(ctx: Context, on: Boolean) {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: throw Exception("no torch")
        cm.setTorchMode(id, on)
    }

    fun volume(ctx: Context, what: String) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (val w = what.lowercase().trim()) {
            "up" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "down" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            else -> {
                val pct = w.removeSuffix("%").toIntOrNull()?.coerceIn(0, 100) ?: return
                am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * pct / 100, AudioManager.FLAG_SHOW_UI)
            }
        }
    }

    fun navigate(ctx: Context, place: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun webSearch(ctx: Context, q: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun copy(ctx: Context, text: String) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Capri", text))
    }
}
