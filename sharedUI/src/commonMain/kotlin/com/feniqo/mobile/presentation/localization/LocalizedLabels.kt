package com.feniqo.mobile.presentation.localization

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.ThemePreference
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.account_password_updated_toast
import feniqomobil.sharedui.generated.resources.email_verification_resend_success
import feniqomobil.sharedui.generated.resources.feedback_attachment_fallback_filename
import feniqomobil.sharedui.generated.resources.feedback_share_chooser_title
import feniqomobil.sharedui.generated.resources.feedback_share_no_app_error
import feniqomobil.sharedui.generated.resources.feedback_share_subject_bug_format
import feniqomobil.sharedui.generated.resources.feedback_share_subject_suggestion_format
import feniqomobil.sharedui.generated.resources.language_english
import feniqomobil.sharedui.generated.resources.language_region_summary
import feniqomobil.sharedui.generated.resources.language_turkish
import feniqomobil.sharedui.generated.resources.theme_dark
import feniqomobil.sharedui.generated.resources.theme_light
import feniqomobil.sharedui.generated.resources.theme_system
import org.jetbrains.compose.resources.stringResource

@Composable
fun localizedThemeLabel(theme: ThemePreference): String =
    when (theme) {
        ThemePreference.SYSTEM -> stringResource(Res.string.theme_system)
        ThemePreference.LIGHT -> stringResource(Res.string.theme_light)
        ThemePreference.DARK -> stringResource(Res.string.theme_dark)
    }

@Composable
fun localizedLanguageName(language: AppLanguage): String =
    when (language) {
        AppLanguage.TR -> stringResource(Res.string.language_turkish)
        AppLanguage.EN -> stringResource(Res.string.language_english)
    }

@Composable
fun localizedLanguageRegionSummary(
    language: AppLanguage,
    currencyCode: String,
): String =
    stringResource(
        Res.string.language_region_summary,
        localizedLanguageName(language),
        currencyCode,
    )

@Composable
fun localizedSettingsLabels(
    theme: ThemePreference,
    language: AppLanguage,
    currencyCode: String,
): Pair<String, String> =
    localizedThemeLabel(theme) to localizedLanguageRegionSummary(language, currencyCode)

@Composable
fun localizedEmailVerificationResendSuccess(): String = stringResource(Res.string.email_verification_resend_success)

@Composable
fun localizedAccountPasswordUpdated(): String = stringResource(Res.string.account_password_updated_toast)

data class LocalizedFeedbackShareStrings(
    val bugSubjectFormat: String,
    val suggestionSubjectFormat: String,
    val chooserTitle: String,
    val noAppErrorMessage: String,
    val defaultAttachmentFallback: String,
)

@Composable
fun localizedFeedbackShareStrings(): LocalizedFeedbackShareStrings =
    LocalizedFeedbackShareStrings(
        bugSubjectFormat = stringResource(Res.string.feedback_share_subject_bug_format),
        suggestionSubjectFormat = stringResource(Res.string.feedback_share_subject_suggestion_format),
        chooserTitle = stringResource(Res.string.feedback_share_chooser_title),
        noAppErrorMessage = stringResource(Res.string.feedback_share_no_app_error),
        defaultAttachmentFallback = stringResource(Res.string.feedback_attachment_fallback_filename),
    )

fun resolveFeedbackShareSubject(
    isBug: Boolean,
    subject: String,
    bugFormat: String,
    suggestionFormat: String,
): String {
    val template = if (isBug) bugFormat else suggestionFormat
    return String.format(template, subject)
}
