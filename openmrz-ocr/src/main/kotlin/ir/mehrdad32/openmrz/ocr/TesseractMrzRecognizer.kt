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
    private enum class RegionKind {
        CROPPED,
        DETECTED,
        BOTTOM_52,
        BOTTOM_64,
    }

    private data class OcrAttempt(
        val rawText: String,
        val confidence: Int,
        val post: MrzPostProcessResult,
        val engine: MrzOcrEngine,
        val region: RegionKind,
    )

    private val appContext = context.applicationContext
    private val dataRoot = File(appContext.filesDir, "openmrz")
    private val fastTess: TessBaseAPI
    private var bestTess: TessBaseAPI? = null
    private var genericTess: TessBaseAPI? = null
    private var closed = false

    init {
        ensureLanguageData(FAST_LANGUAGE, FAST_MIN_MODEL_BYTES)
        fastTess = createTess(FAST_LANGUAGE)
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
            fastTess.recycle()
            bestTess?.recycle()
            bestTess = null
            genericTess?.recycle()
            genericTess = null
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
                engine = MrzOcrEngine.FAST,
                region = if (autoDetectRegion) RegionKind.DETECTED else RegionKind.CROPPED,
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
                engine = best.engine,
            )
        }

        fun runFast(kind: RegionKind): Boolean {
            val region = createRegion(bitmap, kind)
            try {
                if (kind == RegionKind.DETECTED) {
                    regionDetected = region.second
                }

                val prepared = MrzImagePreprocessor.prepareContrast(
                    region.first,
                    config.fastTargetWidth,
                )
                try {
                    val attempt = runAttempt(
                        tess = fastTess,
                        bitmap = prepared,
                        pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,
                        engine = MrzOcrEngine.FAST,
                        region = kind,
                    )
                    attempts += attempt
                    return shouldStopBalanced(attempt)
                } finally {
                    prepared.recycle()
                }
            } finally {
                region.first.recycle()
            }
        }

        fun runBest(
            kind: RegionKind,
            exhaustive: Boolean,
        ): Boolean {
            val region = createRegion(bitmap, kind)
            try {
                val tess = bestEngine()
                val contrast = MrzImagePreprocessor.prepareContrast(
                    region.first,
                    config.accurateTargetWidth,
                )
                try {
                    val first = runAttempt(
                        tess = tess,
                        bitmap = contrast,
                        pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,
                        engine = MrzOcrEngine.BEST,
                        region = kind,
                    )
                    attempts += first

                    if (shouldStopBalanced(first)) return true
                    if (!exhaustive) return false

                    val binary = MrzImagePreprocessor.prepareBinary(
                        region.first,
                        config.accurateTargetWidth,
                    )
                    try {
                        val second = runAttempt(
                            tess = tess,
                            bitmap = binary,
                            pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,
                            engine = MrzOcrEngine.BEST,
                            region = kind,
                        )
                        attempts += second
                        if (isVerified(second)) return true
                    } finally {
                        binary.recycle()
                    }

                    val usefulBest = attempts
                        .filter { it.engine == MrzOcrEngine.BEST }
                        .any { attempt ->
                            val parsed = attempt.post.parseResult as? MrzParseResult.Success
                            parsed != null &&
                                (
                                    parsed.document.validation.isValid ||
                                        parsed.document.validation.checkDigitsValid ||
                                        parsed.document.validation.fields.isValid
                                )
                        }

                    if (!usefulBest) {
                        collectLineAttempt(
                            tess = tess,
                            bitmap = contrast,
                            output = attempts,
                            engine = MrzOcrEngine.BEST,
                            region = kind,
                        )
                    }
                } finally {
                    contrast.recycle()
                }
            } finally {
                region.first.recycle()
            }

            return false
        }

        fun runHybridSecondLine(
            kind: RegionKind,
            base: OcrAttempt,
        ): Boolean {
            val baseLines = base.post.text.lineSequence().toList()
            if (baseLines.size != 2) return false

            val region = createRegion(bitmap, kind)
            try {
                val prepared = MrzImagePreprocessor.prepareContrast(
                    region.first,
                    config.genericTargetWidth,
                )

                try {
                    val lineBitmaps = MrzRegionDetector.splitLines(prepared)
                    if (lineBitmaps.size != 2) {
                        lineBitmaps.forEach { if (!it.isRecycled) it.recycle() }
                        return false
                    }

                    try {
                        val (secondText, secondConfidence) = runRaw(
                            tess = genericEngine(),
                            bitmap = lineBitmaps[1],
                            pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE,
                        )

                        val combined = baseLines[0] + "\n" + secondText.trim()
                        val hybrid = OcrAttempt(
                            rawText = combined,
                            confidence = (base.confidence + secondConfidence) / 2,
                            post = MrzOcrPostProcessor.parse(combined),
                            engine = MrzOcrEngine.HYBRID,
                            region = kind,
                        )
                        attempts += hybrid

                        return shouldStopBalanced(hybrid)
                    } finally {
                        lineBitmaps.forEach { if (!it.isRecycled) it.recycle() }
                    }
                } finally {
                    prepared.recycle()
                }
            } finally {
                region.first.recycle()
            }
        }

        if (!autoDetectRegion) {
            if (runFast(RegionKind.CROPPED)) return finish()
            if (config.mode == MrzRecognitionMode.FAST) return finish()

            val fast = bestFastAttempt(attempts)
            if (fast != null && isChecksumValid(fast)) {
                return finish()
            }

            if (fast != null && isPlausibleCandidate(fast)) {
                runHybridSecondLine(RegionKind.CROPPED, fast)
                if (config.mode == MrzRecognitionMode.BALANCED) {
                    return finish()
                }
            }

            if (config.mode == MrzRecognitionMode.ACCURATE) {
                runBest(
                    kind = RegionKind.CROPPED,
                    exhaustive = true,
                )
            }

            return finish()
        }

        if (runFast(RegionKind.DETECTED)) return finish()
        if (config.mode == MrzRecognitionMode.FAST) return finish()

        var fast = bestFastAttempt(attempts)

        if (fast != null && isChecksumValid(fast)) {
            return finish()
        }

        if (fast == null || !isPlausibleCandidate(fast)) {
            if (runFast(RegionKind.BOTTOM_52)) return finish()
            fast = bestFastAttempt(attempts)

            if (fast != null && isChecksumValid(fast)) {
                return finish()
            }
        }

        if (fast == null || !isPlausibleCandidate(fast)) {
            if (runFast(RegionKind.BOTTOM_64)) return finish()
            fast = bestFastAttempt(attempts)

            if (fast != null && isChecksumValid(fast)) {
                return finish()
            }
        }

        val preferredRegion = fast?.region ?: RegionKind.DETECTED

        if (fast != null && isPlausibleCandidate(fast)) {
            runHybridSecondLine(preferredRegion, fast)

            if (config.mode == MrzRecognitionMode.BALANCED) {
                return finish()
            }
        }

        if (config.mode == MrzRecognitionMode.ACCURATE) {
            val order = listOf(
                preferredRegion,
                RegionKind.DETECTED,
                RegionKind.BOTTOM_52,
                RegionKind.BOTTOM_64,
            ).distinct()

            for (kind in order) {
                if (runBest(kind, exhaustive = true)) break
            }
        }

        return finish()
    }

    private fun createRegion(
        source: Bitmap,
        kind: RegionKind,
    ): Pair<Bitmap, Boolean> = when (kind) {
        RegionKind.CROPPED ->
            source.copy(Bitmap.Config.ARGB_8888, false) to false

        RegionKind.DETECTED -> {
            val detected = MrzRegionDetector.detect(source)
            detected.bitmap to detected.detected
        }

        RegionKind.BOTTOM_52 ->
            bottomCrop(source, 0.52f) to false

        RegionKind.BOTTOM_64 ->
            bottomCrop(source, 0.64f) to false
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
        tess: TessBaseAPI,
        bitmap: Bitmap,
        output: MutableList<OcrAttempt>,
        engine: MrzOcrEngine,
        region: RegionKind,
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
                    tess,
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
                engine = engine,
                region = region,
            )
        } finally {
            lines.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private fun runAttempt(
        tess: TessBaseAPI,
        bitmap: Bitmap,
        pageSegMode: Int,
        engine: MrzOcrEngine,
        region: RegionKind,
    ): OcrAttempt {
        val (text, confidence) = runRaw(tess, bitmap, pageSegMode)
        return OcrAttempt(
            rawText = text,
            confidence = confidence,
            post = MrzOcrPostProcessor.parse(text),
            engine = engine,
            region = region,
        )
    }

    private fun runRaw(
        tess: TessBaseAPI,
        bitmap: Bitmap,
        pageSegMode: Int,
    ): Pair<String, Int> {
        tess.setPageSegMode(pageSegMode)
        tess.setImage(bitmap)
        return tess.getUTF8Text().orEmpty() to
            tess.meanConfidence().coerceIn(0, 100)
    }

    private fun bestFastAttempt(attempts: List<OcrAttempt>): OcrAttempt? =
        attempts
            .asSequence()
            .filter { it.engine == MrzOcrEngine.FAST }
            .maxByOrNull(::attemptScore)

    private fun isChecksumValid(attempt: OcrAttempt): Boolean {
        val parsed = attempt.post.parseResult as? MrzParseResult.Success
            ?: return false
        return parsed.document.validation.checkDigitsValid
    }

    private fun isPlausibleCandidate(attempt: OcrAttempt): Boolean {
        val parsed = attempt.post.parseResult as? MrzParseResult.Success
            ?: return false
        val document = parsed.document
        val fields = document.validation.fields

        return document.documentNumber.isNotBlank() &&
            document.documentCode.isNotBlank() &&
            fields.documentCode &&
            fields.nationality &&
            fields.birthDateFormat &&
            fields.expiryDateFormat &&
            fields.sex &&
            fields.names
    }

    private fun shouldStopBalanced(attempt: OcrAttempt): Boolean {
        val parsed = attempt.post.parseResult as? MrzParseResult.Success
            ?: return false

        // Full ICAO checksum + structural validity is strong enough to stop
        // BALANCED mode. Confidence still controls whether result.isTrusted is true.
        return parsed.document.validation.isValid
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
            (if (validation.documentNumber) 80 else 0) +
            (if (validation.birthDate) 80 else 0) +
            (if (validation.expiryDate) 80 else 0) +
            (if (validation.composite) 200 else 0) +
            attempt.confidence * 5 -
            attempt.post.correctionCount * 60
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

            validation.isValid ->
                MrzScanStatus.CHECKSUM_VALID_LOW_CONFIDENCE

            validation.checkDigitsValid || validation.fields.isValid ->
                MrzScanStatus.NEEDS_REVIEW

            else ->
                MrzScanStatus.NOT_RECOGNIZED
        }
    }

    private fun createTess(language: String): TessBaseAPI =
        TessBaseAPI().also { api ->
            check(api.init(dataRoot.absolutePath, language, TessBaseAPI.OEM_LSTM_ONLY)) {
                "Failed to initialize Tesseract language: $language"
            }
            api.setVariable(
                TessBaseAPI.VAR_CHAR_WHITELIST,
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<",
            )
            api.setVariable("preserve_interword_spaces", "1")
            api.setVariable("user_defined_dpi", "300")
        }

    private fun bestEngine(): TessBaseAPI {
        val current = bestTess
        if (current != null) return current

        ensureLanguageData(BEST_LANGUAGE, BEST_MIN_MODEL_BYTES)
        return createTess(BEST_LANGUAGE).also { bestTess = it }
    }

    private fun genericEngine(): TessBaseAPI {
        val current = genericTess
        if (current != null) return current

        ensureLanguageData(GENERIC_LANGUAGE, GENERIC_MIN_MODEL_BYTES)
        return createTess(GENERIC_LANGUAGE).also { genericTess = it }
    }

    private fun ensureLanguageData(
        language: String,
        minBytes: Long,
    ) {
        val tessdataDir = File(dataRoot, "tessdata")
        val target = File(tessdataDir, "$language.traineddata")

        if (!target.exists() || target.length() < minBytes) {
            tessdataDir.mkdirs()
            appContext.assets.open("tessdata/$language.traineddata").use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private companion object {
        const val FAST_LANGUAGE = "mrz_fast"
        const val BEST_LANGUAGE = "mrz_best"
        const val GENERIC_LANGUAGE = "eng_fast"
        const val FAST_MIN_MODEL_BYTES = 1_000_000L
        const val BEST_MIN_MODEL_BYTES = 10_000_000L
        const val GENERIC_MIN_MODEL_BYTES = 3_000_000L
    }
}
