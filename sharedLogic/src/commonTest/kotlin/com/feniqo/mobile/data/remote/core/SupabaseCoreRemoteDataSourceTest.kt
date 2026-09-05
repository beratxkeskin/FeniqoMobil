package com.feniqo.mobile.data.remote.core

import com.feniqo.mobile.data.remote.dto.BudgetDto
import com.feniqo.mobile.data.remote.dto.CategoryDto
import com.feniqo.mobile.data.remote.dto.DebtDto
import com.feniqo.mobile.data.remote.dto.DebtPaymentDto
import com.feniqo.mobile.data.remote.dto.GoalContributionDto
import com.feniqo.mobile.data.remote.dto.GoalDto
import com.feniqo.mobile.data.remote.dto.ProfileDto
import com.feniqo.mobile.data.remote.dto.RecurringTransactionDto
import com.feniqo.mobile.data.remote.dto.SubscriptionDto
import com.feniqo.mobile.data.remote.dto.TagDto
import com.feniqo.mobile.data.remote.dto.TransactionDto
import com.feniqo.mobile.data.remote.dto.TransactionTagDto
import com.feniqo.mobile.data.remote.dto.WorkspaceDto
import com.feniqo.mobile.data.remote.dto.WorkspaceMemberDto




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
import kotlinx.serialization.json.JsonNull
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
    fun idempotentConditionalWrite_v2_subscription_uses_sync_write_v2_rpc_and_parameters(): Unit = runBlocking {
        var capturedPath: String? = null
        var capturedBody: String? = null
        val rpcResponseJson = """
            {
                "status": "APPLIED",
                "record": {
                    "id": "sub-1",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "name": "Spotify",
                    "amount_minor": 5999,
                    "currency": "TRY",
                    "category_id": "cat-1",
                    "frequency": "MONTHLY",
                    "interval": 1,
                    "start_date": "2026-08-01",
                    "end_date": null,
                    "next_renewal_date": "2026-09-01",
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

        val inputDto = SubscriptionDto(
            id = "sub-1",
            userId = "usr-1",
            workspaceId = null,
            name = "Spotify",
            amountMinor = 5999L,
            currency = "TRY",
            categoryId = "cat-1",
            frequency = "MONTHLY",
            interval = 1,
            startDate = "2026-08-01",
            endDate = null,
            nextRenewalDate = "2026-09-01",
            isActive = true,
            createdAt = "2026-08-01T10:00:00Z",
        )

        val result = dataSource.writeSubscription(
            operationId = "op-sub-1",
            operation = RemoteWriteOperation.CREATE,
            baseVersion = null,
            dto = inputDto,
        )

        assertIs<ConditionalRemoteWriteResult.Applied<SubscriptionDto>>(result)
        assertEquals("sub-1", result.record.id)
        assertEquals(1L, result.record.version)
        assertEquals(5999L, result.record.amountMinor)
        assertEquals("Spotify", result.record.name)

        assertTrue(capturedPath?.contains("sync_write_v2") == true)
        val parsed = json.parseToJsonElement(checkNotNull(capturedBody)).jsonObject
        assertEquals("op-sub-1", parsed["p_operation_id"]?.jsonPrimitive?.content)
        assertEquals("SUBSCRIPTION", parsed["p_entity_type"]?.jsonPrimitive?.content)
        assertEquals("CREATE", parsed["p_operation"]?.jsonPrimitive?.content)
        assertEquals(JsonNull, parsed["p_base_version"])

        val payload = checkNotNull(parsed["p_payload"]?.jsonObject)
        assertEquals("sub-1", payload["id"]?.jsonPrimitive?.content)
        assertEquals("Spotify", payload["name"]?.jsonPrimitive?.content)
        assertEquals(5999L, payload["amount_minor"]?.jsonPrimitive?.content?.toLong())
        assertEquals(JsonNull, payload["workspace_id"])
        assertEquals(JsonNull, payload["end_date"])
        assertEquals("cat-1", payload["category_id"]?.jsonPrimitive?.content)
        assertEquals("2026-09-01", payload["next_renewal_date"]?.jsonPrimitive?.content)
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

    @Test
    fun fetchSubscriptions_uses_correct_endpoint_and_parameters(): Unit = runBlocking {
        var capturedPath: String? = null
        var capturedQuery: String? = null
        var capturedRangeHeader: String? = null

        val responseJson = """
            [
                {
                    "id": "sub-1",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "name": "Spotify",
                    "amount_minor": 5999,
                    "currency": "TRY",
                    "category_id": "cat-1",
                    "frequency": "MONTHLY",
                    "interval": 1,
                    "start_date": "2026-08-01",
                    "end_date": null,
                    "next_renewal_date": "2026-09-01",
                    "is_active": true,
                    "created_at": "2026-08-01T10:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
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

        val page = dataSource.fetchSubscriptions(
            SubscriptionRemoteQuery(
                page = RemotePageRequest(pageIndex = 0, pageSize = 20),
                workspaceScope = RemoteWorkspaceScope.Personal,
            ),
        )

        assertEquals(1, page.items.size)
        assertEquals("sub-1", page.items.first().id)
        assertEquals(1L, page.totalCount)
        assertTrue(capturedPath?.endsWith("/subscriptions") == true)
        assertTrue(capturedQuery?.contains("workspace_id=is.null") == true)
        assertTrue(capturedQuery?.contains("updated_at.asc") == true)
        assertTrue(capturedQuery?.contains("id.asc") == true)
        assertTrue(capturedRangeHeader == "0-19" || capturedQuery?.contains("limit=20") == true)
    }

    @Test
    fun fetchSubscriptions_with_cursor_applies_composite_filtering(): Unit = runBlocking {
        var capturedOrFilter: String? = null

        val responseJson = """
            [
                {
                    "id": "sub-1",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "name": "Netflix",
                    "amount_minor": 19900,
                    "currency": "TRY",
                    "category_id": "cat-1",
                    "frequency": "MONTHLY",
                    "interval": 1,
                    "start_date": "2026-08-01",
                    "end_date": null,
                    "next_renewal_date": "2026-09-01",
                    "is_active": true,
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 1
                },
                {
                    "id": "sub-2",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "name": "Spotify",
                    "amount_minor": 5999,
                    "currency": "TRY",
                    "category_id": "cat-1",
                    "frequency": "MONTHLY",
                    "interval": 1,
                    "start_date": "2026-08-01",
                    "end_date": null,
                    "next_renewal_date": "2026-09-01",
                    "is_active": true,
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 1
                },
                {
                    "id": "sub-3",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "name": "YouTube Premium",
                    "amount_minor": 7999,
                    "currency": "TRY",
                    "category_id": "cat-1",
                    "frequency": "MONTHLY",
                    "interval": 1,
                    "start_date": "2026-08-01",
                    "end_date": null,
                    "next_renewal_date": "2026-09-01",
                    "is_active": true,
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 1
                },
                {
                    "id": "sub-4",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "name": "iCloud",
                    "amount_minor": 4999,
                    "currency": "TRY",
                    "category_id": null,
                    "frequency": "MONTHLY",
                    "interval": 1,
                    "start_date": "2026-08-01",
                    "end_date": null,
                    "next_renewal_date": "2026-09-01",
                    "is_active": true,
                    "created_at": "2026-08-25T18:00:00Z",
                    "updated_at": "2026-08-25T18:00:00Z",
                    "deleted_at": null,
                    "version": 2
                }
            ]
        """.trimIndent()

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

        val page = dataSource.fetchSubscriptions(
            SubscriptionRemoteQuery(
                updatedAfter = RemoteSyncCursor(
                    updatedAt = "2026-08-25T17:00:00Z",
                    entityId = "sub-2",
                ),
            ),
        )

        val orFilter = checkNotNull(capturedOrFilter)
        assertTrue(orFilter.contains("updated_at.gt.") && orFilter.contains("2026-08-25T17:00:00Z"))
        assertTrue(orFilter.contains("updated_at.eq.") && orFilter.contains("2026-08-25T17:00:00Z"))
        assertTrue(orFilter.contains("id.gt.sub-2"))
        assertEquals(listOf("sub-3", "sub-4"), page.items.map { it.id })
    }

    @Test
    fun fetchSubscriptions_includes_tombstone_records(): Unit = runBlocking {
        val responseJson = """
            [
                {
                    "id": "sub-deleted",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "name": "Eski Abonelik",
                    "amount_minor": 19900,
                    "currency": "TRY",
                    "category_id": "cat-1",
                    "frequency": "MONTHLY",
                    "interval": 1,
                    "start_date": "2026-08-01",
                    "end_date": null,
                    "next_renewal_date": "2026-09-01",
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

        val page = dataSource.fetchSubscriptions(SubscriptionRemoteQuery())

        assertEquals(1, page.items.size)
        val item = page.items.first()
        assertEquals("sub-deleted", item.id)
        assertEquals("2026-08-25T17:05:00Z", item.deletedAt)
        assertEquals(3L, item.version)
    }

    @Test
    fun fetchGoals_applies_correct_endpoint_scope_and_ordering(): Unit = runBlocking {
        var capturedPath: String? = null
        var capturedQuery: String? = null
        var capturedRangeHeader: String? = null

        val responseJson = """
            [
                {
                    "id": "goal-1",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "name": "Ev Peşinatı",
                    "target_amount_minor": 100000000,
                    "current_amount_minor": 20000000,
                    "currency": "TRY",
                    "target_date": "2027-12-31",
                    "color_hex": "#10B981",
                    "icon_key": "home",
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
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

        val page = dataSource.fetchGoals(
            GoalRemoteQuery(
                page = RemotePageRequest(pageIndex = 0, pageSize = 20),
                workspaceScope = RemoteWorkspaceScope.Personal,
            ),
        )

        assertEquals(1, page.items.size)
        assertEquals("goal-1", page.items.first().id)
        assertEquals(1L, page.totalCount)
        assertTrue(capturedPath?.endsWith("/goals") == true)
        assertTrue(capturedQuery?.contains("workspace_id=is.null") == true)
        assertTrue(capturedQuery?.contains("updated_at.asc") == true)
        assertTrue(capturedQuery?.contains("id.asc") == true)
        assertTrue(capturedRangeHeader == "0-19" || capturedQuery?.contains("limit=20") == true)
    }

    @Test
    fun fetchGoals_with_cursor_applies_composite_filtering(): Unit = runBlocking {
        var capturedOrFilter: String? = null

        val responseJson = """
            [
                {
                    "id": "goal-1",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "name": "Goal 1",
                    "target_amount_minor": 10000000,
                    "current_amount_minor": 0,
                    "currency": "TRY",
                    "target_date": "2027-12-31",
                    "color_hex": "#10B981",
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 1
                },
                {
                    "id": "goal-2",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "name": "Goal 2",
                    "target_amount_minor": 20000000,
                    "current_amount_minor": 0,
                    "currency": "TRY",
                    "target_date": "2027-12-31",
                    "color_hex": "#10B981",
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 1
                },
                {
                    "id": "goal-3",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "name": "Goal 3",
                    "target_amount_minor": 30000000,
                    "current_amount_minor": 0,
                    "currency": "TRY",
                    "target_date": "2027-12-31",
                    "color_hex": "#10B981",
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 1
                },
                {
                    "id": "goal-4",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "name": "Goal 4",
                    "target_amount_minor": 40000000,
                    "current_amount_minor": 0,
                    "currency": "TRY",
                    "target_date": "2027-12-31",
                    "color_hex": "#10B981",
                    "created_at": "2026-08-25T18:00:00Z",
                    "updated_at": "2026-08-25T18:00:00Z",
                    "deleted_at": null,
                    "version": 2
                }
            ]
        """.trimIndent()

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

        val page = dataSource.fetchGoals(
            GoalRemoteQuery(
                updatedAfter = RemoteSyncCursor(
                    updatedAt = "2026-08-25T17:00:00Z",
                    entityId = "goal-2",
                ),
            ),
        )

        val orFilter = checkNotNull(capturedOrFilter)
        assertTrue(orFilter.contains("updated_at.gt.") && orFilter.contains("2026-08-25T17:00:00Z"))
        assertTrue(orFilter.contains("updated_at.eq.") && orFilter.contains("2026-08-25T17:00:00Z"))
        assertTrue(orFilter.contains("id.gt.goal-2"))
        assertEquals(listOf("goal-3", "goal-4"), page.items.map { it.id })
    }

    @Test
    fun fetchGoalContributions_applies_endpoint_goal_id_and_cursor(): Unit = runBlocking {
        var capturedPath: String? = null
        var capturedQuery: String? = null

        val responseJson = """
            [
                {
                    "id": "contrib-1",
                    "goal_id": "goal-1",
                    "amount_minor": 500000,
                    "currency": "TRY",
                    "direction": "ADD",
                    "occurred_on": "2026-09-01",
                    "note": "Katkı 1",
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 1
                }
            ]
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedQuery = request.url.encodedQuery
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

        val page = dataSource.fetchGoalContributions(
            GoalContributionRemoteQuery(
                goalId = com.feniqo.mobile.domain.model.EntityId("goal-1"),
            ),
        )

        assertEquals(1, page.items.size)
        assertEquals("contrib-1", page.items.first().id)
        assertTrue(capturedPath?.endsWith("/goal_contributions") == true)
        assertTrue(capturedQuery?.contains("goal_id=eq.goal-1") == true)
        assertTrue(capturedQuery?.contains("updated_at.asc") == true)
    }

    @Test
    fun fetchDebts_applies_correct_endpoint_scope_ordering_and_tombstones(): Unit = runBlocking {
        var capturedPath: String? = null
        var capturedQuery: String? = null

        val responseJson = """
            [
                {
                    "id": "debt-1",
                    "user_id": "usr-1",
                    "workspace_id": null,
                    "title": "Borç",
                    "amount_minor": 100000,
                    "currency": "TRY",
                    "type": "DEBT",
                    "due_date": "2026-10-01",
                    "status": "OPEN",
                    "description": "Açıklama",
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": "2026-08-25T17:05:00Z",
                    "version": 2
                }
            ]
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedQuery = request.url.encodedQuery
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

        val page = dataSource.fetchDebts(
            DebtRemoteQuery(
                workspaceScope = RemoteWorkspaceScope.Personal,
            ),
        )

        assertEquals(1, page.items.size)
        val debt = page.items.first()
        assertEquals("debt-1", debt.id)
        assertEquals("2026-08-25T17:05:00Z", debt.deletedAt)
        assertTrue(capturedPath?.endsWith("/debts") == true)
        assertTrue(capturedQuery?.contains("workspace_id=is.null") == true)
        assertTrue(capturedQuery?.contains("updated_at.asc") == true)
    }

    @Test
    fun fetchDebtPayments_applies_endpoint_debt_id_and_cursor(): Unit = runBlocking {
        var capturedPath: String? = null
        var capturedQuery: String? = null

        val responseJson = """
            [
                {
                    "id": "pay-1",
                    "debt_id": "debt-1",
                    "amount_minor": 50000,
                    "currency": "TRY",
                    "paid_on": "2026-09-15",
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 1
                }
            ]
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedQuery = request.url.encodedQuery
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

        val page = dataSource.fetchDebtPayments(
            DebtPaymentRemoteQuery(
                debtId = com.feniqo.mobile.domain.model.EntityId("debt-1"),
            ),
        )

        assertEquals(1, page.items.size)
        assertEquals("pay-1", page.items.first().id)
        assertTrue(capturedPath?.endsWith("/debt_payments") == true)
        assertTrue(capturedQuery?.contains("debt_id=eq.debt-1") == true)
        assertTrue(capturedQuery?.contains("updated_at.asc") == true)
    }

    @Test
    fun fetchWorkspaces_applies_correct_endpoint_deterministic_ordering_tombstones_and_cursor(): Unit = runBlocking {
        var capturedPath: String? = null
        var capturedQuery: String? = null
        var capturedRangeHeader: String? = null

        val responseJson = """
            [
                {
                    "id": "550e8400-e29b-41d4-a716-446655440001",
                    "name": "Ortak Alan 1",
                    "normalized_name": "ortak alan 1",
                    "owner_id": "usr-1",
                    "type_code": "shared",
                    "currency_code": "TRY",
                    "description": "Açıklama 1",
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 1
                },
                {
                    "id": "550e8400-e29b-41d4-a716-446655440002",
                    "name": "Ortak Alan 2",
                    "normalized_name": "ortak alan 2",
                    "owner_id": "usr-1",
                    "type_code": "personal",
                    "currency_code": "USD",
                    "description": null,
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": "2026-08-25T17:30:00Z",
                    "version": 2
                },
                {
                    "id": "550e8400-e29b-41d4-a716-446655440003",
                    "name": "Ortak Alan 3",
                    "normalized_name": "ortak alan 3",
                    "owner_id": "usr-1",
                    "type_code": "shared",
                    "currency_code": "EUR",
                    "description": "Yeni",
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T18:00:00Z",
                    "deleted_at": null,
                    "version": 3
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
                    "Content-Range" to listOf("0-2/3"),
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

        val page = dataSource.fetchWorkspaces(
            WorkspaceRemoteQuery(
                page = RemotePageRequest(pageIndex = 0, pageSize = 20),
                updatedAfter = RemoteSyncCursor(
                    updatedAt = "2026-08-25T17:00:00Z",
                    entityId = "550e8400-e29b-41d4-a716-446655440001",
                ),
            ),
        )

        assertTrue(capturedPath?.endsWith("/workspaces") == true)
        assertTrue(capturedQuery?.contains("updated_at.asc") == true)
        assertTrue(capturedQuery?.contains("id.asc") == true)
        assertTrue(capturedRangeHeader == "0-19" || capturedQuery?.contains("limit=20") == true)

        // Composite cursor filtering: item 1 filtered out, items 2 and 3 kept
        assertEquals(2, page.items.size)
        val item2 = page.items[0]
        assertEquals("550e8400-e29b-41d4-a716-446655440002", item2.id)
        assertEquals("2026-08-25T17:30:00Z", item2.deletedAt) // Tombstone preserved!
        assertEquals("personal", item2.typeCode)
        assertEquals("USD", item2.currencyCode)

        val item3 = page.items[1]
        assertEquals("550e8400-e29b-41d4-a716-446655440003", item3.id)
        assertEquals("shared", item3.typeCode)
        assertEquals("EUR", item3.currencyCode)
    }

    @Test
    fun fetchWorkspaceMembers_applies_correct_endpoint_composite_ordering_and_cursor(): Unit = runBlocking {
        var capturedPath: String? = null
        var capturedQuery: String? = null

        val responseJson = """
            [
                {
                    "workspace_id": "ws-100",
                    "user_id": "user-1",
                    "role_code": "OWNER",
                    "joined_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": null,
                    "version": 1
                },
                {
                    "workspace_id": "ws-100",
                    "user_id": "user-2",
                    "role_code": "EDITOR",
                    "joined_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "deleted_at": "2026-08-25T17:45:00Z",
                    "version": 2
                },
                {
                    "workspace_id": "ws-200",
                    "user_id": "user-1",
                    "role_code": "VIEWER",
                    "joined_at": "2026-08-25T18:00:00Z",
                    "updated_at": "2026-08-25T18:00:00Z",
                    "deleted_at": null,
                    "version": 1
                }
            ]
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedQuery = request.url.encodedQuery
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "Content-Range" to listOf("0-2/3"),
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

        val page = dataSource.fetchWorkspaceMembers(
            WorkspaceMemberRemoteQuery(
                page = RemotePageRequest(pageIndex = 0, pageSize = 50),
                updatedAfter = WorkspaceMemberSyncCursor(
                    updatedAt = "2026-08-25T17:00:00Z",
                    workspaceId = "ws-100",
                    userId = "user-1",
                ),
            ),
        )

        assertTrue(capturedPath?.endsWith("/workspace_members") == true)
        assertTrue(capturedQuery?.contains("updated_at.asc") == true)
        assertTrue(capturedQuery?.contains("workspace_id.asc") == true)
        assertTrue(capturedQuery?.contains("user_id.asc") == true)

        // Composite cursor (updated_at, workspace_id, user_id): item 1 skipped, items 2 and 3 returned
        assertEquals(2, page.items.size)
        val member2 = page.items[0]
        assertEquals("ws-100", member2.workspaceId)
        assertEquals("user-2", member2.userId)
        assertEquals("EDITOR", member2.roleCode)
        assertEquals("2026-08-25T17:45:00Z", member2.deletedAt) // Tombstone preserved!

        val member3 = page.items[1]
        assertEquals("ws-200", member3.workspaceId)
        assertEquals("user-1", member3.userId)
        assertEquals("VIEWER", member3.roleCode)
    }

    @Test
    fun fetchWorkspaces_fails_closed_on_malformed_dto_without_leaking_raw_exceptions(): Unit = runBlocking {
        // Missing required field "type_code" and "currency_code" in D1 canonical DTO
        val malformedJson = """
            [
                {
                    "id": "550e8400-e29b-41d4-a716-446655440001",
                    "name": "Eksik Alanlı Workspace",
                    "normalized_name": "eksik alanlı workspace",
                    "owner_id": "usr-1",
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:00:00Z",
                    "version": 1
                }
            ]
        """.trimIndent()

        val mockEngine = MockEngine {
            respond(
                content = malformedJson,
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

        kotlin.test.assertFailsWith<kotlinx.serialization.SerializationException> {
            dataSource.fetchWorkspaces(WorkspaceRemoteQuery())
        }
    }


    @Test
    fun default_core_remote_data_source_workspace_methods_fail_closed_with_error(): Unit = runBlocking {
        val minimalRemote = object : CoreRemoteDataSource {
            override suspend fun fetchProfile(userId: String): ProfileDto? = null
            override suspend fun fetchCategories(query: CategoryRemoteQuery): RemotePage<CategoryDto> = error("N/A")
            override suspend fun fetchTransactions(query: TransactionRemoteQuery): RemotePage<TransactionDto> = error("N/A")
            override suspend fun fetchBudgets(query: BudgetRemoteQuery): RemotePage<BudgetDto> = error("N/A")
            override suspend fun fetchRecurringTransactions(query: RecurringTransactionRemoteQuery): RemotePage<RecurringTransactionDto> = error("N/A")
            override suspend fun fetchSubscriptions(query: SubscriptionRemoteQuery): RemotePage<SubscriptionDto> = error("N/A")
            override suspend fun fetchGoals(query: GoalRemoteQuery): RemotePage<GoalDto> = error("N/A")
            override suspend fun fetchGoalContributions(query: GoalContributionRemoteQuery): RemotePage<GoalContributionDto> = error("N/A")
            override suspend fun fetchDebts(query: DebtRemoteQuery): RemotePage<DebtDto> = error("N/A")
            override suspend fun fetchDebtPayments(query: DebtPaymentRemoteQuery): RemotePage<DebtPaymentDto> = error("N/A")
            override suspend fun fetchTags(scope: RemoteWorkspaceScope, page: RemotePageRequest): RemotePage<TagDto> = error("N/A")
            override suspend fun fetchTransactionTags(transactionId: String): List<TransactionTagDto> = error("N/A")
            override suspend fun upsertProfile(dto: ProfileDto) = Unit
            override suspend fun upsertCategory(dto: CategoryDto) = Unit
            override suspend fun upsertTransaction(dto: TransactionDto) = Unit
            override suspend fun upsertBudget(dto: BudgetDto) = Unit
            override suspend fun upsertTag(dto: TagDto) = Unit
            override suspend fun upsertTransactionTag(dto: TransactionTagDto) = Unit
        }

        val wsQueryError = kotlin.test.assertFailsWith<IllegalStateException> {
            minimalRemote.fetchWorkspaces(WorkspaceRemoteQuery())
        }
        assertTrue(wsQueryError.message?.contains("not implemented") == true)

        val wsPageError = kotlin.test.assertFailsWith<IllegalStateException> {
            minimalRemote.fetchWorkspaces(RemotePageRequest())
        }
        assertTrue(wsPageError.message?.contains("not implemented") == true)

        val memberQueryError = kotlin.test.assertFailsWith<IllegalStateException> {
            minimalRemote.fetchWorkspaceMembers(WorkspaceMemberRemoteQuery())
        }
        assertTrue(memberQueryError.message?.contains("not implemented") == true)

        val memberPageError = kotlin.test.assertFailsWith<IllegalStateException> {
            minimalRemote.fetchWorkspaceMembers("ws-100", RemotePageRequest())
        }
        assertTrue(memberPageError.message?.contains("not implemented") == true)
    }

    @Test
    fun idempotentConditionalWrite_v2_workspace_uses_sync_write_v2_rpc_and_parameters(): Unit = runBlocking {
        var capturedPath: String? = null
        var capturedBody: String? = null
        val opId = "ws-op-001"
        val rpcResponseJson = """
            {
                "status": "APPLIED",
                "record": {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "name": "Ortak Bütçe",
                    "normalized_name": "ortak bütçe",
                    "owner_id": "usr-1",
                    "type_code": "shared",
                    "currency_code": "TRY",
                    "description": "Ev harcamaları",
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

        val payload = kotlinx.serialization.json.buildJsonObject {
            put("id", kotlinx.serialization.json.JsonPrimitive("550e8400-e29b-41d4-a716-446655440000"))
            put("name", kotlinx.serialization.json.JsonPrimitive("Ortak Bütçe"))
            put("type_code", kotlinx.serialization.json.JsonPrimitive("shared"))
            put("currency_code", kotlinx.serialization.json.JsonPrimitive("TRY"))
            put("description", kotlinx.serialization.json.JsonPrimitive("Ev harcamaları"))
        }

        val result = dataSource.writeWorkspace(
            operationId = opId,
            operation = RemoteWriteOperation.CREATE,
            baseVersion = null,
            payload = payload,
        )

        assertIs<ConditionalRemoteWriteResult.Applied<WorkspaceDto>>(result)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", result.record.id)
        assertEquals("Ortak Bütçe", result.record.name)
        assertEquals("shared", result.record.typeCode)
        assertEquals("TRY", result.record.currencyCode)
        assertEquals(1L, result.record.version)

        assertTrue(capturedPath?.contains("sync_write_v2") == true)
        val parsed = json.parseToJsonElement(checkNotNull(capturedBody)).jsonObject
        assertEquals(opId, parsed["p_operation_id"]?.jsonPrimitive?.content)
        assertEquals("WORKSPACE", parsed["p_entity_type"]?.jsonPrimitive?.content)
        assertEquals("CREATE", parsed["p_operation"]?.jsonPrimitive?.content)
        assertEquals(JsonNull, parsed["p_base_version"])

        val payloadObj = parsed["p_payload"]?.jsonObject
        assertEquals("Ortak Bütçe", payloadObj?.get("name")?.jsonPrimitive?.content)
        assertEquals("shared", payloadObj?.get("type_code")?.jsonPrimitive?.content)
    }

    @Test
    fun idempotentConditionalWrite_v2_workspace_decodes_conflict_and_not_found(): Unit = runBlocking {
        val conflictJson = """
            {
                "status": "CONFLICT",
                "record": {
                    "id": "550e8400-e29b-41d4-a716-446655440000",
                    "name": "Sunucu Workspace",
                    "normalized_name": "sunucu workspace",
                    "owner_id": "usr-9",
                    "type_code": "personal",
                    "currency_code": "USD",
                    "description": null,
                    "created_at": "2026-08-25T17:00:00Z",
                    "updated_at": "2026-08-25T17:10:00Z",
                    "deleted_at": null,
                    "version": 4
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

        val payload = kotlinx.serialization.json.buildJsonObject {
            put("id", kotlinx.serialization.json.JsonPrimitive("550e8400-e29b-41d4-a716-446655440000"))
        }

        val conflictResult = dataSource.writeWorkspace("op-1", RemoteWriteOperation.UPDATE, 2L, payload)
        assertIs<ConditionalRemoteWriteResult.Conflict<WorkspaceDto>>(conflictResult)
        assertEquals(4L, conflictResult.remoteRecord.version)
        assertEquals("Sunucu Workspace", conflictResult.remoteRecord.name)
        assertEquals("personal", conflictResult.remoteRecord.typeCode)

        val notFoundResult = dataSource.writeWorkspace("op-2", RemoteWriteOperation.DELETE, 2L, payload)
        assertIs<ConditionalRemoteWriteResult.NotFound>(notFoundResult)
    }
}



