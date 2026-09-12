package com.feniqo.mobile.presentation.category

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.ReportPeriod
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import com.feniqo.mobile.presentation.budget.nextMonth
import com.feniqo.mobile.presentation.budget.previousMonth
import com.feniqo.mobile.presentation.util.MoneyFormatter
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Kategoriler ekranı için harcama, trend, özet ve içgörü hesaplamalarını yürüten saf domain/presentation motoru.
 * Finansal hesaplarda Double veya Float kesinlikle kullanılmaz; kuruş ve baz puan (0..10_000) tamsayı aritmetiğiyle çalışır.
 */
object CategoryAnalyticsCalculator {

    const val SCALE_BASIS_POINTS = 10_000L
    const val MAX_BASIS_POINTS = 10_000

    /**
     * UI trend artış yüzdesi için ürün kararı olarak belirlenen güvenli üst sınır.
     * 1_000_000 baz puan = %10.000 (100 kat artış).
     * Bu sınırın üzerindeki aşırı artışlar UI'da kontrollü biçimde bu değere kenetlenir (clamp).
     */
    const val MAX_TREND_BASIS_POINTS = 1_000_000

    /**
     * İki Long değerini taşmaya karşı koruyarak toplar.
     * Taşma durumunda sahte değer üretmek yerine fail-closed olarak [ArithmeticException] fırlatır.
     */
    fun safeAdd(a: Long, b: Long): Long {
        require(a >= 0L && b >= 0L) { "safeAdd yalnız negatif olmayan tutarları kabul eder: a=$a, b=$b" }
        val r = a + b
        if (r < 0L || ((a xor r) and (b xor r)) < 0L) {
            throw ArithmeticException("Long overflow in amount calculation: $a + $b")
        }
        return r
    }

    /**
     * Taşma korumalı, HALF_UP yuvarlamalı tamsayı oran (basis-point) hesaplayıcı.
     * Finansal hesaplarda Double veya Float kullanmaz.
     *
     * @param numerator Pay (>= 0)
     * @param denominator Payda (>= 0)
     * @param scale Ölçek katsayısı (varsayılan 10_000L = baz puan)
     * @param roundHalfUp Yarımları yukarı yuvarlama (>= 0.5 ise +1)
     * @param onZeroDenominator Payda sıfır olduğunda çağrılacak eylem (varsayılan 0L)
     */
    fun calculateProportionBasisPoints(
        numerator: Long,
        denominator: Long,
        scale: Long = SCALE_BASIS_POINTS,
        roundHalfUp: Boolean = true,
        onZeroDenominator: () -> Long = { 0L },
    ): Long {
        require(numerator >= 0L) { "Pay negatif olamaz: $numerator" }
        require(denominator >= 0L) { "Payda negatif olamaz: $denominator" }
        require(scale > 0L) { "Ölçek pozitif olmalıdır: $scale" }

        if (denominator == 0L) {
            return onZeroDenominator()
        }
        if (numerator == 0L) {
            return 0L
        }

        // Bölme algoritması: numerator = q * denominator + r
        // (numerator * scale + half) / denominator = q * scale + ((r * scale + half) / denominator)
        val q = numerator / denominator
        val r = numerator % denominator

        // q * scale taşma kontrolü
        if (q > Long.MAX_VALUE / scale) {
            throw ArithmeticException("Oran sonucu Long aralığını aşıyor: numerator=$numerator, denominator=$denominator")
        }
        val qScaled = q * scale

        // Kalan (r) her zaman < denominator'dır.
        val half = if (roundHalfUp) denominator / 2L else 0L

        // Matematiksel olarak kesin, overflow-safe 128-bit tamsayı mulDiv: (r * scale + half) / denominator
        val rFraction = if (r <= (Long.MAX_VALUE - half) / scale) {
            (r * scale + half) / denominator
        } else {
            val target = multiply64(r, scale) + half
            var lowB = 0L
            var highB = scale
            var ans = 0L
            while (lowB <= highB) {
                val mid = (lowB + highB) ushr 1
                val product = multiply64(mid, denominator)
                if (product <= target) {
                    ans = mid
                    lowB = mid + 1L
                } else {
                    highB = mid - 1L
                }
            }
            ans
        }

        return safeAdd(qScaled, rFraction)
    }

    private data class UInt128(val high: Long, val low: Long) : Comparable<UInt128> {
        override fun compareTo(other: UInt128): Int {
            val hComp = (high xor Long.MIN_VALUE).compareTo(other.high xor Long.MIN_VALUE)
            if (hComp != 0) return hComp
            return (low xor Long.MIN_VALUE).compareTo(other.low xor Long.MIN_VALUE)
        }

        operator fun plus(addend: Long): UInt128 {
            require(addend >= 0L) { "addend must be non-negative" }
            val newLow = low + addend
            val carry = if ((newLow xor Long.MIN_VALUE) < (low xor Long.MIN_VALUE)) 1L else 0L
            return UInt128(high = high + carry, low = newLow)
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

    /**
     * Long değerini Int aralığına güvenle dönüştürür; taşma halinde sessiz wrap yerine maxLimit'e kenetler.
     */
    fun safeLongToInt(value: Long, maxLimit: Int = Int.MAX_VALUE): Int {
        if (value <= 0L) return 0
        if (value >= maxLimit.toLong()) return maxLimit
        return value.toInt()
    }

    /**
     * İki tutar arasındaki payı 0..10_000 baz puan aralığında kesin olarak hesaplar.
     */
    fun calculateShareBasisPoints(part: Long, total: Long): Int {
        if (part <= 0L || total <= 0L) return 0
        val rawBp = calculateProportionBasisPoints(numerator = part, denominator = total)
        return safeLongToInt(rawBp, maxLimit = MAX_BASIS_POINTS)
    }

    /**
     * [YearMonth] değerinden mevcut ay ve bir önceki eşdeğer ay için [ReportPeriod] sınırlarını hesaplar.
     * Ayın 1. günü ile son günü tam sınır olarak dahil edilir; Ocak -> Aralık yıl geçişi korunur.
     */
    fun calculateReportPeriods(yearMonth: YearMonth): Pair<ReportPeriod, ReportPeriod> {
        val parts = yearMonth.value.split("-")
        require(parts.size == 2) { "Geçersiz YearMonth formatı: ${yearMonth.value}" }
        val year = parts[0].toInt()
        val month = parts[1].toInt()

        val currentStart = LocalDate(year, month, 1)
        val currentEnd = currentStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        val currentPeriod = ReportPeriod(startDate = currentStart, endDate = currentEnd)

        val prevYearMonth = yearMonth.previousMonth()
        val prevParts = prevYearMonth.value.split("-")
        val prevYear = prevParts[0].toInt()
        val prevMonth = prevParts[1].toInt()

        val prevStart = LocalDate(prevYear, prevMonth, 1)
        val prevEnd = prevStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        val prevPeriod = ReportPeriod(startDate = prevStart, endDate = prevEnd)

        return currentPeriod to prevPeriod
    }

    /**
     * Mevcut ve önceki dönem tutarları üzerinden kategori bazlı trend durumunu hesaplar.
     * Güvenli baz puan ölçeği (0..10_000) ve taşma korumalı tamsayı aritmetiği kullanır.
     */
    fun calculateTrend(
        categoryType: TransactionType,
        currentMinor: Long,
        prevMinor: Long,
    ): CategoryTrend {
        require(currentMinor >= 0L) { "Mevcut dönem tutarı negatif olamaz: $currentMinor" }
        require(prevMinor >= 0L) { "Önceki dönem tutarı negatif olamaz: $prevMinor" }

        if (currentMinor == 0L && prevMinor == 0L) {
            return CategoryTrend.None
        }
        if (prevMinor == 0L && currentMinor > 0L) {
            return CategoryTrend.New
        }
        if (currentMinor == 0L && prevMinor > 0L) {
            // %100 azalış
            return when (categoryType) {
                TransactionType.EXPENSE -> CategoryTrend.Changed(
                    changeBasisPoints = MAX_BASIS_POINTS,
                    movement = TrendMovement.DECREASED,
                    sentiment = TrendSentiment.POSITIVE, // Gider azaldı -> olumlu
                )
                TransactionType.INCOME -> CategoryTrend.Changed(
                    changeBasisPoints = MAX_BASIS_POINTS,
                    movement = TrendMovement.DECREASED,
                    sentiment = TrendSentiment.NEGATIVE, // Gelir azaldı -> olumsuz
                )
            }
        }
        if (currentMinor == prevMinor) {
            return CategoryTrend.Changed(
                changeBasisPoints = 0,
                movement = TrendMovement.UNCHANGED,
                sentiment = TrendSentiment.NEUTRAL,
            )
        }

        val isIncrease = currentMinor > prevMinor
        val diff = if (isIncrease) currentMinor - prevMinor else prevMinor - currentMinor

        val rawBasisPoints = calculateProportionBasisPoints(
            numerator = diff,
            denominator = prevMinor,
            scale = SCALE_BASIS_POINTS,
            roundHalfUp = true,
        )

        // Trend basis point'i UI üst sınırında (MAX_TREND_BASIS_POINTS = 1_000_000, yani %10.000) clamp edilir
        val basisPoints = safeLongToInt(rawBasisPoints, maxLimit = MAX_TREND_BASIS_POINTS)
        val movement = if (isIncrease) TrendMovement.INCREASED else TrendMovement.DECREASED

        val sentiment = when (categoryType) {
            TransactionType.EXPENSE -> if (isIncrease) TrendSentiment.NEGATIVE else TrendSentiment.POSITIVE
            TransactionType.INCOME -> if (isIncrease) TrendSentiment.POSITIVE else TrendSentiment.NEGATIVE
        }

        return CategoryTrend.Changed(
            changeBasisPoints = basisPoints,
            movement = movement,
            sentiment = sentiment,
        )
    }

    /**
     * Kategori listesi, mevcut dönem işlemleri ve önceki dönem işlemleri üzerinden
     * analiz satırlarını ve özet kartı modelini üretir.
     */
    fun calculate(
        categories: List<Category>,
        currentTransactions: List<Transaction>,
        previousTransactions: List<Transaction>,
        selectedTypeFilter: TransactionType?,
        currency: Currency = Currency.TRY,
    ): Pair<CategoriesSummaryUiModel, List<CategorySpendingDisplayModel>> {
        // 1. Para birimi fail-closed doğrulaması
        for (tx in currentTransactions) {
            require(tx.amount.currency == currency) {
                "İşlem para birimi ($currency) ile uyuşmuyor: ${tx.amount.currency} (İşlem ID: ${tx.id.value})"
            }
        }
        for (tx in previousTransactions) {
            require(tx.amount.currency == currency) {
                "Önceki dönem işlem para birimi ($currency) ile uyuşmuyor: ${tx.amount.currency} (İşlem ID: ${tx.id.value})"
            }
        }

        val categoryMap = categories.associateBy { it.id }

        // 2. Kategori / işlem türü tutarlılığı doğrulaması
        for (tx in currentTransactions) {
            val matchingCategory = categoryMap[tx.categoryId]
            if (matchingCategory != null) {
                require(tx.type == matchingCategory.type) {
                    "İşlem türü (${tx.type}) ile kategori türü (${matchingCategory.type}) uyuşmuyor! (İşlem ID: ${tx.id.value}, Kategori: ${matchingCategory.name})"
                }
            }
        }
        for (tx in previousTransactions) {
            val matchingCategory = categoryMap[tx.categoryId]
            if (matchingCategory != null) {
                require(tx.type == matchingCategory.type) {
                    "Önceki dönem işlem türü (${tx.type}) ile kategori türü (${matchingCategory.type}) uyuşmuyor! (İşlem ID: ${tx.id.value}, Kategori: ${matchingCategory.name})"
                }
            }
        }

        // Kategori ID'sine göre işlemleri grupla ve filtrele.
        // Ürün ve mimari kararı: Güncel kategori analiz listesinde silinmiş/kayıp kategori satırı
        // gösterilmediği için, özet ve liste kapsamının birebir tutarlı olması amacıyla (özet toplamı ==
        // listelenen kategorilerin toplamı) kategori haritasında bulunmayan işlemler özet toplamına
        // sessizce dahil edilmez.
        val validCurrentTransactions = currentTransactions.filter { tx ->
            tx.categoryId != null && categoryMap.containsKey(tx.categoryId)
        }
        val currentTxByCat = validCurrentTransactions.groupBy { it.categoryId }
        val prevTxByCat = previousTransactions.filter { it.categoryId != null && categoryMap.containsKey(it.categoryId) }.groupBy { it.categoryId }

        // Filtreye uygun kategorileri belirle
        val filteredCategories = categories.filter { cat ->
            selectedTypeFilter == null || cat.type == selectedTypeFilter
        }

        var totalExpenseMinor = 0L
        var totalIncomeMinor = 0L

        // Gider ve gelir işlemlerinin toplamı (yalnız listelenen geçerli kategoriler, bağımsız ve taşma korumalı)
        for (tx in validCurrentTransactions) {
            val matchingCategory = categoryMap[tx.categoryId]
            if (selectedTypeFilter != null && matchingCategory?.type != selectedTypeFilter) {
                continue
            }
            if (tx.type == TransactionType.EXPENSE) {
                totalExpenseMinor = safeAdd(totalExpenseMinor, tx.amount.amountMinor)
            } else if (tx.type == TransactionType.INCOME) {
                totalIncomeMinor = safeAdd(totalIncomeMinor, tx.amount.amountMinor)
            }
        }

        // Özet kartı hedef türü: INCOME filtresinde INCOME, EXPENSE veya Tümü filtresinde EXPENSE
        val targetType = if (selectedTypeFilter == TransactionType.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
        var topCategoryName: String? = null
        var maxTargetMinor = 0L

        val displayItems = mutableListOf<CategorySpendingDisplayModel>()

        for (category in filteredCategories) {
            val curList = currentTxByCat[category.id] ?: emptyList()
            val prevList = prevTxByCat[category.id] ?: emptyList()

            var curAmountMinor = 0L
            for (tx in curList) {
                curAmountMinor = safeAdd(curAmountMinor, tx.amount.amountMinor)
            }

            var prevAmountMinor = 0L
            for (tx in prevList) {
                prevAmountMinor = safeAdd(prevAmountMinor, tx.amount.amountMinor)
            }

            val currentMoney = Money(curAmountMinor, currency)
            val previousMoney = Money(prevAmountMinor, currency)

            val trend = calculateTrend(
                categoryType = category.type,
                currentMinor = curAmountMinor,
                prevMinor = prevAmountMinor,
            )

            // Hedef türdeki en yüksek kategoriyi tespit et
            if (category.type == targetType && curAmountMinor > maxTargetMinor) {
                maxTargetMinor = curAmountMinor
                topCategoryName = category.name
            }

            val displayModel = CategoryDisplayModel(
                id = category.id,
                name = category.name,
                type = category.type,
                colorHex = category.color.hex,
                iconKey = category.icon?.key,
                isDefault = category.isDefault,
            )

            displayItems.add(
                CategorySpendingDisplayModel(
                    category = displayModel,
                    transactionCount = curList.size,
                    currentPeriodAmount = currentMoney,
                    previousPeriodAmount = previousMoney,
                    formattedCurrentAmount = MoneyFormatter.format(currentMoney),
                    trend = trend,
                )
            )
        }

        // Sıralama kuralı:
        // 1. Tutar > 0 olanlar tutara göre azalan, eşitlikte ada göre alfabetik
        // 2. Tutar == 0 olanlar en sonda, ada göre alfabetik
        val sortedItems = displayItems.sortedWith { a, b ->
            val aAmount = a.currentPeriodAmount.amountMinor
            val bAmount = b.currentPeriodAmount.amountMinor
            when {
                aAmount > 0L && bAmount > 0L -> {
                    val cmp = bAmount.compareTo(aAmount)
                    if (cmp != 0) cmp else a.category.name.lowercase().compareTo(b.category.name.lowercase())
                }
                aAmount > 0L && bAmount == 0L -> -1
                aAmount == 0L && bAmount > 0L -> 1
                else -> a.category.name.lowercase().compareTo(b.category.name.lowercase())
            }
        }

        // En yüksek kategorinin ilgili toplamdaki payı (baz puan: 0..10_000)
        val totalTargetMinor = if (targetType == TransactionType.INCOME) totalIncomeMinor else totalExpenseMinor
        val topCategoryShareBasisPoints = calculateShareBasisPoints(part = maxTargetMinor, total = totalTargetMinor)

        // Mini bar chart oranları: Hedef türde hareket gören en yüksek kategoriler (0..10_000 aralığında)
        val activeItemsForBars = sortedItems.filter {
            it.category.type == targetType && it.currentPeriodAmount.amountMinor > 0L
        }.take(5)

        val miniBarProportionsBasisPoints = if (activeItemsForBars.isNotEmpty() && maxTargetMinor > 0L) {
            activeItemsForBars.map { item ->
                val share = calculateShareBasisPoints(part = item.currentPeriodAmount.amountMinor, total = maxTargetMinor)
                share.coerceIn(1_500, MAX_BASIS_POINTS)
            }
        } else {
            emptyList()
        }

        // Feniqo İçgörü metni türetimi
        val insightText = when {
            targetType == TransactionType.INCOME -> {
                if (topCategoryName != null && maxTargetMinor > 0L && totalIncomeMinor > 0L) {
                    val sharePercent = topCategoryShareBasisPoints / 100
                    "$topCategoryName bu ayki en yüksek gelir kalemin. Toplam gelirlerinin %$sharePercent'ini oluşturuyor."
                } else {
                    "Bu dönemde henüz gelir kaydı bulunmuyor."
                }
            }
            topCategoryName != null && maxTargetMinor > 0L && totalExpenseMinor > 0L -> {
                val sharePercent = topCategoryShareBasisPoints / 100
                "$topCategoryName bu ayki en yüksek harcaman. Toplam giderlerinin %$sharePercent'ini oluşturuyor."
            }
            totalIncomeMinor > 0L && totalExpenseMinor == 0L -> {
                "Bu ay harcamanız bulunmuyor. Gelirleriniz bütçenizi güçlendiriyor."
            }
            totalExpenseMinor == 0L && totalIncomeMinor == 0L -> {
                "Bu dönemde henüz işlem kaydı yok. Kategorilerinizi düzenleyerek harcamalarınızı kolayca takip edin."
            }
            else -> {
                "Harcamalarınız kategorileriniz arasında dengeli dağılmış görünüyor."
            }
        }

        val balanceMessage = when {
            totalExpenseMinor == 0L -> "Harcamaların dengede."
            totalIncomeMinor >= totalExpenseMinor -> "Gelir ve harcamaların dengede."
            else -> "Harcamalarını gözden geçirmelisin."
        }

        val totalCatCount = filteredCategories.size
        val customCatCount = filteredCategories.count { !it.isDefault }

        val summary = CategoriesSummaryUiModel(
            totalCategoriesCount = totalCatCount,
            customCategoriesCount = customCatCount,
            topCategoryName = topCategoryName,
            formattedTopCategoryAmount = if (maxTargetMinor > 0L) {
                MoneyFormatter.format(Money(maxTargetMinor, currency))
            } else {
                null
            },
            topCategoryType = targetType,
            topCategoryShareBasisPoints = topCategoryShareBasisPoints,
            miniBarProportionsBasisPoints = miniBarProportionsBasisPoints,
            insightText = insightText,
            balanceMessage = balanceMessage,
        )

        return summary to sortedItems
    }
}
