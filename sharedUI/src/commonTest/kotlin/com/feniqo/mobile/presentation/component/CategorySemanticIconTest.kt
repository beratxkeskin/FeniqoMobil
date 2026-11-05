package com.feniqo.mobile.presentation.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import com.feniqo.mobile.domain.model.ExpenseCategoryVisualCatalog
import com.feniqo.mobile.domain.model.IncomeCategoryVisualCatalog
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class CategorySemanticIconTest {
    @Test
    fun everyCanonicalIncomeAndExpenseKey_resolvesToDedicatedVector() {
        val keys = ExpenseCategoryVisualCatalog.definitions.map { it.key.key } +
            IncomeCategoryVisualCatalog.definitions.map { it.key.key }

        keys.forEach { key ->
            assertNotEquals(Icons.Outlined.Category, CategorySemanticIconResolver.resolve(key), key)
        }
    }

    @Test
    fun unknownKey_usesGenericCategoryVector() {
        assertSame(Icons.Outlined.Category, CategorySemanticIconResolver.resolve("unknown"))
        assertSame(Icons.Outlined.Category, CategorySemanticIconResolver.resolve(null))
        assertSame(Icons.Outlined.Category, CategorySemanticIconResolver.resolve("   "))
    }

    @Test
    fun resolveOrNull_forEveryCanonicalKey_returnsDedicatedVectorMatchingResolve() {
        val keys = ExpenseCategoryVisualCatalog.definitions.map { it.key.key } +
            IncomeCategoryVisualCatalog.definitions.map { it.key.key }

        keys.forEach { key ->
            val resolvedOrNull = CategorySemanticIconResolver.resolveOrNull(key)
            val resolved = CategorySemanticIconResolver.resolve(key)
            kotlin.test.assertNotNull(resolvedOrNull, "Expected non-null for key: $key")
            assertSame(resolved, resolvedOrNull, "resolve and resolveOrNull should match for key: $key")
            assertNotEquals(Icons.Outlined.Category, resolvedOrNull, key)
        }
    }

    @Test
    fun resolveOrNull_forUnknownNullOrBlank_returnsNull() {
        kotlin.test.assertNull(CategorySemanticIconResolver.resolveOrNull("unknown_category_key"))
        kotlin.test.assertNull(CategorySemanticIconResolver.resolveOrNull(null))
        kotlin.test.assertNull(CategorySemanticIconResolver.resolveOrNull(""))
        kotlin.test.assertNull(CategorySemanticIconResolver.resolveOrNull("    "))
    }
}
