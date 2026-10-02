package ir.mehrdad32.openmrz.ocr

import ir.mehrdad32.openmrz.core.MrzParseResult

enum class MrzScanStatus {
    VERIFIED,
    CHECKSUM_VALID_LOW_CONFIDENCE,
    NEEDS_REVIEW,
    NOT_RECOGNIZED,
}

enum class MrzOcrEngine {
    FAST,
    BEST,
    GENERIC,
    ENSEMBLE,
}

data class MrzOcrResult(
    val rawText: String,
    val normalizedText: String,
    val confidence: Int,
    val parseResult: MrzParseResult,
    val status: MrzScanStatus,
    val correctionCount: Int = 0,
    val regionDetected: Boolean = false,
    val attemptCount: Int = 1,
    val processingTimeMs: Long = 0,
    val engine: MrzOcrEngine = MrzOcrEngine.BEST,
) {
    val isTrusted: Boolean
        get() = status == MrzScanStatus.VERIFIED
}
