package app.capri.mobile

/**
 * How much she has used the brain: asks and tokens per day, kept for two weeks (like capri-desktop's Spend, minus prices,
 * which go stale). Stored as one pref string "day:asks:in:out;day:asks:in:out". Pure, so it is unit tested.
 */
object Spend {
    class Row(val day: Int, val asks: Int, val tokIn: Long, val tokOut: Long) { val tokens get() = tokIn + tokOut }

    const val KEEP_DAYS = 14

    fun parse(raw: String): List<Row> = raw.split(";").mapNotNull { p ->
        val f = p.split(":")
        if (f.size != 4) null else try { Row(f[0].toInt(), f[1].toInt(), f[2].toLong(), f[3].toLong()) } catch (e: NumberFormatException) { null }
    }

    private fun write(rows: List<Row>) = rows.joinToString(";") { "${it.day}:${it.asks}:${it.tokIn}:${it.tokOut}" }

    /** Adds one ask to today's row and drops anything older than two weeks. */
    fun add(raw: String, day: Int, tokIn: Long, tokOut: Long): String {
        val rows = parse(raw).filter { it.day > day - KEEP_DAYS }.toMutableList()
        val i = rows.indexOfFirst { it.day == day }
        if (i >= 0) { val r = rows[i]; rows[i] = Row(day, r.asks + 1, r.tokIn + tokIn.coerceAtLeast(0), r.tokOut + tokOut.coerceAtLeast(0)) }
        else rows.add(Row(day, 1, tokIn.coerceAtLeast(0), tokOut.coerceAtLeast(0)))
        return write(rows.sortedBy { it.day })
    }

    fun today(raw: String, day: Int): Row = parse(raw).firstOrNull { it.day == day } ?: Row(day, 0, 0, 0)

    fun week(raw: String, day: Int): Row {
        val rs = parse(raw).filter { it.day > day - 7 && it.day <= day }
        return Row(day, rs.sumOf { it.asks }, rs.sumOf { it.tokIn }, rs.sumOf { it.tokOut })
    }

    /** About 4 characters to a token, when a provider does not say. */
    fun estimate(chars: Int): Long = (chars / 4 + 1).toLong()

    fun tokens(n: Long): String = when {
        n >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 10_000 -> "${n / 1000}k"
        n >= 1000 -> String.format(java.util.Locale.US, "%.1fk", n / 1000.0)
        else -> n.toString()
    }

    fun line(raw: String, day: Int, ar: Boolean): String {
        val t = today(raw, day); val w = week(raw, day)
        return if (ar) "اليوم: ${t.asks} طلب، ${tokens(t.tokens)} توكن  ·  الأسبوع: ${w.asks} طلب، ${tokens(w.tokens)} توكن"
        else "Today: ${t.asks} asks, ${tokens(t.tokens)} tokens  ·  7 days: ${w.asks} asks, ${tokens(w.tokens)} tokens"
    }
}
