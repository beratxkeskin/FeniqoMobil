package com.feniqo.mobile.data.remote.core

import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.PaymentMethod
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.YearMonth
import kotlin.time.Instant

data class RemotePageRequest(
    val pageIndex: Int = 0,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    init {
        require(pageIndex >= 0) { "Sayfa indeksi negatif olamaz." }
        require(pageSize in 1..MAX_PAGE_SIZE) { "Sayfa boyutu 1..$MAX_PAGE_SIZE aralığında olmalıdır." }
    }

    val range: LongRange
        get() {
            val first = pageIndex.toLong() * pageSize
            return first..(first + pageSize - 1)
        }

    companion object {
        const val DEFAULT_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 100
    }
}

data class RemotePage<T>(
    val items: List<T>,
    val request: RemotePageRequest,
    val totalCount: Long?,
) {
    val hasNextPage: Boolean = totalCount
        ?.let { request.range.last + 1 < it }
        ?: (items.size == request.pageSize)
}

/** Aynı updated_at değerindeki kayıtları kaçırmamak için id ile birlikte taşınan pull cursor'u. */
data class RemoteSyncCursor(
    val updatedAt: String,
    val entityId: String,
) {
    init {
        require(updatedAt.isNotBlank()) { "Sync cursor updatedAt alanı boş olamaz." }
        require(entityId.isNotBlank()) { "Sync cursor entityId alanı boş olamaz." }
        runCatching { Instant.parse(updatedAt) }
            .getOrElse { throw IllegalArgumentException("Sync cursor updatedAt geçerli bir UTC zaman damgası olmalıdır.", it) }
    }
}

sealed interface RemoteWorkspaceScope {
    data object All : RemoteWorkspaceScope
    data object Personal : RemoteWorkspaceScope
    data class Workspace(val id: EntityId) : RemoteWorkspaceScope
}

data class CategoryRemoteQuery(
    val page: RemotePageRequest = RemotePageRequest(),
    val type: TransactionType? = null,
    val workspaceScope: RemoteWorkspaceScope = RemoteWorkspaceScope.All,
    val updatedAfter: RemoteSyncCursor? = null,
)

data class TransactionRemoteQuery(
    val page: RemotePageRequest = RemotePageRequest(),
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
    val type: TransactionType? = null,
    val categoryId: EntityId? = null,
    val paymentMethod: PaymentMethod? = null,
    val workspaceScope: RemoteWorkspaceScope = RemoteWorkspaceScope.All,
    val updatedAfter: RemoteSyncCursor? = null,
) {
    init {
        require(fromDate == null || toDate == null || fromDate <= toDate) {
            "Başlangıç tarihi bitiş tarihinden sonra olamaz."
        }
    }
}

data class BudgetRemoteQuery(
    val page: RemotePageRequest = RemotePageRequest(),
    val month: YearMonth? = null,
    val workspaceScope: RemoteWorkspaceScope = RemoteWorkspaceScope.All,
)
