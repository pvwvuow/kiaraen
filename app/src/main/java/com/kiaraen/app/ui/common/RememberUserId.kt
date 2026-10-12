package com.kiaraen.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import com.kiaraen.app.ui.common.LocalAppContainer

/**
 * Resolves the progress owner id: the signed-in user, or the local-mode id
 * (which is migrated to the real account on first login — see ProgressDao).
 */
const val LOCAL_USER_ID = "local"

@Composable
fun rememberUserId(): String {
    val container = LocalAppContainer.current
    return produceState(LOCAL_USER_ID) {
        value = container.authRepository.currentUserId() ?: LOCAL_USER_ID
    }.value
}
