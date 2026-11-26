package app.capri.mobile

// Wake-on-LAN: "wake my PC" sends the magic packet (6 x FF, then the MAC 16 times) to the whole network, so a PC on the same Wi-Fi that has
// Wake-on-LAN switched on starts up. Pure parsing and packet building (unit tested); the UDP send is in Actions.
object WakeOnLan {
    /** "AA:BB:CC:DD:EE:FF", "aa-bb-cc-dd-ee-ff" or "aabbccddeeff" -> 6 bytes, or null. */
    fun mac(s: String): ByteArray? {
        val h = s.trim().replace(Regex("[:\\-.\\s]"), "")
        if (!Regex("[0-9a-fA-F]{12}").matches(h)) return null
        return ByteArray(6) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun packet(mac: ByteArray): ByteArray {
        val p = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) p[i] = 0xFF.toByte()
        for (r in 0 until 16) System.arraycopy(mac, 0, p, 6 + r * 6, 6)
        return p
    }

    fun pretty(s: String): String = mac(s)?.joinToString(":") { "%02X".format(it) } ?: ""

    /** "wake my pc", "wake up the computer", "شغل الكمبيوتر", "فيّق الكمبيوتر" -> true. */
    fun isAsk(raw: String): Boolean {
        val t = raw.lowercase().replace(Regex("[^a-z0-9\\u0600-\\u06FF ]"), " ").replace('أ', 'ا').replace('إ', 'ا').replace(Regex("\\s+"), " ").trim()
        if (Regex("^(?:please )?(?:wake|wake up|boot|turn on|power on|start)(?: up)? (?:my |the )?(?:pc|computer|laptop|desktop)(?: please| from here| with wake on lan)?$").matches(t)) return true
        if (Regex("^(?:wake on lan|wol)$").matches(t)) return true
        return Regex("^(?:شغلي?|فيقي?|صحي|صحيي|وقظي?|اشعلي?) (?:لي |ل)?(?:ال)?(?:كمبيوتر|كمبيوترى|كومبيوتر|لابتوب|حاسوب|بي سي|pc)(?: حقي| تبعي| من هنا| عن بعد)?$").matches(t)
    }
}
