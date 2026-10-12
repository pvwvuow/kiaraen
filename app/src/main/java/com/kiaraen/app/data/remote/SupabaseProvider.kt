package com.kiaraen.app.data.remote

import com.kiaraen.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Single Supabase client. Only the anon (publishable) key is used here —
 * real security is enforced server-side by RLS (supabase/migrations/00003).
 * The service_role key NEVER reaches this file or the repo.
 *
 * The app is BUILDABLE and RUNNABLE without keys: when SUPABASE_URL is empty
 * the app switches to guest/offline mode instead of crashing:
 *   - [isConfigured] is the single switch every network path checks first
 *   - [client] then points to a harmless placeholder URL that is never called
 */
object SupabaseProvider {

    val url: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isConfigured: Boolean = url.startsWith("http") && anonKey.isNotBlank()

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = if (isConfigured) url else "https://placeholder.supabase.invalid",
            supabaseKey = if (isConfigured) anonKey else "placeholder-anon-key",
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
            // ktor Android engine is auto-detected from the classpath
        }
    }
}
