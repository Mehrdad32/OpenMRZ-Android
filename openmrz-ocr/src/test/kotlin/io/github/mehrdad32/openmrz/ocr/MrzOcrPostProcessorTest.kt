package io.github.mehrdad32.openmrz.ocr

import io.github.mehrdad32.openmrz.core.MrzParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MrzOcrPostProcessorTest {
    @Test
    fun `repairs numeric confusion in nationality field`() {
        val result = MrzOcrPostProcessor.parse(
            """
            P<UTOERIKSSON<K<ANNA<KMARIAK<<<<K<KKKEKEEC<<
            L898902C36UT07408122F1204159ZE184226B<<<<<10
            """.trimIndent()
        )

        val parsed = result.parseResult as MrzParseResult.Success
        assertEquals("UTO", parsed.document.nationality)
        assertTrue(parsed.document.validation.checkDigitsValid)
        assertTrue(result.correctionCount >= 1)
    }

    @Test
    fun `does not falsely trust the corrupted US OCR sample`() {
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

    @Test
    fun `keeps clean US passport sample valid`() {
        val result = MrzOcrPostProcessor.parse(
            """
            P<USAJANE<<MARY<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
            9102392482USA6401171F1812051900781200<129676
            """.trimIndent()
        )

        val parsed = result.parseResult as MrzParseResult.Success
        assertTrue(parsed.document.validation.isValid)
        assertEquals(0, result.correctionCount)
    }
}
