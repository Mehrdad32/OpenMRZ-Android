package io.github.mehrdad32.openmrz.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.roundToInt

internal object MrzImagePreprocessor {
    private const val TARGET_WIDTH = 1800

    fun prepareContrast(source: Bitmap): Bitmap {
        val scaled = scaleForOcr(source)
        val output = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)

        val contrast = 1.85f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            )
        }

        Canvas(output).drawBitmap(
            scaled,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            },
        )

        if (scaled !== source) scaled.recycle()
        return output
    }

    fun prepareBinary(source: Bitmap): Bitmap {
        val scaled = scaleForOcr(source)
        val width = scaled.width
        val height = scaled.height
        val gray = IntArray(width * height)
        val histogram = IntArray(256)
        val row = IntArray(width)

        for (y in 0 until height) {
            scaled.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                val p = row[x]
                val value = (
                    Color.red(p) * 299 +
                        Color.green(p) * 587 +
                        Color.blue(p) * 114
                    ) / 1000
                gray[y * width + x] = value
                histogram[value]++
            }
        }

        val threshold = otsu(histogram, gray.size).coerceIn(90, 215)
        val margin = max(12, width / 90)
        val outputWidth = width + margin * 2
        val outputHeight = height + margin * 2
        val outputPixels = IntArray(outputWidth * outputHeight) { Color.WHITE }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = gray[y * width + x]
                outputPixels[(y + margin) * outputWidth + x + margin] =
                    if (value <= threshold) Color.BLACK else Color.WHITE
            }
        }

        val output = Bitmap.createBitmap(
            outputPixels,
            outputWidth,
            outputHeight,
            Bitmap.Config.ARGB_8888,
        )

        if (scaled !== source) scaled.recycle()
        return output
    }

    private fun scaleForOcr(source: Bitmap): Bitmap {
        if (source.width in 1500..2200) return source

        val width = TARGET_WIDTH
        val height = (
            source.height *
                (width.toFloat() / source.width)
            ).roundToInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun otsu(
        histogram: IntArray,
        total: Int,
    ): Int {
        if (total <= 0) return 160

        var sum = 0.0
        for (i in histogram.indices) sum += i * histogram[i].toDouble()

        var sumBackground = 0.0
        var weightBackground = 0
        var bestVariance = -1.0
        var bestThreshold = 160

        for (threshold in histogram.indices) {
            weightBackground += histogram[threshold]
            if (weightBackground == 0) continue

            val weightForeground = total - weightBackground
            if (weightForeground == 0) break

            sumBackground += threshold * histogram[threshold].toDouble()
            val meanBackground = sumBackground / weightBackground
            val meanForeground = (sum - sumBackground) / weightForeground

            val variance =
                weightBackground.toDouble() *
                    weightForeground.toDouble() *
                    (meanBackground - meanForeground) *
                    (meanBackground - meanForeground)

            if (variance > bestVariance) {
                bestVariance = variance
                bestThreshold = threshold
            }
        }

        return bestThreshold
    }
}
