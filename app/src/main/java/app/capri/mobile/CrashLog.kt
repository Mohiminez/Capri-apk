package app.capri.mobile

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CapriApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

// Writes the reason for any crash to a file, so the next time the app opens it can show you exactly what broke
// (and you can copy it). Nothing is sent anywhere.
object CrashLog {
    private const val FILE = "last_crash.txt"
    private const val STORE = "capri_crash"
    @Volatile private var installed = false

    fun install(app: Application) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { record(app, e, "uncaught error in thread \"" + t.name + "\"", true) } catch (x: Throwable) { }
            if (previous != null) previous.uncaughtException(t, e)
        }
    }

    private fun versionOf(ctx: Context): String =
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Throwable) { "?" }

    // fatal = the app is about to die; the file is written synchronously.
    @Synchronized
    fun record(ctx: Context, e: Throwable, where: String, fatal: Boolean = false) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = StringBuilder()
            .append("Capri crash report\n")
            .append("When: ").append(time).append('\n')
            .append("Kind: ").append(if (fatal) "FATAL (the app closed)" else "handled (the app kept running)").append('\n')
            .append("Where: ").append(where).append('\n')
            .append("App: ").append(versionOf(ctx)).append('\n')
            .append("Phone: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n")
            .append(sw.toString().take(6000))
            .toString()
        try { File(ctx.filesDir, FILE).writeText(text) } catch (x: Throwable) { }
        val sp = ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val ed = sp.edit().putBoolean("unseen", true)
        if (fatal) {
            val now = System.currentTimeMillis()
            val kept = (sp.getString("times", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }.filter { now - it < 600_000L }
            ed.putString("times", (kept + now).takeLast(10).joinToString(","))
        }
        ed.commit()
        try { Diag.log("crash", where + ": " + e) } catch (x: Throwable) { }
    }

    fun last(ctx: Context): String? =
        try { File(ctx.filesDir, FILE).takeIf { it.exists() }?.readText() } catch (e: Throwable) { null }

    // The report, once, if something went wrong since you last looked.
    fun unseen(ctx: Context): String? {
        val sp = ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        return if (sp.getBoolean("unseen", false)) last(ctx) else null
    }

    fun markSeen(ctx: Context) {
        ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putBoolean("unseen", false).apply()
    }

    fun clear(ctx: Context) {
        try { File(ctx.filesDir, FILE).delete() } catch (e: Throwable) { }
        ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    // How many times the app died within the last windowMs. Used to stop a crash loop.
    fun recentFatal(ctx: Context, windowMs: Long): Int {
        val now = System.currentTimeMillis()
        val raw = ctx.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString("times", "") ?: ""
        return raw.split(",").mapNotNull { it.toLongOrNull() }.count { now - it < windowMs }
    }
}
