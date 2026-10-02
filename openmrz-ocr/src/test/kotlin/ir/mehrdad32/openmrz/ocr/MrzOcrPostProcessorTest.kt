package ir.mehrdad32.openmrz.ocr

import ir.mehrdad32.openmrz.core.MrzParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MrzOcrPostProcessorTest {
    @Test
    fun `repairs numeric confusion in nationality field`() {
        val result = MrzOcrPostProcessor.parse(
            """
            P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<
            L898902C36UT07408122F1204159ZE184226B<<<<<10
            """.trimIndent()
        )

        val parsed = result.parseResult as MrzParseResult.Success
        assertEquals("UTO", parsed.document.nationality)
        assertTrue(result.correctionCount >= 1)
    }

    @Test
    fun `uses document number checksum to repair Z read as 2`() {
        val result = MrzOcrPostProcessor.parse(
            """
            P<INDSPECIMEN<<KUMAR<G<<<<<<<<<<<<<<<<<<<<<<
            29999999<0IND8505246M2301017<<<<<<<<<<<<<<<8
            """.trimIndent()
        )

        val parsed = result.parseResult as MrzParseResult.Success
        assertEquals("Z9999999", parsed.document.documentNumber)
        assertTrue(parsed.document.validation.documentNumber)
        assertTrue(result.correctionCount >= 1)
    }

    @Test
    fun `does not force two ambiguous glyphs just to satisfy one bad checksum`() {
        val result = MrzOcrPostProcessor.parse(
            """
            P<ESPGARCIA<MARTINEZ<<LUCIA<<<<<<<<<<<<<<<<<
            PAW8123458ESP9205275F3407147A23456789<<<<<10
            """.trimIndent()
        )

        val parsed = result.parseResult as MrzParseResult.Success
        assertEquals("PAW812345", parsed.document.documentNumber)
        assertFalse(parsed.document.validation.isValid)
    }

    @Test
    fun `removes excess filler before truncating trailing check digits`() {
        val result = MrzOcrPostProcessor.parse(
            """
            P<ESPGARCIA<MARTINEZ<<LUCIA<<<<<<<<<<<<<<<<<
            PAW8123458ESP9205275F3407147A23456789<<<<<<10
            """.trimIndent()
        )

        assertTrue(result.text.lines()[1].endsWith("10"))
        assertEquals(44, result.text.lines()[1].length)
    }

    @Test
    fun `does not falsely trust corrupted US OCR sample`() {
        val result = MrzOcrPostProcessor.parse(
            """
            7<<<BP<USAJANE<<MARY<<<<<<<<S<<<<<6<<<6<<<S8
            9102392482USA6401171F181<2051900781200<12967
            """.trimIndent()
        )

        val parsed = result.parseResult as? MrzParseResult.Success
        if (parsed != null) {
            assertFalse(parsed.document.validation.isValid)
        }
    }
}
