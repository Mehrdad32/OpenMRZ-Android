package ir.mehrdad32.openmrz.ocr

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class DetectedMrzRegion(
    val bitmap: Bitmap,
    val detected: Boolean,
)

internal object MrzRegionDetector {
    private const val ANALYSIS_WIDTH = 900

    fun detect(source: Bitmap): DetectedMrzRegion {
        if (source.width <= 0 || source.height <= 0) {
            return DetectedMrzRegion(source.copy(Bitmap.Config.ARGB_8888, false), false)
        }

        // A very wide, short image is probably already an MRZ crop.
        if (source.height.toFloat() / source.width < 0.34f) {
            return DetectedMrzRegion(
                source.copy(Bitmap.Config.ARGB_8888, false),
                false,
            )
        }

        val analysis = if (source.width > ANALYSIS_WIDTH) {
            val h = (source.height * (ANALYSIS_WIDTH.toFloat() / source.width))
                .roundToInt()
                .coerceAtLeast(1)
            Bitmap.createScaledBitmap(source, ANALYSIS_WIDTH, h, true)
        } else {
            source
        }

        try {
            val startY = (analysis.height * 0.55f).roundToInt()
            val threshold = otsuThreshold(analysis, startY)
            val rawScores = FloatArray(analysis.height)

            for (y in startY until analysis.height) {
                val pixels = IntArray(analysis.width)
                analysis.getPixels(pixels, 0, analysis.width, 0, y, analysis.width, 1)

                var previousDark = false
                var transitions = 0
                var dark = 0

                for (x in pixels.indices step 2) {
                    val isDark = gray(pixels[x]) < threshold
                    if (isDark) dark++
                    if (x > 0 && isDark != previousDark) transitions++
                    previousDark = isDark
                }

                val samples = (analysis.width + 1) / 2f
                val darkRatio = dark / samples
                val transitionRatio = transitions / samples
                rawScores[y] = transitionRatio + darkRatio * 0.35f
            }

            val smoothed = smooth(rawScores, radius = max(2, analysis.height / 260))
            val maxScore = smoothed.copyOfRange(startY, smoothed.size).maxOrNull() ?: 0f

            if (maxScore <= 0f) {
                return fallback(source)
            }

            val scoreThreshold = maxScore * 0.30f
            val active = mutableListOf<Int>()
            for (y in startY until smoothed.size) {
                if (smoothed[y] >= scoreThreshold) active += y
            }

            if (active.isEmpty()) return fallback(source)

            val groups = groupRows(active, mergeGap = max(3, analysis.height / 120))
                .filter { (it.last - it.first + 1) >= 3 }

            if (groups.isEmpty()) return fallback(source)

            val selected = groups.takeLast(min(3, groups.size))
            val first = selected.first().first
            val last = selected.last().last
            val span = max(12, last - first + 1)
            val padding = (span * 0.20f).roundToInt()

            val topA = (first - padding).coerceAtLeast(startY)
            val bottomA = (last + padding).coerceAtMost(analysis.height - 1)

            val scaleX = source.width.toFloat() / analysis.width
            val scaleY = source.height.toFloat() / analysis.height

            val left = (source.width * 0.025f).roundToInt()
            val right = (source.width * 0.975f).roundToInt()
            val top = (topA * scaleY).roundToInt().coerceIn(0, source.height - 1)
            val bottom = ((bottomA + 1) * scaleY).roundToInt().coerceIn(top + 1, source.height)

            val crop = Bitmap.createBitmap(
                source,
                left,
                top,
                (right - left).coerceAtLeast(1),
                (bottom - top).coerceAtLeast(1),
            )

            return DetectedMrzRegion(crop, true)
        } finally {
            if (analysis !== source) analysis.recycle()
        }
    }

    fun splitLines(source: Bitmap): List<Bitmap> {
        if (source.width < 20 || source.height < 12) return emptyList()

        val threshold = otsuThreshold(source, 0)
        val density = FloatArray(source.height)

        for (y in 0 until source.height) {
            val pixels = IntArray(source.width)
            source.getPixels(pixels, 0, source.width, 0, y, source.width, 1)
            var dark = 0
            for (x in pixels.indices step 2) {
                if (gray(pixels[x]) < threshold) dark++
            }
            density[y] = dark / ((source.width + 1) / 2f)
        }

        val smoothed = smooth(density, radius = max(1, source.height / 80))
        val maxDensity = smoothed.maxOrNull() ?: return emptyList()
        if (maxDensity <= 0f) return emptyList()

        val active = mutableListOf<Int>()
        val densityThreshold = max(0.012f, maxDensity * 0.24f)
        for (y in smoothed.indices) {
            if (smoothed[y] >= densityThreshold) active += y
        }

        val groups = groupRows(active, mergeGap = max(2, source.height / 35))
            .filter { (it.last - it.first + 1) >= max(3, source.height / 45) }

        if (groups.size !in 2..5) return emptyList()

        val candidates = if (groups.size <= 3) {
            groups
        } else {
            groups
                .sortedByDescending { range ->
                    (range.last - range.first + 1) *
                        smoothed.copyOfRange(range.first, range.last + 1).average()
                }
                .take(3)
                .sortedBy { it.first }
        }

        if (candidates.size !in 2..3) return emptyList()

        return candidates.map { range ->
            val lineHeight = range.last - range.first + 1
            val paddingY = max(4, (lineHeight * 0.35f).roundToInt())
            val top = (range.first - paddingY).coerceAtLeast(0)
            val bottom = (range.last + paddingY + 1).coerceAtMost(source.height)
            val left = (source.width * 0.01f).roundToInt()
            val right = (source.width * 0.99f).roundToInt()

            Bitmap.createBitmap(
                source,
                left,
                top,
                (right - left).coerceAtLeast(1),
                (bottom - top).coerceAtLeast(1),
            )
        }
    }

    private fun fallback(source: Bitmap): DetectedMrzRegion {
        val top = (source.height * 0.62f).roundToInt().coerceIn(0, source.height - 1)
        val left = (source.width * 0.02f).roundToInt()
        val right = (source.width * 0.98f).roundToInt()
        return DetectedMrzRegion(
            Bitmap.createBitmap(
                source,
                left,
                top,
                (right - left).coerceAtLeast(1),
                (source.height - top).coerceAtLeast(1),
            ),
            false,
        )
    }

    private fun groupRows(
        rows: List<Int>,
        mergeGap: Int,
    ): List<IntRange> {
        if (rows.isEmpty()) return emptyList()

        val groups = mutableListOf<IntRange>()
        var start = rows.first()
        var previous = rows.first()

        for (index in 1 until rows.size) {
            val current = rows[index]
            if (current - previous > mergeGap) {
                groups += start..previous
                start = current
            }
            previous = current
        }

        groups += start..previous
        return groups
    }

    private fun smooth(
        values: FloatArray,
        radius: Int,
    ): FloatArray {
        if (radius <= 0) return values.copyOf()

        val out = FloatArray(values.size)
        for (index in values.indices) {
            val from = max(0, index - radius)
            val to = min(values.lastIndex, index + radius)
            var total = 0f
            for (i in from..to) total += values[i]
            out[index] = total / (to - from + 1)
        }
        return out
    }

    private fun otsuThreshold(
        bitmap: Bitmap,
        startY: Int,
    ): Int {
        val histogram = IntArray(256)
        var total = 0

        val yStep = max(1, bitmap.height / 300)
        val xStep = max(1, bitmap.width / 450)

        for (y in startY.coerceAtLeast(0) until bitmap.height step yStep) {
            val pixels = IntArray(bitmap.width)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in pixels.indices step xStep) {
                histogram[gray(pixels[x])]++
                total++
            }
        }

        if (total == 0) return 160

        var sum = 0.0
        for (i in histogram.indices) sum += i * histogram[i].toDouble()

        var sumBackground = 0.0
        var weightBackground = 0
        var bestVariance = -1.0
        var bestThreshold = 160

        for (threshold in 0..255) {
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

        return bestThreshold.coerceIn(80, 220)
    }

    private fun gray(pixel: Int): Int =
        (
            Color.red(pixel) * 299 +
                Color.green(pixel) * 587 +
                Color.blue(pixel) * 114
            ) / 1000
}
