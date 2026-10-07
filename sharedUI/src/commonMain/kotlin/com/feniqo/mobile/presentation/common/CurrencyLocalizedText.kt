@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.feniqo.mobile.presentation.common

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.Currency
import feniqomobil.sharedui.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun Currency.toLocalizedNameResource(): StringResource =
    when (this) {
        Currency.TRY -> Res.string.currency_try
        Currency.USD -> Res.string.currency_usd
        Currency.EUR -> Res.string.currency_eur
        Currency.GBP -> Res.string.currency_gbp
    }

@Composable
fun Currency.toLocalizedNameText(): String = stringResource(toLocalizedNameResource())
