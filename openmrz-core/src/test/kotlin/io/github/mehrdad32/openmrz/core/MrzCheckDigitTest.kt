package io.github.mehrdad32.openmrz.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MrzCheckDigitTest {
    @Test
    fun `calculates ICAO example document number check digit`() {
        assertEquals('3', MrzCheckDigit.calculate("L898902C<"))
        assertTrue(MrzCheckDigit.isValid("L898902C<", '3'))
    }

    @Test
    fun `maps filler and alphanumeric values correctly`() {
        assertEquals(0, MrzCheckDigit.valueOf('<'))
        assertEquals(0, MrzCheckDigit.valueOf('0'))
        assertEquals(10, MrzCheckDigit.valueOf('A'))
        assertEquals(35, MrzCheckDigit.valueOf('Z'))
    }
}
