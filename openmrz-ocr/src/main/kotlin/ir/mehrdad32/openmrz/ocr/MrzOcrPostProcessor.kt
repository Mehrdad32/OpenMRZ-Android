package ir.mehrdad32.openmrz.ocr

import ir.mehrdad32.openmrz.core.MrzCheckDigit
import ir.mehrdad32.openmrz.core.MrzFormat
import ir.mehrdad32.openmrz.core.MrzParseResult
import ir.mehrdad32.openmrz.core.MrzParser
import ir.mehrdad32.openmrz.core.MrzValidation

internal data class MrzPostProcessResult(
    val text: String,
    val parseResult: MrzParseResult,
    val correctionCount: Int,
)

internal object MrzOcrPostProcessor {
    private data class Layout(
        val format: MrzFormat,
        val lines: Int,
        val length: Int,
    )

    private data class NormalizedLine(
        val text: String,
        val corrections: Int,
    )

    private data class Candidate(
        val text: String,
        val result: MrzParseResult.Success,
        val corrections: Int,
        val score: Int,
    )

    private enum class Expected {
        ALPHA,
        DIGIT,
        ALNUM,
        NAME,
        SEX,
        CHECK,
        ANY,
    }

    private val layouts = listOf(
        Layout(MrzFormat.TD3, lines = 2, length = 44),
        Layout(MrzFormat.TD2, lines = 2, length = 36),
        Layout(MrzFormat.TD1, lines = 3, length = 30),
    )

    fun parse(rawText: String): MrzPostProcessResult {
        val lines = rawText
            .lineSequence()
            .map(::sanitize)
            .filter { it.length >= 12 }
            .toList()

        var best: Candidate? = null

        for (layout in layouts) {
            if (lines.size < layout.lines) continue

            for (start in 0..(lines.size - layout.lines)) {
                val window = lines.subList(start, start + layout.lines)
                val variants = window.mapIndexed { index, line ->
                    lineVariants(layout, index, line)
                }

                for (combination in cartesian(variants)) {
                    val text = combination.joinToString("\n") { it.text }
                    val parsed = MrzParser.parse(text)
                    if (parsed is MrzParseResult.Success) {
                        val corrections = combination.sumOf { it.corrections }
                        val candidate = Candidate(
                            text = text,
                            result = parsed,
                            corrections = corrections,
                            score = score(parsed.document.validation, corrections),
                        )

                        if (best == null || candidate.score > best.score) {
                            best = candidate
                        }

                        if (parsed.document.validation.isValid && corrections == 0) {
                            return MrzPostProcessResult(text, parsed, corrections)
                        }
                    }
                }
            }
        }

        if (best != null) {
            return MrzPostProcessResult(
                text = best.text,
                parseResult = best.result,
                correctionCount = best.corrections,
            )
        }

        val fallback = lines.joinToString("\n")
        return MrzPostProcessResult(
            text = fallback,
            parseResult = MrzParser.parse(fallback),
            correctionCount = 0,
        )
    }

    private fun lineVariants(
        layout: Layout,
        lineIndex: Int,
        source: String,
    ): List<NormalizedLine> {
        val aligned = mutableListOf<String>()

        if (layout.format == MrzFormat.TD3 && lineIndex == 0) {
            val passportStart = source.indexOf("P<")
            if (passportStart in 0..10) {
                aligned += source.substring(passportStart)
            }
        }

        aligned += source

        val resized = linkedSetOf<String>()
        for (line in aligned) {
            when {
                line.length == layout.length -> resized += line

                line.length < layout.length -> {
                    if (layout.length - line.length <= 10) {
                        resized += line.padEnd(layout.length, '<')
                    }
                }

                else -> {
                    val overflow = line.length - layout.length
                    if (overflow <= 14) {
                        for (start in 0..overflow) {
                            resized += line.substring(start, start + layout.length)
                        }
                    }
                }
            }
        }

        return resized
            .map { normalizeForLayout(layout.format, lineIndex, it) }
            .distinctBy { it.text }
    }

    private fun normalizeForLayout(
        format: MrzFormat,
        lineIndex: Int,
        source: String,
    ): NormalizedLine {
        var corrections = 0
        val out = CharArray(source.length)

        for (index in source.indices) {
            val original = source[index]
            val corrected = correct(original, expectedAt(format, lineIndex, index))
            if (corrected != original) corrections++
            out[index] = corrected
        }

        corrections += repairDocumentNumberByChecksum(format, lineIndex, out)

        return NormalizedLine(String(out), corrections)
    }

    private fun repairDocumentNumberByChecksum(
        format: MrzFormat,
        lineIndex: Int,
        chars: CharArray,
    ): Int {
        val spec = when {
            format == MrzFormat.TD3 && lineIndex == 1 -> Pair(0..8, 9)
            format == MrzFormat.TD2 && lineIndex == 1 -> Pair(0..8, 9)
            format == MrzFormat.TD1 && lineIndex == 0 -> Pair(5..13, 14)
            else -> return 0
        }

        val range = spec.first
        val checkIndex = spec.second

        if (checkIndex !in chars.indices) return 0
        val expected = chars[checkIndex]
        if (expected !in '0'..'9') return 0

        fun currentValue(): String =
            buildString { for (index in range) append(chars[index]) }

        if (MrzCheckDigit.isValid(currentValue(), expected)) return 0

        val original = chars.copyOf()

        // Most MRZ OCR mistakes are a single ambiguous glyph. Try the smallest
        // possible repair first so checksum correction never becomes a free-form guess.
        for (index in range) {
            val source = original[index]
            for (alternative in alternatives(source)) {
                if (alternative == source) continue
                chars[index] = alternative

                if (MrzCheckDigit.isValid(currentValue(), expected)) {
                    return 1
                }
            }
            chars[index] = source
        }

        // Two-character repair is still bounded: a TD1/TD2/TD3 document number
        // contains only nine characters.
        for (first in range) {
            val firstSource = original[first]
            for (firstAlternative in alternatives(firstSource)) {
                if (firstAlternative == firstSource) continue
                chars[first] = firstAlternative

                for (second in (first + 1)..range.last) {
                    val secondSource = original[second]
                    for (secondAlternative in alternatives(secondSource)) {
                        if (secondAlternative == secondSource) continue
                        chars[second] = secondAlternative

                        if (MrzCheckDigit.isValid(currentValue(), expected)) {
                            return 2
                        }
                    }
                    chars[second] = secondSource
                }

                chars[first] = firstSource
            }
        }

        original.copyInto(chars)
        return 0
    }

    private fun alternatives(char: Char): CharArray = when (char) {
        '0' -> charArrayOf('0', 'O', 'Q', 'D')
        'O', 'Q', 'D' -> charArrayOf(char, '0')
        '1' -> charArrayOf('1', 'I', 'L')
        'I', 'L' -> charArrayOf(char, '1')
        '2' -> charArrayOf('2', 'Z')
        'Z' -> charArrayOf('Z', '2')
        '5' -> charArrayOf('5', 'S')
        'S' -> charArrayOf('S', '5')
        '6' -> charArrayOf('6', 'G')
        'G' -> charArrayOf('G', '6')
        '8' -> charArrayOf('8', 'B')
        'B' -> charArrayOf('B', '8')
        else -> charArrayOf(char)
    }

    private fun expectedAt(
        format: MrzFormat,
        line: Int,
        index: Int,
    ): Expected = when (format) {
        MrzFormat.TD3 -> when (line) {
            0 -> when (index) {
                0 -> Expected.ALPHA
                1 -> Expected.ANY
                in 2..4 -> Expected.ALPHA
                else -> Expected.NAME
            }

            else -> when (index) {
                in 0..8 -> Expected.ALNUM
                9 -> Expected.CHECK
                in 10..12 -> Expected.ALPHA
                in 13..18 -> Expected.DIGIT
                19 -> Expected.CHECK
                20 -> Expected.SEX
                in 21..26 -> Expected.DIGIT
                27 -> Expected.CHECK
                in 28..41 -> Expected.ALNUM
                42 -> Expected.CHECK
                43 -> Expected.CHECK
                else -> Expected.ANY
            }
        }

        MrzFormat.TD2 -> when (line) {
            0 -> when (index) {
                in 0..1 -> Expected.ALPHA
                in 2..4 -> Expected.ALPHA
                else -> Expected.NAME
            }

            else -> when (index) {
                in 0..8 -> Expected.ALNUM
                9 -> Expected.CHECK
                in 10..12 -> Expected.ALPHA
                in 13..18 -> Expected.DIGIT
                19 -> Expected.CHECK
                20 -> Expected.SEX
                in 21..26 -> Expected.DIGIT
                27 -> Expected.CHECK
                in 28..34 -> Expected.ALNUM
                35 -> Expected.CHECK
                else -> Expected.ANY
            }
        }

        MrzFormat.TD1 -> when (line) {
            0 -> when (index) {
                in 0..4 -> Expected.ALPHA
                in 5..13 -> Expected.ALNUM
                14 -> Expected.CHECK
                else -> Expected.ALNUM
            }

            1 -> when (index) {
                in 0..5 -> Expected.DIGIT
                6 -> Expected.CHECK
                7 -> Expected.SEX
                in 8..13 -> Expected.DIGIT
                14 -> Expected.CHECK
                in 15..17 -> Expected.ALPHA
                in 18..28 -> Expected.ALNUM
                29 -> Expected.CHECK
                else -> Expected.ANY
            }

            else -> Expected.NAME
        }
    }

    private fun correct(char: Char, expected: Expected): Char = when (expected) {
        Expected.ALPHA -> toAlpha(char)
        Expected.DIGIT -> toDigit(char)
        Expected.CHECK -> if (char == '<') '<' else toDigit(char)
        Expected.NAME -> if (char == '<') '<' else toAlpha(char)
        Expected.ALNUM -> char
        Expected.SEX -> char
        Expected.ANY -> char
    }

    private fun toAlpha(char: Char): Char = when (char) {
        '0' -> 'O'
        '1' -> 'I'
        '2' -> 'Z'
        '5' -> 'S'
        '6' -> 'G'
        '8' -> 'B'
        else -> char
    }

    private fun toDigit(char: Char): Char = when (char) {
        'O', 'Q', 'D' -> '0'
        'I', 'L' -> '1'
        'Z' -> '2'
        'S' -> '5'
        'G' -> '6'
        'B' -> '8'
        else -> char
    }

    private fun sanitize(line: String): String = buildString {
        for (source in line.uppercase()) {
            val char = when (source) {
                ' ', '\t', '«', '‹', '⟨', '＜', '|' -> '<'
                else -> source
            }
            if (char in 'A'..'Z' || char in '0'..'9' || char == '<') {
                append(char)
            }
        }
    }

    private fun cartesian(input: List<List<NormalizedLine>>): Sequence<List<NormalizedLine>> {
        if (input.any { it.isEmpty() }) return emptySequence()

        return input.fold(sequenceOf(emptyList())) { acc, options ->
            acc.flatMap { prefix ->
                options.asSequence().map { prefix + it }
            }
        }
    }

    private fun score(
        validation: MrzValidation,
        corrections: Int,
    ): Int =
        (if (validation.isValid) 10_000 else 0) +
            (if (validation.checkDigitsValid) 5_000 else 0) +
            (if (validation.fields.isValid) 2_000 else 0) +
            (if (validation.documentNumber) 300 else 0) +
            (if (validation.birthDate) 300 else 0) +
            (if (validation.expiryDate) 300 else 0) +
            (if (validation.optionalData != false) 100 else 0) +
            (if (validation.composite) 500 else 0) +
            (if (validation.fields.documentCode) 150 else 0) +
            (if (validation.fields.issuingState) 100 else 0) +
            (if (validation.fields.nationality) 100 else 0) -
            corrections * 20
}
