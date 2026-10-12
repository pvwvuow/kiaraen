package com.kiaraen.app.data.remote

import android.content.Context
import com.kiaraen.app.BuildConfig
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SettingsCodeVerifierCache
import io.github.jan.supabase.gotrue.SettingsSessionManager
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.android.Android

/**
 * Single Supabase client. Only the anon (publishable) key is used here —
 * real security is enforced server-side by RLS (supabase/migrations/00003).
 * The service_role key NEVER reaches this file or the repo.
 *
 * The app is BUILDABLE and RUNNABLE without keys: when SUPABASE_URL is empty
 * the app switches to guest/offline mode instead of crashing:
 *   - [isConfigured] is the single switch every network path checks first
 *   - [client] then points to a harmless placeholder URL that is never called
 *
 * ⚠️ Session storage: supabase-kt 2.x's default SettingsSessionManager builds
 * a no-arg multiplatform `Settings()` which cannot obtain an Android Context
 * inside a plain Kotlin object and throws IllegalStateException at client
 * creation (= crash on every launch). We therefore wire an explicit
 * SharedPreferences-backed manager with our application context — the exact
 * fix recommended by supabase-kt's "Session Saving" wiki page.
 *
 * ⚠️ HTTP engine: set explicitly instead of ktor's ServiceLoader discovery,
 * which is fragile in R8-minified release builds.
 */
object SupabaseProvider {

    @Volatile private var appContext: Context? = null

    /** Must run before anything touches [client] (called in Application.onCreate). */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val url: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isConfigured: Boolean = url.startsWith("http") && anonKey.isNotBlank()

    val client: SupabaseClient by lazy {
        val ctx = appContext
            ?: error("SupabaseProvider.init(context) was not called before client access")
        createSupabaseClient(
            supabaseUrl = if (isConfigured) url else "https://placeholder.supabase.invalid",
            supabaseKey = if (isConfigured) anonKey else "placeholder-anon-key",
        ) {
            httpEngine = Android.create()
            install(Auth) {
                // both the session manager AND the PKCE code-verifier cache
                // default to a no-arg Settings() that cannot obtain a Context
                // on Android — wire both explicitly to SharedPreferences.
                val settings = SharedPreferencesSettings(
                    ctx.getSharedPreferences("kiara_session", Context.MODE_PRIVATE),
                )
                sessionManager = SettingsSessionManager(settings)
                codeVerifierCache = SettingsCodeVerifierCache(settings)
            }
            install(Postgrest)
            install(Storage)
        }
    }
}
