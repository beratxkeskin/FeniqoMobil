package com.feniqo.mobile.data.remote.core

import com.feniqo.mobile.domain.model.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteQueryModelsTest {

    @Test
    fun calculates_inclusive_postgrest_ranges() {
        assertEquals(0L..49L, RemotePageRequest().range)
        assertEquals(50L..99L, RemotePageRequest(pageIndex = 1).range)
        assertEquals(40L..59L, RemotePageRequest(pageIndex = 2, pageSize = 20).range)
    }

    @Test
    fun rejects_unbounded_page_sizes() {
        assertFailsWith<IllegalArgumentException> { RemotePageRequest(pageSize = 0) }
        assertFailsWith<IllegalArgumentException> { RemotePageRequest(pageSize = 101) }
    }

    @Test
    fun validates_transaction_date_order() {
        assertFailsWith<IllegalArgumentException> {
            TransactionRemoteQuery(
                fromDate = LocalDate.parse("2026-08-13"),
                toDate = LocalDate.parse("2026-08-01"),
            )
        }
    }

    @Test
    fun reports_next_page_from_exact_count() {
        val first = RemotePage(List(50) { it }, RemotePageRequest(), totalCount = 51)
        val last = RemotePage(listOf(50), RemotePageRequest(pageIndex = 1), totalCount = 51)

        assertTrue(first.hasNextPage)
        assertFalse(last.hasNextPage)
    }

    @Test
    fun default_recurring_transaction_query_has_correct_defaults() {
        val query = RecurringTransactionRemoteQuery()
        assertEquals(0, query.page.pageIndex)
        assertEquals(RemotePageRequest.DEFAULT_PAGE_SIZE, query.page.pageSize)
        assertEquals(RemoteWorkspaceScope.All, query.workspaceScope)
        assertEquals(null, query.updatedAfter)
    }
}
