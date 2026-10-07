@file:Suppress(
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-wildcard-imports",
)

package com.feniqo.mobile.presentation.category

import androidx.compose.runtime.Composable
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun CategoryFormFieldError.toLocalizedText(): String = stringResource(toStringResource())

@Composable
fun CategoryFormLoadError.toLocalizedText(): String = stringResource(toStringResource())

@Composable
fun localizedCategoryIconLabel(key: String?): String = stringResource(key.toIconStringResource())

@Composable
fun localizedCategoryColorName(hex: String): String = stringResource(hex.toColorStringResource())

private fun CategoryFormFieldError.toStringResource(): StringResource =
    when (this) {
        CategoryFormFieldError.NAME_REQUIRED -> Res.string.category_form_error_name_required
        CategoryFormFieldError.NAME_TOO_LONG -> Res.string.category_form_error_name_too_long
        CategoryFormFieldError.COLOR_INVALID -> Res.string.category_form_error_color_invalid
    }

private fun CategoryFormLoadError.toStringResource(): StringResource =
    when (this) {
        CategoryFormLoadError.CATEGORY_NOT_FOUND -> Res.string.category_form_error_not_found
        CategoryFormLoadError.DEFAULT_CATEGORY_READ_ONLY -> Res.string.category_form_error_default_read_only
        CategoryFormLoadError.INVALID_ROUTE -> Res.string.category_form_error_invalid_route
        CategoryFormLoadError.LOAD_FAILED -> Res.string.category_form_error_load_failed
    }

private fun String?.toIconStringResource(): StringResource =
    when (this) {
        null -> Res.string.category_form_icon_none
        "briefcase" -> Res.string.category_form_icon_briefcase
        "laptop" -> Res.string.category_form_icon_laptop
        "graduation-cap" -> Res.string.category_form_icon_graduation
        "trending-up" -> Res.string.category_form_icon_trending_up
        "dollar-sign" -> Res.string.category_form_icon_money
        "utensils" -> Res.string.category_form_icon_food
        "shopping-cart" -> Res.string.category_form_icon_grocery
        "car" -> Res.string.category_form_icon_car
        "home" -> Res.string.category_form_icon_home
        "file-text" -> Res.string.category_form_icon_document
        "music" -> Res.string.category_form_icon_music
        "book-open" -> Res.string.category_form_icon_book
        "heart-pulse" -> Res.string.category_form_icon_health
        "credit-card" -> Res.string.category_form_icon_card
        "percent" -> Res.string.category_form_icon_percent
        "help-circle" -> Res.string.category_form_icon_other
        "piggy-bank" -> Res.string.category_form_icon_savings
        "gift" -> Res.string.category_form_icon_gift
        "tag" -> Res.string.category_form_icon_tag
        else -> Res.string.category_form_icon_custom
    }

private fun String.toColorStringResource(): StringResource =
    when (trim().uppercase()) {
        "#10B981" -> Res.string.category_form_color_emerald
        "#34D399" -> Res.string.category_form_color_light_green
        "#6EE7B7" -> Res.string.category_form_color_mint
        "#059669" -> Res.string.category_form_color_dark_green
        "#FBBF24" -> Res.string.category_form_color_yellow
        "#EF4444" -> Res.string.category_form_color_red
        "#F59E0B" -> Res.string.category_form_color_orange
        "#3B82F6" -> Res.string.category_form_color_blue
        "#EC4899" -> Res.string.category_form_color_pink
        "#8B5CF6" -> Res.string.category_form_color_purple
        "#6366F1" -> Res.string.category_form_color_indigo
        "#6B7280" -> Res.string.category_form_color_gray
        else -> Res.string.category_form_color_custom
    }
