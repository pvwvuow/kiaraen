package com.kiaraen.app.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
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
        client.auth.loginWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        client.auth.signOut()
    }

    suspend fun currentUserId(): String? =
        client.auth.currentSessionOrNull()?.user?.id

    suspend fun isSignedIn(): Boolean = currentUserId() != null
}
