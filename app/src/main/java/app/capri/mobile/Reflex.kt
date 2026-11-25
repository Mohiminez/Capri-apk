package app.capri.mobile

// The fast half of her game playing. A model is too slow to steer a game (seconds per look), so, like the newest game agents
// (PORTAL, GameSense), the model only WRITES a small rule policy and this code runs it many times a second:
// "if a black tile is in the lower half, tap its centre", "tap the fire button every 300 ms", "push the stick toward the red thing".
// Plain arithmetic on a small copy of the screen: quick, and testable without a phone. All coordinates are 0..1 of the screen.
class Reflex(val rules: List<Rule>, val untilMs: Long, val hz: Int) {
    class Act(val kind: String, val x1: Float, val y1: Float, val x2: Float, val y2: Float, val ms: Long)
    /** kind: blob | pixel | notpixel | changed | none. color: dark light red green blue yellow orange purple cyan #rrggbb */
    class Cond(val kind: String = "none", val color: String = "dark", val region: DoubleArray = doubleArrayOf(0.0, 0.0, 1.0, 1.0),
               val pick: String = "largest", val x: Double = 0.5, val y: Double = 0.5, val tol: Double = 0.25, val fill: Double = 0.5, val min: Double = 0.002)
    /** do-item: tap(x,y) | tapblob(dx,dy) | hold | swipe | joy(cx,cy,dx,dy) | follow(cx,cy) */
    class Do(val kind: String, val a: DoubleArray = DoubleArray(0), val ms: Long = 60)
    class Rule(val cond: Cond, val acts: List<Do>, val coolMs: Long)
    class Frame(val px: IntArray, val w: Int, val h: Int)
    class Blob(val x: Float, val y: Float, val share: Float)

    private val lastFired = LongArray(rules.size)
    var fired = 0
        private set
    var lastSeen = ""
        private set

    fun step(f: Frame, prev: Frame?, now: Long): List<Act> {
        val out = ArrayList<Act>()
        for ((i, r) in rules.withIndex()) {
            if (now - lastFired[i] < r.coolMs) continue
            var blob: Blob? = null
            val c = r.cond
            when (c.kind) {
                "blob" -> blob = findBlob(f, c) ?: continue
                "pixel" -> if (!pixelIs(f, c)) continue
                "notpixel" -> if (pixelIs(f, c)) continue
                "changed" -> if (prev == null || !changed(f, prev, c)) continue
            }
            val before = out.size
            for (a in r.acts) {
                val v = a.a
                fun g(k: Int) = if (k < v.size) v[k].toFloat() else 0f
                when (a.kind) {
                    "tap" -> out.add(Act("tap", g(0).coerceIn(0f, 1f), g(1).coerceIn(0f, 1f), g(0).coerceIn(0f, 1f), g(1).coerceIn(0f, 1f), 60))
                    "tapblob" -> if (blob != null) { val x = (blob.x + g(0)).coerceIn(0f, 1f); val y = (blob.y + g(1)).coerceIn(0f, 1f); out.add(Act("tap", x, y, x, y, 60)) }
                    "hold" -> out.add(Act("hold", g(0).coerceIn(0f, 1f), g(1).coerceIn(0f, 1f), g(0).coerceIn(0f, 1f), g(1).coerceIn(0f, 1f), a.ms.coerceAtLeast(100)))
                    "swipe" -> out.add(Act("swipe", g(0).coerceIn(0f, 1f), g(1).coerceIn(0f, 1f), g(2).coerceIn(0f, 1f), g(3).coerceIn(0f, 1f), a.ms.coerceAtLeast(80)))
                    "joy" -> out.add(Act("swipe", g(0), g(1), (g(0) + g(2)).coerceIn(0f, 1f), (g(1) + g(3)).coerceIn(0f, 1f), a.ms.coerceAtLeast(300)))
                    "follow" -> if (blob != null) {
                        val dx = blob.x - 0.5f; val dy = blob.y - 0.5f
                        val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.01f)
                        out.add(Act("swipe", g(0), g(1), (g(0) + dx / len * 0.08f).coerceIn(0f, 1f), (g(1) + dy / len * 0.08f).coerceIn(0f, 1f), a.ms.coerceAtLeast(300)))
                    }
                }
            }
            if (out.size > before || c.kind == "none") { lastFired[i] = now; if (out.size > before) fired++ }
            if (blob != null) lastSeen = "blob %.2f,%.2f".format(blob.x, blob.y)
        }
        return out.take(5)
    }

    companion object {
        /** Does this pixel look like [spec]: dark, light, red, green, blue, yellow, orange, purple, white, black, or "#rrggbb". */
        fun matches(argb: Int, spec: String, tol: Double = 0.25): Boolean {
            val r = (argb shr 16) and 255; val g = (argb shr 8) and 255; val b = argb and 255
            val mx = maxOf(r, g, b) / 255.0; val mn = minOf(r, g, b) / 255.0
            val v = mx; val s = if (mx == 0.0) 0.0 else (mx - mn) / mx
            var hue = 0.0
            if (mx != mn) {
                val d = mx - mn
                hue = when (maxOf(r, g, b)) {
                    r -> ((g - b) / 255.0 / d) % 6
                    g -> (b - r) / 255.0 / d + 2
                    else -> (r - g) / 255.0 / d + 4
                } * 60; if (hue < 0) hue += 360
            }
            return when (spec.lowercase()) {
                "dark", "black" -> v < 0.22
                "light", "white" -> v > 0.85 && s < 0.2
                "red" -> s > 0.5 && v > 0.4 && (hue >= 345 || hue <= 15)
                "orange" -> s > 0.5 && v > 0.5 && hue in 15.0..45.0
                "yellow" -> s > 0.5 && v > 0.5 && hue in 45.0..70.0
                "green" -> s > 0.4 && v > 0.3 && hue in 75.0..160.0
                "cyan" -> s > 0.4 && v > 0.4 && hue in 160.0..200.0
                "blue" -> s > 0.4 && v > 0.3 && hue in 200.0..260.0
                "purple", "pink" -> s > 0.35 && v > 0.4 && hue in 260.0..345.0
                else -> if (spec.startsWith("#") && spec.length == 7) {
                    val c = try { spec.substring(1).toInt(16) } catch (e: Exception) { return false }
                    val dr = (r - ((c shr 16) and 255)) / 255.0; val dg = (g - ((c shr 8) and 255)) / 255.0; val db = (b - (c and 255)) / 255.0
                    Math.sqrt((dr * dr + dg * dg + db * db) / 3.0) < tol
                } else false
            }
        }

        fun pixelIs(f: Frame, c: Cond): Boolean {
            val x = (c.x.coerceIn(0.0, 1.0) * (f.w - 1)).toInt(); val y = (c.y.coerceIn(0.0, 1.0) * (f.h - 1)).toInt()
            return matches(f.px[y * f.w + x], c.color, c.tol)
        }

        fun changed(f: Frame, prev: Frame, c: Cond): Boolean {
            if (f.w != prev.w || f.h != prev.h) return true
            val r = c.region
            val x1 = (r[0] * f.w).toInt(); val y1 = (r[1] * f.h).toInt(); val x2 = (r[2] * f.w).toInt().coerceAtMost(f.w); val y2 = (r[3] * f.h).toInt().coerceAtMost(f.h)
            var diff = 0; var n = 0
            for (y in y1 until y2 step 2) for (x in x1 until x2 step 2) {
                val a = f.px[y * f.w + x]; val b = prev.px[y * f.w + x]
                if (Math.abs(((a shr 16) and 255) - ((b shr 16) and 255)) + Math.abs(((a shr 8) and 255) - ((b shr 8) and 255)) + Math.abs((a and 255) - (b and 255)) > 60) diff++
                n++
            }
            return n > 0 && diff.toDouble() / n >= c.min.coerceAtLeast(0.005)
        }

        fun findBlob(f: Frame, c: Cond): Blob? {
            val spec = c.color; val tol = c.tol
            val r = c.region
            val cell = 6
            val cw = f.w / cell; val ch = f.h / cell
            if (cw < 2 || ch < 2) return null
            val x1 = (r[0] * cw).toInt(); val y1 = (r[1] * ch).toInt(); val x2 = (r[2] * cw).toInt().coerceAtMost(cw); val y2 = (r[3] * ch).toInt().coerceAtMost(ch)
            val need = (cell * cell * c.fill).toInt().coerceAtLeast(1)
            val on = BooleanArray(cw * ch)
            for (cy in y1 until y2) for (cx in x1 until x2) {
                var n = 0
                for (yy in 0 until cell step 2) for (xx in 0 until cell step 2) if (matches(f.px[(cy * cell + yy) * f.w + cx * cell + xx], spec, tol)) n++
                on[cy * cw + cx] = n * 4 >= need
            }
            val seen = BooleanArray(cw * ch)
            var best: Blob? = null; var bestKey = Double.NEGATIVE_INFINITY
            val minCells = (c.min * cw * ch).toInt().coerceAtLeast(2)
            val stack = IntArray(cw * ch)
            for (start in 0 until cw * ch) {
                if (!on[start] || seen[start]) continue
                var sp = 0; stack[sp++] = start; seen[start] = true
                var n = 0; var sx = 0L; var sy = 0L
                while (sp > 0) {
                    val cc = stack[--sp]; val cx = cc % cw; val cy = cc / cw
                    n++; sx += cx; sy += cy
                    for (d in 0 until 4) {
                        val nx = cx + (if (d == 0) 1 else if (d == 1) -1 else 0); val ny = cy + (if (d == 2) 1 else if (d == 3) -1 else 0)
                        if (nx in x1 until x2 && ny in y1 until y2 && on[ny * cw + nx] && !seen[ny * cw + nx]) { seen[ny * cw + nx] = true; stack[sp++] = ny * cw + nx }
                    }
                }
                if (n < minCells) continue
                val bx = (sx.toDouble() / n + 0.5) / cw; val by = (sy.toDouble() / n + 0.5) / ch
                val key = when (c.pick) {
                    "lowest" -> by; "highest" -> -by; "leftmost" -> -bx; "rightmost" -> bx
                    "nearest_center" -> -Math.hypot(bx - 0.5, by - 0.5)
                    else -> n.toDouble()
                }
                if (key > bestKey) { bestKey = key; best = Blob(bx.toFloat(), by.toFloat(), n.toFloat() / (cw * ch)) }
            }
            return best
        }
    }
}
