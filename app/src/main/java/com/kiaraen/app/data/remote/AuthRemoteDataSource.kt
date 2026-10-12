package com.kiaraen.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Auth via Supabase GoTrue. Passwords never touch our database — they are
 * handled (hashed) entirely by the Supabase Auth service.
 */
class AuthRemoteDataSource(private val client: SupabaseClient) {

    suspend fun signUp(email: String, password: String, displayName: String?) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            if (!displayName.isNullOrBlank()) {
                data = buildJsonObject { put("display_name", displayName) }
            }
        }
    }

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /** Sends the "reset your password" email; the user finishes the flow in the browser. */
    suspend fun resetPassword(email: String) {
        client.auth.resetPasswordForEmail(email)
    }

    /**
     * Server sign-out needs the network. Offline it used to fail and keep the
     * session → the auth gate restored it and the user could never leave the
     * account. Fall back to dropping the local session.
     */
    suspend fun signOut() {
        try {
            client.auth.signOut()
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            client.auth.clearSession()
        }
    }

    suspend fun currentUserId(): String? =
        client.auth.currentSessionOrNull()?.user?.id
            ?: SupabaseProvider.storedSession()?.user?.id

    suspend fun userEmail(): String? =
        client.auth.currentSessionOrNull()?.user?.email
            ?: SupabaseProvider.storedSession()?.user?.email

    suspend fun isSignedIn(): Boolean = currentUserId() != null
}
