package com.feniqo.mobile.presentation.settings

import androidx.compose.runtime.Composable
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.account_login_method_account
import feniqomobil.sharedui.generated.resources.account_login_method_email
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

enum class AccountLoginMethod {
    EMAIL,
    ACCOUNT;

    fun toStringResource(): StringResource = when (this) {
        EMAIL -> Res.string.account_login_method_email
        ACCOUNT -> Res.string.account_login_method_account
    }
}

@Composable
fun AccountLoginMethod.toLocalizedText(): String = stringResource(toStringResource())

suspend fun AccountLoginMethod.resolveLocalizedText(): String = getString(toStringResource())
