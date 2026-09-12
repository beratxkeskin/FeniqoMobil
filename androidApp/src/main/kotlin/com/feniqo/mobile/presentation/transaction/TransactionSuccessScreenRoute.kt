package com.feniqo.mobile.presentation.transaction

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.presentation.screen.TransactionSuccessScreen

/**
 * Android Navigation Compose adaptörü: Başarı ekranını TransactionSuccessViewModel'e bağlar.
 */
@Composable
fun TransactionSuccessScreenRoute(
    onAddNewTransaction: (TransactionType) -> Unit,
    onViewTransaction: (EntityId) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionSuccessViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        onClose()
    }

    TransactionSuccessScreen(
        uiState = state,
        onAddNewTransaction = onAddNewTransaction,
        onViewTransaction = onViewTransaction,
        onClose = onClose,
        modifier = modifier,
    )
}
