package app.capri.mobile

import android.content.Context

// Direct phone commands that don't need an AI to figure out: "open WhatsApp and type hi", "scroll down", "go back", "tap Send".
// She runs them with her accessibility hands. Blocking, so call from a background thread.
object Hands {
    fun run(ctx: Context, spec: String): String {
        val acc = CapriAccess.instance ?: return L.t("I need my accessibility access first.", "أحتاج صلاحية الوصول أول.").also { Diag.log("hands", "no accessibility connection for: $spec") }
        val parts = spec.split("|")
        val cmd = parts[0].trim().lowercase()
        val rest = parts.drop(1).joinToString("|").trim()
        return when (cmd) {
            "type" -> if (acc.typeText(rest)) L.t("Typed it.", "كتبتها.")
                else L.t("I can't find a text box. Open one and try again.", "ما لقيت خانة كتابة. افتح وحدة وجرّب مرة ثانية.")
            "tap" -> if (acc.clickText(rest)) L.t("Tapped $rest.", "ضغطت على $rest.")
                else L.t("I can't see \"$rest\" on the screen.", "ما أشوف \"$rest\" على الشاشة.")
            "scroll" -> { acc.scroll(!rest.equals("up", true)); L.t("Scrolled.", "سحبت.") }
            "back", "home", "recents", "notifs", "settings", "shot", "lock" ->
                if (acc.global(cmd)) L.t("Done.", "تمّت.") else L.t("This phone won't let me do that.", "الجوال ما يسمح لي بهذا.")
            "opentype" -> openAndType(ctx, acc, parts.getOrElse(1) { "" }, parts.drop(2).joinToString("|"))
            "test" -> selfTest(ctx, acc)
            else -> L.t("I don't know that move.", "ما أعرف هالأمر.")
        }
    }

    private fun openAndType(ctx: Context, acc: CapriAccess, app: String, text: String): String {
        val found = Actions.findApp(ctx, app) ?: return L.t("I can't find an app called $app.", "ما لقيت تطبيق اسمه $app.")
        Actions.openApp(ctx, app)
        acc.waitFor(found.second, 8000)
        try { Thread.sleep(1500) } catch (e: InterruptedException) { }
        if (text.isBlank()) return L.t("Opened ${found.first}.", "فتحت ${found.first}.")
        var ok = acc.typeText(text)
        if (!ok) {
            // No box yet: tap whatever looks like "write / search / message" and try again.
            for (hint in listOf("type a message", "message", "search", "write", "compose", "new", "رسالة", "بحث", "اكتب")) {
                if (acc.clickText(hint)) {
                    try { Thread.sleep(1000) } catch (e: InterruptedException) { }
                    if (acc.typeText(text)) { ok = true; break }
                }
            }
        }
        return if (ok) L.t("Typed it in ${found.first}.", "كتبتها في ${found.first}.")
        else L.t("I opened ${found.first} but couldn't find where to type. Tell me what to tap.", "فتحت ${found.first} بس ما لقيت وين أكتب. قل لي وش أضغط.")
    }

    // The Home screen's "Test her hands" button: opens Settings and reads it.
    private fun selfTest(ctx: Context, acc: CapriAccess): String {
        val found = Actions.findApp(ctx, "settings") ?: return L.t("I can't find the Settings app to test with.", "ما لقيت تطبيق الإعدادات عشان أجرّب.")
        Actions.openApp(ctx, "settings")
        acc.waitFor(found.second, 8000)
        try { Thread.sleep(1500) } catch (e: InterruptedException) { }
        val lines = acc.dump(2000).lines().size - 1
        if (lines < 3) return L.t("I opened Settings but I can't read it. Is my accessibility access really on?", "فتحت الإعدادات بس ما أقدر أقرأها. تأكد إن صلاحية الوصول شغّالة فعلاً.")
        acc.scroll(true)
        try { Thread.sleep(700) } catch (e: InterruptedException) { }
        acc.global("home")
        return L.t("Hands work. I opened Settings, read $lines things and scrolled.", "اليدين شغّالة. فتحت الإعدادات وقرأت $lines عنصر وسحبت.")
    }
}
