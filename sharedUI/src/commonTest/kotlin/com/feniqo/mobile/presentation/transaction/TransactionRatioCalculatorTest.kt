package com.feniqo.mobile.presentation.transaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionRatioCalculatorTest {

    @Test
    fun calculateBasisPoints_withZeroNumeratorAndPositiveDenominator_returnsZero() {
        val result = TransactionRatioCalculator.calculateBasisPoints(0L, 100_000L)
        assertEquals(0, result)

        val resultLargeDenom = TransactionRatioCalculator.calculateBasisPoints(0L, Long.MAX_VALUE)
        assertEquals(0, resultLargeDenom)
    }

    @Test
    fun calculateBasisPoints_withOneOverTwo_returnsFiveThousand() {
        val result = TransactionRatioCalculator.calculateBasisPoints(1L, 2L)
        assertEquals(5000, result)
    }

    @Test
    fun calculateBasisPoints_withEqualValues_returnsTenThousand() {
        val result = TransactionRatioCalculator.calculateBasisPoints(250_000L, 250_000L)
        assertEquals(10_000, result)
    }

    @Test
    fun calculateBasisPoints_withMaxLongValues_returnsTenThousand() {
        val result = TransactionRatioCalculator.calculateBasisPoints(Long.MAX_VALUE, Long.MAX_VALUE)
        assertEquals(10_000, result)
    }

    @Test
    fun calculateBasisPoints_withMaxLongMinusOneOverMaxLong_returnsNineThousandNineHundredNinetyNine() {
        val numerator = Long.MAX_VALUE - 1L
        val denominator = Long.MAX_VALUE
        val result = TransactionRatioCalculator.calculateBasisPoints(numerator, denominator)
        // Matematiksel floor: 9999.9999... -> 9999 bps
        assertEquals(9999, result)
    }

    @Test
    fun calculateBasisPoints_withHalfOfMaxLongOverMaxLong_returnsExpectedFloorValue() {
        val max = Long.MAX_VALUE
        val half = max / 2L
        val result = TransactionRatioCalculator.calculateBasisPoints(half, max)
        // (Long.MAX_VALUE / 2) / Long.MAX_VALUE = 4999.999999999999999457... -> floor 4999 bps
        assertEquals(4999, result)
    }

    @Test
    fun calculateBasisPoints_withVerySmallPositiveRatio_returnsCorrectFloor() {
        // 1 / Long.MAX_VALUE * 10_000 < 1 -> floor 0
        assertEquals(0, TransactionRatioCalculator.calculateBasisPoints(1L, Long.MAX_VALUE))

        // 1 / 100_000 * 10_000 = 0.1 -> floor 0
        assertEquals(0, TransactionRatioCalculator.calculateBasisPoints(1L, 100_000L))

        // 1 / 10_000 * 10_000 = 1 -> 1 bps
        assertEquals(1, TransactionRatioCalculator.calculateBasisPoints(1L, 10_000L))

        // 5 / 10_000 * 10_000 = 5 -> 5 bps
        assertEquals(5, TransactionRatioCalculator.calculateBasisPoints(5L, 10_000L))
    }

    @Test
    fun calculateBasisPoints_withNumeratorGreaterThanDenominator_returnsTenThousand() {
        val result = TransactionRatioCalculator.calculateBasisPoints(300_000L, 250_000L)
        assertEquals(10_000, result)

        val resultMaxNumerator = TransactionRatioCalculator.calculateBasisPoints(Long.MAX_VALUE, 5000L)
        assertEquals(10_000, resultMaxNumerator)
    }

    @Test
    fun calculateBasisPoints_withInvalidDenominatorOrNumerator_returnsZero() {
        // Sıfır veya negatif payda
        assertEquals(0, TransactionRatioCalculator.calculateBasisPoints(500L, 0L))
        assertEquals(0, TransactionRatioCalculator.calculateBasisPoints(500L, -100L))

        // Negatif pay
        assertEquals(0, TransactionRatioCalculator.calculateBasisPoints(-50L, 100_000L))
        assertEquals(0, TransactionRatioCalculator.calculateBasisPoints(-1L, -1L))
    }

    @Test
    fun calculateBasisPoints_ensuresAllResultsAlwaysWithinZeroToTenThousand() {
        val testCases = listOf(
            0L to 1L,
            1L to 10_000L,
            10_000L to 10_000L,
            750L to 1000L,
            1L to 2L,
            3L to 4L,
            Long.MAX_VALUE / 4L to Long.MAX_VALUE,
            Long.MAX_VALUE / 2L to Long.MAX_VALUE,
            Long.MAX_VALUE - 1L to Long.MAX_VALUE,
            Long.MAX_VALUE to Long.MAX_VALUE,
            Long.MAX_VALUE to Long.MAX_VALUE - 1L,
            -10L to 100L,
            100L to -10L,
            0L to 0L,
        )

        for ((num, den) in testCases) {
            val bps = TransactionRatioCalculator.calculateBasisPoints(num, den)
            assertTrue(bps in 0..10_000, "Bps $bps not in 0..10_000 for $num / $den")
        }
    }
}
