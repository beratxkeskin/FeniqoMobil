package com.feniqo.mobile.presentation.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HelpContentTest {
    @Test
    fun publishedHelpCategories_hasExpectedCategoriesAndArticlesCount() {
        assertEquals(4, publishedHelpCategories.size)

        val categoryIds = publishedHelpCategories.map { it.id }.toSet()
        val expectedCategoryIds =
            setOf(
                "account_login",
                "transactions_categories",
                "backup_sync",
                "privacy_security",
            )
        assertEquals(expectedCategoryIds, categoryIds)

        val articles = publishedHelpCategories.flatMap { it.articles }
        assertEquals(8, articles.size)
    }

    @Test
    fun everyHelpArticle_hasUniqueStableId_andTypedPublishedStatus() {
        val articles = publishedHelpCategories.flatMap { it.articles }
        val ids = articles.map { it.id }

        // Benzersiz ve sabit kimlikler
        assertEquals(ids.size, ids.toSet().size)
        val expectedArticleIds =
            setOf(
                "acc_1",
                "acc_2",
                "txn_1",
                "txn_2",
                "sync_1",
                "sync_2",
                "sec_1",
                "sec_2",
            )
        assertEquals(expectedArticleIds, ids.toSet())

        // Tipli yayın durumu
        assertTrue(articles.all { it.status == HelpArticleStatus.PUBLISHED })
    }

    @Test
    fun everyHelpArticle_hasAtLeastTwoParagraphs() {
        val articles = publishedHelpCategories.flatMap { it.articles }
        assertTrue(articles.all { it.paragraphResList.size >= 2 })
    }

    @Test
    fun articlesCanBeSelectedById_forStableNavigation() {
        val targetId = "sec_1"
        val article = publishedHelpCategories.flatMap { it.articles }.firstOrNull { it.id == targetId }
        assertNotNull(article)
        assertEquals("sec_1", article.id)
    }
}
