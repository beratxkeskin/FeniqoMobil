package com.feniqo.mobile.presentation.category

import com.feniqo.mobile.presentation.component.PRESET_CATEGORY_COLORS
import com.feniqo.mobile.presentation.component.PRESET_CATEGORY_ICONS
import com.feniqo.mobile.presentation.util.ColorParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CategoryFormOptionsTest {

    @Test
    fun presetCategoryColors_areUnique_andMatchValidHexFormat() {
        assertEquals(12, PRESET_CATEGORY_COLORS.size)
        assertEquals(
            PRESET_CATEGORY_COLORS.size,
            PRESET_CATEGORY_COLORS.toSet().size,
            "Tüm renkler benzersiz olmalıdır",
        )

        PRESET_CATEGORY_COLORS.forEach { hex ->
            assertTrue(hex.startsWith("#"), "Renk '#' ile başlamalıdır: $hex")
            assertEquals(7, hex.length, "Renk 6 hex karakteri içermelidir (#RRGGBB): $hex")
            assertNotNull(ColorParser.parseHexColorOrNull(hex), "ColorParser rengi parse edebilmelidir: $hex")
        }
    }

    @Test
    fun presetCategoryIcons_haveUniqueKeys_andSingleNullOption() {
        val keys = PRESET_CATEGORY_ICONS.map { it.key }
        assertEquals(
            keys.size,
            keys.toSet().size,
            "Tüm simge anahtarları benzersiz olmalıdır",
        )

        val nullOptions = PRESET_CATEGORY_ICONS.filter { it.key == null }
        assertEquals(1, nullOptions.size, "Yalnızca bir adet ikonsuz seçenek bulunmalıdır")
        assertEquals("İkonsuz", nullOptions.first().label)
    }

    @Test
    fun presetCategoryIcons_containsAllCanonicalSeedIcons() {
        val canonicalKeys = setOf(
            "briefcase",
            "laptop",
            "graduation-cap",
            "trending-up",
            "dollar-sign",
            "utensils",
            "shopping-cart",
            "car",
            "home",
            "file-text",
            "music",
            "book-open",
            "heart-pulse",
            "credit-card",
            "percent",
            "help-circle",
        )

        val catalogKeys = PRESET_CATEGORY_ICONS.mapNotNull { it.key }.toSet()
        canonicalKeys.forEach { key ->
            assertTrue(catalogKeys.contains(key), "Katalog canonical seed anahtarını içermelidir: $key")
        }
    }

    @Test
    fun presetCategoryIcons_allLabelsAreNonBlank() {
        PRESET_CATEGORY_ICONS.forEach { option ->
            assertTrue(option.label.isNotBlank(), "Simge etiketi boş olamaz: ${option.key}")
        }
    }
}
