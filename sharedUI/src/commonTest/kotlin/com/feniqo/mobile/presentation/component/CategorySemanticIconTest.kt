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
    }
}
