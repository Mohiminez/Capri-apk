package app.capri.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class KokoroTextTest {
    private fun lex(): HashMap<String, String> {
        val m = HashMap<String, String>()
        KokoroText.parseLexicon("""{"hello": "həlˈO", "world": {"DEFAULT": "wˈɜɹld", "VERB": "x"}, "Capri": "kˈæpɹi", "cat": "kˈæt", "x": null, "aé": "ab"}""", m)
        return m
    }

    @Test fun lexiconBothShapes() {
        val m = lex()
        assertEquals("həlˈO", m["hello"])
        assertEquals("wˈɜɹld", m["world"])
        assertEquals("kˈæpɹi", m["Capri"])
        assertEquals(false, m.containsKey("x"))
    }

    @Test fun vocabTable() {
        val v = KokoroText.parseVocab("""{"model_type":"x","n_token":178,"vocab":{";":1,"ʣ":18,"̃":17," ":16,"a":43}, "other":1}""")
        assertEquals(1, v[";"]); assertEquals(16, v[" "]); assertEquals(17, v["̃"]); assertEquals(43, v["a"])
    }

    @Test fun phonemesWordsAndPauses() {
        assertEquals("həlˈO, wˈɜɹld!", KokoroText.phonemes(lex(), "Hello, world!"))
    }

    @Test fun suffixes() {
        val m = lex()
        assertEquals("kˈæts", KokoroText.word(m, "cats"))
        assertEquals("kˈæpɹiz", KokoroText.word(m, "Capri's"))
    }

    @Test fun unknownWordStillSpeaks() {
        assert(KokoroText.word(HashMap(), "blorptex").length > 3)
    }

    @Test fun numbers() {
        assertEquals("one hundred twenty three", KokoroText.numberWords(123))
        assertEquals("two thousand twenty six", KokoroText.numberWords(2026))
        assertEquals("one million", KokoroText.numberWords(1_000_000))
    }

    @Test fun splitsLongText() {
        val text = "One short one. Two! " + "word ".repeat(80) + ", then more words here."
        val p = KokoroText.pieces(text, 120)
        assert(p.size >= 3)
        assert(p.all { it.length <= 125 })
    }

    @Test fun arabicDetection() {
        assert(KokoroText.mostlyArabic("هلا يا خوي كيف الحال"))
        assert(!KokoroText.mostlyArabic("Hello there"))
    }

    @Test fun readsNpyStyle() {
        val f = File.createTempFile("voices", ".bin")
        val rows = 5
        val data = ByteBuffer.allocate(rows * 256 * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (r in 0 until rows) for (k in 0 until 256) data.putFloat(r * 1000f + k)
        val header = "{'descr': '<f4', 'fortran_order': False, 'shape': ($rows, 1, 256), }"
        val pad = 64 - ((10 + header.length + 1) % 64)
        val hdr = header + " ".repeat(pad) + "\n"
        val npy = ByteBuffer.allocate(10 + hdr.length + data.capacity()).order(ByteOrder.LITTLE_ENDIAN)
        npy.put(0x93.toByte()); npy.put("NUMPY".toByteArray()); npy.put(1); npy.put(0); npy.putShort(hdr.length.toShort()); npy.put(hdr.toByteArray()); npy.put(data.array())
        ZipOutputStream(FileOutputStream(f)).use { z -> z.putNextEntry(ZipEntry("af_test.npy")); z.write(npy.array()); z.closeEntry() }
        assertEquals(listOf("af_test"), KokoroText.voiceNames(f.path))
        val s = KokoroText.style(f.path, "af_test", 3)
        assertNotNull(s)
        assertEquals(3000f, s!![0], 0f)
        assertEquals(3255f, s[255], 0f)
        assertEquals(4000f, KokoroText.style(f.path, "af_test", 99)!![0], 0f)   // too many tokens: last row
    }
}

class ArabicTextTest {
    private val vocab = hashMapOf("ا" to 0, "م" to 30, "ر" to 9, "ح" to 23, "ب" to 28, "ا" to 0, " " to 25, "و" to 29, "ع" to 3)

    @Test fun numbersInArabic() {
        assertEquals("خمسة", KokoroText.arabicNumber(5))
        assertEquals("واحد وعشرون", KokoroText.arabicNumber(21))
        assertEquals("مئة وخمسة وعشرون", KokoroText.arabicNumber(125))
        assertEquals("ألفان وعشرون", KokoroText.arabicNumber(2020))
    }

    @Test fun lettersGetBlanksBetween() {
        val ids = KokoroText.arabicIds(vocab, "مرحبا")
        // م ر ح ب ا -> 0 30 0 9 0 23 0 28 0 0 0
        assertEquals(listOf(0L, 30L, 0L, 9L, 0L, 23L, 0L, 28L, 0L, 0L, 0L), ids.toList())
    }

    @Test fun marksAndUnknownCharsDropped() {
        val a = KokoroText.arabicIds(vocab, "مَرْحَباً!")
        val b = KokoroText.arabicIds(vocab, "مرحبا")
        assertEquals(b.size, a.size)
        assertEquals(0, KokoroText.arabicIds(vocab, "hello").size)
    }

    @Test fun flatVocab() {
        val v = KokoroText.parseFlatVocab("{\"ا\": 0, \"ن\": 1, \" \": 25}")
        assertEquals(25, v[" "]); assertEquals(1, v["ن"])
    }
}
