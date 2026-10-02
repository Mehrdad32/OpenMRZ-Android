package ir.mehrdad32.openmrz.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MrzParserTest {
    @Test
    fun `parses ICAO TD3 example`() {
        val result = MrzParser.parse(
            """
            P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<
            L898902C<3UTO6908061F9406236ZE184226B<<<<<14
            """.trimIndent()
        ) as MrzParseResult.Success

        assertEquals(MrzFormat.TD3, result.document.format)
        assertEquals("ERIKSSON", result.document.surname)
        assertEquals(listOf("ANNA", "MARIA"), result.document.givenNames)
        assertEquals("L898902C", result.document.documentNumber)
        assertEquals("UTO", result.document.nationality)
        assertTrue(result.document.validation.isValid)
    }

    @Test
    fun `parses ICAO TD2 example`() {
        val result = MrzParser.parse(
            """
            I<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<
            D231458907UTO7408122F1204159<<<<<<<6
            """.trimIndent()
        ) as MrzParseResult.Success

        assertEquals(MrzFormat.TD2, result.document.format)
        assertTrue(result.document.validation.isValid)
    }

    @Test
    fun `parses ICAO TD1 example`() {
        val result = MrzParser.parse(
            """
            I<UTOD231458907<<<<<<<<<<<<<<<
            7408122F1204159UTO<<<<<<<<<<<6
            ERIKSSON<<ANNA<MARIA<<<<<<<<<<
            """.trimIndent()
        ) as MrzParseResult.Success

        assertEquals(MrzFormat.TD1, result.document.format)
        assertEquals("D23145890", result.document.documentNumber)
        assertTrue(result.document.validation.isValid)
    }

    @Test
    fun `rejects unknown three letter issuer code even when checksums are valid`() {
        val result = MrzParser.parse(
            """
            P<DELAPAZ<<MICHELLE<<<<<<<<<<<<<<<<<<<<<<<<<
            9102392482USA6401171F1812051900781200<129676
            """.trimIndent()
        ) as MrzParseResult.Success

        assertEquals("DEL", result.document.issuingState)
        assertEquals("APAZ", result.document.surname)
        assertTrue(result.document.validation.checkDigitsValid)
        assertFalse(result.document.validation.fields.issuingState)
        assertFalse(result.document.validation.isValid)
    }

    @Test
    fun `rejects filler inside issuing state`() {
        val result = MrzParser.parse(
            """
            P<K<KERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<
            L898902C<3UTO6908061F9406236ZE184226B<<<<<14
            """.trimIndent()
        ) as MrzParseResult.Success

        assertFalse(result.document.validation.fields.issuingState)
        assertFalse(result.document.validation.isValid)
    }

    @Test
    fun `rejects impossible YYMMDD values`() {
        val result = MrzParser.parse(
            """
            P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<
            L898902C<3UTO6908061F2300000ZE184226B<<<<<14
            """.trimIndent()
        ) as MrzParseResult.Success

        assertFalse(result.document.validation.fields.expiryDateFormat)
        assertFalse(result.document.validation.isValid)
    }
}
