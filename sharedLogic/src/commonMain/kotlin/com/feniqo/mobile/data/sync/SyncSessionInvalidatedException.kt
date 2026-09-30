package com.feniqo.mobile.data.sync

import com.feniqo.mobile.domain.model.AppError
import kotlinx.coroutines.CancellationException

internal class SyncSessionInvalidatedException(
    val authError: AppError.Authentication = AppError.Authentication("sync.session_invalidated"),
) : CancellationException(authError.code)
