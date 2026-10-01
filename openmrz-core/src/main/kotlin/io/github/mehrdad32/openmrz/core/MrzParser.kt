package io.github.mehrdad32.openmrz.core

object MrzParser {
    private val allowedChars = ('A'..'Z').toSet() + ('0'..'9').toSet() + '<'

    fun parse(input: String): MrzParseResult {
        val lines = normalize(input)
        if (lines.isEmpty()) {
            return MrzParseResult.Failure(
                MrzParseResult.Failure.Reason.EMPTY_INPUT,
                "No MRZ lines were found.",
            )
        }

        if (lines.any { line -> line.any { it !in allowedChars } }) {
            return MrzParseResult.Failure(
                MrzParseResult.Failure.Reason.INVALID_CHARACTER,
                "MRZ contains unsupported characters.",
            )
        }

        return when {
            lines.size == 3 && lines.all { it.length == 30 } -> parseTd1(lines)
            lines.size == 2 && lines.all { it.length == 36 } -> parseTd2(lines)
            lines.size == 2 && lines.all { it.length == 44 } -> parseTd3(lines)
            else -> MrzParseResult.Failure(
                MrzParseResult.Failure.Reason.UNSUPPORTED_FORMAT,
                "Expected TD1 (3x30), TD2 (2x36), or TD3 (2x44); got " +
                    lines.joinToString(prefix = "[", postfix = "]") { it.length.toString() },
            )
        }
    }

    private fun parseTd3(lines: List<String>): MrzParseResult.Success {
        val l1 = lines[0]
        val l2 = lines[1]
        val documentNumber = l2.substring(0, 9)
        val birthDate = l2.substring(13, 19)
        val expiryDate = l2.substring(21, 27)
        val optionalData = l2.substring(28, 42)
        val compositeData = l2.substring(0, 10) + l2.substring(13, 20) + l2.substring(21, 43)

        return success(
            format = MrzFormat.TD3,
            lines = lines,
            documentCode = l1.substring(0, 2),
            issuingState = l1.substring(2, 5),
            nameField = l1.substring(5, 44),
            documentNumber = documentNumber,
            nationality = l2.substring(10, 13),
            birthDate = birthDate,
            sex = l2[20],
            expiryDate = expiryDate,
            optionalData = optionalData,
            validation = MrzValidation(
                documentNumber = MrzCheckDigit.isValid(documentNumber, l2[9]),
                birthDate = MrzCheckDigit.isValid(birthDate, l2[19]),
                expiryDate = MrzCheckDigit.isValid(expiryDate, l2[27]),
                optionalData = if (l2[42] == '<') null else MrzCheckDigit.isValid(optionalData, l2[42]),
                composite = MrzCheckDigit.isValid(compositeData, l2[43]),
                fields = validateFields(
                    format = MrzFormat.TD3,
                    documentCode = l1.substring(0, 2),
                    issuingState = l1.substring(2, 5),
                    nationality = l2.substring(10, 13),
                    birthDate = birthDate,
                    expiryDate = expiryDate,
                    sex = l2[20],
                    nameField = l1.substring(5, 44),
                ),
            ),
        )
    }

    private fun parseTd2(lines: List<String>): MrzParseResult.Success {
        val l1 = lines[0]
        val l2 = lines[1]
        val documentNumber = l2.substring(0, 9)
        val birthDate = l2.substring(13, 19)
        val expiryDate = l2.substring(21, 27)
        val optionalData = l2.substring(28, 35)
        val compositeData = l2.substring(0, 10) + l2.substring(13, 20) + l2.substring(21, 35)

        return success(
            format = MrzFormat.TD2,
            lines = lines,
            documentCode = l1.substring(0, 2),
            issuingState = l1.substring(2, 5),
            nameField = l1.substring(5, 36),
            documentNumber = documentNumber,
            nationality = l2.substring(10, 13),
            birthDate = birthDate,
            sex = l2[20],
            expiryDate = expiryDate,
            optionalData = optionalData,
            validation = MrzValidation(
                documentNumber = MrzCheckDigit.isValid(documentNumber, l2[9]),
                birthDate = MrzCheckDigit.isValid(birthDate, l2[19]),
                expiryDate = MrzCheckDigit.isValid(expiryDate, l2[27]),
                optionalData = null,
                composite = MrzCheckDigit.isValid(compositeData, l2[35]),
                fields = validateFields(
                    format = MrzFormat.TD2,
                    documentCode = l1.substring(0, 2),
                    issuingState = l1.substring(2, 5),
                    nationality = l2.substring(10, 13),
                    birthDate = birthDate,
                    expiryDate = expiryDate,
                    sex = l2[20],
                    nameField = l1.substring(5, 36),
                ),
            ),
        )
    }

    private fun parseTd1(lines: List<String>): MrzParseResult.Success {
        val l1 = lines[0]
        val l2 = lines[1]
        val l3 = lines[2]
        val documentNumber = l1.substring(5, 14)
        val optional1 = l1.substring(15, 30)
        val birthDate = l2.substring(0, 6)
        val expiryDate = l2.substring(8, 14)
        val optional2 = l2.substring(18, 29)
        val compositeData = l1.substring(5, 30) + l2.substring(0, 7) + l2.substring(8, 15) + optional2

        return success(
            format = MrzFormat.TD1,
            lines = lines,
            documentCode = l1.substring(0, 2),
            issuingState = l1.substring(2, 5),
            nameField = l3,
            documentNumber = documentNumber,
            nationality = l2.substring(15, 18),
            birthDate = birthDate,
            sex = l2[7],
            expiryDate = expiryDate,
            optionalData = optional1 + optional2,
            validation = MrzValidation(
                documentNumber = MrzCheckDigit.isValid(documentNumber, l1[14]),
                birthDate = MrzCheckDigit.isValid(birthDate, l2[6]),
                expiryDate = MrzCheckDigit.isValid(expiryDate, l2[14]),
                optionalData = null,
                composite = MrzCheckDigit.isValid(compositeData, l2[29]),
                fields = validateFields(
                    format = MrzFormat.TD1,
                    documentCode = l1.substring(0, 2),
                    issuingState = l1.substring(2, 5),
                    nationality = l2.substring(15, 18),
                    birthDate = birthDate,
                    expiryDate = expiryDate,
                    sex = l2[7],
                    nameField = l3,
                ),
            ),
        )
    }

    private fun success(
        format: MrzFormat,
        lines: List<String>,
        documentCode: String,
        issuingState: String,
        nameField: String,
        documentNumber: String,
        nationality: String,
        birthDate: String,
        sex: Char,
        expiryDate: String,
        optionalData: String,
        validation: MrzValidation,
    ): MrzParseResult.Success {
        val (surname, givenNames) = parseName(nameField)
        return MrzParseResult.Success(
            MrzDocument(
                format = format,
                documentCode = clean(documentCode),
                issuingState = clean(issuingState),
                documentNumber = clean(documentNumber),
                nationality = clean(nationality),
                birthDate = birthDate,
                sex = when (sex) {
                    'M' -> MrzSex.MALE
                    'F' -> MrzSex.FEMALE
                    else -> MrzSex.UNSPECIFIED
                },
                expiryDate = expiryDate,
                surname = surname,
                givenNames = givenNames,
                optionalData = clean(optionalData),
                rawLines = lines,
                validation = validation,
            ),
        )
    }

    private fun validateFields(
        format: MrzFormat,
        documentCode: String,
        issuingState: String,
        nationality: String,
        birthDate: String,
        expiryDate: String,
        sex: Char,
        nameField: String,
    ): MrzFieldValidation {
        val codeValid = when (format) {
            MrzFormat.TD3 -> documentCode.firstOrNull() == 'P'
            MrzFormat.TD1,
            MrzFormat.TD2,
            -> documentCode.firstOrNull()?.isLetter() == true
        } && documentCode.all { it.isLetter() || it == '<' }

        return MrzFieldValidation(
            documentCode = codeValid,
            issuingState = isThreeLetterCode(issuingState),
            nationality = isThreeLetterCode(nationality),
            birthDateFormat = isValidMrzDate(birthDate),
            expiryDateFormat = isValidMrzDate(expiryDate),
            sex = sex == 'M' || sex == 'F' || sex == 'X' || sex == '<',
            names = nameField.any(Char::isLetter) && nameField.all { it.isLetter() || it == '<' },
        )
    }

    private fun isThreeLetterCode(value: String): Boolean =
        value.length == 3 && value.all { it in 'A'..'Z' }

    private fun isValidMrzDate(value: String): Boolean {
        if (value.length != 6 || !value.all(Char::isDigit)) return false

        val month = value.substring(2, 4).toInt()
        val day = value.substring(4, 6).toInt()

        if (month !in 1..12) return false

        val maxDay = when (month) {
            2 -> 29
            4, 6, 9, 11 -> 30
            else -> 31
        }

        return day in 1..maxDay
    }

    private fun parseName(field: String): Pair<String, List<String>> {
        val sections = field.trim('<').split("<<", limit = 2)
        val surname = cleanName(sections.firstOrNull().orEmpty())
        val givenNames = sections
            .getOrNull(1)
            .orEmpty()
            .split('<')
            .filter { it.isNotBlank() }
        return surname to givenNames
    }

    private fun clean(value: String): String = value.trim('<')

    private fun cleanName(value: String): String =
        value.trim('<').replace('<', ' ').replace(Regex("\\s+"), " ").trim()

    private fun normalize(input: String): List<String> =
        input.lineSequence()
            .map { it.trim().uppercase().replace(' ', '<') }
            .filter { it.isNotEmpty() }
            .toList()
}
