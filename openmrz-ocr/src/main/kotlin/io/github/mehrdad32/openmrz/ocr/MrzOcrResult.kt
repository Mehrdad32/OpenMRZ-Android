package io.github.mehrdad32.openmrz.ocr

import io.github.mehrdad32.openmrz.core.MrzParseResult

enum class MrzScanStatus {
    /** Checksums, field structure and OCR confidence are all acceptable. */
    VERIFIED,

    /** ICAO checksums are valid, but OCR confidence/corrections make the read untrusted. */
    CHECKSUM_VALID_LOW_CONFIDENCE,

    /** An MRZ-shaped candidate was parsed but should be reviewed or rescanned. */
    NEEDS_REVIEW,

    /** No useful TD1/TD2/TD3 candidate was found. */
    NOT_RECOGNIZED,
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
) {
    val isTrusted: Boolean
        get() = status == MrzScanStatus.VERIFIED
}
