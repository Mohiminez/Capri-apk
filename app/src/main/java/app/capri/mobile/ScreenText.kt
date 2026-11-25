package app.capri.mobile

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

// Reads the words on the screen ON THE PHONE with Google's ML Kit (the model ships inside the app, no internet, nothing is sent anywhere).
// Latin letters and digits only: English game menus, not Arabic ones. A failure of any kind gives an empty list, and the game player
// then works exactly as before, with the picture and the grid.
object ScreenText {
    @Volatile private var recognizer: com.google.mlkit.vision.text.TextRecognizer? = null

    private fun rec() = recognizer ?: synchronized(this) {
        recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { recognizer = it }
    }

    /** Words with their boxes as fractions of the bitmap. Takes about 100 to 400 ms on a phone; call it from a worker thread. */
    fun read(bmp: Bitmap): List<Word> {
        return try {
            val text = Tasks.await(rec().process(InputImage.fromBitmap(bmp, 0)), 4, TimeUnit.SECONDS)
            val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
            val out = ArrayList<Word>()
            for (block in text.textBlocks) for (line in block.lines) for (el in line.elements) {
                val b = el.boundingBox ?: continue
                out.add(Word(el.text, b.left / w, b.top / h, b.width() / w, b.height() / h))
            }
            out
        } catch (e: Throwable) { emptyList() }
    }
}
