package com.feniqo.mobile.presentation.util

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColorParserTest {

    @Test
    fun parseHexColorOrNull_validSixDigitHex_parsesWithFullAlpha() {
        val color = ColorParser.parseHexColorOrNull("#10B981")
        assertEquals(Color(red = 0x10, green = 0xB9, blue = 0x81, alpha = 0xFF), color)

        val black = ColorParser.parseHexColorOrNull("#000000")
        assertEquals(Color(red = 0, green = 0, blue = 0, alpha = 255), black)

        val white = ColorParser.parseHexColorOrNull("#FFFFFF")
        assertEquals(Color(red = 255, green = 255, blue = 255, alpha = 255), white)
    }

    @Test
    fun parseHexColorOrNull_caseInsensitive_acceptsBothLowerAndUpper() {
        val lower = ColorParser.parseHexColorOrNull("#10b981")
        val upper = ColorParser.parseHexColorOrNull("#10B981")
        val mixed = ColorParser.parseHexColorOrNull("#10b981")

        assertEquals(upper, lower)
        assertEquals(Color(red = 0x10, green = 0xB9, blue = 0x81, alpha = 0xFF), mixed)

        val mixedHex = ColorParser.parseHexColorOrNull("#aAbBcC")
        assertEquals(Color(red = 0xAA, green = 0xBB, blue = 0xCC, alpha = 0xFF), mixedHex)
    }

    @Test
    fun parseHexColorOrNull_validEightDigitHex_parsesAlphaAndColorsCorrectly() {
        val halfAlpha = ColorParser.parseHexColorOrNull("#8010B981")
        assertEquals(Color(red = 0x10, green = 0xB9, blue = 0x81, alpha = 0x80), halfAlpha)

        val zeroAlpha = ColorParser.parseHexColorOrNull("#00FFFFFF")
        assertEquals(Color(red = 255, green = 255, blue = 255, alpha = 0), zeroAlpha)

        val fullAlpha = ColorParser.parseHexColorOrNull("#FF10B981")
        assertEquals(Color(red = 0x10, green = 0xB9, blue = 0x81, alpha = 0xFF), fullAlpha)
    }

    @Test
    fun parseHexColorOrNull_nullOrEmptyInput_returnsNull() {
        assertNull(ColorParser.parseHexColorOrNull(null))
        assertNull(ColorParser.parseHexColorOrNull(""))
    }

    @Test
    fun parseHexColorOrNull_withoutHashPrefix_returnsNull() {
        assertNull(ColorParser.parseHexColorOrNull("10B981"))
        assertNull(ColorParser.parseHexColorOrNull("FF10B981"))
        assertNull(ColorParser.parseHexColorOrNull("000000"))
    }

    @Test
    fun parseHexColorOrNull_invalidLengths_returnsNull() {
        assertNull(ColorParser.parseHexColorOrNull("#"))
        assertNull(ColorParser.parseHexColorOrNull("#1"))
        assertNull(ColorParser.parseHexColorOrNull("#12"))
        assertNull(ColorParser.parseHexColorOrNull("#123"))
        assertNull(ColorParser.parseHexColorOrNull("#1234"))
        assertNull(ColorParser.parseHexColorOrNull("#12345"))
        assertNull(ColorParser.parseHexColorOrNull("#1234567"))
        assertNull(ColorParser.parseHexColorOrNull("#123456789"))
    }

    @Test
    fun parseHexColorOrNull_invalidCharacters_returnsNull() {
        assertNull(ColorParser.parseHexColorOrNull("#GGGGGG"))
        assertNull(ColorParser.parseHexColorOrNull("#10B98Z"))
        assertNull(ColorParser.parseHexColorOrNull("# 10B981"))
        assertNull(ColorParser.parseHexColorOrNull("#10B98-"))
        assertNull(ColorParser.parseHexColorOrNull("#10.981"))
        assertNull(ColorParser.parseHexColorOrNull("#8010B98G"))
    }
}
