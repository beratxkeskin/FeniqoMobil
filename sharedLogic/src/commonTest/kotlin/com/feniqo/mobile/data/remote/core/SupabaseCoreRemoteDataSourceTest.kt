package com.feniqo.mobile.data.remote.core

import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionDto

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SupabaseCoreRemoteDataSourceTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun conditionalWrite_v1_uses_sync_write_v1_rpc_and_parameters(): Unit = runBlocking {
        var capturedPath: String? = null
        var capturedBody: String? = null
        val rpcResponseJson = """
            {
                "status": "APPLIED",
                "record": {
                    "id": "2d98a8d8-8e8a-a181-ca24-573d825edfbb",
                    "user_id": "fdbd49aa-640a-4ec5-9f1a-f348a949034c",
                    "workspace_id": null,
                    "name": "Market",
                    "slug": "market",
                    "type": "expense",
                    "color": "#EF4444",
                    "icon": "tag",
                    "is_default": false,
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 1
                }
            }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedBody = when (val b = request.body) {
                is OutgoingContent.ByteArrayContent -> b.bytes().decodeToString()
                is TextContent -> b.text
                else -> b.toString()
            }
            respond(
                content = rpcResponseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "sb_publishable_dummy_key_1234567890abcdef",
        ) {
            httpEngine = mockEngine
            install(Postgrest)
        }

        val dataSource = SupabaseCoreRemoteDataSource(client)

        val inputDto = CategoryDto(
            id = "2d98a8d8-8e8a-a181-ca24-573d825edfbb",
            userId = "fdbd49aa-640a-4ec5-9f1a-f348a949034c",
            name = "Market",
            type = "expense",
            color = "#EF4444",
            icon = "tag",
            isDefault = false,
            createdAt = "2026-08-25T17:00:00Z",
        )

        val result = dataSource.writeCategory(
            operation = RemoteWriteOperation.CREATE,
            baseVersion = null,
            dto = inputDto,
        )

        assertIs<ConditionalRemoteWriteResult.Applied<CategoryDto>>(result)
        assertEquals("2d98a8d8-8e8a-a181-ca24-573d825edfbb", result.record.id)
        assertEquals(1L, result.record.version)

        assertTrue(capturedPath?.contains("sync_write_v1") == true)
        val parsed = json.parseToJsonElement(checkNotNull(capturedBody)).jsonObject
        assertEquals("CATEGORY", parsed["p_entity_type"]?.jsonPrimitive?.content)
        assertEquals("CREATE", parsed["p_operation"]?.jsonPrimitive?.content)
        assertTrue(parsed.containsKey("p_payload"))
    }

    @Test
    fun idempotentConditionalWrite_v2_uses_sync_write_v2_and_p_operation_id(): Unit = runBlocking {
        var capturedPath: String? = null
        var capturedBody: String? = null
        val opId = "0123456789abcdef0123456789abcdef"
        val rpcResponseJson = """
            {
                "status": "APPLIED",
                "record": {
                    "id": "tx-123",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "amount_minor": 15000,
                    "currency": "TRY",
                    "type": "expense",
                    "category_id": "cat-1",
                    "description": "Öğle yemeği",
                    "payment_method": "credit_card",
                    "transaction_date": "2026-08-25",
                    "receipt_path": null,
                    "installment_number": null,
                    "total_installments": null,
                    "installment_group_id": null,
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 2
                }
            }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedBody = when (val b = request.body) {
                is OutgoingContent.ByteArrayContent -> b.bytes().decodeToString()
                is TextContent -> b.text
                else -> b.toString()
            }
            respond(
                content = rpcResponseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "sb_publishable_dummy_key_1234567890abcdef",
        ) {
            httpEngine = mockEngine
            install(Postgrest)
        }

        val dataSource = SupabaseCoreRemoteDataSource(client)

        val inputDto = TransactionDto(
            id = "tx-123",
            userId = "usr-1",
            amountMinor = 15000,
            currency = "TRY",
            type = "expense",
            categoryId = "cat-1",
            description = "Öğle yemeği",
            paymentMethod = "credit_card",
            transactionDate = "2026-08-25",
            createdAt = "2026-08-25T17:00:00Z",
        )

        val result = dataSource.writeTransaction(
            operationId = opId,
            operation = RemoteWriteOperation.UPDATE,
            baseVersion = 1L,
            dto = inputDto,
        )

        assertIs<ConditionalRemoteWriteResult.Applied<TransactionDto>>(result)
        assertEquals("tx-123", result.record.id)
        assertEquals(2L, result.record.version)

        assertTrue(capturedPath?.contains("sync_write_v2") == true)
        val parsed = json.parseToJsonElement(checkNotNull(capturedBody)).jsonObject
        assertEquals(opId, parsed["p_operation_id"]?.jsonPrimitive?.content)
        assertEquals("TRANSACTION", parsed["p_entity_type"]?.jsonPrimitive?.content)
        assertEquals("UPDATE", parsed["p_operation"]?.jsonPrimitive?.content)
        assertEquals("1", parsed["p_base_version"]?.jsonPrimitive?.content)
    }

    @Test
    fun idempotentConditionalWrite_v2_preserves_exact_operation_id_across_retries(): Unit = runBlocking {
        val capturedBodies = mutableListOf<String>()
        val opId = "abcdef0123456789abcdef0123456789"
        val rpcResponseJson = """
            {
                "status": "APPLIED",
                "record": {
                    "id": "tx-123",
                    "user_id": "usr-1",
                    "amount_minor": 15000,
                    "currency": "TRY",
                    "type": "expense",
                    "category_id": "cat-1",
                    "payment_method": "credit_card",
                    "transaction_date": "2026-08-25",
                    "created_at": "2026-08-25T17:00:00Z",
                    "version": 1
                }
            }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            val b = when (val body = request.body) {
                is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
                is TextContent -> body.text
                else -> body.toString()
            }
            capturedBodies.add(b)
            respond(
                content = rpcResponseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "sb_publishable_dummy_key_1234567890abcdef",
        ) {
            httpEngine = mockEngine
            install(Postgrest)
        }

        val dataSource = SupabaseCoreRemoteDataSource(client)

        val inputDto = TransactionDto(
            id = "tx-123",
            userId = "usr-1",
            amountMinor = 15000,
            currency = "TRY",
            type = "expense",
            categoryId = "cat-1",
            paymentMethod = "credit_card",
            transactionDate = "2026-08-25",
            createdAt = "2026-08-25T17:00:00Z",
        )

        // Attempt 1
        dataSource.writeTransaction(opId, RemoteWriteOperation.CREATE, null, inputDto)
        // Attempt 2 (mock retry with same opId)
        dataSource.writeTransaction(opId, RemoteWriteOperation.CREATE, null, inputDto)

        assertEquals(2, capturedBodies.size)
        val body1 = json.parseToJsonElement(capturedBodies[0]).jsonObject
        val body2 = json.parseToJsonElement(capturedBodies[1]).jsonObject

        assertEquals(opId, body1["p_operation_id"]?.jsonPrimitive?.content)
        assertEquals(opId, body2["p_operation_id"]?.jsonPrimitive?.content)
        assertEquals(body1["p_operation_id"], body2["p_operation_id"])
    }

    @Test
    fun idempotentConditionalWrite_v2_decodes_conflict_and_not_found(): Unit = runBlocking {
        val conflictJson = """
            {
                "status": "CONFLICT",
                "record": {
                    "id": "cat-1",
                    "name": "Sunucu Kategorisi",
                    "type": "expense",
                    "color": "#123456",
                    "created_at": "2026-08-25T17:00:00Z",
                    "version": 5
                }
            }
        """.trimIndent()

        val notFoundJson = """
            {
                "status": "NOT_FOUND",
                "record": null
            }
        """.trimIndent()

        var callCount = 0
        val mockEngine = MockEngine {
            val content = if (callCount == 0) conflictJson else notFoundJson
            callCount++
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "sb_publishable_dummy_key_1234567890abcdef",
        ) {
            httpEngine = mockEngine
            install(Postgrest)
        }

        val dataSource = SupabaseCoreRemoteDataSource(client)

        val inputDto = CategoryDto(
            id = "cat-1",
            name = "Yerel Kategori",
            type = "expense",
            color = "#123456",
            createdAt = "2026-08-25T17:00:00Z",
        )

        val conflictResult = dataSource.writeCategory("op-1", RemoteWriteOperation.UPDATE, 2L, inputDto)
        assertIs<ConditionalRemoteWriteResult.Conflict<CategoryDto>>(conflictResult)
        assertEquals(5L, conflictResult.remoteRecord.version)
        assertEquals("Sunucu Kategorisi", conflictResult.remoteRecord.name)

        val notFoundResult = dataSource.writeCategory("op-2", RemoteWriteOperation.DELETE, 2L, inputDto)
        assertIs<ConditionalRemoteWriteResult.NotFound>(notFoundResult)
    }

    @Test
    fun idempotentConditionalWrite_v2_budget_uses_sync_write_v2_rpc_and_parameters(): Unit = runBlocking {
        var capturedPath: String? = null
        var capturedBody: String? = null
        val rpcResponseJson = """
            {
                "status": "APPLIED",
                "record": {
                    "id": "bgt-1",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "category_id": "cat-1",
                    "month": "2026-08",
                    "limit_minor": 500000,
                    "currency": "TRY",
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 1
                }
            }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedBody = when (val b = request.body) {
                is OutgoingContent.ByteArrayContent -> b.bytes().decodeToString()
                is TextContent -> b.text
                else -> b.toString()
            }
            respond(
                content = rpcResponseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "sb_publishable_dummy_key_1234567890abcdef",
        ) {
            httpEngine = mockEngine
            install(Postgrest)
        }

        val dataSource = SupabaseCoreRemoteDataSource(client)

        val inputDto = BudgetDto(
            id = "bgt-1",
            userId = "usr-1",
            categoryId = "cat-1",
            month = "2026-08",
            limitMinor = 500000L,
            currency = "TRY",
            createdAt = "2026-08-25T17:00:00Z",
        )

        val result = dataSource.writeBudget(
            operationId = "op-bgt-1",
            operation = RemoteWriteOperation.CREATE,
            baseVersion = null,
            dto = inputDto,
        )

        assertIs<ConditionalRemoteWriteResult.Applied<BudgetDto>>(result)
        assertEquals("bgt-1", result.record.id)
        assertEquals(1L, result.record.version)
        assertEquals(500000L, result.record.limitMinor)

        assertTrue(capturedPath?.contains("sync_write_v2") == true)
        val parsed = json.parseToJsonElement(checkNotNull(capturedBody)).jsonObject
        assertEquals("op-bgt-1", parsed["p_operation_id"]?.jsonPrimitive?.content)
        assertEquals("BUDGET", parsed["p_entity_type"]?.jsonPrimitive?.content)
        assertEquals("CREATE", parsed["p_operation"]?.jsonPrimitive?.content)
    }

    @Test
    fun idempotentConditionalWrite_v2_recurring_transaction_uses_sync_write_v2_rpc_and_parameters(): Unit = runBlocking {
        var capturedPath: String? = null
        var capturedBody: String? = null
        val rpcResponseJson = """
            {
                "status": "APPLIED",
                "record": {
                    "id": "rec-1",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "amount_minor": 50000,
                    "currency": "TRY",
                    "type": "expense",
                    "category_id": "cat-1",
                    "description": "Internet",
                    "payment_method": "CREDIT_CARD",
                    "frequency": "MONTHLY",
                    "interval": 1,
                    "start_date": "2026-08-01",
                    "end_date": null,
                    "last_generated_date": null,
                    "is_active": true,
                    "created_at": "2026-08-01T10:00:00Z",
                    "updated_at": "2026-08-01T10:00:00Z",
                    "deleted_at": null,
                    "version": 1
                }
            }
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedBody = when (val b = request.body) {
                is OutgoingContent.ByteArrayContent -> b.bytes().decodeToString()
                is TextContent -> b.text
                else -> b.toString()
            }
            respond(
                content = rpcResponseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val client = createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "sb_publishable_dummy_key_1234567890abcdef",
        ) {
            httpEngine = mockEngine
            install(Postgrest)
        }

        val dataSource = SupabaseCoreRemoteDataSource(client)

        val inputDto = RecurringTransactionDto(
            id = "rec-1",
            userId = "usr-1",
            workspaceId = null,
            amountMinor = 50000L,
            currency = "TRY",
            type = "expense",
            categoryId = "cat-1",
            description = "Internet",
            paymentMethod = "CREDIT_CARD",
            frequency = "MONTHLY",
            interval = 1,
            startDate = "2026-08-01",
            endDate = null,
            lastGeneratedDate = null,
            isActive = true,
            createdAt = "2026-08-01T10:00:00Z",
        )

        val result = dataSource.writeRecurringTransaction(
            operationId = "op-rec-1",
            operation = RemoteWriteOperation.CREATE,
            baseVersion = null,
            dto = inputDto,
        )

        assertIs<ConditionalRemoteWriteResult.Applied<RecurringTransactionDto>>(result)
        assertEquals("rec-1", result.record.id)
        assertEquals(1L, result.record.version)
        assertEquals(50000L, result.record.amountMinor)

        assertTrue(capturedPath?.contains("sync_write_v2") == true)
        val parsed = json.parseToJsonElement(checkNotNull(capturedBody)).jsonObject
        assertEquals("op-rec-1", parsed["p_operation_id"]?.jsonPrimitive?.content)
        assertEquals("RECURRING_TRANSACTION", parsed["p_entity_type"]?.jsonPrimitive?.content)
        assertEquals("CREATE", parsed["p_operation"]?.jsonPrimitive?.content)
    }

    @Test
    fun fetchRecurringTransactions_queries_correct_endpoint_with_personal_scope_and_order(): Unit = runBlocking {
        var capturedPath: String? = null
        var capturedQuery: String? = null
        var capturedRangeHeader: String? = null

        val responseJson = """
            [
                {
                    "id": "rec-1",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "amount_minor": 50000,
                    "currency": "TRY",
                    "type": "expense",
                    "category_id": "cat-1",
                    "description": "Internet",
                    "payment_method": "CREDIT_CARD",
                    "frequency": "MONTHLY",
                    "interval": 1,
                    "start_date": "2026-08-01",
                    "end_date": null,
                    "last_generated_date": null,
                    "is_active": true,
                    "created_at": "2026-08-01T10:00:00Z",
                    "updated_at": "2026-08-01T10:00:00Z",
                    "deleted_at": null,
                    "version": 1
                }
            ]
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedQuery = request.url.encodedQuery
            capturedRangeHeader = request.headers["Range"]
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "Content-Range" to listOf("0-0/1"),
                ),
            )
        }

        val client = createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "sb_publishable_dummy_key_1234567890abcdef",
        ) {
            httpEngine = mockEngine
            install(Postgrest)
        }

        val dataSource = SupabaseCoreRemoteDataSource(client)

        val page = dataSource.fetchRecurringTransactions(
            RecurringTransactionRemoteQuery(
                page = RemotePageRequest(pageIndex = 0, pageSize = 20),
                workspaceScope = RemoteWorkspaceScope.Personal,
            ),
        )

        assertEquals(1, page.items.size)
        assertEquals("rec-1", page.items.first().id)
        assertEquals(1L, page.totalCount)
        assertTrue(capturedPath?.endsWith("/recurring_transactions") == true)
        assertTrue(capturedQuery?.contains("workspace_id=is.null") == true)
        assertTrue(capturedQuery?.contains("updated_at.asc") == true)
        assertTrue(capturedQuery?.contains("id.asc") == true)
        assertTrue(capturedRangeHeader == "0-19" || capturedQuery?.contains("limit=20") == true)
    }



    @Test
    fun fetchRecurringTransactions_with_cursor_applies_composite_filtering(): Unit = runBlocking {
        var capturedQuery: String? = null

        val responseJson = """
            [
                {
                    "id": "rec-1",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "amount_minor": 50000,
                    "currency": "TRY",
                    "type": "expense",
                    "category_id": "cat-1",
                    "description": "Internet",
                    "payment_method": "CREDIT_CARD",
                    "frequency": "MONTHLY",
                    "interval": 1,
                    "start_date": "2026-08-01",
                    "end_date": null,
                    "last_generated_date": null,
                    "is_active": true,
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 1
                },
                {
                    "id": "rec-2",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "amount_minor": 60000,
                    "currency": "TRY",
                    "type": "expense",
                    "category_id": "cat-1",
                    "description": "Kira",
                    "payment_method": "BANK_TRANSFER",
                    "frequency": "MONTHLY",
                    "interval": 1,
                    "start_date": "2026-08-01",
                    "end_date": null,
                    "last_generated_date": null,
                    "is_active": true,
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 1
                },
                {
                    "id": "rec-3",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "amount_minor": 70000,
                    "currency": "TRY",
                    "type": "expense",
                    "category_id": "cat-1",
                    "description": "Aidat",
                    "payment_method": "BANK_TRANSFER",
                    "frequency": "MONTHLY",
                    "interval": 1,
                    "start_date": "2026-08-01",
                    "end_date": null,
                    "last_generated_date": null,
                    "is_active": true,
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 1
                },
                {
                    "id": "rec-4",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "amount_minor": 80000,
                    "currency": "TRY",
                    "type": "expense",
                    "category_id": "cat-1",
                    "description": "Elektrik",
                    "payment_method": "CREDIT_CARD",
                    "frequency": "MONTHLY",
                    "interval": 1,
                    "start_date": "2026-08-01",
                    "end_date": null,
                    "last_generated_date": null,
                    "is_active": true,
                    "created_at": "2026-08-25T18:00:00Z",
                    "updated_at": "2026-08-25T18:00:00Z",
                    "deleted_at": null,
                    "version": 2
                }
            ]
        """.trimIndent()

        var capturedOrFilter: String? = null

        val mockEngine = MockEngine { request ->
            capturedOrFilter = request.url.parameters["or"]
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "Content-Range" to listOf("0-3/4"),
                ),
            )
        }

        val client = createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "sb_publishable_dummy_key_1234567890abcdef",
        ) {
            httpEngine = mockEngine
            install(Postgrest)
        }

        val dataSource = SupabaseCoreRemoteDataSource(client)

        val page = dataSource.fetchRecurringTransactions(
            RecurringTransactionRemoteQuery(
                updatedAfter = RemoteSyncCursor(
                    updatedAt = "2026-08-25T17:00:00Z",
                    entityId = "rec-2",
                ),
            ),
        )

        val orFilter = checkNotNull(capturedOrFilter)
        assertTrue(orFilter.contains("updated_at.gt.") && orFilter.contains("2026-08-25T17:00:00Z"))
        assertTrue(orFilter.contains("updated_at.eq.") && orFilter.contains("2026-08-25T17:00:00Z"))
        assertTrue(orFilter.contains("id.gt.rec-2"))
        assertEquals(listOf("rec-3", "rec-4"), page.items.map { it.id })
    }




    @Test
    fun fetchRecurringTransactions_includes_tombstone_records(): Unit = runBlocking {
        val responseJson = """
            [
                {
                    "id": "rec-deleted",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "amount_minor": 50000,
                    "currency": "TRY",
                    "type": "expense",
                    "category_id": "cat-1",
                    "description": "Eski Abonelik",
                    "payment_method": "CREDIT_CARD",
                    "frequency": "MONTHLY",
                    "interval": 1,
                    "start_date": "2026-08-01",
                    "end_date": null,
                    "last_generated_date": null,
                    "is_active": false,
                    "created_at": "2026-08-01T10:00:00Z",
                    "updated_at": "2026-08-25T17:05:00Z",
                    "deleted_at": "2026-08-25T17:05:00Z",
                    "version": 3
                }
            ]
        """.trimIndent()

        val mockEngine = MockEngine { _ ->
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "Content-Range" to listOf("0-0/1"),
                ),
            )
        }

        val client = createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "sb_publishable_dummy_key_1234567890abcdef",
        ) {
            httpEngine = mockEngine
            install(Postgrest)
        }

        val dataSource = SupabaseCoreRemoteDataSource(client)

        val page = dataSource.fetchRecurringTransactions(RecurringTransactionRemoteQuery())

        assertEquals(1, page.items.size)
        val item = page.items.first()
        assertEquals("rec-deleted", item.id)
        assertEquals("2026-08-25T17:05:00Z", item.deletedAt)
        assertEquals(3L, item.version)
    }
}


