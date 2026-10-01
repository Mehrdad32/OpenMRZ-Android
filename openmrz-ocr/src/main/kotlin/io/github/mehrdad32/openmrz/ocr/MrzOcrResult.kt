package io.github.mehrdad32.openmrz.ocr

import io.github.mehrdad32.openmrz.core.MrzParseResult

data class MrzOcrResult(
    val rawText: String,
    val normalizedText: String,
    val confidence: Int,
    val parseResult: MrzParseResult,
)
