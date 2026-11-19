package app.capri.mobile

import kotlin.math.abs
import kotlin.math.floor

// Small audio helpers. pitchShift raises (or lowers) the voice WITHOUT changing how fast it talks.
object Dsp {
    fun resample(x: FloatArray, ratio: Float): FloatArray {           // ratio > 1 = shorter and higher
        if (x.size < 2 || ratio <= 0f) return x
        val n = (x.size / ratio).toInt().coerceAtLeast(1)
        return FloatArray(n) { i ->
            val p = i * ratio
            val k = floor(p).toInt().coerceIn(0, x.size - 2)
            val f = p - k
            x[k] * (1 - f) + x[k + 1] * f
        }
    }

    // WSOLA time stretch: factor 1.2 makes the clip 1.2x longer, same pitch.
    fun stretch(x: FloatArray, factor: Float): FloatArray {
        if (x.size < 2048 || abs(factor - 1f) < 0.02f) return x
        val win = 640; val hs = win / 2; val search = 96
        val ha = hs / factor
        val outLen = (x.size * factor).toInt() + win
        val out = FloatArray(outLen); val norm = FloatArray(outLen)
        val w = FloatArray(win) { (0.5 - 0.5 * Math.cos(2 * Math.PI * it / win)).toFloat() }
        var pos = 0; var o = 0; var prevTail = -1
        var frame = 0
        while (o + win < outLen) {
            var a = (frame * ha).toInt()
            if (a + win + search >= x.size) break
            if (prevTail >= 0) {                                   // pick the nearby segment that continues the last one best
                var best = a; var bestC = -1e30f
                var d = -search
                while (d <= search) {
                    val s = a + d
                    if (s >= 0 && s + win < x.size) {
                        var c = 0f
                        var q = 0
                        while (q < hs) { c += x[s + q] * x[prevTail + q]; q += 4 }
                        if (c > bestC) { bestC = c; best = s }
                    }
                    d += 2
                }
                a = best
            }
            for (q in 0 until win) { out[o + q] += x[a + q] * w[q]; norm[o + q] += w[q] }
            prevTail = a + hs
            o += hs; frame++
        }
        val n = (x.size * factor).toInt().coerceAtMost(outLen)
        return FloatArray(n) { if (norm[it] > 1e-3f) out[it] / norm[it] else out[it] }
    }

    fun pitchShift(x: FloatArray, ratio: Float): FloatArray {
        if (abs(ratio - 1f) < 0.02f) return x
        return stretch(resample(x, ratio), ratio)
    }
}
