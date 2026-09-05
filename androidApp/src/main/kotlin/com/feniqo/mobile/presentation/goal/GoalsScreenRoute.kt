package com.feniqo.mobile.presentation.goal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.screen.GoalsScreen

/**
 * Android Jetpack Compose Navigation için Goals rotası adaptörüdür.
 * Hilt GoalsViewModel'e bağlanır, UI state'ini toplar ve stateless GoalsScreen'e aktarır.
 */
@Composable
fun GoalsScreenRoute(
    onAddGoal: () -> Unit,
    onGoalClick: (EntityId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        GoalsScreen(
            state = state,
            onRetry = { viewModel.onIntent(GoalsIntent.Retry) },
            onAddGoal = onAddGoal,
            onGoalClick = onGoalClick,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
