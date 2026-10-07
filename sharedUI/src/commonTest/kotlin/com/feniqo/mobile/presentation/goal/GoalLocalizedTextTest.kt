@file:Suppress(
    "ktlint:standard:max-line-length",
    "ktlint:standard:function-signature",
    "ktlint:standard:multiline-expression-wrapping",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:argument-list-wrapping",
)

package com.feniqo.mobile.presentation.goal

import com.feniqo.mobile.domain.model.GoalStatus
import feniqomobil.sharedui.generated.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GoalLocalizedTextTest {
    @Test
    fun goalFormFieldError_mapsAll11EnumValuesExhaustively() {
        GoalFormFieldError.entries.forEach { error ->
            val resource = error.toLocalizedResource()
            assertNotNull(resource, "Resource mapping missing for $error")
        }
        assertEquals(11, GoalFormFieldError.entries.size)
        assertEquals(Res.string.goal_error_name_required, GoalFormFieldError.NAME_REQUIRED.toLocalizedResource())
        assertEquals(Res.string.goal_error_name_too_long, GoalFormFieldError.NAME_TOO_LONG.toLocalizedResource())
        assertEquals(Res.string.goal_error_target_amount_required, GoalFormFieldError.TARGET_AMOUNT_REQUIRED.toLocalizedResource())
        assertEquals(Res.string.goal_error_target_amount_non_positive, GoalFormFieldError.TARGET_AMOUNT_NON_POSITIVE.toLocalizedResource())
        assertEquals(Res.string.goal_error_target_amount_invalid, GoalFormFieldError.TARGET_AMOUNT_INVALID.toLocalizedResource())
        assertEquals(Res.string.goal_error_initial_amount_negative, GoalFormFieldError.INITIAL_AMOUNT_NEGATIVE.toLocalizedResource())
        assertEquals(Res.string.goal_error_initial_amount_invalid, GoalFormFieldError.INITIAL_AMOUNT_INVALID.toLocalizedResource())
        assertEquals(
            Res.string.goal_error_initial_amount_not_allowed_in_edit,
            GoalFormFieldError.INITIAL_AMOUNT_NOT_ALLOWED_IN_EDIT.toLocalizedResource(),
        )
        assertEquals(Res.string.goal_error_target_date_required, GoalFormFieldError.TARGET_DATE_REQUIRED.toLocalizedResource())
        assertEquals(Res.string.goal_error_color_invalid, GoalFormFieldError.COLOR_INVALID.toLocalizedResource())
        assertEquals(Res.string.goal_error_currency_locked_in_edit, GoalFormFieldError.CURRENCY_LOCKED_IN_EDIT.toLocalizedResource())
    }

    @Test
    fun goalContributionFormFieldError_mapsAll8EnumValuesExhaustively() {
        GoalContributionFormFieldError.entries.forEach { error ->
            val resource = error.toLocalizedResource()
            assertNotNull(resource, "Resource mapping missing for $error")
        }
        assertEquals(8, GoalContributionFormFieldError.entries.size)
        assertEquals(Res.string.goal_contrib_error_amount_required, GoalContributionFormFieldError.AMOUNT_REQUIRED.toLocalizedResource())
        assertEquals(
            Res.string.goal_contrib_error_amount_non_positive,
            GoalContributionFormFieldError.AMOUNT_NON_POSITIVE.toLocalizedResource(),
        )
        assertEquals(Res.string.goal_contrib_error_amount_invalid, GoalContributionFormFieldError.AMOUNT_INVALID.toLocalizedResource())
        assertEquals(
            Res.string.goal_contrib_error_exceeds_current,
            GoalContributionFormFieldError.EXCEEDS_CURRENT_AMOUNT.toLocalizedResource(),
        )
        assertEquals(Res.string.goal_contrib_error_date_required, GoalContributionFormFieldError.DATE_REQUIRED.toLocalizedResource())
        assertEquals(Res.string.goal_contrib_error_note_too_long, GoalContributionFormFieldError.NOTE_TOO_LONG.toLocalizedResource())
        assertEquals(
            Res.string.goal_contrib_error_currency_mismatch,
            GoalContributionFormFieldError.CURRENCY_MISMATCH.toLocalizedResource(),
        )
        assertEquals(Res.string.goal_contrib_error_parent_missing, GoalContributionFormFieldError.PARENT_GOAL_MISSING.toLocalizedResource())
    }

    @Test
    fun goalStatusFilter_mapsAllFiltersToLabelSummaryTitleAndCount() {
        GoalStatusFilter.entries.forEach { filter ->
            assertNotNull(filter.toLocalizedResource(), "Resource missing for $filter")
            assertNotNull(filter.toLocalizedSummaryTitleResource(), "Summary title missing for $filter")
            assertNotNull(filter.toLocalizedSummaryCountResource(), "Summary count missing for $filter")
        }
        assertEquals(3, GoalStatusFilter.entries.size)
        assertEquals(Res.string.goal_filter_all, GoalStatusFilter.ALL.toLocalizedResource())
        assertEquals(Res.string.goal_filter_active, GoalStatusFilter.ACTIVE.toLocalizedResource())
        assertEquals(Res.string.goal_filter_achieved, GoalStatusFilter.ACHIEVED.toLocalizedResource())

        assertEquals(Res.string.goal_summary_title_all, GoalStatusFilter.ALL.toLocalizedSummaryTitleResource())
        assertEquals(Res.string.goal_summary_title_active, GoalStatusFilter.ACTIVE.toLocalizedSummaryTitleResource())
        assertEquals(Res.string.goal_summary_title_achieved, GoalStatusFilter.ACHIEVED.toLocalizedSummaryTitleResource())

        assertEquals(Res.string.goal_summary_count_all, GoalStatusFilter.ALL.toLocalizedSummaryCountResource())
        assertEquals(Res.string.goal_summary_count_active, GoalStatusFilter.ACTIVE.toLocalizedSummaryCountResource())
        assertEquals(Res.string.goal_summary_count_achieved, GoalStatusFilter.ACHIEVED.toLocalizedSummaryCountResource())
    }

    @Test
    fun goalStatus_and_goalDetailStatus_mapCorrectly() {
        GoalStatus.entries.forEach { status ->
            assertNotNull(status.toLocalizedResource(), "Resource missing for $status")
        }
        assertEquals(Res.string.goal_card_status_active, GoalStatus.IN_PROGRESS.toLocalizedResource())
        assertEquals(Res.string.goal_card_status_achieved, GoalStatus.ACHIEVED.toLocalizedResource())

        GoalDetailStatus.entries.forEach { status ->
            assertNotNull(status.toLocalizedResource(), "Resource missing for $status")
        }
        assertEquals(3, GoalDetailStatus.entries.size)
        assertEquals(Res.string.goal_card_status_active, GoalDetailStatus.ACTIVE.toLocalizedResource())
        assertEquals(Res.string.goal_card_status_achieved, GoalDetailStatus.ACHIEVED.toLocalizedResource())
        assertEquals(Res.string.goal_card_badge_past_due, GoalDetailStatus.PAST_DUE.toLocalizedResource())
    }
}
