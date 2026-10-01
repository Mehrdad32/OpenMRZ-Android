package ir.mehrdad32.openmrz.core

object MrzCheckDigit {
    private val weights = intArrayOf(7, 3, 1)

    fun calculate(data: CharSequence): Char {
        val sum = data.withIndex().sumOf { (index, char) ->
            valueOf(char) * weights[index % weights.size]
        }
        return ('0'.code + (sum % 10)).toChar()
    }

    fun isValid(data: CharSequence, expected: Char): Boolean {
        if (expected !in '0'..'9') return false
        return calculate(data) == expected
    }

    fun valueOf(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'A'..'Z' -> char - 'A' + 10
        '<' -> 0
        else -> throw IllegalArgumentException("Unsupported MRZ character: '$char'")
    }
}
