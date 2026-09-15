package com.feniqo.mobile.presentation.subscription

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.screen.SubscriptionDetailScreen

/**
 * Android Navigation Compose rotası adaptörüdür.
 * ViewModel ve platform bağımlılıklarını (UriHandler, Toast mesajları) bağlar.
 */
@Composable
fun SubscriptionDetailScreenRoute(
    viewModel: SubscriptionDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onMessage: (FinanceUiMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is SubscriptionDetailEvent.NavigateBack -> onNavigateBack()
                is SubscriptionDetailEvent.ShowMessage -> onMessage(event.message)
            }
        }
    }

    SubscriptionDetailScreen(
        uiState = uiState,
        onBack = onNavigateBack,
        onEditClick = {
            viewModel.subscriptionEntityId?.let { id ->
                onNavigateToEdit(id.value)
            }
        },
        onToggleLifecycleClick = {
            viewModel.onToggleLifecycle()
        },
        onCancelClick = {
            viewModel.onCancelSubscription()
        },
        onToggleReminderClick = {
            viewModel.onToggleReminder()
        },
        onAdvanceRenewal = {
            viewModel.onAdvanceRenewal()
        },
        onManageSubscription = {
            val url = uiState.websiteUrl
            if (!url.isNullOrBlank()) {
                val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    "https://$url"
                } else {
                    url
                }
                runCatching {
                    uriHandler.openUri(formattedUrl)
                }.onFailure {
                    onMessage(FinanceUiMessage.GENERIC_ERROR)
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
