package ir.mehrdad32.openmrz.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import ir.mehrdad32.openmrz.core.MrzParseResult
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

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
            "Failed to initialize Tesseract with bundled MRZ language data."
        }
        tess.setVariable(
            TessBaseAPI.VAR_CHAR_WHITELIST,
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<",
        )
        tess.setVariable("preserve_interword_spaces", "1")
        tess.setVariable("user_defined_dpi", "300")
    }

    @Synchronized
    fun recognize(bitmap: Bitmap): MrzOcrResult =
        recognizeInternal(bitmap, autoDetectRegion = config.autoDetectRegion)

    @Synchronized
    fun recognizeCropped(bitmap: Bitmap): MrzOcrResult =
        recognizeInternal(bitmap, autoDetectRegion = false)

    override fun close() {
        if (!closed) {
            closed = true
            tess.recycle()
        }
    }

    private fun recognizeInternal(
        bitmap: Bitmap,
        autoDetectRegion: Boolean,
    ): MrzOcrResult {
        check(!closed) { "Recognizer is already closed." }

        val startedNs = System.nanoTime()
        val attempts = mutableListOf<OcrAttempt>()
        var regionDetected = false

        fun finish(): MrzOcrResult {
            val best = attempts.maxByOrNull(::attemptScore) ?: OcrAttempt(
                rawText = "",
                confidence = 0,
                post = MrzOcrPostProcessor.parse(""),
            )

            return MrzOcrResult(
                rawText = best.rawText,
                normalizedText = best.post.text,
                confidence = best.confidence,
                parseResult = best.post.parseResult,
                status = statusFor(best),
                correctionCount = best.post.correctionCount,
                regionDetected = regionDetected,
                attemptCount = attempts.size,
                processingTimeMs = (System.nanoTime() - startedNs) / 1_000_000L,
            )
        }

        fun processRegion(
            region: Bitmap,
            allowBinary: Boolean,
            allowLineFallback: Boolean,
        ): Boolean {
            try {
                val contrast = MrzImagePreprocessor.prepareContrast(region)
                try {
                    val first = runAttempt(
                        contrast,
                        TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,
                    )
                    attempts += first

                    if (isVerified(first)) return true
                    if (config.mode == MrzRecognitionMode.FAST) return false

                    if (allowBinary) {
                        val binary = MrzImagePreprocessor.prepareBinary(region)
                        try {
                            val second = runAttempt(
                                binary,
                                TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,
                            )
                            attempts += second
                            if (isVerified(second)) return true
                        } finally {
                            binary.recycle()
                        }
                    }

                    if (allowLineFallback && !hasUsefulAttempt(attempts)) {
                        collectLineAttempt(contrast, attempts)
                        if (attempts.any(::isVerified)) return true
                    }
                } finally {
                    contrast.recycle()
                }
            } finally {
                region.recycle()
            }

            return false
        }

        if (!autoDetectRegion) {
            processRegion(
                region = bitmap.copy(Bitmap.Config.ARGB_8888, false),
                allowBinary = config.mode != MrzRecognitionMode.FAST,
                allowLineFallback = config.mode == MrzRecognitionMode.ACCURATE,
            )
            return finish()
        }

        val detected = MrzRegionDetector.detect(bitmap)
        regionDetected = detected.detected

        if (
            processRegion(
                region = detected.bitmap,
                allowBinary = config.mode != MrzRecognitionMode.FAST,
                allowLineFallback = false,
            )
        ) {
            return finish()
        }

        if (config.mode == MrzRecognitionMode.FAST) return finish()

        if (
            config.mode == MrzRecognitionMode.BALANCED &&
            hasUsefulAttempt(attempts)
        ) {
            return finish()
        }

        if (
            processRegion(
                region = bottomCrop(bitmap, 0.52f),
                allowBinary = config.mode == MrzRecognitionMode.ACCURATE,
                allowLineFallback = false,
            )
        ) {
            return finish()
        }

        if (
            config.mode == MrzRecognitionMode.BALANCED &&
            hasUsefulAttempt(attempts)
        ) {
            return finish()
        }

        processRegion(
            region = bottomCrop(bitmap, 0.64f),
            allowBinary = config.mode == MrzRecognitionMode.ACCURATE,
            allowLineFallback = config.mode == MrzRecognitionMode.ACCURATE,
        )

        return finish()
    }

    private fun bottomCrop(
        source: Bitmap,
        startFraction: Float,
    ): Bitmap {
        val top = (source.height * startFraction)
            .roundToInt()
            .coerceIn(0, source.height - 1)
        val left = (source.width * 0.015f)
            .roundToInt()
            .coerceIn(0, source.width - 1)
        val right = (source.width * 0.985f)
            .roundToInt()
            .coerceIn(left + 1, source.width)

        return Bitmap.createBitmap(
            source,
            left,
            top,
            right - left,
            source.height - top,
        )
    }

    private fun collectLineAttempt(
        bitmap: Bitmap,
        output: MutableList<OcrAttempt>,
    ) {
        val lines = MrzRegionDetector.splitLines(bitmap)
        if (lines.size !in 2..3) {
            lines.forEach { if (!it.isRecycled) it.recycle() }
            return
        }

        try {
            val texts = mutableListOf<String>()
            val confidences = mutableListOf<Int>()

            for (line in lines) {
                val (text, confidence) = runRaw(
                    line,
                    TessBaseAPI.PageSegMode.PSM_SINGLE_LINE,
                )
                texts += text.trim()
                confidences += confidence
            }

            val combined = texts.joinToString("\n")
            output += OcrAttempt(
                rawText = combined,
                confidence = confidences.average().toInt(),
                post = MrzOcrPostProcessor.parse(combined),
            )
        } finally {
            lines.forEach { if (!it.isRecycled) it.recycle() }
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
        return tess.getUTF8Text().orEmpty() to
            tess.meanConfidence().coerceIn(0, 100)
    }

    private fun hasUsefulAttempt(attempts: List<OcrAttempt>): Boolean =
        attempts.any(::isUseful)

    private fun isUseful(attempt: OcrAttempt): Boolean {
        val parsed = attempt.post.parseResult as? MrzParseResult.Success
            ?: return false
        val validation = parsed.document.validation

        return validation.isValid ||
            validation.checkDigitsValid ||
            validation.fields.isValid
    }

    private fun isVerified(attempt: OcrAttempt): Boolean =
        statusFor(attempt) == MrzScanStatus.VERIFIED

    private fun attemptScore(attempt: OcrAttempt): Int {
        val parsed = attempt.post.parseResult as? MrzParseResult.Success
            ?: return attempt.confidence - 2_000

        val validation = parsed.document.validation

        return (if (validation.isValid) 20_000 else 0) +
            (if (validation.checkDigitsValid) 10_000 else 0) +
            (if (validation.fields.isValid) 4_000 else 0) +
            (if (validation.documentNumber) 500 else 0) +
            (if (validation.birthDate) 500 else 0) +
            (if (validation.expiryDate) 500 else 0) +
            (if (validation.composite) 800 else 0) +
            attempt.confidence -
            attempt.post.correctionCount * 25
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
        const val LANGUAGE = "mrz"
        const val MIN_MODEL_BYTES = 10_000_000L
    }
}
