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
    val fastTargetWidth: Int = 1280,
    val accurateTargetWidth: Int = 1800,
    val genericTargetWidth: Int = 1500,
) {
    init {
        require(fastTargetWidth in 800..2000)
        require(accurateTargetWidth in fastTargetWidth..2600)
        require(genericTargetWidth in 900..2200)
    }
}
