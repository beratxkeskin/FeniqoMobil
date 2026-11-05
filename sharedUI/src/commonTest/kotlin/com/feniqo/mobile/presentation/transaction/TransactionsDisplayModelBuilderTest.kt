package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.InstallmentInfo
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Merchant
import com.feniqo.mobile.domain.model.MerchantAlias
import com.feniqo.mobile.domain.model.MerchantMatchSource
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReceiptPath
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionMerchantInfo
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.usecase.StarterMerchantCatalog
import com.feniqo.mobile.presentation.component.MerchantLogoFallbackResolver
import com.feniqo.mobile.presentation.component.MerchantLogoVisualKind
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionsDisplayModelBuilderTest {
    private val today = LocalDate(2026, 8, 21)
    private val yesterday = LocalDate(2026, 8, 20)
    private val pastDate = LocalDate(2026, 8, 15)

    private val cat1 =
        Category(
            id = EntityId("cat-1"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Market",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#4CAF50"),
            icon = CategoryIcon("shopping"),
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )

    private val catDeleted =
        Category(
            id = EntityId("cat-deleted"),
            ownerId = EntityId("user-1"),
            workspaceId = null,
            name = "Eski Kategori",
            type = TransactionType.EXPENSE,
            color = CategoryColor("#E91E63"),
            icon = CategoryIcon("archive"),
            isDefault = false,
            createdAt = Instant.parse("2026-08-01T00:00:00Z"),
        )

    @Test
    fun build_withEmptyTransactions_returnsEmptyList() {
        val result = TransactionsDisplayModelBuilder.build(emptyList(), listOf(cat1), today)
        assertTrue(result.isEmpty())
    }

    @Test
    fun build_groupsByDateDescending_andFormatsDateHeadersCorrectly() {
        val trx1 = createTrx("t1", pastDate, Instant.parse("2026-08-15T10:00:00Z"))
        val trx2 = createTrx("t2", today, Instant.parse("2026-08-21T10:00:00Z"))
        val trx3 = createTrx("t3", yesterday, Instant.parse("2026-08-20T10:00:00Z"))

        val result = TransactionsDisplayModelBuilder.build(listOf(trx1, trx2, trx3), listOf(cat1), today)

        assertEquals(3, result.size)
        assertEquals(today, result[0].date)
        assertEquals("Bugün", result[0].formattedDate)
        assertEquals(yesterday, result[1].date)
        assertEquals("Dün", result[1].formattedDate)
        assertEquals(pastDate, result[2].date)
        assertEquals("15 Ağustos 2026", result[2].formattedDate)
    }

    @Test
    fun build_sortsSameDateTransactionsByCreatedAtDescending_andDeterministicallyById() {
        val trx1 = createTrx("t1", today, Instant.parse("2026-08-21T09:00:00Z"))
        val trx2 = createTrx("t2", today, Instant.parse("2026-08-21T12:00:00Z"))
        val trx3 = createTrx("t3", today, Instant.parse("2026-08-21T09:00:00Z"))

        val result = TransactionsDisplayModelBuilder.build(listOf(trx1, trx2, trx3), listOf(cat1), today)

        assertEquals(1, result.size)
        val items = result[0].items
        assertEquals(3, items.size)
        assertEquals(EntityId("t2"), items[0].id)
        assertEquals(EntityId("t1"), items[1].id)
        assertEquals(EntityId("t3"), items[2].id)
    }

    @Test
    fun build_formatsAmounts_withSignsAndCurrenciesCorrectly() {
        val expense =
            createTrx(
                id = "t1",
                date = today,
                type = TransactionType.EXPENSE,
                amount = Money(150000L, Currency.TRY),
            )
        val income =
            createTrx(
                id = "t2",
                date = today,
                type = TransactionType.INCOME,
                amount = Money(500000L, Currency.USD),
            )

        val result = TransactionsDisplayModelBuilder.build(listOf(expense, income), listOf(cat1), today)
        val items = result[0].items

        assertEquals("-1.500,00 ₺", items.first { it.id.value == "t1" }.formattedAmount)
        assertEquals("+5.000,00 $", items.first { it.id.value == "t2" }.formattedAmount)
    }

    @Test
    fun build_resolvesActiveAndDeletedCategoriesFromHistory() {
        val trx1 = createTrx(id = "t1", categoryId = "cat-1")
        val trx2 = createTrx(id = "t2", categoryId = "cat-deleted")
        val trx3 = createTrx(id = "t3", categoryId = "cat-unknown")

        val result =
            TransactionsDisplayModelBuilder.build(
                transactions = listOf(trx1, trx2, trx3),
                categoryHistory = listOf(cat1, catDeleted),
                today = today,
            )
        val items = result[0].items

        val item1 = items.first { it.id.value == "t1" }
        assertEquals("Market", item1.categoryName)
        assertEquals("#4CAF50", item1.categoryColorHex)
        assertEquals("shopping", item1.categoryIconKey)

        val item2 = items.first { it.id.value == "t2" }
        assertEquals("Eski Kategori", item2.categoryName)
        assertEquals("#E91E63", item2.categoryColorHex)
        assertEquals("archive", item2.categoryIconKey)

        val item3 = items.first { it.id.value == "t3" }
        assertEquals("Bilinmeyen kategori", item3.categoryName)
        assertNull(item3.categoryColorHex)
        assertNull(item3.categoryIconKey)
    }

    @Test
    fun build_handlesReceiptAndInstallmentBadges() {
        val withReceipt =
            createTrx(
                id = "t1",
                receiptPath = ReceiptPath("receipts/img1.jpg"),
                installment = InstallmentInfo(1, 3, EntityId("grp-1")),
            )
        val withoutReceipt =
            createTrx(
                id = "t2",
                receiptPath = null,
                installment = null,
            )

        val result =
            TransactionsDisplayModelBuilder.build(
                transactions = listOf(withReceipt, withoutReceipt),
                categoryHistory = listOf(cat1),
                today = today,
            )
        val items = result[0].items

        val item1 = items.first { it.id.value == "t1" }
        assertTrue(item1.hasReceipt)
        assertEquals("1/3", item1.installment?.badgeText)
        assertEquals(1, item1.installment?.number)
        assertEquals(3, item1.installment?.total)

        val item2 = items.first { it.id.value == "t2" }
        assertFalse(item2.hasReceipt)
        assertNull(item2.installment)
    }

    @Test
    fun build_withSortOrderOldest_sortsDatesAscending() {
        val trxPast = createTrx("t1", pastDate, Instant.parse("2026-08-15T10:00:00Z"))
        val trxToday = createTrx("t2", today, Instant.parse("2026-08-21T10:00:00Z"))
        val trxYesterday = createTrx("t3", yesterday, Instant.parse("2026-08-20T10:00:00Z"))

        val result =
            TransactionsDisplayModelBuilder.build(
                transactions = listOf(trxToday, trxPast, trxYesterday),
                categoryHistory = listOf(cat1),
                today = today,
                sortOrder = TransactionSortOrder.OLDEST,
            )

        assertEquals(3, result.size)
        assertEquals(pastDate, result[0].date)
        assertEquals(yesterday, result[1].date)
        assertEquals(today, result[2].date)
    }

    @Test
    fun build_withSortOrderAmountDesc_sortsAmountsDescendingWithinDateGroups() {
        val trxSmall = createTrx("tSmall", today, amount = Money(1000L, Currency.TRY))
        val trxLarge = createTrx("tLarge", today, amount = Money(50000L, Currency.TRY))
        val trxMedium = createTrx("tMedium", today, amount = Money(20000L, Currency.TRY))

        val result =
            TransactionsDisplayModelBuilder.build(
                transactions = listOf(trxSmall, trxLarge, trxMedium),
                categoryHistory = listOf(cat1),
                today = today,
                sortOrder = TransactionSortOrder.AMOUNT_DESC,
            )

        assertEquals(1, result.size)
        val items = result[0].items
        assertEquals(EntityId("tLarge"), items[0].id)
        assertEquals(EntityId("tMedium"), items[1].id)
        assertEquals(EntityId("tSmall"), items[2].id)
    }

    @Test
    fun build_withSortOrderAmountAsc_sortsAmountsAscendingWithinDateGroups() {
        val trxSmall = createTrx("tSmall", today, amount = Money(1000L, Currency.TRY))
        val trxLarge = createTrx("tLarge", today, amount = Money(50000L, Currency.TRY))
        val trxMedium = createTrx("tMedium", today, amount = Money(20000L, Currency.TRY))

        val result =
            TransactionsDisplayModelBuilder.build(
                transactions = listOf(trxLarge, trxSmall, trxMedium),
                categoryHistory = listOf(cat1),
                today = today,
                sortOrder = TransactionSortOrder.AMOUNT_ASC,
            )

        assertEquals(1, result.size)
        val items = result[0].items
        assertEquals(EntityId("tSmall"), items[0].id)
        assertEquals(EntityId("tMedium"), items[1].id)
        assertEquals(EntityId("tLarge"), items[2].id)
    }

    @Test
    fun build_calculatesDailyNetCorrectly_andHandlesMultiCurrencyAsNull() {
        // Gider: 100 TL, Gelir: 300 TL -> Net: +200 TL
        val expense = createTrx("t1", today, type = TransactionType.EXPENSE, amount = Money(10000L, Currency.TRY))
        val income = createTrx("t2", today, type = TransactionType.INCOME, amount = Money(30000L, Currency.TRY))

        val result =
            TransactionsDisplayModelBuilder.build(
                transactions = listOf(expense, income),
                categoryHistory = listOf(cat1),
                today = today,
            )

        assertEquals(1, result.size)
        assertEquals("+200,00 ₺", result[0].dailyNetFormatted)
        assertFalse(result[0].isDailyNetNegative)

        // Farklı para birimi içeren grup -> fail-closed null
        val usdTrx = createTrx("t3", yesterday, type = TransactionType.INCOME, amount = Money(5000L, Currency.USD))
        val tryTrx = createTrx("t4", yesterday, type = TransactionType.EXPENSE, amount = Money(5000L, Currency.TRY))

        val multiCurrencyResult =
            TransactionsDisplayModelBuilder.build(
                transactions = listOf(usdTrx, tryTrx),
                categoryHistory = listOf(cat1),
                today = today,
            )

        assertEquals(1, multiCurrencyResult.size)
        assertNull(multiCurrencyResult[0].dailyNetFormatted)
    }

    private val testMerchantWithLogo =
        Merchant(
            id = "test_verified_brand",
            displayName = "Doğrulanmış Marka",
            aliases = listOf(MerchantAlias("DOĞRULANMIŞ MARKA")),
            categoryIconKey = CategoryIcon("shopping"),
            verifiedLogoKey = "verified_brand_logo_key",
            catalogVersion = 10,
        )
    private val testMerchantWithoutLogo =
        Merchant(
            id = "test_no_logo_brand",
            displayName = "Logosuz Marka",
            aliases = listOf(MerchantAlias("LOGOSUZ MARKA")),
            categoryIconKey = CategoryIcon("shopping"),
            verifiedLogoKey = null,
            catalogVersion = 10,
        )
    private val testCatalog = listOf(testMerchantWithLogo, testMerchantWithoutLogo)

    @Test
    fun buildItem_nullMerchantInfo_returnsNullMerchantDisplay() {
        val trx = createTrx("t1", merchantInfo = null)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)
        assertNull(item.merchant)
    }

    @Test
    fun buildItem_knownMerchantWithConfidence75_resolvesMerchantDisplayWithLogo() {
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_verified_brand"),
                matchSource = MerchantMatchSource.STRONG_BRAND,
                confidence = 75,
                catalogVersion = 8,
            )
        val trx = createTrx("t1", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

        val merchant = item.merchant
        assertNotNull(merchant)
        assertEquals(EntityId("test_verified_brand"), merchant.merchantId)
        assertEquals("Doğrulanmış Marka", merchant.displayName)
        assertEquals("verified_brand_logo_key", merchant.verifiedLogoKey)
        assertTrue(merchant.canShowMerchantLogo)
    }

    @Test
    fun buildItem_knownMerchantWithConfidence100_resolvesMerchantDisplayWithLogo() {
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_verified_brand"),
                matchSource = MerchantMatchSource.USER_VERIFIED,
                confidence = 100,
                catalogVersion = 8,
            )
        val trx = createTrx("t1", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

        val merchant = item.merchant
        assertNotNull(merchant)
        assertEquals(EntityId("test_verified_brand"), merchant.merchantId)
        assertEquals("Doğrulanmış Marka", merchant.displayName)
        assertEquals("verified_brand_logo_key", merchant.verifiedLogoKey)
        assertTrue(merchant.canShowMerchantLogo)
    }

    @Test
    fun buildItem_confidenceBelow75_doesNotLeakMerchantIdentityToPresentation() {
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_verified_brand"),
                matchSource = MerchantMatchSource.SHORT_ALIAS,
                confidence = 74,
                catalogVersion = 8,
            )
        val trx = createTrx("t1", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

        assertNull(item.merchant)
    }

    @Test
    fun buildItem_unknownMerchantId_returnsNullMerchantDisplaySafely() {
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("non_existent_merchant"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 1,
            )
        val trx = createTrx("t1", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

        assertNull(item.merchant)
    }

    @Test
    fun buildItem_futureV11CatalogVersionAgainstV10Catalog_returnsNullMerchantDisplaySafely() {
        // transaction catalogVersion (11) > current merchant catalogVersion (10)
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_verified_brand"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 11,
            )
        val trx = createTrx("t1", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

        assertNull(item.merchant)
    }

    @Test
    fun buildItem_v1AttributionOlderThanV10Catalog_resolvesWithCurrentCatalogRecord() {
        // transaction catalogVersion (1) <= current merchant catalogVersion (10)
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_verified_brand"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 1,
            )
        val trx = createTrx("t1", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

        val merchant = item.merchant
        assertNotNull(merchant)
        assertEquals("Doğrulanmış Marka", merchant.displayName)
        assertEquals("verified_brand_logo_key", merchant.verifiedLogoKey)
        assertTrue(merchant.canShowMerchantLogo)
    }

    @Test
    fun buildItem_v2AttributionOlderThanV10Catalog_resolvesWithCurrentCatalogRecord() {
        // transaction catalogVersion (2) <= current merchant catalogVersion (10)
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_verified_brand"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 2,
            )
        val trx = createTrx("t1", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

        val merchant = item.merchant
        assertNotNull(merchant)
        assertEquals("Doğrulanmış Marka", merchant.displayName)
        assertEquals("verified_brand_logo_key", merchant.verifiedLogoKey)
        assertTrue(merchant.canShowMerchantLogo)
    }

    @Test
    fun buildItem_v3AttributionOlderThanV10Catalog_resolvesWithCurrentCatalogRecord() {
        // transaction catalogVersion (3) <= current merchant catalogVersion (10)
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_verified_brand"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 3,
            )
        val trx = createTrx("t1", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

        val merchant = item.merchant
        assertNotNull(merchant)
        assertEquals("Doğrulanmış Marka", merchant.displayName)
        assertEquals("verified_brand_logo_key", merchant.verifiedLogoKey)
        assertTrue(merchant.canShowMerchantLogo)
    }

    @Test
    fun buildItem_v4AttributionOlderThanV10Catalog_resolvesWithCurrentCatalogRecord() {
        // transaction catalogVersion (4) <= current merchant catalogVersion (10)
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_verified_brand"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 4,
            )
        val trx = createTrx("t1", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

        val merchant = item.merchant
        assertNotNull(merchant)
        assertEquals("Doğrulanmış Marka", merchant.displayName)
        assertEquals("verified_brand_logo_key", merchant.verifiedLogoKey)
        assertTrue(merchant.canShowMerchantLogo)
    }

    @Test
    fun buildItem_v5AttributionOlderThanV10Catalog_resolvesWithCurrentCatalogRecord() {
        // transaction catalogVersion (5) <= current merchant catalogVersion (10)
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_verified_brand"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 5,
            )
        val trx = createTrx("t1", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

        val merchant = item.merchant
        assertNotNull(merchant)
        assertEquals("Doğrulanmış Marka", merchant.displayName)
        assertEquals("verified_brand_logo_key", merchant.verifiedLogoKey)
        assertTrue(merchant.canShowMerchantLogo)
        assertEquals(5, info.catalogVersion)
        assertEquals(10, testMerchantWithLogo.catalogVersion)
    }

    @Test
    fun buildItem_v8AttributionOlderThanV10Catalog_resolvesWithCurrentCatalogRecord() {
        // transaction catalogVersion (8) <= current merchant catalogVersion (10)
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_verified_brand"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 8,
            )
        val trx = createTrx("t1", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

        val merchant = item.merchant
        assertNotNull(merchant)
        assertEquals("Doğrulanmış Marka", merchant.displayName)
        assertEquals("verified_brand_logo_key", merchant.verifiedLogoKey)
        assertTrue(merchant.canShowMerchantLogo)
        assertEquals(8, info.catalogVersion)
        assertEquals(10, testMerchantWithLogo.catalogVersion)
    }

    @Test
    fun buildItem_v9AttributionOlderThanV10Catalog_resolvesWithCurrentCatalogRecord() {
        // transaction catalogVersion (9) <= current merchant catalogVersion (10)
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_verified_brand"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 9,
            )
        val trx = createTrx("t1", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

        val merchant = item.merchant
        assertNotNull(merchant)
        assertEquals("Doğrulanmış Marka", merchant.displayName)
        assertEquals("verified_brand_logo_key", merchant.verifiedLogoKey)
        assertTrue(merchant.canShowMerchantLogo)
        assertEquals(9, info.catalogVersion)
        assertEquals(10, testMerchantWithLogo.catalogVersion)
    }

    @Test
    fun buildItem_v6AttributionOlderThanV10Catalog_resolvesWithCurrentCatalogRecord() {
        // transaction catalogVersion (6) <= current merchant catalogVersion (10)
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_verified_brand"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 6,
            )
        val trx = createTrx("t1", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

        val merchant = item.merchant
        assertNotNull(merchant)
        assertEquals("Doğrulanmış Marka", merchant.displayName)
        assertEquals("verified_brand_logo_key", merchant.verifiedLogoKey)
        assertTrue(merchant.canShowMerchantLogo)
        assertEquals(6, info.catalogVersion)
        assertEquals(10, testMerchantWithLogo.catalogVersion)
    }

    @Test
    fun buildItem_olderOrEqualCatalogVersion_resolvesWithCurrentCatalogRecord() {
        // transaction catalogVersion (1..10) <= current merchant catalogVersion (10)
        for (historicalVersion in 1..10) {
            val info =
                TransactionMerchantInfo(
                    merchantId = EntityId("test_verified_brand"),
                    matchSource = MerchantMatchSource.EXACT_ALIAS,
                    confidence = 95,
                    catalogVersion = historicalVersion,
                )
            val trx = createTrx("t1", merchantInfo = info)
            val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

            val merchant = item.merchant
            assertNotNull(merchant)
            assertEquals("Doğrulanmış Marka", merchant.displayName)
            assertEquals("verified_brand_logo_key", merchant.verifiedLogoKey)
            assertTrue(merchant.canShowMerchantLogo)
        }
    }

    @Test
    fun buildItem_knownMerchantWithNullVerifiedLogoKey_preservesDisplayNameAndExposesNullKey() {
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_no_logo_brand"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 8,
            )
        val trx = createTrx("t1", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

        val merchant = item.merchant
        assertNotNull(merchant)
        assertEquals("Logosuz Marka", merchant.displayName)
        assertNull(merchant.verifiedLogoKey)
        assertTrue(merchant.canShowMerchantLogo)
    }

    @Test
    fun buildItem_verifiedLogoKeyFromCatalog_isCarriedCorrectly() {
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_verified_brand"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 8,
            )
        val trx = createTrx("t1", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, cat1, catalog = testCatalog)

        assertEquals("verified_brand_logo_key", item.merchant?.verifiedLogoKey)
    }

    @Test
    fun buildItem_realStarterMerchant_istanbulkart_preservesDisplayNameAndExposesNullKey() {
        val istanbulkart = StarterMerchantCatalog.merchants.first { it.id == "istanbulkart" }
        assertEquals("İstanbulkart", istanbulkart.displayName)
        assertNull(istanbulkart.verifiedLogoKey)
        assertEquals(10, istanbulkart.catalogVersion)

        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("istanbulkart"),
                matchSource = MerchantMatchSource.USER_VERIFIED,
                confidence = 100,
                catalogVersion = 8,
            )
        val transCat = cat1.copy(icon = CategoryIcon("transportation"))
        val trx = createTrx("t-ik", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, transCat, catalog = StarterMerchantCatalog.merchants)

        val merchant = item.merchant
        assertNotNull(merchant)
        assertEquals("İstanbulkart", merchant.displayName)
        assertNull(merchant.verifiedLogoKey)
        assertTrue(merchant.canShowMerchantLogo)

        val fallbackDecision =
            MerchantLogoFallbackResolver.resolve(
                verifiedLogoKey = merchant.verifiedLogoKey,
                packagedLogoAvailable = false,
                categoryIconKey = transCat.icon?.key,
                fallbackLabel = merchant.displayName,
            )
        assertEquals(MerchantLogoVisualKind.CATEGORY_ICON, fallbackDecision.kind)
        assertNotNull(fallbackDecision.iconVector)
        assertNull(fallbackDecision.logoKey)
    }

    @Test
    fun buildItem_realStarterMerchant_ajet_preservesDisplayNameAndExposesNullKey() {
        val ajet = StarterMerchantCatalog.merchants.first { it.id == "ajet" }
        assertEquals("AJet", ajet.displayName)
        assertNull(ajet.verifiedLogoKey)
        assertEquals(10, ajet.catalogVersion)

        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("ajet"),
                matchSource = MerchantMatchSource.USER_VERIFIED,
                confidence = 100,
                catalogVersion = 8,
            )
        val travelCat = cat1.copy(icon = CategoryIcon("travel"))
        val trx = createTrx("t-ajet", merchantInfo = info)
        val item = TransactionsDisplayModelBuilder.buildItem(trx, travelCat, catalog = StarterMerchantCatalog.merchants)

        val merchant = item.merchant
        assertNotNull(merchant)
        assertEquals("AJet", merchant.displayName)
        assertNull(merchant.verifiedLogoKey)
        assertTrue(merchant.canShowMerchantLogo)

        val fallbackDecision =
            MerchantLogoFallbackResolver.resolve(
                verifiedLogoKey = merchant.verifiedLogoKey,
                packagedLogoAvailable = false,
                categoryIconKey = travelCat.icon?.key,
                fallbackLabel = merchant.displayName,
            )
        assertEquals(MerchantLogoVisualKind.CATEGORY_ICON, fallbackDecision.kind)
        assertNotNull(fallbackDecision.iconVector)
        assertNull(fallbackDecision.logoKey)
    }

    @Test
    fun buildItem_missingCategory_preservesUnavailableStateAndFallbackName() {
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_verified_brand"),
                matchSource = MerchantMatchSource.EXACT_ALIAS,
                confidence = 95,
                catalogVersion = 6,
            )
        val trx = createTrx("t1", categoryId = "cat-deleted", merchantInfo = info)
        val item =
            TransactionsDisplayModelBuilder.buildItem(
                transaction = trx,
                category = null,
                fallbackCategoryName = "Bilinmeyen kategori",
                catalog = testCatalog,
            )

        assertTrue(item.isCategoryUnavailable)
        assertEquals("Bilinmeyen kategori", item.categoryName)
        assertNull(item.categoryColorHex)
        assertNull(item.categoryIconKey)
        val merchant = item.merchant
        assertNotNull(merchant)
        assertEquals("Doğrulanmış Marka", merchant.displayName)
    }

    @Test
    fun allSurfacesProduceIdenticalMerchantDisplayResult_forSameTransaction() {
        val info =
            TransactionMerchantInfo(
                merchantId = EntityId("test_verified_brand"),
                matchSource = MerchantMatchSource.USER_VERIFIED,
                confidence = 100,
                catalogVersion = 6,
            )
        val trx = createTrx("tx-common", merchantInfo = info)

        // 1. Transactions list mapping
        val groupedList =
            TransactionsDisplayModelBuilder.build(
                transactions = listOf(trx),
                categoryHistory = listOf(cat1),
                today = today,
                catalog = testCatalog,
            )
        val listItem = groupedList.first().items.first()

        // 2. Direct single item mapping (Detay ve Başarı aynı buildItem yolunu kullanır)
        val detailItem =
            TransactionsDisplayModelBuilder.buildItem(
                transaction = trx,
                category = cat1,
                catalog = testCatalog,
            )

        val successItem =
            TransactionsDisplayModelBuilder.buildItem(
                transaction = trx,
                category = cat1,
                fallbackCategoryName = "",
                catalog = testCatalog,
            )

        assertEquals(listItem.merchant, detailItem.merchant)
        assertEquals(listItem.merchant, successItem.merchant)
        val merchant = listItem.merchant
        assertNotNull(merchant)
        assertEquals("test_verified_brand", merchant.merchantId.value)
        assertEquals("Doğrulanmış Marka", merchant.displayName)
        assertEquals("verified_brand_logo_key", merchant.verifiedLogoKey)
        assertEquals(true, merchant.canShowMerchantLogo)
    }

    private fun createTrx(
        id: String,
        date: LocalDate = today,
        createdAt: Instant = Instant.parse("2026-08-21T10:00:00Z"),
        type: TransactionType = TransactionType.EXPENSE,
        amount: Money = Money(10000L, Currency.TRY),
        categoryId: String = "cat-1",
        receiptPath: ReceiptPath? = null,
        installment: InstallmentInfo? = null,
        merchantInfo: TransactionMerchantInfo? = null,
    ) = Transaction(
        id = EntityId(id),
        ownerId = EntityId("user-1"),
        workspaceId = null,
        amount = amount,
        type = type,
        categoryId = EntityId(categoryId),
        description = "Açıklama $id",
        paymentMethod = PaymentMethod.CREDIT_CARD,
        transactionDate = date,
        receiptPath = receiptPath,
        installment = installment,
        createdAt = createdAt,
        merchantInfo = merchantInfo,
    )
}
