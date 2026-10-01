package io.github.mehrdad32.openmrz.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import io.github.mehrdad32.openmrz.core.MrzParseResult
import java.io.Closeable
import java.io.File

class TesseractMrzRecognizer(
    context: Context,
    private val config: MrzRecognizerConfig = MrzRecognizerConfig(),
) : Closeable {
    private data class OcrAttempt(
        val rawText: String,
        val confidence: Int,
        val post: MrzPostProcessResult,
    )

    private val appContext = context.applicationContext
    private val tess = TessBaseAPI()
    private var closed = false

    init {
        val dataRoot = ensureLanguageData()
        check(tess.init(dataRoot.absolutePath, LANGUAGE, TessBaseAPI.OEM_LSTM_ONLY)) {
            "Failed to initialize Tesseract with bundled OCR language data."
        }

        tess.setVariable(
            TessBaseAPI.VAR_CHAR_WHITELIST,
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<",
        )
        tess.setVariable("preserve_interword_spaces", "1")
        tess.setVariable("user_defined_dpi", "300")
    }

    @Synchronized
    fun recognize(bitmap: Bitmap): MrzOcrResult {
        check(!closed) { "Recognizer is already closed." }

        val region = if (config.autoDetectRegion) {
            MrzRegionDetector.detect(bitmap)
        } else {
            DetectedMrzRegion(
                bitmap.copy(Bitmap.Config.ARGB_8888, false),
                false,
            )
        }

        val attempts = mutableListOf<OcrAttempt>()
        val prepared = mutableListOf<Bitmap>()

        try {
            val contrast = MrzImagePreprocessor.prepareContrast(region.bitmap)
            prepared += contrast
            collectAttempts(contrast, attempts)

            if (config.mode == MrzRecognitionMode.ACCURATE) {
                val binary = MrzImagePreprocessor.prepareBinary(region.bitmap)
                prepared += binary
                collectAttempts(binary, attempts)
            }

            val best = attempts.maxByOrNull(::attemptScore)
                ?: OcrAttempt(
                    rawText = "",
                    confidence = 0,
                    post = MrzOcrPostProcessor.parse(""),
                )

            val status = statusFor(best)

            return MrzOcrResult(
                rawText = best.rawText,
                normalizedText = best.post.text,
                confidence = best.confidence,
                parseResult = best.post.parseResult,
                status = status,
                correctionCount = best.post.correctionCount,
                regionDetected = region.detected,
                attemptCount = attempts.size,
            )
        } finally {
            prepared.forEach { if (!it.isRecycled) it.recycle() }
            if (!region.bitmap.isRecycled) region.bitmap.recycle()
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            tess.recycle()
        }
    }

    private fun collectAttempts(
        bitmap: Bitmap,
        output: MutableList<OcrAttempt>,
    ) {
        output += runAttempt(bitmap, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)

        val lines = MrzRegionDetector.splitLines(bitmap)
        if (lines.size in 2..3) {
            try {
                val texts = mutableListOf<String>()
                val confidences = mutableListOf<Int>()

                for (line in lines) {
                    val attempt = runRaw(line, TessBaseAPI.PageSegMode.PSM_SINGLE_LINE)
                    texts += attempt.first.trim()
                    confidences += attempt.second
                }

                val combined = texts.joinToString("\n")
                output += OcrAttempt(
                    rawText = combined,
                    confidence = if (confidences.isEmpty()) 0 else confidences.average().toInt(),
                    post = MrzOcrPostProcessor.parse(combined),
                )
            } finally {
                lines.forEach { if (!it.isRecycled) it.recycle() }
            }
        }
    }

    private fun runAttempt(
        bitmap: Bitmap,
        pageSegMode: Int,
    ): OcrAttempt {
        val (text, confidence) = runRaw(bitmap, pageSegMode)
        return OcrAttempt(
            rawText = text,
            confidence = confidence,
            post = MrzOcrPostProcessor.parse(text),
        )
    }

    private fun runRaw(
        bitmap: Bitmap,
        pageSegMode: Int,
    ): Pair<String, Int> {
        tess.setPageSegMode(pageSegMode)
        tess.setImage(bitmap)
        val text = tess.getUTF8Text().orEmpty()
        val confidence = tess.meanConfidence().coerceIn(0, 100)
        return text to confidence
    }

    private fun attemptScore(attempt: OcrAttempt): Int {
        val parsed = attempt.post.parseResult as? MrzParseResult.Success

        var score = attempt.confidence.coerceIn(0, 100)
        score -= attempt.post.correctionCount * 2

        if (parsed != null) {
            val validation = parsed.document.validation
            if (validation.fields.isValid) score += 40
            if (validation.checkDigitsValid) score += 80
            if (validation.isValid) score += 40
        }

        return score
    }

    private fun statusFor(attempt: OcrAttempt): MrzScanStatus {
        val parsed = attempt.post.parseResult as? MrzParseResult.Success
            ?: return MrzScanStatus.NOT_RECOGNIZED

        val validation = parsed.document.validation

        return when {
            validation.isValid &&
                attempt.confidence >= config.minTrustedConfidence &&
                attempt.post.correctionCount <= config.maxTrustedCorrections ->
                MrzScanStatus.VERIFIED

            validation.checkDigitsValid ->
                MrzScanStatus.CHECKSUM_VALID_LOW_CONFIDENCE

            validation.fields.isValid ->
                MrzScanStatus.NEEDS_REVIEW

            else ->
                MrzScanStatus.NOT_RECOGNIZED
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
        const val MIN_MODEL_BYTES = 5_000_000L
    }
}
