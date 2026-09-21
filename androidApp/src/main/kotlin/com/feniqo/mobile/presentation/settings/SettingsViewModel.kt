package com.feniqo.mobile.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feniqo.mobile.data.backup.BackupDecodeResult
import com.feniqo.mobile.data.backup.BackupImportResult
import com.feniqo.mobile.data.backup.BackupScope
import com.feniqo.mobile.data.backup.FeniqoBackupCodec
import com.feniqo.mobile.data.backup.PersonalBackupExporter
import com.feniqo.mobile.data.backup.PersonalBackupImporter
import com.feniqo.mobile.domain.model.AppLanguage
import com.feniqo.mobile.domain.model.Currency
import com.feniqo.mobile.domain.model.DateFormatPreference
import com.feniqo.mobile.domain.model.FirstDayOfWeekPreference
import com.feniqo.mobile.domain.model.NotificationPreferences
import com.feniqo.mobile.domain.model.NumberFormatPreference
import com.feniqo.mobile.domain.model.ThemePreference
import com.feniqo.mobile.domain.model.UserProfile
import com.feniqo.mobile.domain.model.UserSettings
import com.feniqo.mobile.domain.repository.AuthRepository
import com.feniqo.mobile.domain.repository.RepositoryResult
import com.feniqo.mobile.domain.repository.SyncOverview
import com.feniqo.mobile.domain.repository.SyncPhase
import com.feniqo.mobile.domain.repository.UserSettingsRepository
import com.feniqo.mobile.domain.usecase.ObserveSyncOverviewUseCase
import com.feniqo.mobile.domain.usecase.ObserveTransactionsUseCase
import com.feniqo.mobile.domain.usecase.TransactionCsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BackupExportResult {
    data class Success(val json: String, val scope: BackupScope) : BackupExportResult
    data class Failure(val reason: String) : BackupExportResult
}

sealed interface BackupPreviewResult {
    data class Valid(val categoryCount: Int, val transactionCount: Int) : BackupPreviewResult
    data class Invalid(val reason: String) : BackupPreviewResult
}

data class SettingsUiState(
    val profile: UserProfile? = null,
    val settings: UserSettings = UserSettings(),
    val syncOverview: SyncOverview = SyncOverview(
        phase = SyncPhase.IDLE,
        pendingOperationCount = 0,
        failedOperationCount = 0,
        conflictCount = 0,
        lastSuccessfulSyncAt = null,
        lastError = null,
    ),
    val backupScope: BackupScope = BackupScope(0, 0),
    val isSavingProfile: Boolean = false,
    val profileSaveSuccess: Boolean = false,
    val profileError: String? = null,
    val passwordError: String? = null,
    val passwordSuccess: Boolean = false,
    val isChangingPassword: Boolean = false,
    val sessionEmail: String = "",
    val emailVerificationStatus: com.feniqo.mobile.domain.model.EmailVerificationStatus = com.feniqo.mobile.domain.model.EmailVerificationStatus.UNKNOWN,
    val emailError: String? = null,
    val emailVerificationSent: Boolean = false,
    val isSendingEmailVerification: Boolean = false,
    val signOutError: String? = null,
) {
    val displayEmail: String
        get() = profile?.email?.trim()?.ifBlank { null } ?: sessionEmail.trim()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val backupExporter: PersonalBackupExporter,
    private val backupImporter: PersonalBackupImporter,
    private val observeTransactions: ObserveTransactionsUseCase,
    private val csvExporter: TransactionCsvExporter,
    observeSyncOverviewUseCase: ObserveSyncOverviewUseCase,
) : ViewModel() {

    private val isSavingProfile = MutableStateFlow(false)
    private val profileSaveSuccess = MutableStateFlow(false)
    private val profileError = MutableStateFlow<String?>(null)

    private val passwordError = MutableStateFlow<String?>(null)
    private val passwordSuccess = MutableStateFlow(false)
    private val isChangingPassword = MutableStateFlow(false)

    private val emailError = MutableStateFlow<String?>(null)
    private val emailVerificationSent = MutableStateFlow(false)
    private val isSendingEmailVerification = MutableStateFlow(false)

    private val signOutError = MutableStateFlow<String?>(null)
    private val backupScopeState = MutableStateFlow(BackupScope(0, 0))

    private data class FormOperationState(
        val isSavingProfile: Boolean,
        val profileSaveSuccess: Boolean,
        val profileError: String?,
        val passwordError: String?,
        val passwordSuccess: Boolean,
        val isChangingPassword: Boolean,
        val emailError: String?,
        val emailVerificationSent: Boolean,
        val isSendingEmailVerification: Boolean,
        val signOutError: String?,
    )

    private val formOperationsFlow = combine(
        combine(
            isSavingProfile,
            profileSaveSuccess,
            profileError,
            passwordError,
            passwordSuccess,
        ) { sp, pss, pe, pwe, pws ->
            FiveFlags(sp, pss, pe, pwe, pws)
        },
        combine(
            isChangingPassword,
            emailError,
            emailVerificationSent,
            isSendingEmailVerification,
            signOutError,
        ) { cp, ee, evs, sev, soe ->
            FiveFlags2(cp, ee, evs, sev, soe)
        },
    ) { f1, f2 ->
        FormOperationState(
            isSavingProfile = f1.sp,
            profileSaveSuccess = f1.pss,
            profileError = f1.pe,
            passwordError = f1.pwe,
            passwordSuccess = f1.pws,
            isChangingPassword = f2.cp,
            emailError = f2.ee,
            emailVerificationSent = f2.evs,
            isSendingEmailVerification = f2.sev,
            signOutError = f2.soe,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            authRepository.observeCurrentProfile(),
            authRepository.observeSession(),
            userSettingsRepository.observeSettings(),
        ) { profile, session, settings ->
            Triple(profile, session, settings)
        },
        observeSyncOverviewUseCase(),
        backupScopeState,
        formOperationsFlow,
    ) { pss, sync, scope, forms ->
        val (profile, session, settings) = pss
        val verificationStatus = session?.emailVerificationStatus
            ?: com.feniqo.mobile.domain.model.EmailVerificationStatus.UNKNOWN
        SettingsUiState(
            profile = profile,
            sessionEmail = session?.email.orEmpty(),
            settings = settings,
            syncOverview = sync,
            backupScope = scope,
            emailVerificationStatus = verificationStatus,
            isSavingProfile = forms.isSavingProfile,
            profileSaveSuccess = forms.profileSaveSuccess,
            profileError = forms.profileError,
            passwordError = forms.passwordError,
            passwordSuccess = forms.passwordSuccess,
            isChangingPassword = forms.isChangingPassword,
            emailError = forms.emailError,
            emailVerificationSent = forms.emailVerificationSent,
            isSendingEmailVerification = forms.isSendingEmailVerification,
            signOutError = forms.signOutError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshBackupScope()
    }

    fun refreshBackupScope() {
        viewModelScope.launch {
            runCatching {
                backupScopeState.value = backupExporter.calculateScope()
            }
        }
    }

    // --- Görünüm & Biçim Ayarları ---

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch {
            userSettingsRepository.updateTheme(theme)
        }
    }

    fun setMaskAmounts(mask: Boolean) {
        viewModelScope.launch {
            userSettingsRepository.updateMaskAmounts(mask)
        }
    }

    fun setDateFormat(format: DateFormatPreference) {
        viewModelScope.launch {
            userSettingsRepository.updateDateFormat(format)
        }
    }

    fun setNumberFormat(format: NumberFormatPreference) {
        viewModelScope.launch {
            userSettingsRepository.updateNumberFormat(format)
        }
    }

    fun setFirstDayOfWeek(firstDay: FirstDayOfWeekPreference) {
        viewModelScope.launch {
            userSettingsRepository.updateFirstDayOfWeek(firstDay)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            userSettingsRepository.updateLanguage(language)
        }
    }

    fun setDefaultCurrency(currency: Currency) {
        viewModelScope.launch {
            userSettingsRepository.updateCurrency(currency)
        }
    }

    // --- Bildirim Ayarları ---

    fun setHideAmountsInNotifications(hide: Boolean) {
        viewModelScope.launch {
            val current = uiState.value.settings.notifications
            userSettingsRepository.updateNotificationPreferences(current.copy(hideAmountsInNotifications = hide))
        }
    }

    fun updateNotificationPreferences(preferences: NotificationPreferences) {
        viewModelScope.launch {
            userSettingsRepository.updateNotificationPreferences(preferences)
        }
    }

    fun setRegion(region: String) {
        viewModelScope.launch {
            userSettingsRepository.updateRegion(region)
        }
    }

    // --- Profil & Kişisel Bilgiler ---

    fun savePersonalInfo(fullName: String) {
        if (isSavingProfile.value) return
        isSavingProfile.value = true
        viewModelScope.launch {
            profileError.value = null
            profileSaveSuccess.value = false
            try {
                val nameValid = com.feniqo.mobile.domain.validation.AuthValidationRules.validateFullName(fullName)
                if (nameValid is com.feniqo.mobile.domain.validation.AuthValidationResult.Invalid) {
                    profileError.value = "Görünen ad en az ${com.feniqo.mobile.domain.validation.AuthValidationRules.MIN_FULL_NAME_LENGTH} karakter olmalıdır."
                    return@launch
                }

                val normalized = com.feniqo.mobile.domain.validation.AuthValidationRules.normalizeFullName(fullName)
                when (authRepository.updateFullName(normalized)) {
                    is RepositoryResult.Success -> profileSaveSuccess.value = true
                    is RepositoryResult.Failure -> profileError.value = "Kişisel bilgiler kaydedilemedi. Bağlantınızı ve eşitleme durumunu kontrol edip tekrar deneyin."
                }
            } finally {
                isSavingProfile.value = false
            }
        }
    }

    fun clearProfileStatus() {
        profileError.value = null
        profileSaveSuccess.value = false
    }

    // --- Parola Değiştirme ---

    fun changePassword(oldPass: String, newPass: String, confirmPass: String) {
        if (isChangingPassword.value) return
        viewModelScope.launch {
            isChangingPassword.value = true
            passwordError.value = null
            passwordSuccess.value = false

            try {
                val oldValid = com.feniqo.mobile.domain.validation.AuthValidationRules.validateLoginPassword(oldPass)
                if (oldValid is com.feniqo.mobile.domain.validation.AuthValidationResult.Invalid) {
                    passwordError.value = "Mevcut parolanızı girmelisiniz."
                    return@launch
                }

                val newValid = com.feniqo.mobile.domain.validation.AuthValidationRules.validateNewPassword(newPass)
                if (newValid is com.feniqo.mobile.domain.validation.AuthValidationResult.Invalid) {
                    passwordError.value = when (newValid.error) {
                        com.feniqo.mobile.domain.validation.AuthValidationError.PASSWORD_REQUIRED -> "Yeni parola girmelisiniz."
                        com.feniqo.mobile.domain.validation.AuthValidationError.NEW_PASSWORD_TOO_SHORT ->
                            "Yeni parola en az ${com.feniqo.mobile.domain.validation.AuthValidationRules.MIN_PASSWORD_LENGTH} karakter olmalıdır."
                        else -> "Geçersiz yeni parola."
                    }
                    return@launch
                }

                val confirmValid = com.feniqo.mobile.domain.validation.AuthValidationRules.validateConfirmPassword(newPass, confirmPass)
                if (confirmValid is com.feniqo.mobile.domain.validation.AuthValidationResult.Invalid) {
                    passwordError.value = "Yeni parolalar birbiriyle eşleşmiyor."
                    return@launch
                }

                if (newPass == oldPass) {
                    passwordError.value = "Yeni parolanız mevcut parolanızdan farklı olmalıdır."
                    return@launch
                }

                when (val result = authRepository.changePassword(oldPass, newPass)) {
                    is RepositoryResult.Success -> {
                        passwordSuccess.value = true
                        passwordError.value = null
                    }
                    is RepositoryResult.Failure -> {
                        passwordSuccess.value = false
                        passwordError.value = when (result.error.code) {
                            "auth_invalid_credentials" -> "Mevcut parolanız hatalı."
                            "auth_password_unchanged" -> "Yeni parolanız mevcut parolanızdan farklı olmalıdır."
                            "auth_invalid_input" -> "Yeni parola güvenlik koşullarını karşılamıyor."
                            "auth_session_expired" -> "Oturumunuzun süresi doldu. Lütfen yeniden giriş yapın."
                            "auth_reauthentication_required" -> "Güvenliğiniz için yeniden giriş yapmanız gerekiyor."
                            "network_unavailable", "auth_rate_limited" -> "Bağlantı kurulamadı. Lütfen biraz sonra tekrar deneyin."
                            else -> "Parola güncellenemedi. Lütfen tekrar deneyin."
                        }
                    }
                }
            } finally {
                isChangingPassword.value = false
            }
        }
    }

    fun clearPasswordStatus() {
        passwordError.value = null
        passwordSuccess.value = false
    }

    // --- E-posta Değiştirme ---

    fun requestEmailChange(newEmail: String, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch {
            isSendingEmailVerification.value = true
            emailError.value = null
            emailVerificationSent.value = false

            val emailValid = com.feniqo.mobile.domain.validation.AuthValidationRules.validateEmail(newEmail)
            if (emailValid is com.feniqo.mobile.domain.validation.AuthValidationResult.Invalid) {
                emailError.value = when (emailValid.error) {
                    com.feniqo.mobile.domain.validation.AuthValidationError.EMAIL_REQUIRED -> "E-posta adresi girmelisiniz."
                    com.feniqo.mobile.domain.validation.AuthValidationError.EMAIL_INVALID -> "Geçerli bir e-posta adresi girin."
                    else -> "Geçersiz e-posta adresi."
                }
                isSendingEmailVerification.value = false
                return@launch
            }

            // Kapsam C: Auth sağlayıcısının gerçek e-posta doğrulama bağlantısı göndermesi gerekir.
            emailVerificationSent.value = true
            isSendingEmailVerification.value = false
            onSuccess?.invoke()
        }
    }

    fun refreshEmailVerificationStatus() {
        viewModelScope.launch {
            authRepository.refreshSession()
        }
    }

    fun resendEmailVerification(email: String) {
        if (isSendingEmailVerification.value) return
        viewModelScope.launch {
            isSendingEmailVerification.value = true
            emailError.value = null
            emailVerificationSent.value = false

            val emailValid = com.feniqo.mobile.domain.validation.AuthValidationRules.validateEmail(email)
            if (emailValid is com.feniqo.mobile.domain.validation.AuthValidationResult.Invalid) {
                emailError.value = "Geçerli bir e-posta adresi bulunamadı."
                isSendingEmailVerification.value = false
                return@launch
            }

            try {
                when (val result = authRepository.resendEmailConfirmation(email.trim())) {
                    is RepositoryResult.Success -> {
                        emailVerificationSent.value = true
                        emailError.value = null
                    }
                    is RepositoryResult.Failure -> {
                        emailVerificationSent.value = false
                        emailError.value = when (result.error.code) {
                            "auth_rate_limited" -> "Lütfen tekrar göndermeden önce biraz bekleyin."
                            "network_unavailable" -> "İnternet bağlantınızı kontrol edin."
                            else -> "Doğrulama e-postası gönderilemedi. Lütfen tekrar deneyin."
                        }
                    }
                }
            } finally {
                isSendingEmailVerification.value = false
            }
        }
    }

    fun clearEmailStatus() {
        emailError.value = null
        emailVerificationSent.value = false
    }

    // --- Çıkış ---

    fun signOut(onComplete: () -> Unit) {
        viewModelScope.launch {
            signOutError.value = null
            when (authRepository.signOut()) {
                is RepositoryResult.Success -> onComplete()
                is RepositoryResult.Failure -> signOutError.value = "Çıkış yapılamadı. Lütfen tekrar deneyin."
            }
        }
    }

    // --- Veri Yönetimi & Dışa Aktarma ---

    suspend fun buildCsv(): String = csvExporter.export(observeTransactions().first())

    suspend fun exportJsonBackup(): BackupExportResult = runCatching {
        val scope = backupExporter.calculateScope()
        val json = backupExporter.export()
        BackupExportResult.Success(json = json, scope = scope)
    }.getOrElse {
        BackupExportResult.Failure(it.message ?: "export_failed")
    }

    fun previewBackup(raw: String): BackupPreviewResult = when (val result = FeniqoBackupCodec.decode(raw)) {
        is BackupDecodeResult.Invalid -> BackupPreviewResult.Invalid(result.reason)
        is BackupDecodeResult.Valid -> BackupPreviewResult.Valid(
            categoryCount = result.backup.categories.size,
            transactionCount = result.backup.transactions.size,
        )
    }

    suspend fun importBackup(raw: String): BackupImportResult = backupImporter.import(raw)
}

private data class FiveFlags(
    val sp: Boolean,
    val pss: Boolean,
    val pe: String?,
    val pwe: String?,
    val pws: Boolean,
)

private data class FiveFlags2(
    val cp: Boolean,
    val ee: String?,
    val evs: Boolean,
    val sev: Boolean,
    val soe: String?,
)
