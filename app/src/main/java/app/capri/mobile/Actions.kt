package app.capri.mobile

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.view.KeyEvent

// Capri ends a reply with tags like [[open:Spotify]] when they ask for something on the phone. This runs them and strips them.
object Actions {
    private val tag = Regex("\\[\\[(\\w+):([^\\]]*)\\]\\]")

    fun strip(s: String): String = tag.replace(s, "").replace(Regex("\\s+"), " ").trim()

    fun run(ctx: Context, prefs: Prefs, reply: String): String {
        var extra = ""
        for (m in tag.findAll(reply)) {
            val arg = m.groupValues[2].trim()
            try {
                when (m.groupValues[1].lowercase()) {
                    "open" -> if (!openApp(ctx, arg)) extra = " Couldn't find that app."
                    "url" -> if (arg.startsWith("http://") || arg.startsWith("https://")) start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(arg)))
                    "music" -> playMusic(ctx, arg)
                    "media" -> media(ctx, arg)
                    "timer" -> {
                        val parts = arg.split("|", limit = 2)
                        val secs = parts[0].trim().toIntOrNull()?.coerceIn(1, 86400) ?: 0
                        if (secs > 0) start(ctx, Intent(AlarmClock.ACTION_SET_TIMER)
                            .putExtra(AlarmClock.EXTRA_LENGTH, secs)
                            .putExtra(AlarmClock.EXTRA_MESSAGE, parts.getOrElse(1) { "Capri" }.trim())
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                    }
                    "alarm" -> {
                        val parts = arg.split("|", limit = 2)
                        val hm = parts[0].trim().split(":")
                        val hour = hm[0].trim().toInt()
                        val minute = hm.getOrElse(1) { "0" }.trim().toInt()
                        start(ctx, Intent(AlarmClock.ACTION_SET_ALARM)
                            .putExtra(AlarmClock.EXTRA_HOUR, hour)
                            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                            .putExtra(AlarmClock.EXTRA_MESSAGE, parts.getOrElse(1) { "Capri" }.trim())
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                    }
                    "remember" -> prefs.addNote(arg)
                }
            } catch (e: Exception) {
                extra = " That didn't work on this phone."
            }
        }
        return strip(reply) + extra
    }

    private fun start(ctx: Context, i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun openApp(ctx: Context, name: String): Boolean {
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val q = name.lowercase().trim()
        if (q.isEmpty()) return false
        fun label(i: Int) = apps[i].loadLabel(pm).toString().lowercase()
        val idx = apps.indices.firstOrNull { label(it) == q }
            ?: apps.indices.firstOrNull { label(it).startsWith(q) }
            ?: apps.indices.firstOrNull { label(it).contains(q) }
            ?: return false
        val launch = pm.getLaunchIntentForPackage(apps[idx].activityInfo.packageName) ?: return false
        start(ctx, launch)
        return true
    }

    private fun playMusic(ctx: Context, query: String) {
        try {
            start(ctx, Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                .putExtra(SearchManager.QUERY, query))
        } catch (e: Exception) {
            start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))))
        }
    }

    private fun media(ctx: Context, what: String) {
        val code = when (what.lowercase()) {
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
