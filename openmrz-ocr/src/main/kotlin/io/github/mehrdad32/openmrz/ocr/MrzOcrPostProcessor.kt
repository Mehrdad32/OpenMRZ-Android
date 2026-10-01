package io.github.mehrdad32.openmrz.ocr

import io.github.mehrdad32.openmrz.core.MrzParseResult
import io.github.mehrdad32.openmrz.core.MrzParser
import io.github.mehrdad32.openmrz.core.MrzValidation

internal object MrzOcrPostProcessor {
    private data class Layout(val lines: Int, val length: Int)
    private data class Candidate(
        val text: String,
        val result: MrzParseResult.Success,
        val score: Int,
    )

    private val layouts = listOf(
        Layout(lines = 2, length = 44),
        Layout(lines = 2, length = 36),
        Layout(lines = 3, length = 30),
    )

    fun parse(rawText: String): Pair<String, MrzParseResult> {
        val lines = rawText
            .lineSequence()
            .map(::sanitize)
            .filter { it.length >= 20 }
            .toList()

        var best: Candidate? = null

        for (layout in layouts) {
            if (lines.size < layout.lines) continue

            for (start in 0..(lines.size - layout.lines)) {
                val window = lines.subList(start, start + layout.lines)
                val variants = window.mapIndexed { index, line ->
                    lineVariants(
                        line,
                        layout.length,
                        canPadRight = index == 0 || (layout.lines == 3 && index == 2),
                    )
                }

                for (combination in cartesian(variants)) {
                    val text = combination.joinToString("\n")
                    val parsed = MrzParser.parse(text)
                    if (parsed is MrzParseResult.Success) {
                        val candidate = Candidate(text, parsed, score(parsed.document.validation))
                        if (best == null || candidate.score > best.score) best = candidate
                        if (parsed.document.validation.isValid) return text to parsed
                    }
                }
            }
        }

        if (best != null) return best.text to best.result

        val fallback = lines.joinToString("\n")
        return fallback to MrzParser.parse(fallback)
    }

    private fun sanitize(line: String): String = buildString {
        for (source in line.uppercase()) {
            val char = when (source) {
                ' ', '\t', '«', '‹', '⟨', '＜' -> '<'
                else -> source
            }
            if (char in 'A'..'Z' || char in '0'..'9' || char == '<') append(char)
        }
    }

    private fun lineVariants(line: String, target: Int, canPadRight: Boolean): List<String> {
        if (line.length == target) return listOf(line)

        if (line.length < target) {
            if (!canPadRight || target - line.length > 8) return emptyList()
            return listOf(line.padEnd(target, '<'))
        }

        val overflow = line.length - target
        if (overflow > 12) return emptyList()
        return (0..overflow).map { start -> line.substring(start, start + target) }
    }

    private fun cartesian(input: List<List<String>>): Sequence<List<String>> {
        if (input.any { it.isEmpty() }) return emptySequence()
        return input.fold(sequenceOf(emptyList())) { acc, options ->
            acc.flatMap { prefix -> options.asSequence().map { prefix + it } }
        }
    }

    private fun score(validation: MrzValidation): Int =
        (if (validation.documentNumber) 2 else 0) +
            (if (validation.birthDate) 2 else 0) +
            (if (validation.expiryDate) 2 else 0) +
            (if (validation.optionalData != false) 1 else 0) +
            (if (validation.composite) 4 else 0)
}
