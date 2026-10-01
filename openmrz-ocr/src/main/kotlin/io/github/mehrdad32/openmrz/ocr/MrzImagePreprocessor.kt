package io.github.mehrdad32.openmrz.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.roundToInt

internal object MrzImagePreprocessor {
    private const val TARGET_WIDTH = 1600

    fun prepare(source: Bitmap): Bitmap {
        val scaled = scaleForOcr(source)
        val output = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)

        val contrast = 1.65f
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

    private fun scaleForOcr(source: Bitmap): Bitmap {
        if (source.width in 1300..1900) return source
        val width = TARGET_WIDTH
        val height = (source.height * (width.toFloat() / source.width)).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }
}
