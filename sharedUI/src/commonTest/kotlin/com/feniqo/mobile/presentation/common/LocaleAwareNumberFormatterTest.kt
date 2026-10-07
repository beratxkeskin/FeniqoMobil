package com.feniqo.mobile.presentation.common

import com.feniqo.mobile.domain.model.AssetQuantity
import com.feniqo.mobile.domain.model.Currency
import kotlin.test.Test
import kotlin.test.assertEquals

class LocaleAwareNumberFormatterTest {
    @Test
    fun formatBasisPointsRateNumber_formatsWholeAndFractionalNumbers() {
        assertEquals("80", formatBasisPointsRateNumber(8_000, ","))
        assertEquals("80,5", formatBasisPointsRateNumber(8_050, ","))
        assertEquals("80,99", formatBasisPointsRateNumber(8_099, ","))
        assertEquals("80.99", formatBasisPointsRateNumber(8_099, "."))
        assertEquals("0", formatBasisPointsRateNumber(0, ","))
        assertEquals("0,05", formatBasisPointsRateNumber(5, ","))
        assertEquals("25", formatBasisPointsRateNumber(-2_500, ","))
        assertEquals("8,5", formatBasisPointsRateNumber(-850, ","))
    }

    @Test
    fun formatBasisPointsRateNumber_handlesIntMinValueSafelyWithoutOverflow() {
        val minValueResultTr = formatBasisPointsRateNumber(Int.MIN_VALUE, ",")
        assertEquals("21474836,48", minValueResultTr)

        val minValueResultEn = formatBasisPointsRateNumber(Int.MIN_VALUE, ".")
        assertEquals("21474836.48", minValueResultEn)

        val maxValueResult = formatBasisPointsRateNumber(Int.MAX_VALUE, ",")
        assertEquals("21474836,47", maxValueResult)
    }

    @Test
    fun formatAssetQuantity_formatsScaleCorrectly() {
        val whole = AssetQuantity(unscaledValue = 30L, scale = 0)
        assertEquals("30", formatAssetQuantity(whole, ","))
        assertEquals("30", formatAssetQuantity(whole, "."))

        val decimal = AssetQuantity(unscaledValue = 12_340L, scale = 4)
        assertEquals("1,234", formatAssetQuantity(decimal, ","))
        assertEquals("1.234", formatAssetQuantity(decimal, "."))

        val smallFraction = AssetQuantity(unscaledValue = 5L, scale = 2)
        assertEquals("0,05", formatAssetQuantity(smallFraction, ","))
        assertEquals("0.05", formatAssetQuantity(smallFraction, "."))
    }

    @Test
    fun formatMinorUnitsToInputText_formatsDecimalSeparator() {
        assertEquals("125", formatMinorUnitsToInputText(12_500L, Currency.TRY, ','))
        assertEquals("125,5", formatMinorUnitsToInputText(12_550L, Currency.TRY, ','))
        assertEquals("125.5", formatMinorUnitsToInputText(12_550L, Currency.TRY, '.'))
        assertEquals("125,05", formatMinorUnitsToInputText(12_505L, Currency.TRY, ','))
        assertEquals("125.05", formatMinorUnitsToInputText(12_505L, Currency.TRY, '.'))
        assertEquals("0", formatMinorUnitsToInputText(0L, Currency.TRY, ','))
        assertEquals("0.5", formatMinorUnitsToInputText(50L, Currency.USD, '.'))
    }
}
