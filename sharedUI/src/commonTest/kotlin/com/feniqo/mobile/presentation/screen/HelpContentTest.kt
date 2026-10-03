package com.feniqo.mobile.presentation.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelpContentTest {
    @Test
    fun everyHelpEntry_isPublishedAndHasUsefulContent() {
        val articles = publishedHelpCategories.flatMap { it.articles }

        assertEquals(8, articles.size)
        assertTrue(articles.all { it.status == "Yayında" })
        assertTrue(articles.all { it.paragraphs.size >= 2 })
        assertTrue(articles.all { article -> article.paragraphs.all { it.isNotBlank() } })
    }
}
