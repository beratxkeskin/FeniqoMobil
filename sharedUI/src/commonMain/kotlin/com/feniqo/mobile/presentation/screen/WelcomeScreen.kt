package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feniqo.mobile.presentation.auth.AuthPrimaryButton
import com.feniqo.mobile.presentation.auth.AuthSecondaryButton
import com.feniqo.mobile.presentation.auth.AuthTopBar
import com.feniqo.mobile.presentation.auth.WelcomeHeroIllustration
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme

/**
 * Pano A - 01 Karşılama Ekranı.
 * Feniqo marka kimliği, yükselen çubuklar ve hedef illüstrasyonu ile
 * "Hesap oluştur" ve "Giriş yap" başlangıç eylemlerini sunar.
 */
@Composable
fun WelcomeScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            AuthTopBar(
                title = "Feniqo",
                onBack = null,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .padding(horizontal = FeniqoSpacing.Screen, vertical = FeniqoSpacing.Medium),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
                ) {
                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                    // 01 Karşılama illüstrasyonu
                    WelcomeHeroIllustration()

                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))

                    // Başlık ve Açıklama
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = "Paranı tanı.\nGeleceğini planla.",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 30.sp,
                                lineHeight = 36.sp,
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Harcamalarını, hedeflerini ve birikimlerini tek yerde takip et.",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                lineHeight = 22.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

                    // Aksiyonlar
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AuthPrimaryButton(
                            text = "Hesap oluştur",
                            onClick = onNavigateToRegister,
                        )

                        AuthSecondaryButton(
                            text = "Giriş yap",
                            onClick = onNavigateToLogin,
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun WelcomeScreenPreview() {
    FeniqoTheme {
        WelcomeScreen(
            onNavigateToRegister = {},
            onNavigateToLogin = {},
        )
    }
}
