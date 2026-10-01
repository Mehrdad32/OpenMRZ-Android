package io.github.mehrdad32.openmrz.ocr

import io.github.mehrdad32.openmrz.core.MrzFormat
import io.github.mehrdad32.openmrz.core.MrzParseResult
import io.github.mehrdad32.openmrz.core.MrzParser
import io.github.mehrdad32.openmrz.core.MrzValidation

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

                        if (
                            parsed.document.validation.isValid &&
                            corrections == 0
                        ) {
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

        return NormalizedLine(String(out), corrections)
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
        Expected.SEX -> when (char) {
            'M', 'F', 'X', '<' -> char
            else -> char
        }
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
        (if (validation.documentNumber) 4 else 0) +
            (if (validation.birthDate) 4 else 0) +
            (if (validation.expiryDate) 4 else 0) +
            (if (validation.optionalData != false) 2 else 0) +
            (if (validation.composite) 8 else 0) +
            (if (validation.fields.documentCode) 5 else 0) +
            (if (validation.fields.issuingState) 3 else 0) +
            (if (validation.fields.nationality) 3 else 0) +
            (if (validation.fields.birthDateFormat) 2 else 0) +
            (if (validation.fields.expiryDateFormat) 2 else 0) +
            (if (validation.fields.sex) 1 else 0) +
            (if (validation.fields.names) 2 else 0) -
            corrections.coerceAtMost(10)
}
