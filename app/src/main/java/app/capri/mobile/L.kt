package app.capri.mobile

import android.content.Context
import java.util.Locale

// Tiny two-language helper: t("English", "العربية").
object L {
    @Volatile var ar = false

    // 0 = follow the phone, 1 = English, 2 = Arabic
    fun lang(p: Prefs): Int = p.getI("lang", 0)

    fun init(p: Prefs) {
        ar = when (lang(p)) {
            1 -> false
            2 -> true
            else -> Locale.getDefault().language == "ar"
        }
    }

    fun t(en: String, arabic: String): String = if (ar) arabic else en

    fun locale(p: Prefs): Locale = when (lang(p)) {
        1 -> Locale.ENGLISH
        2 -> Locale("ar")
        else -> Locale.getDefault()
    }
}
