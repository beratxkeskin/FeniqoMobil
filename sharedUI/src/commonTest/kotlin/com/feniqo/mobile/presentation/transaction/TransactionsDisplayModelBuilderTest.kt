package com.feniqo.mobile.presentation.transaction

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CategoryColor
import com.feniqo.mobile.domain.model.CategoryIcon
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.InstallmentInfo
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.ReceiptPath
import com.feniqo.mobile.domain.model.Transaction
import com.feniqo.mobile.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TransactionsDisplayModelBuilderTest {

    private val today = LocalDate(2026, 8, 21)
    private val yesterday = LocalDate(2026, 8, 20)
    private val pastDate = LocalDate(2026, 8, 15)

    private val cat1 = Category(
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

    private val catDeleted = Category(
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
        val expense = createTrx(
            id = "t1",
            date = today,
            type = TransactionType.EXPENSE,
            amount = Money(150000L, Currency.TRY),
        )
        val income = createTrx(
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

        val result = TransactionsDisplayModelBuilder.build(
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
        val withReceipt = createTrx(
            id = "t1",
            receiptPath = ReceiptPath("receipts/img1.jpg"),
            installment = InstallmentInfo(1, 3, EntityId("grp-1")),
        )
        val withoutReceipt = createTrx(
            id = "t2",
            receiptPath = null,
            installment = null,
        )

        val result = TransactionsDisplayModelBuilder.build(
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

    private fun createTrx(
        id: String,
        date: LocalDate = today,
        createdAt: Instant = Instant.parse("2026-08-21T10:00:00Z"),
        type: TransactionType = TransactionType.EXPENSE,
        amount: Money = Money(10000L, Currency.TRY),
        categoryId: String = "cat-1",
        receiptPath: ReceiptPath? = null,
        installment: InstallmentInfo? = null,
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
    )
}
