package app.capri.mobile

// "Why is my phone slow?": a plain verdict from numbers the phone already has. Pure (no Android), unit tested; Actions gathers the numbers.
object PhoneHealth {
    class Stats(
        val ramFreeMb: Long, val ramTotalMb: Long, val storageFreeMb: Long, val storageTotalMb: Long,
        val batteryPct: Int, val charging: Boolean, val tempC: Float, val powerSave: Boolean, val thermal: Int
    )

    /** Findings, most important first. thermal: PowerManager's status 0 none .. 6 shutdown. */
    fun findings(s: Stats, ar: Boolean): List<String> {
        val out = ArrayList<String>()
        if (s.thermal >= 2 || s.tempC >= 43f) out.add(if (ar) "الجوال حار (${"%.0f".format(s.tempC)}°): يبطّئ نفسه عشان يبرد. شيل الغطاء وبعّده عن الشاحن والشمس." else "The phone is hot (${"%.0f".format(s.tempC)}°C), so it slows itself down to cool off. Take the case off and keep it away from the charger and the sun.")
        val ramFree = if (s.ramTotalMb > 0) s.ramFreeMb * 100 / s.ramTotalMb else 100
        if (ramFree < 12) out.add(if (ar) "الرام شبه ممتلية (فاضي $ramFree%): سكّر التطبيقات اللي ما تحتاجها وأعد تشغيل الجوال." else "Memory is nearly full ($ramFree% free): close apps you don't need and restart the phone.")
        val stFree = if (s.storageTotalMb > 0) s.storageFreeMb * 100 / s.storageTotalMb else 100
        if (stFree < 10 || s.storageFreeMb < 2048) out.add(if (ar) "التخزين شبه ممتلي (فاضي ${s.storageFreeMb / 1024} جيجا): امسح مقاطع وكاش، الجوال يبطأ إذا التخزين ممتلي." else "Storage is nearly full (${s.storageFreeMb / 1024} GB free): delete videos and caches, a full phone gets slow.")
        if (s.powerSave) out.add(if (ar) "وضع توفير الطاقة شغال: يقلل سرعة المعالج. طفّيه إذا تبي أداء." else "Battery saver is on and it limits the processor. Turn it off if you want speed.")
        if (!s.charging && s.batteryPct in 0..15) out.add(if (ar) "البطارية ${s.batteryPct}%: الجوال يخفف الأداء لما توطي." else "Battery is ${s.batteryPct}%, and phones throttle when it gets this low.")
        return out
    }

    fun say(s: Stats, ar: Boolean): String {
        val f = findings(s, ar)
        val head = if (ar) "الرام الفاضي ${s.ramFreeMb} من ${s.ramTotalMb} ميجا، التخزين الفاضي ${s.storageFreeMb / 1024} جيجا، البطارية ${s.batteryPct}%، الحرارة ${"%.0f".format(s.tempC)}°."
                   else "Free memory ${s.ramFreeMb} of ${s.ramTotalMb} MB, free storage ${s.storageFreeMb / 1024} GB, battery ${s.batteryPct}%, temperature ${"%.0f".format(s.tempC)}°C."
        if (f.isEmpty()) return head + (if (ar) " كل شي طبيعي. إذا للحين بطيء: أعد تشغيل الجوال وحدّث التطبيقات الثقيلة." else " Nothing looks wrong. If it is still slow, restart the phone and update the heavy apps.")
        return head + "\n" + f.joinToString("\n")
    }

    fun isAsk(raw: String): Boolean {
        val t = raw.lowercase().replace(Regex("[^a-z0-9\\u0600-\\u06FF ]"), " ").replace('أ', 'ا').replace('إ', 'ا').replace(Regex("\\s+"), " ").trim()
        return Regex("(?:why is|why's) (?:my |the )?(?:phone|device)(?: so)? (?:slow|laggy|lagging|hot|heating)|(?:phone|device) (?:is )?(?:slow|laggy|hot)|phone health|check my phone").containsMatchIn(t) ||
            Regex("ليش (?:جوالي|الجوال|تلفوني|هاتفي) (?:بطيء|بطي|يهنگ|يهنج|يعلق|حار|سخن|يسخن|ثقيل)|جوالي (?:بطيء|بطي|يهنگ|يهنج|ثقيل|حار)|فحص (?:الجوال|جوالي)|صحه (?:الجوال|جوالي)").containsMatchIn(t)
    }
}
