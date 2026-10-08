package com.feniqo.mobile.presentation.common

import androidx.compose.runtime.Composable
import com.feniqo.mobile.domain.model.Currency
import feniqomobil.sharedui.generated.resources.Res
import feniqomobil.sharedui.generated.resources.currency_eur
import feniqomobil.sharedui.generated.resources.currency_gbp
import feniqomobil.sharedui.generated.resources.currency_try
import feniqomobil.sharedui.generated.resources.currency_usd
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun Currency.symbol(): String =
    when (this) {
        Currency.TRY -> "₺"
        Currency.USD -> "$"
        Currency.EUR -> "€"
        Currency.GBP -> "£"
    }

fun Currency.toLocalizedNameResource(): StringResource =
    when (this) {
        Currency.TRY -> Res.string.currency_try
        Currency.USD -> Res.string.currency_usd
        Currency.EUR -> Res.string.currency_eur
        Currency.GBP -> Res.string.currency_gbp
    }

@Composable
fun Currency.toLocalizedNameText(): String = stringResource(toLocalizedNameResource())
