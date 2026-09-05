package com.feniqo.mobile.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.feniqo.mobile.domain.model.Goal
import com.feniqo.mobile.domain.model.GoalContributionDirection
import com.feniqo.mobile.presentation.goal.GoalContributionFormFieldError
import com.feniqo.mobile.presentation.goal.GoalContributionFormInput
import com.feniqo.mobile.presentation.goal.GoalContributionFormInputErrors
import com.feniqo.mobile.presentation.theme.FeniqoRadius
import com.feniqo.mobile.presentation.theme.FeniqoSpacing
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter

@Composable
fun GoalContributionFormScreen(
    parentGoal: Goal,
    input: GoalContributionFormInput,
    errors: GoalContributionFormInputErrors,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onAmountChange: (String) -> Unit,
    onDirectionChange: (GoalContributionDirection) -> Unit,
    onDateClick: () -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEnabled = !isSubmitting

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeniqoSpacing.Large),
        ) {
            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Üst Başlık
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
            ) {
                TextButton(
                    onClick = onBack,
                    enabled = isEnabled,
                ) {
                    Text("← Geri")
                }
                Text(
                    text = "Hedef Hareketi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(modifier = Modifier.height(FeniqoSpacing.Medium))

            // Form Gövdesi
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.Large),
            ) {
                // Parent Goal Bilgi Kartı
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(FeniqoRadius.Medium),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(FeniqoSpacing.Medium),
                        verticalArrangement = Arrangement.spacedBy(FeniqoSpacing.ExtraSmall),
                    ) {
                        Text(
                            text = parentGoal.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Mevcut: ${MoneyFormatter.format(parentGoal.currentAmount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "Hedef: ${MoneyFormatter.format(parentGoal.targetAmount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Hareket Yönü Seçimi (Ekle / Çıkar)
                Column {
                    Text(
                        text = "İşlem Yönü",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(FeniqoSpacing.Small))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FeniqoSpacing.Small),
                    ) {
                        FilterChip(
                            selected = input.direction == GoalContributionDirection.ADD,
                            onClick = { onDirectionChange(GoalContributionDirection.ADD) },
                            label = { Text("+ Para Ekle") },
                            enabled = isEnabled,
                        )
                        FilterChip(
                            selected = input.direction == GoalContributionDirection.REMOVE,
                            onClick = { onDirectionChange(GoalContributionDirection.REMOVE) },
                            label = { Text("- Para Çıkar") },
                            enabled = isEnabled,
                        )
                    }
                }

                // Tutar Alanı
                OutlinedTextField(
                    value = input.amountInput,
                    onValueChange = onAmountChange,
                    label = { Text("Tutar *") },
                    placeholder = { Text("0,00") },
                    trailingIcon = {
                        Text(
                            text = parentGoal.targetAmount.currency.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = FeniqoSpacing.Medium),
                        )
                    },
                    isError = errors.amountError != null,
                    supportingText = {
                        if (errors.amountError != null) {
                            Text(
                                text = when (errors.amountError) {
                                    GoalContributionFormFieldError.AMOUNT_REQUIRED -> "Tutar zorunludur."
                                    GoalContributionFormFieldError.AMOUNT_NON_POSITIVE -> "Tutar sıfırdan büyük olmalıdır."
                                    GoalContributionFormFieldError.EXCEEDS_CURRENT_AMOUNT -> "Çıkarılacak tutar mevcut birikimi (${MoneyFormatter.format(parentGoal.currentAmount)}) aşamaz."
                                    GoalContributionFormFieldError.CURRENCY_MISMATCH -> "Para birimi uyuşmuyor."
                                    else -> "Geçersiz tutar."
                                },
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else if (input.direction == GoalContributionDirection.REMOVE) {
                            Text(
                                text = "Maksimum çekilebilir: ${MoneyFormatter.format(parentGoal.currentAmount)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    singleLine = true,
                    enabled = isEnabled,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Tarih Seçici
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(FeniqoRadius.Medium))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .clickable(
                            role = Role.Button,
                            enabled = isEnabled,
                            onClick = onDateClick,
                        )
                        .padding(FeniqoSpacing.Medium),
                ) {
                    Text(
                        text = "İşlem Tarihi *",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(FeniqoSpacing.ExtraSmall))
                    Text(
                        text = input.occurredOn?.let { DateFormatter.formatReadableDate(it) } ?: "Tarih Seçin",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (input.occurredOn != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                }
                if (errors.dateError != null) {
                    Text(
                        text = "İşlem tarihi seçilmelidir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = FeniqoSpacing.Small),
                    )
                }

                // Not Alanı (Opsiyonel)
                OutlinedTextField(
                    value = input.noteInput,
                    onValueChange = onNoteChange,
                    label = { Text("Not (Opsiyonel)") },
                    placeholder = { Text("Örn: Maaş primi, ikramiye") },
                    isError = errors.noteError != null,
                    supportingText = {
                        errors.noteError?.let {
                            Text(
                                text = "Not en fazla 500 karakter olabilir.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    singleLine = true,
                    enabled = isEnabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(FeniqoSpacing.Large))
            }

            // Kaydet Butonu
            Button(
                onClick = onSubmit,
                enabled = isEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = FeniqoSpacing.Medium),
                shape = RoundedCornerShape(FeniqoRadius.Medium),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = if (input.direction == GoalContributionDirection.ADD) "Para Ekle" else "Para Çıkar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
