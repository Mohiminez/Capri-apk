package app.capri.mobile

/** A tiny JSON reader, the same everywhere (including unit tests, where org.json is not available). */
object ModJson {
    // A tiny JSON reader (objects, arrays, strings, numbers, true/false/null) so a mod file is parsed the same everywhere, including in unit tests.
    class Reader(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(depth: Int = 0): Any? {
            if (depth > 12) throw IllegalArgumentException("deep")
            ws()
            if (i >= s.length) throw IllegalArgumentException("end")
            return when (val c = s[i]) {
                '{' -> { i++; val m = LinkedHashMap<String, Any?>(); ws(); if (s[i] == '}') { i++; return m }
                    while (true) { ws(); val k = str(); ws(); expect(':'); m[k] = value(depth + 1); ws(); if (s[i] == ',') { i++; continue }; expect('}'); break }; m }
                '[' -> { i++; val l = ArrayList<Any?>(); ws(); if (s[i] == ']') { i++; return l }
                    while (true) { l.add(value(depth + 1)); ws(); if (s[i] == ',') { i++; continue }; expect(']'); break }; l }
                '"' -> str()
                't' -> { lit("true"); true }
                'f' -> { lit("false"); false }
                'n' -> { lit("null"); null }
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("bad")
            }
        }
        fun expect(c: Char) { if (i >= s.length || s[i] != c) throw IllegalArgumentException("expected $c"); i++ }
        fun lit(w: String) { if (!s.startsWith(w, i)) throw IllegalArgumentException("bad"); i += w.length }
        fun num(): Double { val st = i; while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++; return s.substring(st, i).toDouble() }
        fun str(): String {
            expect('"'); val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw IllegalArgumentException("str")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> { val e = s[i++]; when (e) { 'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }; else -> sb.append(e) } }
                    else -> sb.append(c)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun readObject(text: String): Map<String, Any?>? = try {
        val r = Reader(text.trim()); val v = r.value(); r.ws()
        if (r.i != r.s.length) null else v as? Map<String, Any?>
    } catch (e: Exception) { null }
}
