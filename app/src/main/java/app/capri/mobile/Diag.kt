package app.capri.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// A setup report you can paste to someone when something doesn't work. Never includes keys, tokens, notes or chat.
object Diag {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun log(tag: String, msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        lines.addLast("$t [$tag] ${msg.take(220)}")
        while (lines.size > 40) lines.removeFirst()
    }

    @Synchronized
    fun report(ctx: Context, prefs: Prefs): String {
        val sb = StringBuilder()
        sb.append("Capri mobile report\n")
        sb.append("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        try { sb.append("App: ${ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName}\n") } catch (e: Exception) { }
        val mi = android.app.ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(mi)
        sb.append("RAM: ${mi.availMem / 1_000_000} MB free of ${mi.totalMem / 1_000_000} MB\n\n")
        sb.append("Running: ${CapriService.running}\n")
        sb.append("Floating over apps: ${Settings.canDrawOverlays(ctx)}\n")
        sb.append("Microphone: ${ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED}\n")
        try {
            val n = ctx.packageManager.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0).size
            sb.append("Apps I can see: $n" + (if (n < 8) "  (too few: the phone hides installed apps, allow Installed apps in Capri's permissions)" else "") + "\n")
        } catch (e: Throwable) { sb.append("Apps I can see: error ${e.message}\n") }
        sb.append("Accessibility connected: ${CapriAccess.instance != null} (switched on in settings: ${CapriAccess.enabledInSettings(ctx)})\n")
        val listeners = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: ""
        sb.append("Notification access: ${listeners.contains(ctx.packageName)}\n")
        sb.append("Foreground app seen: ${CapriAccess.foreground.ifEmpty { "(none)" }}\n\n")
        sb.append("Security level: ${prefs.getI("security", 1)}\n")
        sb.append("Provider: ${Providers.all[prefs.cur].name}, model: ${prefs.model(prefs.cur).ifEmpty { "(auto)" }}\n")
        val withKey = Providers.all.indices.filter { it != Providers.LOCAL && prefs.key(it).isNotEmpty() }.map { Providers.all[it].name }
        sb.append("Providers with a key: ${withKey.joinToString().ifEmpty { "(none)" }}\n")
        sb.append("Offline model: ${prefs.getS("localModel", "").ifEmpty { "(none)" }}\n")
        sb.append("Voice engine: ${prefs.getI("ttsEngine", 0)}, ears: ${prefs.getI("sttEngine", 0)}, language: ${prefs.getI("lang", 0)}\n")
        sb.append("Quiet: ${prefs.getB("quiet", false)}, game mode: ${prefs.getB("gameMode", false)}, no-AI: ${prefs.getB("noAI", false)}\n\n")
        CrashLog.last(ctx)?.let { sb.append("Last error:\n").append(it.take(2500)).append("\n\n") }
        sb.append("Recent events:\n")
        if (lines.isEmpty()) sb.append("(none)\n") else for (l in lines) sb.append(l).append('\n')
        return sb.toString()
    }
}
