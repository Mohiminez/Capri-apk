package app.capri.mobile

import android.os.Handler
import android.os.Looper
import android.os.Message

// coerceIn() throws when the range is empty (a tiny screen, a view that isn't measured yet). These two never throw.
fun Int.clampTo(lo: Int, hi: Int): Int = if (hi < lo) lo else if (this < lo) lo else if (this > hi) hi else this
fun Float.clampTo(lo: Float, hi: Float): Float = if (hi < lo) lo else if (this < lo) lo else if (this > hi) hi else this

// A Handler that never lets one failing task kill the whole service: the error goes to the crash log and the next task still runs.
class SafeHandler(looper: Looper, private val onError: (Throwable) -> Unit) : Handler(looper) {
    override fun dispatchMessage(msg: Message) {
        try {
            super.dispatchMessage(msg)
        } catch (e: Throwable) {
            try { onError(e) } catch (e2: Throwable) { }
        }
    }
}

// A background thread that logs its own failures instead of taking the whole app down with it.
fun bg(name: String = "capri-bg", ctx: android.content.Context? = null, work: () -> Unit): Thread {
    val t = Thread({
        try {
            work()
        } catch (e: Throwable) {
            if (ctx != null) CrashLog.record(ctx, e, "background job ($name)") else Diag.log("bg", name + ": " + e)
        }
    }, name)
    t.start()
    return t
}
