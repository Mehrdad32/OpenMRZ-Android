package io.github.mehrdad32.openmrz.core

enum class MrzFormat { TD1, TD2, TD3 }
enum class MrzSex { MALE, FEMALE, UNSPECIFIED }

data class MrzValidation(
    val documentNumber: Boolean,
    val birthDate: Boolean,
    val expiryDate: Boolean,
    val optionalData: Boolean?,
    val composite: Boolean,
) {
    val isValid: Boolean
        get() = documentNumber && birthDate && expiryDate && composite && optionalData != false
}

data class MrzDocument(
    val format: MrzFormat,
    val documentCode: String,
    val issuingState: String,
    val documentNumber: String,
    val nationality: String,
    val birthDate: String,
    val sex: MrzSex,
    val expiryDate: String,
    val surname: String,
    val givenNames: List<String>,
    val optionalData: String,
    val rawLines: List<String>,
    val validation: MrzValidation,
)

sealed interface MrzParseResult {
    data class Success(val document: MrzDocument) : MrzParseResult

    data class Failure(
        val reason: Reason,
        val message: String,
    ) : MrzParseResult {
        enum class Reason { EMPTY_INPUT, UNSUPPORTED_FORMAT, INVALID_CHARACTER }
    }
}
