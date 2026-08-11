package com.feniqo.mobile.domain.usecase

internal const val BASIS_POINT_SCALE = 10_000

/** Money üst sınırı sayesinde kayan nokta kullanmadan güvenli baz puan oranı üretir. */
internal fun rateBasisPoints(numerator: Long, denominator: Long): Int {
    require(denominator > 0) { "Oran paydası sıfırdan büyük olmalıdır." }
    val scaled = numerator * BASIS_POINT_SCALE / denominator
    return scaled.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}
