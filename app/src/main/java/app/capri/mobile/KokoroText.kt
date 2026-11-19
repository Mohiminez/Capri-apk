package app.capri.mobile

import java.util.zip.ZipFile

// The pure-Kotlin half of her on-phone voice: turning English text into the phoneme symbols Kokoro was trained on, and reading its files.
// (Kokoro is the voice the Windows version uses.) No Android classes here, so it can be unit tested on a computer.

// A tiny JSON reader, enough for the flat files we read (word lists and the vocab table). It never builds a huge object tree.
class MiniJson(private val s: String) {
    var i = 0
    fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
    fun peek(): Char { ws(); return if (i < s.length) s[i] else '\u0000' }
    fun eat(c: Char): Boolean { if (peek() == c) { i++; return true }; return false }
    fun expect(c: Char) { if (!eat(c)) throw IllegalStateException("expected '$c' at $i") }
    fun end() = i >= s.length

    fun string(): String {
        expect('"')
        val sb = StringBuilder()
        while (i < s.length) {
            val c = s[i++]
            if (c == '"') return sb.toString()
            if (c == '\\') {
                val n = s[i++]
                when (n) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                    'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                    else -> sb.append(n)
                }
            } else sb.append(c)
        }
        throw IllegalStateException("unterminated string")
    }

    fun number(): Double {
        ws()
        val st = i
        while (i < s.length && (s[i].isDigit() || s[i] == '-' || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+')) i++
        return s.substring(st, i).toDouble()
    }

    // skips any value (string, number, object, array, literal)
    fun skip() {
        when (peek()) {
            '"' -> string()
            '{' -> { i++; if (!eat('}')) { do { string(); expect(':'); skip() } while (eat(',')); expect('}') } }
            '[' -> { i++; if (!eat(']')) { do { skip() } while (eat(',')); expect(']') } }
            else -> { ws(); while (i < s.length && s[i] != ',' && s[i] != '}' && s[i] != ']' && !s[i].isWhitespace()) i++ }
        }
    }
}

object KokoroText {
    // {"word": "phonemes"} or {"word": {"DEFAULT": "phonemes", "VERB": "..."}}  ->  word to phonemes
    fun parseLexicon(json: String, into: HashMap<String, String>) {
        val j = MiniJson(json)
        j.expect('{')
        if (j.eat('}')) return
        do {
            val key = j.string()
            j.expect(':')
            when (j.peek()) {
                '"' -> into[key] = j.string()
                '{' -> {
                    j.expect('{')
                    var first: String? = null
                    var def: String? = null
                    if (!j.eat('}')) {
                        do {
                            val k = j.string()
                            j.expect(':')
                            if (j.peek() == '"') { val v = j.string(); if (first == null) first = v; if (k == "DEFAULT") def = v } else j.skip()
                        } while (j.eat(','))
                        j.expect('}')
                    }
                    (def ?: first)?.let { into[key] = it }
                }
                else -> j.skip()
            }
        } while (j.eat(','))
    }

    // config.json of Kokoro: { ..., "vocab": {"symbol": id, ...}, ... }  ->  symbol to id
    fun parseVocab(json: String): HashMap<String, Int> {
        val out = HashMap<String, Int>()
        val at = json.indexOf("\"vocab\"")
        if (at < 0) return out
        val j = MiniJson(json)
        j.i = at + 7
        j.expect(':')
        j.expect('{')
        if (j.eat('}')) return out
        do {
            val k = j.string()
            j.expect(':')
            out[k] = j.number().toInt()
        } while (j.eat(','))
        return out
    }

    // a plain {"token": id, ...} file (the Arabic voice's vocab.json)
    fun parseFlatVocab(json: String): HashMap<String, Int> {
        val out = HashMap<String, Int>()
        val j = MiniJson(json)
        j.expect('{')
        if (j.eat('}')) return out
        do { val k = j.string(); j.expect(':'); out[k] = j.number().toInt() } while (j.eat(','))
        return out
    }

    // ---- Arabic: the MMS voice reads letters directly (no phonemes), with a blank (id 0) between every letter ----

    private val aOnes = arrayOf("صفر", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر", "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر")
    private val aTens = arrayOf("", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون")
    private val aHund = arrayOf("", "مئة", "مئتان", "ثلاثمئة", "أربعمئة", "خمسمئة", "ستمئة", "سبعمئة", "ثمانمئة", "تسعمئة")

    fun arabicNumber(n: Long): String {
        if (n < 0) return "سالب " + arabicNumber(-n)
        if (n < 20) return aOnes[n.toInt()]
        if (n < 100) { val u = (n % 10).toInt(); val t = (n / 10).toInt(); return if (u == 0) aTens[t] else aOnes[u] + " و" + aTens[t] }
        if (n < 1000) { val r = n % 100; return aHund[(n / 100).toInt()] + (if (r == 0L) "" else " و" + arabicNumber(r)) }
        if (n < 1_000_000) {
            val k = n / 1000; val r = n % 1000
            val kw = when { k == 1L -> "ألف"; k == 2L -> "ألفان"; k in 3..10 -> aOnes[k.toInt()] + " آلاف"; else -> arabicNumber(k) + " ألف" }
            return kw + (if (r == 0L) "" else " و" + arabicNumber(r))
        }
        if (n < 1_000_000_000L) {
            val m = n / 1_000_000; val r = n % 1_000_000
            val mw = when { m == 1L -> "مليون"; m == 2L -> "مليونان"; m in 3..10 -> aOnes[m.toInt()] + " ملايين"; else -> arabicNumber(m) + " مليون" }
            return mw + (if (r == 0L) "" else " و" + arabicNumber(r))
        }
        return n.toString().map { aOnes[it - '0'] }.joinToString(" ")
    }

    // Text -> letters the voice knows. Digits become words, vowel marks and stray symbols are dropped.
    fun arabicClean(text: String): String {
        val sb = StringBuilder()
        for (c in text) sb.append(when (c) { in '\u0660'..'\u0669' -> '0' + (c - '\u0660'); in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0'); else -> c })
        var t = sb.toString()
        t = Regex("\\d+").replace(t) { " " + arabicNumber(it.value.take(12).toLongOrNull() ?: 0) + " " }
        t = t.replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
        return t
    }

    fun arabicIds(vocab: Map<String, Int>, text: String): LongArray {
        val letters = ArrayList<Long>()
        var lastSpace = true
        val space = vocab[" "]
        for (c in arabicClean(text).lowercase()) {
            val id = vocab[c.toString()]
            if (id != null && c != ' ') { letters.add(id.toLong()); lastSpace = false }
            else if (space != null && (c.isWhitespace() || c in ",.!?;:\u060C\u061B\u061F-\"()")) { if (!lastSpace) { letters.add(space.toLong()); lastSpace = true } }
        }
        while (letters.isNotEmpty() && letters.last() == space?.toLong()) letters.removeAt(letters.size - 1)
        if (letters.isEmpty()) return LongArray(0)
        val out = LongArray(letters.size * 2 + 1)          // 0 is the blank between letters
        for (i in letters.indices) out[2 * i + 1] = letters[i]
        return out
    }

    // ---- numpy .npy inside the voices .bin (a zip of  name.npy  files, each float32 [rows, 1, 256]) ----

    fun voiceNames(zipPath: String): List<String> = try {
        ZipFile(zipPath).use { z -> z.entries().asSequence().map { it.name }.filter { it.endsWith(".npy") }.map { it.removeSuffix(".npy") }.sorted().toList() }
    } catch (e: Exception) { emptyList() }

    // The style vector for one voice, picked by how many phoneme tokens the sentence has (that is how Kokoro chooses it).
    fun style(zipPath: String, voice: String, tokens: Int): FloatArray? = try {
        ZipFile(zipPath).use { z ->
            val e = z.getEntry("$voice.npy") ?: return null
            val b = z.getInputStream(e).use { it.readBytes() }
            if (b.size < 12 || b[0] != 0x93.toByte()) return null
            val start = if (b[6].toInt() == 1) 10 + ((b[8].toInt() and 255) or ((b[9].toInt() and 255) shl 8))
                        else 12 + ((b[8].toInt() and 255) or ((b[9].toInt() and 255) shl 8) or ((b[10].toInt() and 255) shl 16) or ((b[11].toInt() and 255) shl 24))
            val n = (b.size - start) / 4
            val rows = n / 256
            if (rows <= 0) return null
            val row = tokens.coerceIn(0, rows - 1)
            val out = FloatArray(256)
            for (k in 0 until 256) {
                val p = start + (row * 256 + k) * 4
                val bits = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8) or ((b[p + 2].toInt() and 255) shl 16) or ((b[p + 3].toInt() and 255) shl 24)
                out[k] = java.lang.Float.intBitsToFloat(bits)
            }
            out
        }
    } catch (e: Exception) { null }

    // ---- text to phonemes ----

    fun mostlyArabic(text: String): Boolean {
        var ar = 0; var lat = 0
        for (c in text) { if (c in '؀'..'ۿ' || c in 'ݐ'..'ݿ') ar++ else if (c in 'a'..'z' || c in 'A'..'Z') lat++ }
        return ar > lat
    }

    private val ones = arrayOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val tens = arrayOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    fun numberWords(n0: Long): String {
        var n = n0
        if (n < 0) return "minus " + numberWords(-n)
        if (n < 20) return ones[n.toInt()]
        if (n < 100) return tens[(n / 10).toInt()] + (if (n % 10 != 0L) " " + ones[(n % 10).toInt()] else "")
        if (n < 1000) return ones[(n / 100).toInt()] + " hundred" + (if (n % 100 != 0L) " " + numberWords(n % 100) else "")
        val units = listOf(1_000_000_000L to "billion", 1_000_000L to "million", 1000L to "thousand")
        for ((v, name) in units) if (n >= v) return numberWords(n / v) + " " + name + (if (n % v != 0L) " " + numberWords(n % v) else "")
        return n.toString()
    }

    private val tok = Regex("[A-Za-z]+(?:'[A-Za-z]+)*|\\d+(?:[.,]\\d+)*%?|[^\\sA-Za-z\\d]")
    private val voiceless = "ptkfθ"
    private val sibilant = "szʃʒʧʤ"

    private fun lookup(lex: Map<String, String>, w: String): String? = lex[w] ?: lex[w.lowercase()] ?: lex[w.lowercase().replaceFirstChar { it.uppercase() }]

    private fun suffixS(p: String): String = when {
        p.isEmpty() -> p
        sibilant.indexOf(p.last()) >= 0 -> p + "ᵻz"
        voiceless.indexOf(p.last()) >= 0 -> p + "s"
        else -> p + "z"
    }

    private fun suffixEd(p: String): String = when {
        p.isEmpty() -> p
        p.last() == 't' || p.last() == 'd' -> p + "ᵻd"
        voiceless.indexOf(p.last()) >= 0 || "sʃʧ".indexOf(p.last()) >= 0 -> p + "t"
        else -> p + "d"
    }

    // A word that is not in the dictionary: try its stem plus an ending, else fall back to rough letter rules.
    fun word(lex: Map<String, String>, raw: String): String {
        val w = raw.replace("’", "'")
        lookup(lex, w)?.let { return it }
        val lw = w.lowercase()
        if (lw.endsWith("'s")) lookup(lex, lw.dropLast(2))?.let { return suffixS(it) }
        if (lw.endsWith("n't")) lookup(lex, lw.dropLast(3))?.let { return it + "nt" }
        if (lw.length > 3 && lw.endsWith("s")) {
            lookup(lex, lw.dropLast(1))?.let { return suffixS(it) }
            if (lw.endsWith("es")) lookup(lex, lw.dropLast(2))?.let { return suffixS(it) }
            if (lw.endsWith("ies")) lookup(lex, lw.dropLast(3) + "y")?.let { return it.dropLast(1) + "z" }
        }
        if (lw.length > 3 && lw.endsWith("ed")) {
            val c = listOf(lw.dropLast(2), lw.dropLast(1), if (lw.length > 4 && lw[lw.length - 3] == lw[lw.length - 4]) lw.dropLast(3) else "")
            for (s in c) if (s.isNotEmpty()) lookup(lex, s)?.let { return suffixEd(it) }
        }
        if (lw.length > 4 && lw.endsWith("ing")) {
            val c = listOf(lw.dropLast(3), lw.dropLast(3) + "e", if (lw.length > 5 && lw[lw.length - 4] == lw[lw.length - 5]) lw.dropLast(4) else "")
            for (s in c) if (s.isNotEmpty()) lookup(lex, s)?.let { return it + "ɪŋ" }
        }
        if (lw.length > 3 && lw.endsWith("ly")) lookup(lex, lw.dropLast(2))?.let { return it + "li" }
        return letters(lw)
    }

    // Rough letter-to-sound, only for words nobody listed (made-up names). Pronounceable, not perfect.
    private fun letters(w: String): String {
        val rules = listOf("tch" to "ʧ", "sch" to "sk", "igh" to "I", "ough" to "O", "ph" to "f", "sh" to "ʃ", "ch" to "ʧ", "th" to "θ", "ck" to "k", "ng" to "ŋ", "wh" to "w", "qu" to "kw",
            "ee" to "i", "ea" to "i", "oo" to "u", "ou" to "W", "ow" to "W", "ai" to "A", "ay" to "A", "oa" to "O", "oi" to "Y", "oy" to "Y", "au" to "ɔ", "aw" to "ɔ", "er" to "ɜɹ", "ar" to "ɑɹ", "or" to "ɔɹ", "ir" to "ɜɹ", "ur" to "ɜɹ")
        val single = mapOf('a' to "æ", 'b' to "b", 'c' to "k", 'd' to "d", 'e' to "ɛ", 'f' to "f", 'g' to "ɡ", 'h' to "h", 'i' to "ɪ", 'j' to "ʤ", 'k' to "k", 'l' to "l", 'm' to "m", 'n' to "n",
            'o' to "ɑ", 'p' to "p", 'q' to "k", 'r' to "ɹ", 's' to "s", 't' to "t", 'u' to "ʌ", 'v' to "v", 'w' to "w", 'x' to "ks", 'y' to "j", 'z' to "z", '\'' to "")
        val sb = StringBuilder()
        var i = 0
        outer@ while (i < w.length) {
            for ((g, p) in rules) if (w.startsWith(g, i)) { sb.append(p); i += g.length; continue@outer }
            val c = w[i]
            if (c == 'c' && i + 1 < w.length && w[i + 1] in "eiy") sb.append('s')
            else if (c == 'g' && i + 1 < w.length && w[i + 1] in "eiy") sb.append("ʤ")
            else if (c == 'e' && i == w.length - 1 && w.length > 2) { /* silent final e */ }
            else sb.append(single[c] ?: "")
            i++
        }
        return sb.toString()
    }

    private val keep = ".,!?;:—…"

    // Text -> one string of phoneme symbols, with spaces between words and the punctuation Kokoro uses for pauses.
    fun phonemes(lex: Map<String, String>, text: String): String {
        val clean = text.replace("’", "'").replace("‘", "'").replace("“", "\"").replace("”", "\"").replace("–", "—").replace("-", " ")
        val sb = StringBuilder()
        for (m in tok.findAll(clean)) {
            val t = m.value
            val c = t[0]
            when {
                c.isLetter() -> { if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' '); sb.append(word(lex, t)) }
                c.isDigit() -> {
                    val pct = t.endsWith("%")
                    val num = t.removeSuffix("%").replace(",", "")
                    val words = if (num.contains('.')) numberWords(num.substringBefore('.').toLongOrNull() ?: 0) + " point " + num.substringAfter('.').map { ones[it - '0'] }.joinToString(" ")
                                else numberWords(num.toLongOrNull() ?: 0)
                    for (w in (words + if (pct) " percent" else "").split(' ')) { if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' '); sb.append(word(lex, w)) }
                }
                keep.indexOf(c) >= 0 -> sb.append(c)
                c == '\n' -> sb.append('.')
            }
        }
        return sb.toString().trim()
    }

    // Splits long text into sentences, and sentences that are still too long at commas, so each piece fits the model.
    fun pieces(text: String, maxChars: Int = 220): List<String> {
        val out = ArrayList<String>()
        for (s in text.split(Regex("(?<=[.!?…\u061F\u06D4])\\s+|\\n+"))) {
            var cur = s.trim()
            if (cur.isEmpty()) continue
            while (cur.length > maxChars) {
                var cut = cur.lastIndexOf(',', maxChars)
                if (cut < 40) cut = cur.lastIndexOf(' ', maxChars)
                if (cut < 40) cut = maxChars
                out.add(cur.substring(0, cut + 1).trim())
                cur = cur.substring(cut + 1).trim()
            }
            if (cur.isNotEmpty()) out.add(cur)
        }
        return out
    }
}
