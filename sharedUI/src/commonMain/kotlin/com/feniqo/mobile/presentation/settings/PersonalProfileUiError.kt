package com.feniqo.mobile.presentation.settings

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.validation.AuthValidationRules
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.profile_error_name_too_short
import feniqomobil.sharedui.generated.resources.profile_error_save_failed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

enum class PersonalProfileUiError {
    NAME_TOO_SHORT,
    SAVE_FAILED;

    fun toStringResource(): StringResource = when (this) {
        NAME_TOO_SHORT -> Res.string.profile_error_name_too_short
        SAVE_FAILED -> Res.string.profile_error_save_failed
    }
}

@Composable
fun PersonalProfileUiError.toLocalizedText(): String = when (this) {
    PersonalProfileUiError.NAME_TOO_SHORT -> stringResource(
        Res.string.profile_error_name_too_short,
        AuthValidationRules.MIN_FULL_NAME_LENGTH,
    )
    PersonalProfileUiError.SAVE_FAILED -> stringResource(Res.string.profile_error_save_failed)
}

suspend fun PersonalProfileUiError.resolveLocalizedText(): String = when (this) {
    PersonalProfileUiError.NAME_TOO_SHORT -> getString(
        Res.string.profile_error_name_too_short,
        AuthValidationRules.MIN_FULL_NAME_LENGTH,
    )
    PersonalProfileUiError.SAVE_FAILED -> getString(Res.string.profile_error_save_failed)
}
