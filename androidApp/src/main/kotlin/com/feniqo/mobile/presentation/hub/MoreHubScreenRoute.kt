package com.feniqo.mobile.presentation.hub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.presentation.screen.MoreHubScreen

@Composable
fun MoreHubScreenRoute(
    onNavigateToAssets: () -> Unit,
    onNavigateToGoals: () -> Unit,
    onNavigateToDebts: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    onNavigateToRecurringTransactions: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToSharedSpaces: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MoreHubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MoreHubScreen(
        state = state,
        onNavigateToAssets = onNavigateToAssets,
        onNavigateToGoals = onNavigateToGoals,
        onNavigateToDebts = onNavigateToDebts,
        onNavigateToSubscriptions = onNavigateToSubscriptions,
        onNavigateToRecurringTransactions = onNavigateToRecurringTransactions,
        onNavigateToCategories = onNavigateToCategories,
        onNavigateToSharedSpaces = onNavigateToSharedSpaces,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToProfile = onNavigateToProfile,
        modifier = modifier,
    )
}
