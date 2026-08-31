package com.feniqo.mobile.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.theme.FeniqoTheme

/**
 * İşlem formunda makbuz bağlama, değiştirme ve kaldırma durumlarını yöneten stateless bileşendir.
 * Platformdan bağımsızdır; dosya seçimi ve yükleme gibi platform işlemleri üst katmandan sağlanan callback'lerle yürütülür.
 */
@Composable
fun ReceiptAttachmentSection(
    hasReceipt: Boolean,
    isActionInProgress: Boolean,
    onAttachReceipt: () -> Unit,
    onRemoveReceipt: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isButtonInteractive = enabled && !isActionInProgress

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
    ) {
        Text(
            text = "Makbuz (İsteğe Bağlı)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (!hasReceipt) {
            // Makbuz bağlı değilken: Makbuz Ekle aksiyonu
            OutlinedButton(
                onClick = onAttachReceipt,
                enabled = isButtonInteractive,
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics { contentDescription = "Makbuz ekle" },
            ) {
                if (isActionInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(FeniqoSpacing.Small))
                    Text(
                        text = "İşleniyor...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    Text(
                        text = "Makbuz Ekle",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        } else {
            // Makbuz bağlıyken: Durum kartı, Değiştir ve Kaldır aksiyonları
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(FeniqoSpacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(FeniqoRadius.Small),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.defaultMinSize(minWidth = 24.dp, minHeight = 24.dp),
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "✓",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            Text(
                                text = "Makbuz bağlı",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }

                        if (isActionInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onRemoveReceipt,
                            enabled = isButtonInteractive,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
                                .semantics { contentDescription = "Makbuzu kaldır" },
                        ) {
                            Text(
                                text = "Kaldır",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isButtonInteractive) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        Spacer(modifier = Modifier.size(FeniqoSpacing.ExtraSmall))

                        OutlinedButton(
                            onClick = onAttachReceipt,
                            enabled = isButtonInteractive,
                            shape = RoundedCornerShape(FeniqoRadius.Small),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
                                .semantics { contentDescription = "Makbuzu değiştir" },
                        ) {
                            Text(
                                text = "Değiştir",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// Previews
// -------------------------------------------------------------------------

@Preview(name = "Receipt Attachment - No Receipt Light", showBackground = true)
@Composable
private fun ReceiptAttachmentSectionNoReceiptLightPreview() {
    FeniqoTheme(darkTheme = false) {
        Surface(modifier = Modifier.padding(FeniqoSpacing.Large)) {
            ReceiptAttachmentSection(
                hasReceipt = false,
                isActionInProgress = false,
                onAttachReceipt = {},
                onRemoveReceipt = {},
            )
        }
    }
}

@Preview(name = "Receipt Attachment - No Receipt Dark", showBackground = true)
@Composable
private fun ReceiptAttachmentSectionNoReceiptDarkPreview() {
    FeniqoTheme(darkTheme = true) {
        Surface(modifier = Modifier.padding(FeniqoSpacing.Large)) {
            ReceiptAttachmentSection(
                hasReceipt = false,
                isActionInProgress = false,
                onAttachReceipt = {},
                onRemoveReceipt = {},
            )
        }
    }
}

@Preview(name = "Receipt Attachment - With Receipt Light", showBackground = true)
@Composable
private fun ReceiptAttachmentSectionWithReceiptLightPreview() {
    FeniqoTheme(darkTheme = false) {
        Surface(modifier = Modifier.padding(FeniqoSpacing.Large)) {
            ReceiptAttachmentSection(
                hasReceipt = true,
                isActionInProgress = false,
                onAttachReceipt = {},
                onRemoveReceipt = {},
            )
        }
    }
}

@Preview(name = "Receipt Attachment - With Receipt Dark", showBackground = true)
@Composable
private fun ReceiptAttachmentSectionWithReceiptDarkPreview() {
    FeniqoTheme(darkTheme = true) {
        Surface(modifier = Modifier.padding(FeniqoSpacing.Large)) {
            ReceiptAttachmentSection(
                hasReceipt = true,
                isActionInProgress = false,
                onAttachReceipt = {},
                onRemoveReceipt = {},
            )
        }
    }
}

@Preview(name = "Receipt Attachment - In Progress Light", showBackground = true)
@Composable
private fun ReceiptAttachmentSectionInProgressLightPreview() {
    FeniqoTheme(darkTheme = false) {
        Surface(modifier = Modifier.padding(FeniqoSpacing.Large)) {
            ReceiptAttachmentSection(
                hasReceipt = false,
                isActionInProgress = true,
                onAttachReceipt = {},
                onRemoveReceipt = {},
            )
        }
    }
}
