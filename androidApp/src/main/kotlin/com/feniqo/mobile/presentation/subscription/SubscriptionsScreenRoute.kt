package com.feniqo.mobile.presentation.subscription

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.presentation.screen.SubscriptionsScreen
import com.feniqo.mobile.presentation.theme.FeniqoSpacing

/**
 * Android Jetpack Compose Navigation için Subscriptions rotası adaptörüdür.
 * Hilt SubscriptionsViewModel'e bağlanır, UI state'ini toplar ve stateless SubscriptionsScreen'e aktarır.
 * Android 13+ (API 33+) cihazlarda kullanıcıya bildirim izni isteme CTA banner'ı sunar.
 */
@Composable
fun SubscriptionsScreenRoute(
    onAddSubscription: () -> Unit,
    onSubscriptionClick: (EntityId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var isPermissionGranted by remember {
        mutableStateOf(checkNotificationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        isPermissionGranted = granted
    }

    val showPermissionBanner = SubscriptionNotificationPermissionHelper.shouldShowPermissionPrompt(
        sdkInt = Build.VERSION.SDK_INT,
        isPermissionGranted = isPermissionGranted,
    )

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        SubscriptionsScreen(
            state = state,
            onRetry = { viewModel.onIntent(SubscriptionsIntent.Retry) },
            onAddSubscription = onAddSubscription,
            onSubscriptionClick = onSubscriptionClick,
            modifier = Modifier.fillMaxSize(),
            bannerContent = if (showPermissionBanner) {
                {
                    SubscriptionNotificationPermissionBanner(
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                }
            } else {
                null
            },
        )
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return true
    }
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun SubscriptionNotificationPermissionBanner(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FeniqoSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
        ) {
            Text(
                text = "Abonelik hatırlatıcılarını aç",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Yaklaşan ve vadesi gelen abonelikler için bildirim al.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Bildirimlere izin ver")
            }
        }
    }
}
