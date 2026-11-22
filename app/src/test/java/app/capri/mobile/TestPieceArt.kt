package app.capri.mobile

// Pieces with real, different silhouettes, drawn in the cell's own coordinates (-0.5..0.5), so that tests can tell a rook from a knight.
object PieceArt {
    private fun disc(x: Float, y: Float, cx: Float, cy: Float, r: Float) = (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r
    private fun box(x: Float, y: Float, x0: Float, y0: Float, x1: Float, y1: Float) = x in x0..x1 && y in y0..y1
    private fun ell(x: Float, y: Float, cx: Float, cy: Float, rx: Float, ry: Float) = ((x - cx) / rx) * ((x - cx) / rx) + ((y - cy) / ry) * ((y - cy) / ry) <= 1f
    private fun trap(x: Float, y: Float, yTop: Float, yBot: Float, wTop: Float, wBot: Float): Boolean {
        if (y < yTop || y > yBot) return false
        val w = wTop + (wBot - wTop) * (y - yTop) / (yBot - yTop)
        return Math.abs(x) <= w
    }

    fun inside(type: Int, x: Float, y: Float): Boolean = when (type) {
        1 -> disc(x, y, 0f, 0.12f, 0.2f) || disc(x, y, 0f, -0.12f, 0.1f) || box(x, y, -0.26f, 0.24f, 0.26f, 0.32f)
        2 -> ell(x, y, 0.04f, 0.1f, 0.2f, 0.25f) || box(x, y, -0.3f, -0.3f, 0.06f, -0.04f) || box(x, y, -0.26f, 0.24f, 0.26f, 0.32f)
        3 -> ell(x, y, 0f, -0.02f, 0.13f, 0.3f) || box(x, y, -0.2f, 0.26f, 0.2f, 0.34f) || disc(x, y, 0f, -0.34f, 0.05f)
        4 -> box(x, y, -0.22f, -0.2f, 0.22f, 0.32f) || box(x, y, -0.22f, -0.34f, -0.12f, -0.2f) || box(x, y, -0.05f, -0.34f, 0.05f, -0.2f) || box(x, y, 0.12f, -0.34f, 0.22f, -0.2f)
        5 -> trap(x, y, -0.22f, 0.32f, 0.1f, 0.3f) || disc(x, y, -0.2f, -0.28f, 0.06f) || disc(x, y, -0.1f, -0.33f, 0.06f) || disc(x, y, 0f, -0.35f, 0.06f) || disc(x, y, 0.1f, -0.33f, 0.06f) || disc(x, y, 0.2f, -0.28f, 0.06f)
        6 -> trap(x, y, -0.15f, 0.32f, 0.12f, 0.28f) || box(x, y, -0.04f, -0.4f, 0.04f, -0.15f) || box(x, y, -0.13f, -0.34f, 0.13f, -0.27f)
        else -> false
    }

    // The colour at a point of a piece cell, or null when the background shows through.
    fun pixel(pc: Int, x: Float, y: Float): Int? {
        val t = Math.abs(pc); val white = pc > 0
        if (inside(t, x, y)) return (255 shl 24) or (if (white) 0xF8F8F8 else 0x2B2B2B)
        val e = 0.035f
        if (inside(t, x + e, y) || inside(t, x - e, y) || inside(t, x, y + e) || inside(t, x, y - e)) return (255 shl 24) or (if (white) 0x303030 else 0xE0E0E0)
        return null
    }
}
