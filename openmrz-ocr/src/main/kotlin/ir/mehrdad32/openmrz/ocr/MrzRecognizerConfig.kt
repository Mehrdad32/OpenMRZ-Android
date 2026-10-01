package ir.mehrdad32.openmrz.ocr

enum class MrzRecognitionMode {
    FAST,
    BALANCED,
    ACCURATE,
}

data class MrzRecognizerConfig(
    val mode: MrzRecognitionMode = MrzRecognitionMode.BALANCED,
    val autoDetectRegion: Boolean = true,
    val minTrustedConfidence: Int = 45,
    val maxTrustedCorrections: Int = 2,
)
