package app.capri.mobile

// Remembers which brains failed lately, so one busy or broken provider doesn't cost every message a long wait: a provider that just failed is
// tried last for a while (30 s, then 2 min, then 5 min while it keeps failing; a refused key waits 10 min) and goes back to the front as
// soon as it answers once. Nothing is ever dropped: if every brain is cooling, the order stays as it was. Pure, unit tested.
class ProviderHealth {
    private class St(var fails: Int = 0, var until: Long = 0L)
    private val st = HashMap<Int, St>()

    @Synchronized fun ok(i: Int) { st.remove(i) }

    /** code = HTTP status, or 0 for a network error or anything else. */
    @Synchronized fun fail(i: Int, code: Int, now: Long) {
        val s = st.getOrPut(i) { St() }
        s.fails++
        val wait = when {
            code == 401 || code == 403 || code == 402 -> 10 * 60_000L
            s.fails <= 1 -> 30_000L
            s.fails == 2 -> 120_000L
            else -> 300_000L
        }
        s.until = now + wait
    }

    @Synchronized fun cooling(i: Int, now: Long): Boolean = (st[i]?.until ?: 0L) > now

    /** Healthy ones first (in your order), then the cooling ones (in your order). */
    @Synchronized fun order(chain: List<Int>, now: Long): List<Int> {
        val (cool, fine) = chain.partition { (st[it]?.until ?: 0L) > now }
        return fine + cool
    }
}
