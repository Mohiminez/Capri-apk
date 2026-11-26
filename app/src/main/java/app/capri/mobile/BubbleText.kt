package app.capri.mobile

// Text rules of the speech bubble, moved out of CapriService so they can be unit tested.
object BubbleText {
    const val PAGE = 170

    /** Pages of about [PAGE] characters, broken after a sentence end (. ! ? or the Arabic question mark). */
    fun pages(text: String): List<String> {
        if (text.length <= PAGE) return listOf(text)
        val out = ArrayList<String>()
        var cur = StringBuilder()
        for (part in text.split(Regex("(?<=[.!?؟])\\s+"))) {
            if (cur.length + part.length > PAGE && cur.isNotEmpty()) { out.add(cur.toString().trim()); cur = StringBuilder() }
            cur.append(part).append(' ')
        }
        if (cur.isNotBlank()) out.add(cur.toString().trim())
        return out
    }
}
