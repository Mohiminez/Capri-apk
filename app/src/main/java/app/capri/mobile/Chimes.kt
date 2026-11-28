package app.capri.mobile

import kotlin.math.*

/**
 * Her chimes, ported from capri-desktop's Chimes.cs: six synthesized sets (Glass, Pulse, Air, Shard, Burst, Crush), each with
 * three sounds (sent, heard, listening). Pure: [render] returns 16-bit mono samples at [RATE]; the service plays them.
 */
object Chimes {
    const val RATE = 48000
    val sets = listOf("Glass", "Pulse", "Air", "Shard", "Burst", "Crush")
    // The order the Sound set picker steps through (the desktop's Picked).
    val picked = listOf(0, 3, 4, 5, 1, 2)
    const val SENT = 0; const val HEARD = 1; const val LISTENING = 2
    const val DEFAULT_VOLUME = 40
    private val made = arrayOfNulls<FloatArray>(sets.size * 3)

    private const val D5 = 587.33; private const val Fs5 = 739.99; private const val A5 = 880.0; private const val D6 = 1174.66
    private const val D4 = 293.66; private const val Fs4 = 369.99; private const val A4 = 440.0

    private fun n(sec: Double) = Math.round(sec * RATE).toInt()

    /** Their volume is a percent, heard as a curve: 40% is about 12 dB under 100%. */
    fun gain(volume: Int): Double = (volume.coerceIn(0, 100) / 100.0).pow(1.5)

    fun render(set: Int, chime: Int, volume: Int): ShortArray {
        val shape = made(set, chime); val g = gain(volume)
        return ShortArray(shape.size) { (Math.round((shape[it] * g).coerceIn(-1.0, 1.0) * 32767)).toShort() }
    }

    @Synchronized fun made(set0: Int, chime: Int): FloatArray {
        val set = set0.coerceIn(0, sets.size - 1); val at = set * 3 + chime
        made[at]?.let { return it }
        val s = when (set) { 1 -> pulse(chime); 2 -> air(chime); 3 -> shard(chime); 4 -> burst(chime); 5 -> crush(chime); else -> glass(chime) }
        made[at] = s; return s
    }

    private fun glass(c: Int): FloatArray {
        val b = DoubleArray(n(0.34))
        when (c) {
            SENT -> { strike(b, 0.0, A5, 0.8, 0.07); strike(b, 0.045, D6, 1.0, 0.085) }
            HEARD -> { strike(b, 0.0, D6, 0.75, 0.06); strike(b, 0.062, A5, 1.0, 0.1) }
            else -> { strike(b, 0.0, Fs5, 0.6, 0.06); strike(b, 0.04, A5, 0.75, 0.065); strike(b, 0.08, D6, 1.0, 0.09) }
        }
        return finish(b, 0.42, 6000.0)
    }

    private fun strike(b: DoubleArray, at: Double, pitch: Double, loud: Double, decay: Double) {
        val ratios = doubleArrayOf(1.0, 2.76, 5.4); val gains = doubleArrayOf(1.0, 0.22, 0.07); val fades = doubleArrayOf(1.0, 1.9, 3.2)
        val start = n(at); val rnd = java.util.Random(pitch.toInt() * 7L + start)
        for (i in start until b.size) {
            val t = (i - start).toDouble() / RATE
            val attack = min(1.0, t / 0.0015)
            var sum = 0.0
            for (p in ratios.indices) sum += gains[p] * exp(-t * fades[p] / decay) * sin(2 * PI * pitch * ratios[p] * t)
            if (t < 0.004) sum += 0.12 * (1 - t / 0.004) * (rnd.nextDouble() * 2 - 1)
            b[i] += loud * attack * sum
        }
    }

    private fun pulse(c: Int): FloatArray {
        val b = DoubleArray(n(0.36))
        when (c) {
            SENT -> synth(b, 0.0, D4, A4, 0.05, 1.0, 0.11)
            HEARD -> { synth(b, 0.0, A4, A4, 0.0, 0.8, 0.07); synth(b, 0.09, D4, D4, 0.0, 1.0, 0.1) }
            else -> { synth(b, 0.0, D4, D4, 0.0, 0.6, 0.06); synth(b, 0.07, Fs4, Fs4, 0.0, 0.75, 0.06); synth(b, 0.14, A4, A4, 0.0, 1.0, 0.09) }
        }
        return finish(b, 0.5, 3200.0)
    }

    private fun synth(b: DoubleArray, at: Double, from: Double, to: Double, glide: Double, loud: Double, decay: Double) {
        val start = n(at); var pa = 0.0; var pb = 0.0
        for (i in start until b.size) {
            val t = (i - start).toDouble() / RATE
            val along = if (glide <= 0) 1.0 else min(1.0, t / glide)
            val pitch = from * (to / from).pow(1 - (1 - along).pow(2))
            pa += 2 * PI * pitch * 1.0008 / RATE; pb += 2 * PI * pitch * 0.9992 / RATE
            val attack = min(1.0, t / 0.004)
            val body = exp(-t / decay); val bright = exp(-t / (decay * 0.35))
            var wave = 0.0
            for (ph in doubleArrayOf(pa, pb)) wave += sin(ph) + bright * (0.35 * sin(2 * ph) + 0.15 * sin(3 * ph) + 0.06 * sin(4 * ph))
            b[i] += loud * attack * body * wave * 0.5
        }
    }

    private fun air(c: Int): FloatArray {
        val b = DoubleArray(n(0.36))
        when (c) {
            SENT -> chirp(b, 0.0, 1500.0, 2300.0, 0.07, 1.0, 0.05)
            HEARD -> { chirp(b, 0.0, 2300.0, 1750.0, 0.05, 0.8, 0.035); chirp(b, 0.085, 2000.0, 1500.0, 0.06, 1.0, 0.05) }
            else -> chirp(b, 0.0, 1200.0, 2000.0, 0.16, 1.0, 0.08)
        }
        return finish(b, 0.36, 8000.0)
    }

    private fun chirp(b: DoubleArray, at: Double, from: Double, to: Double, sweep: Double, loud: Double, decay: Double) {
        val start = n(at); val rnd = java.util.Random(from.toInt() + start.toLong())
        var phase = 0.0; var low = 0.0; var band = 0.0
        for (i in start until b.size) {
            val t = (i - start).toDouble() / RATE
            val along = min(1.0, t / sweep)
            val pitch = from * (to / from).pow(along)
            phase += 2 * PI * pitch / RATE
            val env = min(1.0, t / 0.006) * (if (t < sweep) 1.0 else exp(-(t - sweep) / decay))
            val f = 2 * sin(PI * min(pitch * 1.1, RATE / 6.0) / RATE)
            val noise = rnd.nextDouble() * 2 - 1
            low += f * band
            val high = noise - low - 0.9 * band
            band += f * high
            b[i] += loud * env * (0.55 * sin(phase) + 0.18 * sin(2 * phase) + 0.5 * band)
        }
    }

    private fun shard(c: Int): FloatArray {
        val b = DoubleArray(n(0.36))
        when (c) {
            SENT -> {
                val grain = DoubleArray(n(0.02)); strike(grain, 0.0, A5, 1.0, 0.05)
                repeat(b, grain, 0.0, 0.026, 3, 0.75)
                snapped(b, 0.085, D6, 1.0, 0.09, 0.03, 0.014, 2.0)
            }
            HEARD -> { strike(b, 0.0, D6, 0.75, 0.05); strike(b, 0.06, A5, 1.0, 0.11); crushed(b, 0.11, 0.3, 2, 14, 5) }
            else -> {
                val note = DoubleArray(n(0.016)); val pitches = doubleArrayOf(Fs5, A5, D6)
                for (i in 0 until 3) { note.fill(0.0); strike(note, 0.0, pitches[i], 1.0, 0.04); repeat(b, note, i * 0.055, 0.02, 2, 0.6 + i * 0.15) }
                strike(b, 0.15, D6, 0.9, 0.08)
            }
        }
        return finish(b, 0.42, 7000.0)
    }

    private fun burst(c: Int): FloatArray {
        val b = DoubleArray(n(0.34))
        when (c) {
            SENT -> {
                stepped(b, 0.0, doubleArrayOf(1175.0, 1319.0, 1480.0, 1760.0, 1976.0, 2349.0), 0.011, 0.6, 1.0, 0.012)
                val tail = DoubleArray(n(0.03)); stepped(tail, 0.0, doubleArrayOf(2349.0), 0.012, 1.0, 1.0, 0.004)
                repeat(b, tail, 0.08, 0.03, 3, 0.5)
            }
            HEARD -> {
                blip(b, 0.0, 0.035, 2349.0, 1760.0, 0.9, 1.0, 0.03)
                val bl = DoubleArray(n(0.034)); blip(bl, 0.0, 0.03, 1760.0, 1175.0, 1.0, 1.0, 0.004)
                repeat(b, bl, 0.06, 0.034, 3, 0.5)
            }
            else -> hopping(b, 0.0, doubleArrayOf(1480.0, 1976.0, 1480.0, 1976.0, 1480.0, 1976.0, 1480.0, 1976.0, 2349.0), 0.024, 0.0016, 0.55, 1.0, 0.05)
        }
        crushed(b, 0.0, 0.34, 2, 2, 7)
        return finish(b, if (c == LISTENING) 0.2 else 0.27, 5500.0)
    }

    private fun stepped(b: DoubleArray, at: Double, p: DoubleArray, step: Double, lf: Double, lt: Double, decay: Double) =
        hop(b, at, p, DoubleArray(p.size) { step }, lf, lt, decay)

    private fun hopping(b: DoubleArray, at: Double, p: DoubleArray, step: Double, shrink: Double, lf: Double, lt: Double, decay: Double) =
        hop(b, at, p, DoubleArray(p.size) { max(0.008, step - it * shrink) }, lf, lt, decay)

    private fun hop(b: DoubleArray, at: Double, pitches: DoubleArray, lengths: DoubleArray, lf: Double, lt: Double, decay: Double) {
        val start = n(at); val edge = n(0.0015)
        val total = lengths.sum(); val body = n(total); val tail = n(decay * 4)
        var phase = 0.0; var step = 0; var stepEnd = lengths[0]
        var i = 0
        while (i < body + tail && start + i < b.size) {
            val t = i.toDouble() / RATE
            while (step < pitches.size - 1 && t >= stepEnd) { step++; stepEnd += lengths[step] }
            phase += 2 * PI * pitches[step] / RATE
            val loud = lf + (lt - lf) * min(1.0, t / total)
            val env = min(1.0, (i + 1).toDouble() / edge) * (if (i < body) 1.0 else exp(-(t - total) / decay) * (1 - (i - body).toDouble() / tail))
            b[start + i] += loud * env * (sin(phase) + 0.15 * sin(3 * phase))
            i++
        }
    }

    private fun crush(c: Int): FloatArray {
        val b = DoubleArray(n(0.36))
        when (c) {
            SENT -> { synth(b, 0.0, D4, A4, 0.09, 1.0, 0.12); crushed(b, 0.0, 0.36, 6, 6, 5); gate(b, 0.045, 0.012); gate(b, 0.085, 0.01) }
            HEARD -> {
                synth(b, 0.0, A4, A4, 0.0, 0.8, 0.06); synth(b, 0.08, D4, D4, 0.0, 1.0, 0.12)
                crushed(b, 0.08, 0.36, 3, 16, 4); gate(b, 0.15, 0.012); gate(b, 0.19, 0.016)
            }
            else -> {
                synth(b, 0.0, D4, D4, 0.0, 0.7, 0.3); synth(b, 0.12, A4, A4, 0.0, 0.8, 0.09)
                crushed(b, 0.0, 0.12, 10, 3, 4)
                for (gp in doubleArrayOf(0.016, 0.042, 0.064, 0.082, 0.096)) gate(b, gp, 0.012 - gp * 0.06)
            }
        }
        return finish(b, 0.45, 3500.0)
    }

    private fun repeat(b: DoubleArray, grain: DoubleArray, at: Double, gap: Double, count: Int, fade: Double) {
        val edge = n(0.0015)
        for (copy in 0 until count) {
            val start = n(at + copy * gap); val loud = fade.pow(copy)
            var i = 0
            while (i < grain.size && start + i < b.size) {
                val ramp = min(1.0, min((i + 1).toDouble() / edge, (grain.size - i).toDouble() / edge))
                b[start + i] += grain[i] * loud * ramp
                i++
            }
        }
    }

    private fun snapped(b: DoubleArray, at: Double, pitch: Double, loud: Double, decay: Double, snapAt: Double, snapFor: Double, ratio: Double) {
        val start = n(at); var phase = 0.0; var upper = 0.0
        for (i in start until b.size) {
            val t = (i - start).toDouble() / RATE
            val now = if (t >= snapAt && t < snapAt + snapFor) pitch * ratio else pitch
            phase += 2 * PI * now / RATE; upper += 2 * PI * now * 2.76 / RATE
            val attack = min(1.0, t / 0.0015)
            b[i] += loud * attack * (exp(-t / decay) * sin(phase) + 0.2 * exp(-t * 1.9 / decay) * sin(upper))
        }
    }

    private fun blip(b: DoubleArray, at: Double, length: Double, from: Double, to: Double, loud: Double, depth: Double, decay: Double) {
        val start = n(at); val count = n(length); val edge = n(0.0015); val tail = n(decay * 4)
        var phase = 0.0; var wobble = 0.0; var i = 0
        while (i < count + tail && start + i < b.size) {
            val t = i.toDouble() / RATE
            val along = min(1.0, t / length)
            val pitch = from * (to / from).pow(along)
            wobble += 2 * PI * pitch * 0.5 / RATE
            phase += 2 * PI * pitch / RATE + depth * exp(-t / 0.01) * sin(wobble) * 0.2
            val env = min(1.0, (i + 1).toDouble() / edge) * (if (i < count) 1.0 else exp(-(t - length) / decay) * (1 - (i - count).toDouble() / tail))
            b[start + i] += loud * env * (sin(phase) + 0.25 * sin(3 * phase))
            i++
        }
    }

    private fun crushed(b: DoubleArray, from: Double, to: Double, holdFrom: Int, holdTo: Int, bits: Int) {
        val start = n(from); val end = min(b.size, n(to))
        var most = 0.0
        for (i in start until end) most = max(most, abs(b[i]))
        if (most <= 0 || end <= start) return
        val steps = 2.0.pow(bits - 1) / most; var held = 0.0; var left = 0
        for (i in start until end) {
            if (left-- <= 0) {
                val along = (i - start).toDouble() / (end - start)
                left = Math.round(holdFrom + (holdTo - holdFrom) * along).toInt() - 1
                held = Math.round(b[i] * steps) / steps
            }
            b[i] = held
        }
    }

    private fun gate(b: DoubleArray, at: Double, length: Double) {
        val start = n(at); val count = n(max(0.002, length)); val edge = n(0.001)
        var i = 0
        while (i < count && start + i < b.size) {
            val depth = min(1.0, min((i + 1).toDouble() / edge, (count - i).toDouble() / edge))
            b[start + i] *= 1 - depth
            i++
        }
    }

    private fun finish(b: DoubleArray, peak: Double, lowpass: Double): FloatArray {
        var smooth = 0.0; val k = 1 - exp(-2 * PI * lowpass / RATE); var most = 0.0
        for (i in b.indices) { smooth += k * (b[i] - smooth); b[i] = smooth; most = max(most, abs(smooth)) }
        val fade = n(0.02)
        return FloatArray(b.size) { i ->
            val tail = min(1.0, (b.size - 1 - i).toDouble() / fade)
            (if (most <= 0) 0.0 else b[i] / most * peak * tail).toFloat()
        }
    }
}
