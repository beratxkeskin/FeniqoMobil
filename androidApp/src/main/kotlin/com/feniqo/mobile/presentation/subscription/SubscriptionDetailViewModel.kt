package com.feniqo.mobile.presentation.subscription

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.SetSubscriptionLifecycleCommand
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus
import com.feniqo.mobile.domain.model.SubscriptionPayment
import com.feniqo.mobile.domain.model.SubscriptionPriceHistory
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.usecase.AdvanceSubscriptionRenewalUseCase
import com.feniqo.mobile.domain.usecase.DeleteSubscriptionUseCase
import com.feniqo.mobile.domain.usecase.ObserveActiveWorkspaceUseCase
import com.feniqo.mobile.domain.usecase.ObserveCategoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionPaymentsUseCase
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionPriceHistoriesUseCase
import com.feniqo.mobile.domain.usecase.ObserveSubscriptionUseCase
import com.feniqo.mobile.domain.usecase.SetSubscriptionLifecycleUseCase
import com.feniqo.mobile.domain.usecase.UpdateSubscriptionUseCase
import com.feniqo.mobile.navigation.SubscriptionDetailRoute
import com.feniqo.mobile.presentation.common.CurrentDateProvider
import com.feniqo.mobile.presentation.common.FinanceUiMessage
import com.feniqo.mobile.presentation.util.DateFormatter
import com.feniqo.mobile.presentation.util.MoneyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Month
import javax.inject.Inject

sealed interface SubscriptionDetailEvent {
    data object NavigateBack : SubscriptionDetailEvent
    data class ShowMessage(val message: FinanceUiMessage) : SubscriptionDetailEvent
}

/**
 * Görsel 2 Abonelik Detay Ekranı'nın reaktif SSOT ViewModel'idir.
 */
@HiltViewModel
class SubscriptionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeSubscriptionUseCase: ObserveSubscriptionUseCase,
    private val observeCategoriesUseCase: ObserveCategoriesUseCase,
    private val observeSubscriptionPriceHistoriesUseCase: ObserveSubscriptionPriceHistoriesUseCase,
    private val observeSubscriptionPaymentsUseCase: ObserveSubscriptionPaymentsUseCase,
    private val observeActiveWorkspaceUseCase: ObserveActiveWorkspaceUseCase,
    private val advanceSubscriptionRenewalUseCase: AdvanceSubscriptionRenewalUseCase,
    private val setSubscriptionLifecycleUseCase: SetSubscriptionLifecycleUseCase,
    private val updateSubscriptionUseCase: UpdateSubscriptionUseCase,
    private val deleteSubscriptionUseCase: DeleteSubscriptionUseCase,
    private val currentDateProvider: CurrentDateProvider,
) : ViewModel() {

    val subscriptionEntityId: EntityId? = (savedStateHandle.get<String>("subscriptionId")
        ?: runCatching { savedStateHandle.toRoute<SubscriptionDetailRoute>().subscriptionId }.getOrNull())
        ?.trim()?.takeIf { it.isNotEmpty() }?.let {
            runCatching { EntityId(it) }.getOrNull()
        }

    private val _uiState = MutableStateFlow(
        SubscriptionDetailUiState(
            isLoading = true,
            isNotFound = subscriptionEntityId == null,
        ),
    )
    val uiState: StateFlow<SubscriptionDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<SubscriptionDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        observeSubscriptionDetail()
    }

    private fun observeSubscriptionDetail() {
        val targetId = subscriptionEntityId
        if (targetId == null) {
            _uiState.update { it.copy(isLoading = false, isNotFound = true) }
            return
        }

        viewModelScope.launch {
            try {
                combine(
                    observeSubscriptionUseCase(targetId),
                    observeCategoriesUseCase(),
                    observeSubscriptionPriceHistoriesUseCase(),
                    observeSubscriptionPaymentsUseCase(),
                    observeActiveWorkspaceUseCase(),
                ) { subscription, categories, allPriceHistories, allPayments, activeWorkspace ->
                    if (subscription == null) {
                        _uiState.update { it.copy(isLoading = false, isNotFound = true) }
                    } else {
                        val category = subscription.categoryId?.let { catId ->
                            categories.firstOrNull { it.id == catId }
                        }
                        val matchingPriceHistories = allPriceHistories.filter { it.subscriptionId == targetId }
                        val matchingPayments = allPayments.filter { it.subscriptionId == targetId }
                        val today = currentDateProvider.today()
                        val monthlyBars = computeMonthlyBars(matchingPayments, subscription.amount.currency, today)
                        val recentPayments = computeRecentPayments(matchingPayments)

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isNotFound = false,
                                subscription = subscription,
                                category = category,
                                priceHistories = matchingPriceHistories,
                                payments = matchingPayments,
                                monthlyChartBars = monthlyBars,
                                recentPayments = recentPayments,
                                activeWorkspaceName = activeWorkspace?.name,
                                errorMessage = null,
                            )
                        }
                    }
                }.catch { e ->
                    if (e is CancellationException) throw e
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message,
                        )
                    }
                }.collect {}
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message)
                }
            }
        }
    }

    /**
     * Vadesi gelmiş yenilemeyi ödendi olarak işaretler, sonraki vadeye ilerletir ve ödeme kaydı ekler.
     */
    fun onAdvanceRenewal() {
        val targetId = subscriptionEntityId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionSubmitting = true) }
            when (advanceSubscriptionRenewalUseCase(targetId)) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(isActionSubmitting = false) }
                    _events.send(SubscriptionDetailEvent.ShowMessage(FinanceUiMessage.SUBSCRIPTION_RENEWED))
                }
                is RepositoryResult.Failure -> {
                    _uiState.update { it.copy(isActionSubmitting = false) }
                    _events.send(SubscriptionDetailEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                }
            }
        }
    }

    /**
     * Aboneliği duraklatır veya devam ettirir.
     */
    fun onToggleLifecycle() {
        val targetId = subscriptionEntityId ?: return
        val currentSub = _uiState.value.subscription ?: return
        val newStatus = if (currentSub.lifecycleStatus == SubscriptionLifecycleStatus.PAUSED) {
            SubscriptionLifecycleStatus.ACTIVE
        } else {
            SubscriptionLifecycleStatus.PAUSED
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isActionSubmitting = true) }
            when (setSubscriptionLifecycleUseCase(SetSubscriptionLifecycleCommand(targetId, newStatus))) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(isActionSubmitting = false) }
                    _events.send(SubscriptionDetailEvent.ShowMessage(FinanceUiMessage.SUBSCRIPTION_SAVED))
                }
                is RepositoryResult.Failure -> {
                    _uiState.update { it.copy(isActionSubmitting = false) }
                    _events.send(SubscriptionDetailEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                }
            }
        }
    }

    /**
     * Aboneliği iptal durumuna alır.
     */
    fun onCancelSubscription() {
        val targetId = subscriptionEntityId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionSubmitting = true) }
            when (setSubscriptionLifecycleUseCase(SetSubscriptionLifecycleCommand(targetId, SubscriptionLifecycleStatus.CANCELLED))) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(isActionSubmitting = false) }
                    _events.send(SubscriptionDetailEvent.ShowMessage(FinanceUiMessage.SUBSCRIPTION_SAVED))
                }
                is RepositoryResult.Failure -> {
                    _uiState.update { it.copy(isActionSubmitting = false) }
                    _events.send(SubscriptionDetailEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                }
            }
        }
    }

    /**
     * Hatırlatıcı tercihini açar veya kapatır.
     */
    fun onToggleReminder() {
        val currentSub = _uiState.value.subscription ?: return
        val newReminderEnabled = !currentSub.reminderEnabled
        val command = UpdateSubscriptionCommand(
            id = currentSub.id,
            name = currentSub.name,
            amount = currentSub.amount,
            categoryId = currentSub.categoryId,
            renewalRule = currentSub.renewalRule,
            lifecycleStatus = currentSub.lifecycleStatus,
            reminderEnabled = newReminderEnabled,
            websiteUrl = currentSub.websiteUrl,
            notes = currentSub.notes,
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isActionSubmitting = true) }
            when (updateSubscriptionUseCase(command)) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(isActionSubmitting = false) }
                    _events.send(SubscriptionDetailEvent.ShowMessage(FinanceUiMessage.SUBSCRIPTION_SAVED))
                }
                is RepositoryResult.Failure -> {
                    _uiState.update { it.copy(isActionSubmitting = false) }
                    _events.send(SubscriptionDetailEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                }
            }
        }
    }

    /**
     * Aboneliği siler ve sayfadan geri çıkar.
     */
    fun onDeleteSubscription() {
        val targetId = subscriptionEntityId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionSubmitting = true) }
            when (deleteSubscriptionUseCase(targetId)) {
                is RepositoryResult.Success -> {
                    _uiState.update { it.copy(isActionSubmitting = false) }
                    _events.send(SubscriptionDetailEvent.NavigateBack)
                }
                is RepositoryResult.Failure -> {
                    _uiState.update { it.copy(isActionSubmitting = false) }
                    _events.send(SubscriptionDetailEvent.ShowMessage(FinanceUiMessage.GENERIC_ERROR))
                }
            }
        }
    }

    /**
     * Son 6 ayın gerçek ödemelerinden çubuk grafik modellerini üretir.
     */
    private fun computeMonthlyBars(
        payments: List<SubscriptionPayment>,
        currency: com.feniqo.mobile.domain.model.Currency,
        today: LocalDate,
    ): List<SubscriptionMonthlyBarModel> {
        val currentYear = today.year
        val currentMonthNum = today.month.ordinal + 1

        val months = (5 downTo 0).map { offset ->
            var targetMonth = currentMonthNum - offset
            var targetYear = currentYear
            while (targetMonth <= 0) {
                targetMonth += 12
                targetYear -= 1
            }
            Pair(targetYear, targetMonth)
        }

        val monthlySums = months.map { (year, monthNum) ->
            val monthPayments = payments.filter { p ->
                p.paymentDate.year == year && (p.paymentDate.month.ordinal + 1) == monthNum
            }
            val totalMinor = monthPayments.sumOf { it.amount.amountMinor }
            Triple(year, monthNum, totalMinor)
        }

        val maxAmount = monthlySums.maxOfOrNull { it.third }?.coerceAtLeast(1L) ?: 1L

        return monthlySums.map { (year, monthNum, totalMinor) ->
            val monthEnum = Month.entries[monthNum - 1]
            val monthLabel = DateFormatter.formatShortMonth(monthEnum)
            val isCurrent = (year == currentYear && monthNum == currentMonthNum)
            val ratio = if (totalMinor > 0L) (totalMinor.toFloat() / maxAmount.toFloat()).coerceIn(0.15f, 1f) else 0f
            val formatted = if (totalMinor > 0L) MoneyFormatter.format(Money(totalMinor, currency)) else "0"

            SubscriptionMonthlyBarModel(
                monthLabel = monthLabel,
                year = year,
                monthNumber = monthNum,
                amountMinor = totalMinor,
                formattedAmount = formatted,
                isCurrentMonth = isCurrent,
                ratio = ratio,
            )
        }
    }

    /**
     * En son gerçekleşen ödemelerden ilk 5 tanesini hazırlar.
     */
    private fun computeRecentPayments(
        payments: List<SubscriptionPayment>,
    ): List<SubscriptionRecentPaymentModel> {
        return payments.sortedByDescending { it.paymentDate }
            .take(5)
            .map { p ->
                SubscriptionRecentPaymentModel(
                    id = p.id.value,
                    formattedDate = DateFormatter.formatReadableDate(p.paymentDate),
                    formattedAmount = MoneyFormatter.format(p.amount),
                    isManual = p.sourceType == com.feniqo.mobile.domain.model.SubscriptionPaymentSourceType.MANUAL,
                )
            }
    }
}
