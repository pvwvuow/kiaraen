package com.kiaraen.app.data.repository

import com.kiaraen.app.data.remote.AuthRemoteDataSource
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth

/**
 * Thin auth facade. The ViewModel layer sees only this class, so swapping the
 * backend (or adding anonymous sessions later) never touches the UI.
 */
class AuthRepository(private val client: SupabaseClient) {

    private val remote = AuthRemoteDataSource(client)

    val sessionChanges = client.auth.sessionStatus

    suspend fun restoreSession(): String? = remote.currentUserId()

    suspend fun signUp(email: String, password: String, displayName: String?): Result<Unit> =
        runCatching { remote.signUp(email, password, displayName) }

    suspend fun signIn(email: String, password: String): Result<Unit> =
        runCatching { remote.signIn(email, password) }

    suspend fun signOut(): Result<Unit> = runCatching { remote.signOut() }

    suspend fun currentUserId(): String? = remote.currentUserId()
}
