package com.kiaraen.app.data.repository

import com.kiaraen.app.data.remote.AuthRemoteDataSource
import com.kiaraen.app.data.remote.SupabaseProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth

/** Raised when an auth action is attempted while no Supabase keys are wired in. */
class NotConfiguredException :
    IllegalStateException("supabase-not-configured")

/**
 * Thin auth facade. The ViewModel layer sees only this class, so swapping the
 * backend (or adding anonymous sessions later) never touches the UI.
 * Every network path first checks [SupabaseProvider.isConfigured] so the app
 * stays fully usable (guest/offline mode) in key-less builds.
 */
class AuthRepository(private val client: SupabaseClient) {

    private val remote = AuthRemoteDataSource(client)

    /** Cold-start only: reading sessionStatus touches the client lazily. */
    val sessionChanges by lazy { client.auth.sessionStatus }

    suspend fun restoreSession(): String? = currentUserId()

    suspend fun signUp(email: String, password: String, displayName: String?): Result<Unit> =
        guarded { remote.signUp(email, password, displayName) }

    suspend fun signIn(email: String, password: String): Result<Unit> =
        guarded { remote.signIn(email, password) }

    suspend fun resetPassword(email: String): Result<Unit> =
        guarded { remote.resetPassword(email) }

    suspend fun signOut(): Result<Unit> = guarded { remote.signOut() }

    suspend fun currentUserId(): String? =
        if (SupabaseProvider.isConfigured) remote.currentUserId() else null

    suspend fun userEmail(): String? =
        if (SupabaseProvider.isConfigured) remote.userEmail() else null

    private inline fun guarded(block: () -> Unit): Result<Unit> =
        if (!SupabaseProvider.isConfigured) Result.failure(NotConfiguredException())
        else runCatching(block)
}
