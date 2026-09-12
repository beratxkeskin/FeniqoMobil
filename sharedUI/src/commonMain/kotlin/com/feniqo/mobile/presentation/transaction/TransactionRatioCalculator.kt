package com.feniqo.mobile.presentation.transaction

/**
 * Taşma korumalı (overflow-safe), kesin floor sözleşmesine sahip tamsayı baz puan (0..10_000 bps) hesaplayıcı.
 * Double veya Float kullanılmaz; 128-bit tamsayı aritmetiğiyle Long.MAX_VALUE değerlerinde dahi
 * taşma veya hassasiyet kaybı olmaksızın matematiksel olarak kesin floor sonucunu üretir.
 */
object TransactionRatioCalculator {

    private const val BASIS_POINT_SCALE = 10_000L
    private const val MULTIPLY_SAFE_LIMIT = Long.MAX_VALUE / BASIS_POINT_SCALE

    /**
     * [numerator] / [denominator] oranını 0..10_000 aralığında baz puan olarak hesaplar.
     *
     * Sözleşme:
     * - [numerator] <= 0 veya [denominator] <= 0 ise 0 döner.
     * - [numerator] >= [denominator] ise 10_000 döner.
     * - Aksi halde kesin floor: floor((numerator * 10_000) / denominator) döner.
     * - Double/Float kullanılmaz.
     * - Long overflow üretilmez.
     * - Sonuç daima 0..10_000 aralığındadır.
     */
    fun calculateBasisPoints(numerator: Long, denominator: Long): Int {
        if (denominator <= 0L || numerator <= 0L) return 0
        if (numerator >= denominator) return BASIS_POINT_SCALE.toInt()

        // numerator < denominator garantidir.
        // Eğer numerator * 10_000L Long sınırları içindeyse doğrudan tam tamsayı bölme (floor):
        if (numerator <= MULTIPLY_SAFE_LIMIT) {
            return ((numerator * BASIS_POINT_SCALE) / denominator).toInt().coerceIn(0, BASIS_POINT_SCALE.toInt())
        }

        // numerator > MULTIPLY_SAFE_LIMIT durumunda:
        // Doğrudan çarpım Long taşması üretir. Hassasiyet kaybını önlemek için pay ve payda kaydırılmaz;
        // 128-bit kesin tamsayı ikili arama (binary search) ile floor((numerator * 10_000) / denominator) bulunur.
        val target = multiply64(numerator, BASIS_POINT_SCALE)
        var low = 0L
        var high = BASIS_POINT_SCALE
        var ans = 0L

        while (low <= high) {
            val mid = (low + high) ushr 1
            val product = multiply64(mid, denominator)
            if (product <= target) {
                ans = mid
                low = mid + 1L
            } else {
                high = mid - 1L
            }
        }

        return ans.toInt().coerceIn(0, BASIS_POINT_SCALE.toInt())
    }

    private data class UInt128(val high: Long, val low: Long) : Comparable<UInt128> {
        override fun compareTo(other: UInt128): Int {
            val hComp = (high xor Long.MIN_VALUE).compareTo(other.high xor Long.MIN_VALUE)
            if (hComp != 0) return hComp
            return (low xor Long.MIN_VALUE).compareTo(other.low xor Long.MIN_VALUE)
        }
    }

    private fun multiply64(a: Long, b: Long): UInt128 {
        require(a >= 0L && b >= 0L) { "Operands must be non-negative" }
        val aHigh = a ushr 32
        val aLow = a and 0xFFFF_FFFFL
        val bHigh = b ushr 32
        val bLow = b and 0xFFFF_FFFFL

        val p0 = aLow * bLow
        val p1 = aLow * bHigh
        val p2 = aHigh * bLow
        val p3 = aHigh * bHigh

        val mid0 = (p0 ushr 32) + (p1 and 0xFFFF_FFFFL)
        val mid1 = (mid0 and 0xFFFF_FFFFL) + (p2 and 0xFFFF_FFFFL)
        val low = (mid1 shl 32) or (p0 and 0xFFFF_FFFFL)
        val high = p3 + (p1 ushr 32) + (p2 ushr 32) + (mid0 ushr 32) + (mid1 ushr 32)
        return UInt128(high = high, low = low)
    }
}
