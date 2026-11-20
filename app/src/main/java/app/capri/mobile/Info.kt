package app.capri.mobile

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Plain answers about the phone that need no AI and no internet.
object DeviceInfo {
    fun answer(ctx: Context, kind: String): String {
        val loc = if (L.ar) Locale("ar") else Locale.ENGLISH
        return try {
            when (kind) {
                "time" -> L.t("It's ", "الساعة ") + SimpleDateFormat("h:mm a", loc).format(Date())
                "date" -> {
                    val g = SimpleDateFormat("EEEE d MMMM yyyy", loc).format(Date())
                    val h = try {
                        val d = java.time.chrono.HijrahDate.now()
                        " (" + java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", loc).format(d) + " " + L.t("AH", "هـ") + ")"
                    } catch (e: Throwable) { "" }
                    L.t("Today is ", "اليوم ") + g + h
                }
                "battery" -> {
                    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                    val pct = if (lvl >= 0) lvl * 100 / scale else -1
                    val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
                    if (pct < 0) L.t("I can't read the battery.", "ما أقدر أقرأ البطارية.")
                    else L.t("Battery is at $pct%", "البطارية $pct%") + (if (charging) L.t(" and charging.", " وتشحن.") else if (pct <= 15) L.t(". Charge me soon, choom.", ". اشحن قريب يا خوي.") else ".")
                }
                "storage" -> {
                    val s = StatFs(Environment.getDataDirectory().path)
                    val free = s.availableBytes / 1_000_000_000.0
                    val total = s.totalBytes / 1_000_000_000.0
                    L.t("Free storage: %.1f GB of %.1f GB.", "المساحة الفاضية: %.1f جيجا من %.1f.").format(Locale.US, free, total)
                }
                "network" -> {
                    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                    when {
                        caps == null -> L.t("You're offline.", "ما في نت.")
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> L.t("You're online on Wi-Fi.", "متصل بالواي فاي.")
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> L.t("You're online on mobile data.", "متصل ببيانات الجوال.")
                        else -> L.t("You're online.", "متصل بالنت.")
                    }
                }
                else -> ""
            }
        } catch (e: Throwable) { L.t("I couldn't read that.", "ما قدرت أقرأها.") }
    }
}

// Orders that work even with no brain at all.
object LocalAsks {
    private fun digits(s: String): String {
        val sb = StringBuilder()
        for (c in s) sb.append(when (c) { in '\u0660'..'\u0669' -> '0' + (c - '\u0660'); in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0'); else -> c })
        return sb.toString()
    }

    fun match(raw: String): String? {
        var t = digits(raw.lowercase()).replace("\u2019", "'").replace("'", " ").replace(Regex("[^a-z0-9:\u0600-\u06FF ]"), " ").replace(Regex("\\s+"), " ").trim()
        t = t.replace(Regex("^(hey |hi |ok |okay )?(capri |كابري |يا كابري )?(please |can you |could you |لو سمحت |ممكن )*"), "").replace(Regex(" (please|لو سمحت)$"), "").trim()
        if (t.isEmpty()) return null
        t = t.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه')

        // games
        if (Regex("^(?:(?:let s |lets |i want to |wanna )?play )?(?:connect (?:4|four)|four in a row|4 in a row)(?: with me)?$|^(?:نلعب |العب |لعب |يلا نلعب )?(?:اربعه بصف|فور في صف|اربعه في صف|4 بصف)(?: معي)?$").matches(t))
            return L.t("Let's go. [[game:c4]]", "يلا. [[game:c4]]")
        if (Regex("^(?:(?:let s |lets |i want to |wanna )?play )?(?:tic tac toe|tictactoe|noughts and crosses|xo)(?: with me)?$|^(?:نلعب |العب |لعب |يلا نلعب )?(?:اكس او|اكس اوه|xo|اوه اكس)(?: معي)?$").matches(t))
            return L.t("You're X. [[game:ttt]]", "أنت X. [[game:ttt]]")
        if (Regex("^(?:(?:let s |lets |i want to |wanna )?play )?(?:rock paper scissors|rps)(?: with me)?$|^(?:نلعب |العب |لعب |يلا نلعب )?(?:حجر ورقه مقص|حجره ورقه مقص|حجر ورق مقص)(?: معي)?$").matches(t))
            return L.t("Rock, paper... [[game:rps]]", "حجر ورقة... [[game:rps]]")
        if (Regex("^(?:play |العب |نلعب |لعبه |يلا نلعب )?(?:a game|game|games|لعبه|العاب|الالعاب)(?: with me| معي)?$").matches(t))
            return L.t("Pick one: chess, connect four, tic-tac-toe, rock-paper-scissors. [[game:c4]]", "اختار: شطرنج، أربعة بصف، XO، حجر ورقة مقص. [[game:c4]]")

        // phone facts
        if (Regex("^(?:what time is it|what s the time|whats the time|the time|time|time now|current time|كم الساعه|الساعه كم|كم الساعه الحين|كم الوقت|الوقت|شكد الساعه|شكد الساعة)$").matches(t)) return "[[info:time]]"
        if (Regex("^(?:what s the date|whats the date|what is the date|today s date|todays date|the date|date|what day is it|what day is today|شنو التاريخ|ما هو التاريخ|التاريخ|اي يوم اليوم|ايش اليوم|شنو اليوم|وش اليوم|اليوم كم)$").matches(t)) return "[[info:date]]"
        if (Regex("^(?:battery|battery level|battery status|how much battery(?: do i have| is left| left)?|how s my battery|hows my battery|check (?:the )?battery|كم البطاريه|البطاريه|نسبه البطاريه|وش نسبه البطاريه|كم الشحن|شكد البطاريه|شكد الشحن|كم باقي شحن)$").matches(t)) return "[[info:battery]]"
        if (Regex("^(?:storage|free storage|how much storage(?: is left| do i have)?|كم المساحه|المساحه|المساحه الفاضيه|كم باقي مساحه)$").matches(t)) return "[[info:storage]]"
        if (Regex("^(?:am i online|do i have internet|is the internet on|internet|wifi|wi fi|هل انا متصل|في نت|اكو نت|النت|الواي فاي)$").matches(t)) return "[[info:network]]"

        // flashlight and volume
        if (Regex("^(?:turn on |switch on |enable )?(?:the )?(?:flashlight|torch|flash light)(?: on)?$|^(?:شغل|شغلي|افتح|افتحي|فعل)(?: ال)?(?:كشاف|فلاش|الكشاف|الفلاش|المصباح|الضوء)$|^(?:كشاف|فلاش)$").matches(t)) return "[[flash:on]]"
        if (Regex("^(?:turn off |switch off |disable )(?:the )?(?:flashlight|torch|flash light)$|^(?:flashlight|torch) off$|^(?:اطفي|اطفه|اقفل|سكر|سكري|طفي|طفّي)(?: ال)?(?:كشاف|فلاش|الكشاف|الفلاش|المصباح|الضوء)$").matches(t)) return "[[flash:off]]"
        if (Regex("^(?:volume up|turn (?:it |the volume )?up|louder|raise the volume|ارفع الصوت|ارفعي الصوت|زيد الصوت|زيدي الصوت|علي الصوت|عالي الصوت|صوت اعلى)$").matches(t)) return "[[volume:up]]"
        if (Regex("^(?:volume down|turn (?:it |the volume )?down|quieter|lower the volume|وطي الصوت|وطيه|نزل الصوت|نزلي الصوت|خفض الصوت|قلل الصوت|صوت اقل)$").matches(t)) return "[[volume:down]]"
        if (Regex("^(?:mute|mute the volume|mute the phone|اكتم الصوت|ميوت|اسكت الجوال|اسكتي الجوال)$").matches(t)) return "[[volume:mute]]"
        Regex("^(?:set (?:the )?volume (?:to )?|volume |خلي الصوت |خلّي الصوت |الصوت )(\\d{1,3}) ?(?:%|percent|بالمئه|بالمية)?$").find(t)?.let { return "[[volume:${it.groupValues[1]}]]" }

        // timers and alarms (Arabic; the English timer is in QuickAsks)
        Regex("^(?:تايمر|مؤقت|موقت|عداد|ضبط مؤقت|اضبط مؤقت|حط مؤقت|سوي مؤقت|سوي تايمر|حط تايمر)(?: لمده| ل| لـ)? ?(\\d+) ?(ثانيه|ثواني|دقيقه|دقايق|دقائق|ساعه|ساعات)$").find(t)?.let {
            val n = it.groupValues[1].toInt(); val u = it.groupValues[2]
            val secs = when { u.startsWith("ساع") -> n * 3600; u.startsWith("دق") -> n * 60; else -> n }
            return L.t("Timer's running.", "المؤقت شغّال.") + " [[timer:$secs|Capri]]"
        }
        Regex("^(?:wake me up|set an alarm|alarm)(?: for| at)? (\\d{1,2})(?::(\\d{2}))? ?(am|pm)?$").find(t)?.let { return alarm(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        Regex("^(?:صحيني|صحيني الساعه|منبه|منبه الساعه|اضبط منبه الساعه|حط منبه الساعه|سوي منبه الساعه|اضبط المنبه الساعه|حط المنبه الساعه|صحيني الساعة) ?(\\d{1,2})(?::(\\d{2}))? ?(صباحا|الصبح|ص|مساء|م|بالليل|العصر)?$").find(t)?.let {
            val ap = it.groupValues[3]
            return alarm(it.groupValues[1], it.groupValues[2], if (ap in setOf("مساء", "م", "بالليل", "العصر")) "pm" else if (ap.isNotEmpty()) "am" else "")
        }
        return null
    }

    private fun alarm(hs: String, ms: String, ap: String): String? {
        var h = hs.toInt(); val m = ms.toIntOrNull() ?: 0
        if (h > 23 || m > 59) return null
        if (ap == "pm" && h < 12) h += 12
        if (ap == "am" && h == 12) h = 0
        return L.t("Alarm set.", "ضبطت المنبه.") + " [[alarm:$h:${"%02d".format(m)}|Capri]]"
    }
}
