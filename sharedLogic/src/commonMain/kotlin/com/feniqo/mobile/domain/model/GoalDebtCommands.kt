package com.feniqo.mobile.domain.model

/**
 * Yeni birikim hedefi oluşturma komutu.
 * UI'dan yalnızca kullanıcı tarafından belirlenen değerleri taşır;
 * oturum (ownerId), workspaceId, createdAt ve başlangıç birikim tutarı repository katmanında atanır.
 */
data class CreateGoalCommand(
    val name: String,
    val targetAmount: Money,
    val initialAmount: Money? = null,
    val targetDate: LocalDate,
    val color: CategoryColor,
    val icon: CategoryIcon? = null,
)

/**
 * Mevcut birikim hedefini güncelleme komutu.
 * Yalnızca kullanıcı tarafından değiştirilebilir meta alanları ve hedef tutarını taşır.
 * Mevcut birikim tutarı (currentAmount) doğrudan bu komutla değil, katkı hareketleriyle güncellenir.
 */
data class UpdateGoalCommand(
    val id: EntityId,
    val name: String,
    val targetAmount: Money,
    val targetDate: LocalDate,
    val color: CategoryColor,
    val icon: CategoryIcon? = null,
)

/**
 * Hedefe para ekleme veya hedeften para çıkarma komutu.
 */
data class AddGoalContributionCommand(
    val goalId: EntityId,
    val amount: Money,
    val direction: GoalContributionDirection = GoalContributionDirection.ADD,
    val occurredOn: LocalDate,
    val note: String? = null,
)

/**
 * Yeni borç veya alacak kaydı oluşturma komutu.
 */
data class CreateDebtCommand(
    val title: String,
    val amount: Money,
    val type: DebtType,
    val dueDate: LocalDate,
    val description: String? = null,
)

/**
 * Mevcut borç veya alacak kaydını güncelleme komutu.
 */
data class UpdateDebtCommand(
    val id: EntityId,
    val title: String,
    val amount: Money,
    val type: DebtType,
    val dueDate: LocalDate,
    val description: String? = null,
)

/**
 * Borç veya alacağa ödeme / tahsilat ekleme komutu.
 */
data class AddDebtPaymentCommand(
    val debtId: EntityId,
    val amount: Money,
    val paidOn: LocalDate,
)
