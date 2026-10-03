package com.feniqo.mobile.presentation.localization

import androidx.compose.runtime.Composable

@Composable
expect fun provideAppLocale(
    languageTag: String?,
    content: @Composable () -> Unit,
)
