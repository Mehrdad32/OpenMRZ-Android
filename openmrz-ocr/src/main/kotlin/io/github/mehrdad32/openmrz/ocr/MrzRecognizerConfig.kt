package io.github.mehrdad32.openmrz.ocr

enum class MrzRecognitionMode {
    FAST,
    ACCURATE,
}

data class MrzRecognizerConfig(
    val mode: MrzRecognitionMode = MrzRecognitionMode.ACCURATE,
    val autoDetectRegion: Boolean = true,
    val minTrustedConfidence: Int = 45,
    val maxTrustedCorrections: Int = 2,
)
