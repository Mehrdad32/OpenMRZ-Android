package ir.mehrdad32.openmrz.android

import android.graphics.Bitmap
import ir.mehrdad32.openmrz.ocr.MrzRecognizerConfig
import kotlin.math.roundToInt

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f)
        require(top in 0f..1f)
        require(right in 0f..1f)
        require(bottom in 0f..1f)
        require(right > left)
        require(bottom > top)
    }

    internal fun crop(source: Bitmap): Bitmap {
        val x = (source.width * left).roundToInt().coerceIn(0, source.width - 1)
        val y = (source.height * top).roundToInt().coerceIn(0, source.height - 1)
        val r = (source.width * right).roundToInt().coerceIn(x + 1, source.width)
        val b = (source.height * bottom).roundToInt().coerceIn(y + 1, source.height)

        return Bitmap.createBitmap(source, x, y, r - x, b - y)
    }
}

data class OpenMrzScannerConfig(
    val recognition: MrzRecognizerConfig = MrzRecognizerConfig(),
    val analysisIntervalMs: Long = 1_100L,
    val regionOfInterest: NormalizedRect = NormalizedRect(
        left = 0.03f,
        top = 0.48f,
        right = 0.97f,
        bottom = 0.93f,
    ),
    val emitUnrecognizedResults: Boolean = true,
)
