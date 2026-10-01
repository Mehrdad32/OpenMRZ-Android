package io.github.mehrdad32.openmrz.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.Closeable
import java.io.File

class TesseractMrzRecognizer(
    context: Context,
) : Closeable {
    private val appContext = context.applicationContext
    private val tess = TessBaseAPI()
    private var closed = false

    init {
        val dataRoot = ensureLanguageData()
        check(tess.init(dataRoot.absolutePath, LANGUAGE, TessBaseAPI.OEM_LSTM_ONLY)) {
            "Failed to initialize Tesseract with bundled OCR language data."
        }
        tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
        tess.setVariable(
            TessBaseAPI.VAR_CHAR_WHITELIST,
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<",
        )
        tess.setVariable("preserve_interword_spaces", "1")
    }

    @Synchronized
    fun recognize(bitmap: Bitmap): MrzOcrResult {
        check(!closed) { "Recognizer is already closed." }

        val prepared = MrzImagePreprocessor.prepare(bitmap)
        return try {
            tess.setImage(prepared)
            val rawText = tess.getUTF8Text().orEmpty()
            val confidence = tess.meanConfidence().coerceIn(0, 100)
            val (normalized, parsed) = MrzOcrPostProcessor.parse(rawText)
            MrzOcrResult(
                rawText = rawText,
                normalizedText = normalized,
                confidence = confidence,
                parseResult = parsed,
            )
        } finally {
            prepared.recycle()
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            tess.recycle()
        }
    }

    private fun ensureLanguageData(): File {
        val dataRoot = File(appContext.filesDir, "openmrz")
        val tessdataDir = File(dataRoot, "tessdata")
        val target = File(tessdataDir, LANGUAGE + ".traineddata")

        if (!target.exists() || target.length() < MIN_MODEL_BYTES) {
            tessdataDir.mkdirs()
            appContext.assets.open("tessdata/" + LANGUAGE + ".traineddata").use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return dataRoot
    }

    private companion object {
        const val LANGUAGE = "eng"
        const val MIN_MODEL_BYTES = 1_000_000L
    }
}
