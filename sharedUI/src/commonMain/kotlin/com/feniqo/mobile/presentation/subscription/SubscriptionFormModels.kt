package com.feniqo.mobile.presentation.subscription

import com.feniqo.mobile.domain.model.Category
import com.feniqo.mobile.domain.model.CreateSubscriptionCommand
import com.feniqo.mobile.domain.model.EntityId
import com.feniqo.mobile.domain.model.LocalDate
import com.feniqo.mobile.domain.model.Money
import com.feniqo.mobile.domain.model.RecurrenceFrequency
import com.feniqo.mobile.domain.model.RecurrenceRule
import com.feniqo.mobile.domain.model.Subscription
import com.feniqo.mobile.domain.model.TransactionType
import com.feniqo.mobile.domain.model.UpdateSubscriptionCommand
import com.feniqo.mobile.presentation.common.FinanceUiMessage

/**
 * Düzenleme modu için SSOT veri yükleme durumları.
 */
sealed interface SubscriptionEditLoadState {
    data object Idle : SubscriptionEditLoadState
    data object Loading : SubscriptionEditLoadState
    data class Ready(
        val draft: SubscriptionFormDraft,
        val isActive: Boolean,
    ) : SubscriptionEditLoadState
    data object NotFound : SubscriptionEditLoadState
    data class Error(val message: FinanceUiMessage) : SubscriptionEditLoadState
}

/**
 * İlk composition anında edit ID mevcutken formun boş görünmesini önlemek için geçerli yükleme durumunu belirler.
 */
fun resolveEffectiveSubscriptionEditLoadState(
    subscriptionId: EntityId?,
    loadState: SubscriptionEditLoadState,
): SubscriptionEditLoadState {
    return if (subscriptionId != null && loadState is SubscriptionEditLoadState.Idle) {
        SubscriptionEditLoadState.Loading
    } else {
        loadState
    }
}

/**
 * Abonelik oluşturma veya düzenleme formunun saf taslak modelidir.
 */
data class SubscriptionFormDraft(
    val subscriptionId: EntityId? = null,
    val name: String,
    val amount: Money,
    val categoryId: EntityId? = null,
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val nextRenewalDate: LocalDate = startDate,
    val lifecycleStatus: com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus = com.feniqo.mobile.domain.model.SubscriptionLifecycleStatus.ACTIVE,
    val reminderEnabled: Boolean = true,
    val websiteUrl: String? = null,
    val notes: String? = null,
) {
    val isEditMode: Boolean get() = subscriptionId != null
    val isCreateMode: Boolean get() = subscriptionId == null

    /**
     * Taslaktan yeni abonelik oluşturma komutu üretir.
     * Edit modunda çağrılırsa fail-closed olarak IllegalStateException fırlatır.
     */
    fun toCreateCommand(): CreateSubscriptionCommand {
        check(isCreateMode) {
            "Düzenleme taslağından (ID: ${subscriptionId?.value}) create komutu üretilemez."
        }
        return CreateSubscriptionCommand(
            name = name.trim(),
            amount = amount,
            categoryId = categoryId,
            renewalRule = RecurrenceRule(
                frequency = frequency,
                interval = interval,
                startDate = startDate,
                endDate = endDate,
            ),
            nextRenewalDate = nextRenewalDate,
            lifecycleStatus = lifecycleStatus,
            reminderEnabled = reminderEnabled,
            websiteUrl = websiteUrl,
            notes = notes,
        )
    }

    /**
     * Taslaktan mevcut aboneliği güncelleme komutu üretir.
     * Create modunda çağrılırsa fail-closed olarak IllegalStateException fırlatır.
     */
    fun toUpdateCommand(): UpdateSubscriptionCommand {
        val targetId = checkNotNull(subscriptionId) {
            "Yeni kayıt taslağından update komutu üretilemez; geçerli bir subscriptionId gereklidir."
        }
        return UpdateSubscriptionCommand(
            id = targetId,
            name = name.trim(),
            amount = amount,
            categoryId = categoryId,
            renewalRule = RecurrenceRule(
                frequency = frequency,
                interval = interval,
                startDate = startDate,
                endDate = endDate,
            ),
            lifecycleStatus = lifecycleStatus,
            reminderEnabled = reminderEnabled,
            websiteUrl = websiteUrl,
            notes = notes,
        )
    }

    companion object {
        /**
         * Var olan bir domain [Subscription] nesnesinden düzenleme taslağı tohumlar (seed eder).
         */
        fun fromDomain(subscription: Subscription): SubscriptionFormDraft =
            SubscriptionFormDraft(
                subscriptionId = subscription.id,
                name = subscription.name,
                amount = subscription.amount,
                categoryId = subscription.categoryId,
                frequency = subscription.renewalRule.frequency,
                interval = subscription.renewalRule.interval,
                startDate = subscription.renewalRule.startDate,
                endDate = subscription.renewalRule.endDate,
                nextRenewalDate = subscription.nextRenewalDate,
                lifecycleStatus = subscription.lifecycleStatus,
                reminderEnabled = subscription.reminderEnabled,
                websiteUrl = subscription.websiteUrl,
                notes = subscription.notes,
            )
    }
}

/**
 * Abonelik formu UI durum modelidir.
 */
data class SubscriptionFormUiState(
    val draft: SubscriptionFormDraft,
    val availableCategories: List<Category> = emptyList(),
    val mutationState: SubscriptionMutationState = SubscriptionMutationState(),
    val activeWorkspaceName: String? = null,
) {
    val isEditMode: Boolean get() = draft.isEditMode
    val isCreateMode: Boolean get() = draft.isCreateMode
    val selectedCategory: Category? get() = draft.categoryId?.let { id -> availableCategories.firstOrNull { it.id == id } }
    val isSelectedCategoryMissing: Boolean get() = draft.categoryId != null && selectedCategory == null

    companion object {
        /**
         * Form taslağı ve tüm kategori listesinden, tür (yalnız EXPENSE) filtresini uygulayarak UI durumunu üretir.
         */
        fun create(
            draft: SubscriptionFormDraft,
            categories: List<Category>,
            mutationState: SubscriptionMutationState = SubscriptionMutationState(),
        ): SubscriptionFormUiState {
            val filteredCategories = categories.filter { it.type == TransactionType.EXPENSE }
            return SubscriptionFormUiState(
                draft = draft,
                availableCategories = filteredCategories,
                mutationState = mutationState,
            )
        }
    }
}

/**
 * Hızlı seçim için popüler abonelik şablonu.
 */
data class PopularSubscriptionTemplate(
    val name: String,
    val defaultCategoryKeywords: List<String>,
    val defaultWebsiteUrl: String,
    val defaultFrequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
)

val POPULAR_SUBSCRIPTION_TEMPLATES = listOf(
    PopularSubscriptionTemplate(
        name = "Netflix",
        defaultCategoryKeywords = listOf("eğlence", "entertainment", "medya", "yayın", "streaming"),
        defaultWebsiteUrl = "https://www.netflix.com",
    ),
    PopularSubscriptionTemplate(
        name = "Spotify",
        defaultCategoryKeywords = listOf("müzik", "music", "eğlence", "entertainment"),
        defaultWebsiteUrl = "https://www.spotify.com",
    ),
    PopularSubscriptionTemplate(
        name = "YouTube Premium",
        defaultCategoryKeywords = listOf("eğlence", "video", "medya", "entertainment"),
        defaultWebsiteUrl = "https://www.youtube.com/premium",
    ),
    PopularSubscriptionTemplate(
        name = "iCloud+",
        defaultCategoryKeywords = listOf("bulut", "teknoloji", "yazılım", "cloud", "hizmet"),
        defaultWebsiteUrl = "https://www.icloud.com",
    ),
    PopularSubscriptionTemplate(
        name = "Notion",
        defaultCategoryKeywords = listOf("üretkenlik", "productivity", "yazılım", "teknoloji"),
        defaultWebsiteUrl = "https://www.notion.so",
    ),
    PopularSubscriptionTemplate(
        name = "Amazon Prime",
        defaultCategoryKeywords = listOf("alışveriş", "eğlence", "shopping", "entertainment"),
        defaultWebsiteUrl = "https://www.amazon.com/prime",
    ),
    PopularSubscriptionTemplate(
        name = "Google One",
        defaultCategoryKeywords = listOf("bulut", "teknoloji", "cloud"),
        defaultWebsiteUrl = "https://one.google.com",
    ),
    PopularSubscriptionTemplate(
        name = "Disney+",
        defaultCategoryKeywords = listOf("eğlence", "medya", "streaming"),
        defaultWebsiteUrl = "https://www.disneyplus.com",
    ),
)
