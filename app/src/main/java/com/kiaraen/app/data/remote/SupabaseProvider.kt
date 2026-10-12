package com.kiaraen.app.data.remote

import com.kiaraen.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.GoTrue
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.android.Android

/**
 * Single Supabase client. Only the anon (publishable) key is used here —
 * real security is enforced server-side by RLS (supabase/migrations/00003).
 * The service_role key NEVER reaches this file or the repo.
 */
object SupabaseProvider {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(GoTrue)
        install(Postgrest)
        install(Storage)
        // ktor Android engine is auto-detected from the classpath
    }
}
